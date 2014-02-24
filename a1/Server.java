import java.io.*;
import java.net.*;
import java.util.Arrays;

public class Server {
    public void usage() {
        System.err.println("Usage: server");
        System.exit(1); 
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server();

        if ( args.length > 0 ) {
            System.err.println("No arguments needed for server.");
            server.usage();
        }
    
        
        ServerSocket tServerSocket = new ServerSocket(0);
        System.out.println("n_port: " + tServerSocket.getLocalPort());
        // max length for udp datagram
        byte[] receiveData = new byte[65000]; 
        byte[] sendData;
        while(true) {
            Socket connectionSocket = tServerSocket.accept();
            BufferedReader inFromClient = 
                new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
            DataOutputStream outToClient =
                new DataOutputStream(connectionSocket.getOutputStream());
           
            // initiate negotiation 
            if ( inFromClient.read() == 13 ) {
                // let OS find open port
                DatagramSocket uServerSocket = new DatagramSocket();

                // send random port to client
                outToClient.writeBytes(Integer.toString(uServerSocket.getLocalPort()) + "\n");

                DatagramPacket receivePacket =
                    new DatagramPacket(receiveData, receiveData.length);
                uServerSocket.receive(receivePacket);
                byte[] realData = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
                String msg = new String(realData);
                InetAddress IPAddress = receivePacket.getAddress();
                int port = receivePacket.getPort();
                String reversedStr = new StringBuffer(msg).reverse().toString();
                sendData = reversedStr.getBytes();
                DatagramPacket sendPacket =
                    new DatagramPacket(sendData, sendData.length, IPAddress, port);
                uServerSocket.send(sendPacket);
            } // if
        } // while
    }
}


