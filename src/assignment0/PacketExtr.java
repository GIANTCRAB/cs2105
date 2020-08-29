import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;

public class PacketExtr {

    public static void main(String[] args) throws IOException {
        final int maxBytesToReadPerBuff = 4; // One character is 2 bytes long
        final int maxBytesToWritePerBuff = 12;
        final byte[] dataToSearchFor = "B".getBytes();

        ReadableByteChannel in = Channels.newChannel(System.in);

        final int totalBytes = System.in.available();
        int nBytesToRead = totalBytes;
        StringBuilder fullPacketString = new StringBuilder();

        while (nBytesToRead > 0) {
            nBytesToRead -= maxBytesToReadPerBuff;
            int bytesToRead = maxBytesToReadPerBuff;
            if (nBytesToRead < 0) {
                // negative
                bytesToRead += nBytesToRead;
            }
            final byte[] bytes = new byte[bytesToRead];
            System.in.readNBytes(bytes, 0, bytesToRead);
            final String info = new String(bytes);
            fullPacketString.append(info);

            final int searchResult = find(bytes, dataToSearchFor);
            if (searchResult != -1) {
                int remainingBytesLen = 0;
                if(bytes.length > searchResult + dataToSearchFor.length) {
                    final byte[] remainingBytes = Arrays.copyOfRange(bytes, searchResult + dataToSearchFor.length, bytes.length);
                    System.out.write(remainingBytes);
                    System.out.flush();

                    remainingBytesLen = remainingBytes.length;
                }


                // TODO: verify splitting
                final String[] firstSplit = fullPacketString.toString().split("B");
                final int packetSize = Integer.parseInt(firstSplit[0].replaceAll("[^0-9]", ""));

                // Read bytes and flush them in a small manner
                int packetBytesToRead = packetSize - remainingBytesLen;
                byte[] fullPacket = new byte[0];
                while (packetBytesToRead > 0) {
                    packetBytesToRead -= maxBytesToWritePerBuff;
                    int bytesToReadForSeg = maxBytesToWritePerBuff;
                    if (packetBytesToRead < 0) {
                        // negative value
                        bytesToReadForSeg += packetBytesToRead;
                    }
                    final byte[] segmentPacketBytes = new byte[bytesToReadForSeg];
                    System.in.readNBytes(segmentPacketBytes, 0, bytesToReadForSeg);

                    System.out.write(segmentPacketBytes);
                    System.out.flush();
                }

                fullPacketString = new StringBuilder();
            }
        }
    }

    private static int find(byte[] buffer, byte[] key) {
        for (int i = 0; i <= buffer.length - key.length; i++) {
            int j = 0;
            while (j < key.length && buffer[i + j] == key[j]) {
                j++;
            }
            if (j == key.length) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
