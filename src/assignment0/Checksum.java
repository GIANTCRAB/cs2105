
import java.io.*;
import java.util.zip.CRC32;

public class Checksum {
    public static void main(String[] args) throws IOException {
        final String fileName = args[0];

        DataInputStream reader = new DataInputStream(new FileInputStream(fileName));
        int nBytesToRead = reader.available();
        if (nBytesToRead > 0) {
            byte[] bytes = new byte[nBytesToRead];
            reader.read(bytes);
            CRC32 checksum = new CRC32();
            checksum.update(bytes);

            long checksumOutput = checksum.getValue();

            System.out.println(checksumOutput);
        }
    }
}