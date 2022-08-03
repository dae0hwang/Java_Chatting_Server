import java.io.*;
import java.net.Socket;

public class Client {
    public static void main(String[] args) {
        Socket sock = null;
        try {
            sock = new Socket("127.0.0.1", 5510);

            //receive message Thread start
            ServerHandler chandler = new ServerHandler(sock);
            Thread receiveThread = new Thread(chandler);
            receiveThread.start();

            //only first client send message type is 1111
            boolean first = true;
            int type;
            System.out.print("Register your name: ");

            while (true) {
                OutputStream toServer = sock.getOutputStream();
                DataOutputStream dos = new DataOutputStream(toServer);

                //String sendmessage.
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String sendMessage = br.readLine();
                byte[] bytes = sendMessage.getBytes("UTF-8");

                //make Header
                byte[] header;
                int length = bytes.length;
                if (first) {
                    type = 1111;
                } else {
                    type = 2222;
                }
                //complete making header
                Header.encodeHeader(length, type);
                header = Header.bytesHeader;

                //output header and message
                dos.write(header, 0, 8);
                dos.write(bytes, 0, bytes.length);
                dos.flush();
                //second send messaage is first = false
                first = false;
            }
        } catch (IOException ex) {
            System.out.println("Connection termination (" + ex + ")");
        } finally {
            try {
                if (sock != null) {
                    sock.close();
                }
            } catch (IOException ex) {
            }
        }
    }
}