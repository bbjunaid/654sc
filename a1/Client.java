import java.io.*;
import java.net.*;
import java.util.Arrays;

public class Client {
    public void usage() {
        System.err.println("Usage: client <hostname/server_address> <n_port> <msg>");
        System.exit(1); 
    }

    public static void main(String[] args) throws Exception {
        Client client = new Client(); 
        if ( args.length != 3 ) {
            System.err.println("Invalid number of arguments.");
            client.usage();
        }

        if( args[2].length() == 0 ) {
            System.err.println("Message must be larger than 0 bytes.");
            client.usage();
        }

        String hostname = args[0];
        System.out.println("Msg input: " + args[2]);
        String msg = args[2];
        int nPort = Integer.parseInt(args[1]);
        Socket tClientSocket = null;

        try {
            tClientSocket = new Socket(hostname, nPort);
        } catch (UnknownHostException e) {
            System.err.println("IP address of host could not be determined");
            client.usage();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            client.usage();
        }

        String clientRequest;
        String serverResponse;

        DataOutputStream outToServer = 
            new DataOutputStream(tClientSocket.getOutputStream());

        BufferedReader inFromServer = 
            new BufferedReader(new
            InputStreamReader(tClientSocket.getInputStream()));

        outToServer.write(13); // initiate negotiation with server

        int rPort = Integer.parseInt(inFromServer.readLine());
        System.out.println("Random port from server: " + rPort);

        tClientSocket.close();

        DatagramSocket uClientSocket = new DatagramSocket();

        InetAddress IPAddress = InetAddress.getByName(hostname);

        byte[] sendData = msg.getBytes();
        System.out.println("Msg: " + new String(sendData));
        System.out.println("Send Data length: " + sendData.length);
    
        byte[] receiveData = new byte[sendData.length];
        DatagramPacket sendPacket =
            new DatagramPacket(sendData, sendData.length, IPAddress, rPort);
        uClientSocket.send(sendPacket);

        DatagramPacket receivePacket = 
            new DatagramPacket(receiveData, receiveData.length);
        uClientSocket.receive(receivePacket);
        String modifiedMsg = new String(receivePacket.getData());
        System.out.println("From Server: " + modifiedMsg);
        uClientSocket.close();
    }
}
