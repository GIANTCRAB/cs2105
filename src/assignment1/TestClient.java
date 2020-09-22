package assignment1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TestClient {
    public static void main(String[] args) {
        TestClient testClient = new TestClient();
        testClient.testCaseOne();
    }

    public class EchoClient {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public void startConnection(String ip, int port) throws IOException {
            clientSocket = new Socket(ip, port);
            out = new PrintWriter(clientSocket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            new EchoClientHandler().start();
        }

        public void sendMessage(String msg) throws IOException {
            out.print(msg);
            out.flush();
        }

        public void stopConnection() throws IOException {
            in.close();
            out.close();
            clientSocket.close();
        }

        private class EchoClientHandler extends Thread {
            public void run() {
                int currByte;
                char currChar;
                try {
                    while ((currByte = in.read()) != -1) {
                        // do something
                        currChar = (char) currByte;
                        System.out.print(currChar);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void testCaseOne() {
        EchoClient client1 = new EchoClient();
        try {
            client1.startConnection("127.0.0.1", 8080);
            client1.sendMessage("GET /key/CS2105  ");
            Thread.sleep(500);
            client1.sendMessage("POST /key/ModuleCode Content-Length 6  CS2105");
            Thread.sleep(500);
            client1.sendMessage("GET /key/ModuleCode  ");
            Thread.sleep(500);
            client1.sendMessage("POST /key/ModuleCode UtterRubbish Content-Length 36  I really dislike this module man wtf");
            Thread.sleep(500);
            client1.sendMessage("GET /key/ModuleCode  ");
            Thread.sleep(500);
            client1.sendMessage("DELETE /key/ModuleCode  ");
            Thread.sleep(500);
            client1.sendMessage("GET /key/ModuleCode  ");
            Thread.sleep(500);
            client1.sendMessage("GET /counter/CS2105  ");
            Thread.sleep(500);
            client1.sendMessage("POST /counter/StudentNumber  ");
            Thread.sleep(500);
            client1.sendMessage("GET /counter/StudentNumber  ");
            //client1.stopConnection();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
