package assignment1;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {
    private ServerSocket serverSocket;
    private DataOutputStream out;
    private DataInputStream in;
    private final int portNumber;

    private static final HashMap<String, byte[]> kvStore = new HashMap<>();
    private static final Map<String, Integer> counterStore = new HashMap<>();

    public static void main(String[] args) throws IOException {
        final WebServer webServer = new WebServer(Integer.parseInt(args[0]));
        webServer.start();
    }

    public WebServer(int portNumber) {
        this.portNumber = portNumber;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(this.portNumber);
        while (true) {
            final Socket clientSocket = serverSocket.accept();
            clientSocket.setKeepAlive(true);
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());
            readPacket();
            in.close();
            out.close();
            clientSocket.close();
        }
    }

    public void stop() throws IOException {
        serverSocket.close();
    }

    public void readPacket() throws IOException {
        int currByte;
        char currChar;
        // 2 continuous newlines = end of header
        final char headerBoundaryChar = ' ';
        int continuousNewLineCount = 0;
        final int maxContinuousNewLineCount = 2;
        boolean headerComplete = false;
        String header = "";
        final String contentLengthHeaderName = "content-length";
        int contentLengthHeaderPosition = -1;
        List<String> headerInfo = new ArrayList<>();
        int payloadSizeBytes = 0;
        int payloadIndex = 0;
        byte[] payload = new byte[payloadSizeBytes];
        // read data only if it is a POST
        String storeType;
        String keyToWrite = "";

        while ((currByte = in.read()) != -1) { // not reached end of stream
            if (0 == payloadSizeBytes) {
                // Get header
                currChar = (char) currByte;
                if (!headerComplete) {
                    if (currChar != headerBoundaryChar) {
                        header += String.valueOf(currChar);
                        continuousNewLineCount = 0; // Reset continuous new line data
                        continue;
                    } else {
                        // One part of the header is completed
                        continuousNewLineCount++;
                        if (continuousNewLineCount < maxContinuousNewLineCount) {
                            headerInfo.add(header);
                            if (header.toLowerCase().equals(contentLengthHeaderName)) {
                                // The next one would be the content length size
                                contentLengthHeaderPosition = headerInfo.size();
                            }
                            header = "";
                            continue;
                        } else {
                            // Complete end boundary
                            headerComplete = true;
                            continuousNewLineCount = 0; // Reset continuous new line data
                            header = "";
                            // carry on to the next step of parsing the data, do not call continue
                        }
                    }
                }

                // Parsing header data
                final String responseType = headerInfo.get(0).toLowerCase();
                // Read store type
                // path is /counter/<key> or /cache/<key>
                final String[] pathData = headerInfo.get(1).split("/", 3);
                // 0 is empty
                storeType = pathData[1];
                keyToWrite = pathData[2];

                // Check if content length header has been specified
                if (contentLengthHeaderPosition == -1) {
                    // no content-length in header
                    if (storeType.toLowerCase().equals("counter")) {
                        final int count = counterStore.getOrDefault(keyToWrite, 0);
                        if (responseType.equals("get")) {
                            printOkResponseWithContent(String.valueOf(count).getBytes());
                        } else {
                            // POST to increment
                            counterStore.put(keyToWrite, count + 1);
                            printOkResponseWithNoContent();
                        }

                        // Reset
                        headerInfo = new ArrayList<>();
                        headerComplete = false;
                    } else {
                        // key-value store
                        if (responseType.equals("get")) {
                            // retrieve data
                            if (kvStore.containsKey(keyToWrite)) {
                                byte[] data = kvStore.get(keyToWrite);
                                printOkResponseWithContent(data);
                            } else {
                                printNotFoundResponse();
                            }

                            // Reset
                            headerInfo = new ArrayList<>();
                            headerComplete = false;
                        } else if (responseType.equals("delete")) {
                            // Deletion spec
                            if (kvStore.containsKey(keyToWrite)) {
                                byte[] data = kvStore.remove(keyToWrite);
                                printOkResponseWithContent(data);
                            } else {
                                printNotFoundResponse();
                            }

                            // Reset
                            headerInfo = new ArrayList<>();
                            headerComplete = false;
                        }
                    }
                } else {
                    // Get payload size in bytes from header
                    payloadSizeBytes = Integer.parseInt(headerInfo.get(contentLengthHeaderPosition));
                    if (payloadSizeBytes != 0) {
                        // Make sure content len isn't 0
                        payload = new byte[payloadSizeBytes];
                        payloadIndex = 0;
                    } else {
                        // Content len is 0, just reset and read next packet
                        kvStore.put(keyToWrite, new byte[0]);
                        printOkResponseWithNoContent();

                        headerInfo = new ArrayList<>();
                        headerComplete = false;
                    }

                    // Reset content length header position
                    contentLengthHeaderPosition = -1;
                }
                continue; // currChar will be headerBoundaryChar, hence go to next iteration to read 1st payload byte
            }

            // Read 1 byte of payload
            payload[payloadIndex] = (byte) currByte;
            payloadIndex++;
            if (payloadIndex >= payloadSizeBytes) { // finished reading payload
                kvStore.put(keyToWrite, payload);
                // Write out that it is done
                printOkResponseWithNoContent();
                // Reset
                payloadSizeBytes = 0;
                headerInfo = new ArrayList<>();
                headerComplete = false;
            }
        }
    }

    public void printNotFoundResponse() throws IOException {
        out.write("404 NotFound  ".getBytes());
        out.flush();
    }

    public void printOkResponseWithNoContent() throws IOException {
        out.write("200 OK  ".getBytes());
        out.flush();
    }

    public void printOkResponseWithContent(byte[] content) throws IOException {
        byte[] a = ("200 OK content-length " + content.length + "  ").getBytes();
        byte[] c = new byte[a.length + content.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(content, 0, c, a.length, content.length);
        out.write(c);
        out.flush();
    }
}