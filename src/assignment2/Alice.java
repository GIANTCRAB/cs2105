package assignment2;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * Sender class.
 * <p>
 * 1. Read from STDIN
 * 2. Form reliable connection using UDP
 * 3. Send data through established connection
 */
public class Alice {
    private DatagramSocket socket;
    private InetAddress address;
    private final int portNumber;
    private final int maxHeaderSizePerPacket = 18;
    private final int maxDataSizePerPacket = 46;
    private final int timeout = 50;
    private final int minSequenceNumber = 0;
    private final int maxSequenceNumber = 29999;
    private final int sequenceNumberSize = maxSequenceNumber + 1;

    private final int HEADER_SEQUENCE_NUMBER_SIZE = 4;
    private final int HEADER_ACK_FLAG_SIZE = 2;
    private final int HEADER_LEN_SIZE = 4;
    private final int HEADER_CHECKSUM_SIZE = 8;

    private final Queue<DataPacket> packetQueue = new LinkedList<>();

    public static void main(String[] args) throws IOException {
        new Alice(Integer.parseInt(args[0]));
    }

    public Alice(int portNumber) throws IOException {
        this.socket = new DatagramSocket();
        this.address = InetAddress.getByName("localhost");
        this.portNumber = portNumber;

        // Start sender service that reads from a queue on a separate thread
        new PacketProcessor(this.packetQueue).start();

        // Start reading service that pipes data to the queue on the main thread
        new DataReader(System.in, this.packetQueue).readStream();
    }

    private class DataReader {
        private final InputStream inputStream;
        private final Queue<DataPacket> packetQueue;

        public DataReader(InputStream inputStream, Queue<DataPacket> packetQueue) {
            this.inputStream = inputStream;
            this.packetQueue = packetQueue;
        }

        public void readStream() throws IOException {
            int currByte;
            byte currData;
            byte[] payload = new byte[maxDataSizePerPacket];
            int payloadSize = 0;

            while ((currByte = inputStream.read()) != -1) {
                currData = (byte) currByte;
                // Put into payload
                if (payloadSize < maxDataSizePerPacket) {
                    payload[payloadSize] = currData;
                    payloadSize++;
                } else {
                    this.packetQueue.add(new DataPacket(generateInitialSequenceNumber(), payload));
                    // Reset data
                    payloadSize = 0;
                    payload = new byte[maxDataSizePerPacket];
                    // Add the data into the new payload
                    payload[payloadSize] = currData;
                    payloadSize++;
                }
            }

            // stream has ended, flush out remaining data
            if (payloadSize > 0) {
                // repackage payload into proper size
                final byte[] repackagedPayload = new byte[payloadSize];
                ByteBuffer.wrap(repackagedPayload).put(payload, 0, payloadSize);
                this.packetQueue.add(new DataPacket(generateInitialSequenceNumber(), repackagedPayload));
            }
        }
    }

    private class PacketProcessor extends Thread {
        private final Queue<DataPacket> packetQueue;

        public PacketProcessor(Queue<DataPacket> packetQueue) {
            this.packetQueue = packetQueue;
        }

        public void run() {
            // Read from packet queue
            while (true) {
                while (!this.packetQueue.isEmpty()) {
                    final DataPacket dataPacket = this.packetQueue.poll();

                    try {
                        sendPacket(dataPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void sendPacket(DataPacket dataPacket) throws IOException {
        final byte[] dataPacketData = dataPacket.toData();
        boolean messageNotAcknowledged = true;

        while (messageNotAcknowledged) {
            final DatagramPacket sentPacket = new DatagramPacket(dataPacketData, maxHeaderSizePerPacket + maxDataSizePerPacket, address, this.portNumber);
            socket.send(sentPacket);

            // Receiving reply
            socket.setSoTimeout(timeout);
            final byte[] ackData = new byte[maxHeaderSizePerPacket];
            final DatagramPacket receivedPacket = new DatagramPacket(ackData, maxHeaderSizePerPacket);
            try {
                socket.receive(receivedPacket);

                final AckPacketVerifier ackPacketVerifier = new AckPacketVerifier(dataPacket, ackData);

                messageNotAcknowledged = !ackPacketVerifier.isPacketAcknowledged(); // Get the inverse
            } catch (SocketException | SocketTimeoutException socketException) {
                // timeout
            }
        }
    }

    // Random initial sequence number allows easier detection of packet corruption
    private int generateInitialSequenceNumber() {
        return new Random().nextInt((this.maxSequenceNumber - this.minSequenceNumber) + 1) + this.minSequenceNumber;
    }

    private class DataPacket {
        private final int sequenceNumber;
        private final char ackFlag = 0;
        private final int dataLength;
        private final long checkSum;
        private final byte[] data;

        public DataPacket(int sequenceNumber, byte[] data) {
            this.sequenceNumber = sequenceNumber;
            this.data = data;
            this.dataLength = this.data.length;
            this.checkSum = this.getChecksum();
        }

        //TODO: verify logic
        public int getNextSequenceNumber() {
            final int nextSequenceNumber = this.sequenceNumber + this.dataLength;

            if (nextSequenceNumber > maxSequenceNumber) {
                // Wrap around
                return nextSequenceNumber - sequenceNumberSize;
            }

            // No wrap needed
            return nextSequenceNumber;
        }

        private long getChecksum() {
            CRC32 checksum = new CRC32();
            checksum.update(this.data);
            return checksum.getValue();
        }

        public byte[] toData() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(maxHeaderSizePerPacket + maxDataSizePerPacket);

            // Handle header data
            final byte[] sequenceNumberBytes = ByteBuffer.allocate(HEADER_SEQUENCE_NUMBER_SIZE).putInt(sequenceNumber).array();
            byteBuffer.put(sequenceNumberBytes, 0, HEADER_SEQUENCE_NUMBER_SIZE);

            final byte[] ackFlagBytes = ByteBuffer.allocate(HEADER_ACK_FLAG_SIZE).putChar(ackFlag).array();
            byteBuffer.put(ackFlagBytes);

            final byte[] dataLenBytes = ByteBuffer.allocate(HEADER_LEN_SIZE).putInt(dataLength).array();
            byteBuffer.put(dataLenBytes);

            final byte[] checksumBytes = ByteBuffer.allocate(HEADER_CHECKSUM_SIZE).putLong(checkSum).array();
            byteBuffer.put(checksumBytes);

            // Handle content data
            final byte[] dataBytes = ByteBuffer.allocate(dataLength).put(data).array();
            byteBuffer.put(dataBytes);

            return byteBuffer.array();
        }
    }

    /**
     * Verify if the ack packet has the correct sequence number + has ack flag
     * <p>
     * 3 cases:
     * 1. Correct - sequence number has been incremented by dataPacket size. (continue)
     * 2. Resend previous - sequence number has NO incrementation, is exactly the same. (resend)
     * 3. Corrupt - sequence number does not satisfy the above 2 cases, most likely corrupt. (resend)
     */
    private class AckPacketVerifier {
        private final DataPacket dataPacket;
        private final byte[] ackPacketData;
        private final char ackFlag = 1;

        public AckPacketVerifier(DataPacket dataPacket, byte[] ackPacketData) {
            this.dataPacket = dataPacket;
            this.ackPacketData = ackPacketData;
        }

        /**
         * If both ack number and ack flag are correct, then packet should be valid
         *
         * @return
         */
        public boolean isPacketAcknowledged() {

            try {
                final PacketHeader packetHeader = new PacketHeader();

                // First part is ack number
                // Second part is ack flag
                return packetHeader.getSequenceNumber() == this.dataPacket.getNextSequenceNumber() && packetHeader.getAckFlag() == this.ackFlag;
            } catch (PacketHeader.PacketHeaderCorrupted packetHeaderCorrupted) {
                return false;
            }
        }

        private class PacketHeader {
            private int sequenceNumber;
            private char ackFlag;

            public PacketHeader() throws PacketHeaderCorrupted {
                try {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(ackPacketData);
                    final byte[] sequenceNumberBuffer = new byte[HEADER_SEQUENCE_NUMBER_SIZE];
                    byteBuffer.get(sequenceNumberBuffer, 0, HEADER_SEQUENCE_NUMBER_SIZE);
                    this.sequenceNumber = ByteBuffer.wrap(sequenceNumberBuffer).getInt();

                    final byte[] ackFlagBuffer = new byte[HEADER_ACK_FLAG_SIZE];
                    byteBuffer.get(ackFlagBuffer, 0, HEADER_ACK_FLAG_SIZE);
                    this.ackFlag = ByteBuffer.wrap(ackFlagBuffer).getChar();
                } catch (BufferUnderflowException | BufferOverflowException | IndexOutOfBoundsException e) {
                    throw new PacketHeaderCorrupted();
                }
            }

            public int getSequenceNumber() {
                return sequenceNumber;
            }

            public char getAckFlag() {
                return this.ackFlag;
            }

            private class PacketHeaderCorrupted extends Exception {
            }
        }
    }
}
