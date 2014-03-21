import java.io.*;
import java.net.*;
import java.util.Arrays;

/*  The Server opens a TCP connection and outputs the port number it will inititate negotation
    from the Client on. Once the Server receives a negotiation signal, it opens a UDP socket on
    a random port and informs the client of this port.

    It then listens on this port for a message from the client. It receives the message in a UDP
    packet, extracts the length of the message, allocates the bytes for it and reverses it, and
    then sends it back to the client in a packet, and closes this udp connection.
*/
public class Server {
    public void usage() {
        System.err.println("Usage: server");
        System.exit(1); 
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server(); // needed to output usage message

        if ( args.length > 0 ) {
            System.err.println("No arguments needed for server.");
            server.usage();
        }
    
        // Let the OS pick n_port for TCP connection by specifying 0 as the port 
        ServerSocket tServerSocket = new ServerSocket(0);
        System.out.println("n_port: " + tServerSocket.getLocalPort());

        // Byte arrays for sending and receiving udp messages.
        // max length for udp datagram is 65kB. 
        byte[] receiveData = new byte[65000]; 
        byte[] sendData;
        while(true) {
            Socket connectionSocket = tServerSocket.accept(); // accept TCP connection from client

            // Input and output streams to communicate from/to client
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
                
                // Receieve the udp packet from the server, potentially, this could be as big as
                // the size of a udp datagram, 65kB
                DatagramPacket receivePacket =
                    new DatagramPacket(receiveData, receiveData.length);
                uServerSocket.receive(receivePacket);
                
                // Determine the actual length of the message from the header, and assign it to a string
                byte[] realData = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
                String msg = new String(realData);
                String reversedStr = new StringBuffer(msg).reverse().toString();

                // Determine the clients IP address and port number to send the reversed string back
                // in a udp packet
                InetAddress IPAddress = receivePacket.getAddress();
                int port = receivePacket.getPort();
                sendData = reversedStr.getBytes();
                DatagramPacket sendPacket =
                    new DatagramPacket(sendData, sendData.length, IPAddress, port);
        
                uServerSocket.send(sendPacket); // send the reversed string back to client
                uServerSocket.close(); // close the udp connection
            } // if
        } // while
    }
}
