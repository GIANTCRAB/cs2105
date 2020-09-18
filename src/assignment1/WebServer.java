package assignment1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WebServer {
    private ServerSocket serverSocket;
    private int portNumber;

    private static ConcurrentMap<String, byte[]> kvStore = new ConcurrentHashMap<>();
    private static ConcurrentMap<String, Integer> counterStore = new ConcurrentHashMap<>();

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
            new EchoClientHandler(serverSocket.accept()).start();
        }
    }

    public void stop() throws IOException {
        serverSocket.close();
    }

    private static class EchoClientHandler extends Thread {
        private final Socket clientSocket;
        private OutputStream out;
        private InputStream in;

        public EchoClientHandler(Socket socket) throws IOException {
            this.clientSocket = socket;
            out = clientSocket.getOutputStream();
            in = clientSocket.getInputStream();
        }

        public void run() {
            try {
                this.readPacket();
                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void readPacket() throws IOException {
            int currByte;
            char currChar;
            // 2 continuous newlines = end of header
            char headerBoundaryChar = '\n';
            int continuousNewLineCount = 0;
            final int maxContinuousNewLineCount = 2;
            String header = "";
            int payloadSizeBytes = 0;
            int payloadIndex = 0;
            byte[] payload = new byte[payloadSizeBytes];
            // read data only if it is a POST
            boolean readData = true;
            String storeType = "";
            String keyToWrite = "";

            // Read one byte at a time from stdin
            while ((currByte = in.read()) != -1) { // not reached end of stream
                if (0 == payloadSizeBytes && readData) {
                    // Get header
                    currChar = (char) currByte;
                    if (currChar != headerBoundaryChar && continuousNewLineCount != maxContinuousNewLineCount) {
                        header += String.valueOf(currChar);
                        continuousNewLineCount = 0; // Reset continuous new line data
                        continue;
                    } else {
                        if (continuousNewLineCount < maxContinuousNewLineCount) {
                            header += String.valueOf(currChar);
                            continuousNewLineCount++;
                            continue;
                        } else {
                            continuousNewLineCount++;
                            // Now move on to reading the data, do not call "continue"
                        }
                    }

                    final String[] headerData = header.split("\n");
                    // Read store type
                    final String[] pathData = header.split("/");
                    // 0 is empty
                    storeType = pathData[1];
                    keyToWrite = pathData[2];

                    if (headerData.length < 4) {
                        // no content-length in header
                        readData = false;

                        if (storeType.toLowerCase().equals("counter")) {
                            final int count = counterStore.getOrDefault(keyToWrite, 0);
                            if (headerData[0].toLowerCase().equals("get")) {
                                printOkResponseWithContent(String.valueOf(count).getBytes());
                            } else {
                                // POST to increment
                                counterStore.put(keyToWrite, count + 1);
                                printOkResponseWithNoContent();
                            }
                        } else {
                            // key-value store
                            if (headerData[0].toLowerCase().equals("get")) {
                                // retrieve data
                                byte[] data = kvStore.get(keyToWrite);
                                if (data == null) {
                                    printNotFoundResponse();
                                } else {
                                    printOkResponseWithContent(data);
                                }
                            } else {
                            }
                        }
                    } else {
                        // Get payload size in bytes from header
                        payloadSizeBytes = Integer.parseInt(headerData[3]);
                        payload = new byte[payloadSizeBytes];
                        payloadIndex = 0;
                    }
                    continue; // currChar will be headerBoundaryChar, hence go to next iteration to read 1st payload byte
                }

                if (readData) {
                    // Read 1 byte of payload
                    payload[payloadIndex] = (byte) currByte;
                    payloadIndex++;
                    if (payloadIndex >= payloadSizeBytes) { // finished reading payload
                        kvStore.put(keyToWrite, payload);
                        // Write out that it is done
                        printOkResponseWithNoContent();
                        // Reset
                        payloadSizeBytes = 0;
                        header = "";
                        readData = true;
                    }
                }
            }
        }

        public void printNotFoundResponse() throws IOException {
            out.write("404\nNotFound\n\n".getBytes());
            out.flush();
        }

        public void printOkResponseWithNoContent() throws IOException {
            out.write("200\nOK\n\n".getBytes());
            out.flush();
        }

        public void printOkResponseWithContent(byte[] content) throws IOException {
            out.write(("200\nOK\n" + content.length + "\n").getBytes());
            out.write(content);
            out.write("\n\n".getBytes());
            out.flush();
        }
    }
}