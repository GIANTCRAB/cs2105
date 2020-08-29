package assignment0;

public class PacketExtr {
    public static void main(String[] args) throws Exception {
        int currByte;
        char currChar;
        char headerBoundaryChar = 'B';
        String header = "";
        int payloadSizeBytes = 0;
        int payloadIndex = 0;
        byte[] payload = new byte[payloadSizeBytes];

        // Read one byte at a time from stdin
        while ((currByte = System.in.read()) != -1) { // not reached end of stream
            if (0 == payloadSizeBytes) {
                // Get header
                currChar = (char) currByte;
                if (currChar != headerBoundaryChar) {
                    header += String.valueOf(currChar);
                    continue;
                }

                // Get payload size in bytes from header
                payloadSizeBytes = Integer.parseInt(header.replaceAll("[^0-9]", ""));
                payload = new byte[payloadSizeBytes];
                payloadIndex = 0;
                continue; // currChar will be headerBoundaryChar, hence go to next iteration to read 1st payload byte
            }

            // Read 1 byte of payload
            payload[payloadIndex] = (byte) currByte;
            payloadIndex++;
            if (payloadIndex >= payloadSizeBytes) { // finished reading payload
                System.out.write(payload);
                payloadSizeBytes = 0;
                header = "";
            }
        }
    }
}