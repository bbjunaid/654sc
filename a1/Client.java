import java.io.*;
import java.net.*;
import java.util.Arrays;

/*  The Client is provided the Server hostname, the negotiation port, as well as a message to be
    sent to, and subsequently be reversed by the server. 
    
    The client initially opens a TCP socket to send a negotiation signal to the server. The server
    responds by returning a random port number over which the client will send data through a UDP
    socket. The client constructs the UDP packet with the input message and sends it to the server.
    It receives the reversed message from the server through a UDP packet, and outputs this reversed
    message, and then closes its UDP connection.
*/ 
public class Client {
    public void usage() {
        System.err.println("Usage: client <hostname/server_address> <n_port> <msg>");
        System.exit(1); 
    }

    public static void main(String[] args) throws Exception {
        Client client = new Client(); // for purposes of printing usage message
        
        // Check for valid arguments
        if ( args.length != 3 ) {
            System.err.println("Invalid number of arguments.");
            client.usage();
        }

        if( args[2].length() == 0 ) {
            System.err.println("Message must be larger than 0 bytes.");
            client.usage();
        }
        int nPort = 0;
        try {
            nPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("n_port must be a valid integer");
            client.usage();
        }

        String hostname = args[0];
        String msg = args[2];
        Socket tClientSocket = null;
        
        // If the hostname is wrong, an UnknownHostException will be raised. If a connection
        // cannot be formed, then possibly the port number is wrong and thus a general
        // exception will be raised 
        try {
            tClientSocket = new Socket(hostname, nPort);
        } catch (UnknownHostException e) {
            System.err.println("IP address of host could not be determined");
            client.usage();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            client.usage();
        }

        // Output and input streams for the TCP connection
        DataOutputStream outToServer = 
            new DataOutputStream(tClientSocket.getOutputStream());

        BufferedReader inFromServer = 
            new BufferedReader(new
            InputStreamReader(tClientSocket.getInputStream()));

        outToServer.write(13); // initiate negotiation with server

        int rPort = Integer.parseInt(inFromServer.readLine()); // server responds with random port

        tClientSocket.close(); // no need for TCP connection anymore

        DatagramSocket uClientSocket = new DatagramSocket(); // udp socket

        InetAddress IPAddress = InetAddress.getByName(hostname); // needed to send udp packet

        // Send and receive message lengths will be the same
        byte[] sendData = msg.getBytes();
        byte[] receiveData = new byte[sendData.length];

        // Send the udp packet with message to reverse
        DatagramPacket sendPacket =
            new DatagramPacket(sendData, sendData.length, IPAddress, rPort);
        uClientSocket.send(sendPacket);

        // Extract the reversed message from the server packet and print it
        DatagramPacket receivePacket = 
            new DatagramPacket(receiveData, receiveData.length);
        uClientSocket.receive(receivePacket);
        String modifiedMsg = new String(receivePacket.getData());
        System.out.println("From Server: " + modifiedMsg);

        uClientSocket.close(); // close the UDP socket
    }
}
