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
        }

        public void sendMessage(String msg) throws IOException {
            out.print(msg);
            out.flush();
        }

        public void readMsg() throws IOException {
            in.lines().forEach(System.out::println);
        }

        public void stopConnection() throws IOException {
            in.close();
            out.close();
            clientSocket.close();
        }
    }

    public void testCaseOne() {
        EchoClient client1 = new EchoClient();
        try {
            client1.startConnection("127.0.0.1", 8080);
            client1.sendMessage("GET\n/key/CS2105\n\n");
            client1.sendMessage("POST\n/key/ModuleCode\nContent-Length\n6\n\nCS2105");
            client1.sendMessage("GET\n/key/ModuleCode\n\n");
            client1.sendMessage("GET\n/counter/CS2105\n\n");
            client1.sendMessage("POST\n/counter/StudentNumber\n\n");
            client1.sendMessage("GET\n/counter/StudentNumber\n\n");
            client1.readMsg();
            client1.stopConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
