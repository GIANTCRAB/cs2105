package assignment2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * Receiver class
 * <p>
 * 1. Listen for UDP messages
 * 2. Establish reliable connection using UDP
 * 3. Receive data through established connection
 * 4. Write to STOUT
 */
public class Bob {
    private final DatagramSocket socket;
    private final int maxHeaderSizePerPacket = 18;
    private final int maxDataSizePerPacket = 46;
    private final int maxSequenceNumber = 29999;
    private final int sequenceNumberSize = maxSequenceNumber + 1;

    private final int HEADER_SEQUENCE_NUMBER_SIZE = 4;
    private final int HEADER_ACK_FLAG_SIZE = 2;
    private final int HEADER_LEN_SIZE = 4;
    private final int HEADER_CHECKSUM_SIZE = 8;

    public static void main(String[] args) throws IOException {
        new Bob(Integer.parseInt(args[0]));
    }

    public Bob(int portNumber) throws IOException {
        socket = new DatagramSocket(portNumber);
        this.run();
    }

    public void run() throws IOException {
        AckPacket previousAckPacket = null;
        boolean running = true;

        while (running) {
            final byte[] receivedBuffer = new byte[maxHeaderSizePerPacket + maxDataSizePerPacket];
            final DatagramPacket receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);
            socket.receive(receivedPacket);

            // Get sender details
            final InetAddress senderAddress = receivedPacket.getAddress();
            final int senderPort = receivedPacket.getPort();

            try {
                final ReceivedDataPacket receivedDataPacket = new ReceivedDataPacket(previousAckPacket, receivedBuffer);
                // Write received data to STOUT
                System.out.write(receivedDataPacket.getParsedData());

                // Reply with ack message
                final AckPacket ackPacket = new AckPacket(receivedDataPacket);
                final byte[] ackPacketData = ackPacket.toData();
                previousAckPacket = ackPacket;

                final DatagramPacket ackReplyPacket = new DatagramPacket(ackPacketData, maxHeaderSizePerPacket, senderAddress, senderPort);
                socket.send(ackReplyPacket);
            } catch (ReceivedDataPacket.PacketCorrupted | ReceivedDataPacket.PacketDuplicate packetError) {
                // Check if previous ack packet exists
                // If does exists, send. If not, then wait for timeout.
                if (previousAckPacket != null) {
                    final DatagramPacket ackReplyPacket = new DatagramPacket(previousAckPacket.toData(), maxHeaderSizePerPacket, senderAddress, senderPort);
                    socket.send(ackReplyPacket);
                }
            }

        }
        socket.close();
    }

    private class ReceivedDataPacket {
        private final char expectedAckFlag = 0;
        private final int duplicateSequenceNumber;

        private final byte[] receivedData;
        private int sequenceNumber;
        private char ackFlag;
        private int dataLength;
        private long checkSum;
        private final byte[] rawData = new byte[maxDataSizePerPacket];
        private byte[] parsedData;

        public ReceivedDataPacket(AckPacket ackPacket, byte[] receivedData) throws PacketCorrupted, PacketDuplicate {
            if (ackPacket == null) {
                this.duplicateSequenceNumber = -1;
            } else {
                this.duplicateSequenceNumber = ackPacket.getSequenceNumber();
            }
            this.receivedData = receivedData;
            this.parsePacket();
        }

        public byte[] getParsedData() {
            return parsedData;
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

        private void parsePacket() throws PacketCorrupted, PacketDuplicate {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(this.receivedData);

            try {
                // Handle header data
                final byte[] sequenceNumberBytes = new byte[HEADER_SEQUENCE_NUMBER_SIZE];
                byteBuffer.get(sequenceNumberBytes, 0, HEADER_SEQUENCE_NUMBER_SIZE);
                this.sequenceNumber = ByteBuffer.wrap(sequenceNumberBytes).getInt();
                if (this.duplicateSequenceNumber != -1 && this.duplicateSequenceNumber == this.sequenceNumber) {
                    throw new PacketDuplicate();
                }

                final byte[] ackFlagBytes = new byte[HEADER_ACK_FLAG_SIZE];
                byteBuffer.get(ackFlagBytes, 0, HEADER_ACK_FLAG_SIZE);
                this.ackFlag = ByteBuffer.wrap(ackFlagBytes).getChar();
                if (this.ackFlag != expectedAckFlag) {
                    throw new PacketCorrupted();
                }

                final byte[] dataLenBytes = new byte[HEADER_LEN_SIZE];
                byteBuffer.get(dataLenBytes, 0, HEADER_LEN_SIZE);
                this.dataLength = ByteBuffer.wrap(dataLenBytes).getInt();

                final byte[] checksumBytes = new byte[HEADER_CHECKSUM_SIZE];
                byteBuffer.get(checksumBytes, 0, HEADER_CHECKSUM_SIZE);
                this.checkSum = ByteBuffer.wrap(checksumBytes).getLong();

                // Handle content data
                byteBuffer.get(this.rawData, 0, maxDataSizePerPacket);

                if (this.dataLength > 0) {
                    // There must always be data
                    // Assume dataLength is correct first
                    final byte[] extractedData = new byte[this.dataLength];
                    ByteBuffer.wrap(extractedData).put(rawData, 0, this.dataLength);
                    // Verify checksum
                    final CRC32 checksum = new CRC32();
                    checksum.update(extractedData);
                    if (this.checkSum == checksum.getValue()) {
                        // Data is correct
                        this.parsedData = extractedData;
                    } else {
                        throw new PacketCorrupted();
                    }
                } else {
                    throw new PacketCorrupted();
                }
            } catch (BufferUnderflowException | BufferOverflowException | IndexOutOfBoundsException e) {
                throw new PacketCorrupted();
            }
        }

        private class PacketCorrupted extends Exception {
        }

        private class PacketDuplicate extends Exception {
        }
    }

    private class AckPacket {
        private final ReceivedDataPacket receivedDataPacket;
        private final char ackFlag = 1;

        public AckPacket(ReceivedDataPacket receivedDataPacket) {
            this.receivedDataPacket = receivedDataPacket;
        }

        private int getSequenceNumber() {
            return this.receivedDataPacket.getNextSequenceNumber();
        }

        public byte[] toData() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(maxHeaderSizePerPacket);

            byte[] sequenceNumberBytes = ByteBuffer.allocate(HEADER_SEQUENCE_NUMBER_SIZE).putInt(this.getSequenceNumber()).array();
            byteBuffer.put(sequenceNumberBytes);

            byte[] ackFlagBytes = ByteBuffer.allocate(HEADER_ACK_FLAG_SIZE).putChar(ackFlag).array();
            byteBuffer.put(ackFlagBytes);

            return byteBuffer.array();
        }
    }
}
