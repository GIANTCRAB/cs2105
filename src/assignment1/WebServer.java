package assignment1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WebServer {
    private ServerSocketChannel serverSocket;
    private SocketChannel clientSocket;
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
        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(this.portNumber));
        while (true) {
            System.out.println("starting");
            clientSocket = serverSocket.accept();
            System.out.println("accepted n reading");
            readPacket();
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
        final char headerBoundaryChar = '\n';
        int continuousNewLineCount = 0;
        final int maxContinuousNewLineCount = 2;
        boolean headerComplete = false;
        String header = "";
        final int maxHeaderSize = 4;
        List<String> headerInfo = new ArrayList<>(maxHeaderSize);
        int payloadSizeBytes = 0;
        int payloadIndex = 0;
        byte[] payload = new byte[payloadSizeBytes];
        // read data only if it is a POST
        String storeType;
        String keyToWrite = "";

        // read by byte buffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (clientSocket.read(buffer) != -1) { // not reached end of stream
            System.out.println("reading byte");
            ByteArrayInputStream is = new ByteArrayInputStream(buffer.array());
            while ((currByte = is.read()) != 0) {
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
                    System.out.println("responses data");
                    System.out.println(headerInfo.get(0));
                    // Read store type
                    // path is /counter/<key> or /cache/<key>
                    final String[] pathData = headerInfo.get(1).split("/");
                    // 0 is empty
                    System.out.println("path data");
                    System.out.println(headerInfo.get(1));
                    storeType = pathData[1];
                    keyToWrite = pathData[2];

                    System.out.println("header size");
                    System.out.println(headerInfo.size());

                    if (headerInfo.size() < maxHeaderSize) {
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
                            headerInfo = new ArrayList<>(maxHeaderSize);
                            headerComplete = false;
                        } else {
                            // key-value store
                            if (responseType.equals("get")) {
                                // retrieve data
                                byte[] data = kvStore.get(keyToWrite);
                                if (data == null) {
                                    printNotFoundResponse();
                                } else {
                                    printOkResponseWithContent(data);
                                }

                                // Reset
                                headerInfo = new ArrayList<>(maxHeaderSize);
                                headerComplete = false;
                            }
                        }
                    } else {
                        // Get payload size in bytes from header
                        payloadSizeBytes = Integer.parseInt(headerInfo.get(3));
                        payload = new byte[payloadSizeBytes];
                        payloadIndex = 0;
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
                    headerInfo = new ArrayList<>(maxHeaderSize);
                    headerComplete = false;
                }
            }
        }
    }

    public void printNotFoundResponse() throws IOException {
        clientSocket.write(ByteBuffer.wrap("404\nNotFound\n\n".getBytes()));
    }

    public void printOkResponseWithNoContent() throws IOException {
        clientSocket.write(ByteBuffer.wrap("200\nOK\n\n".getBytes()));
    }

    public void printOkResponseWithContent(byte[] content) throws IOException {
        clientSocket.write(ByteBuffer.wrap(("200\nOK\ncontent-length\n" + content.length + "\n\n").getBytes()));
        clientSocket.write(ByteBuffer.wrap(content));
    }
}