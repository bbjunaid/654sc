import java.io.*;
import java.net.*;
import java.util.Arrays;

public class receiver {
    private int expectedSeqNum;                 // packet expected from sender
    private DatagramSocket uReceiverSocket;     // socket to receive packets and send ACKs/EOT
    
    // User input variables
    private InetAddress IPAddress;              // ip-address of nEmmulator
    private int portEmulatorFromReceiver;       // port Emulator expects to receive data from receiver
    private int portReceiverFromEmulator;       // port receiver expects to receive data from nEmulator

    // Constructor for receiver 
    public receiver() {
        expectedSeqNum = 0;
        IPAddress = null;
    }

    // Usage message
    private void usage() {
        String usage = "Usage: receiver <nEmulator host> <port: nEmulator from Receiver> "
                     + "<port: Receiver from nEmulator> <output file name>";
        System.err.println(usage);
        System.exit(1); 
    }
   
    // Sends a GBN packet to emulator  
    // Input: gbn packet
    // Output void
    private void sendToEmulator(packet p) {
        byte[] sendData = p.getUDPdata();   // get the UDP data of the packet
        DatagramPacket sendDatagram =
            new DatagramPacket(sendData, sendData.length, IPAddress, portEmulatorFromReceiver);

        try {
            uReceiverSocket.send(sendDatagram);     // send the datagram
        } catch (Exception e) {
            System.out.println("Could not send packet to emulator");
            System.exit(1);
        }
    }

    // Main logic for the receiver program
    private void begin(String[] args) throws Exception {
        // check for valid arguments
        if ( args.length != 4 ) {
            System.err.println("Invalid number of arguments");
            usage();
        }

        String hostname = args[0];
        String filename = args[3];

        // verify filename
        if (filename.length() == 0) {
            System.out.println("Enter a valid filename");
            usage();
        }

        // verify ports as integers
        try {
            portEmulatorFromReceiver = Integer.parseInt(args[1]);
            portReceiverFromEmulator = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("Ports must be a valid integers");
            usage();
        }
        // check if hostname / port is valid
        try { 
            IPAddress = InetAddress.getByName(hostname); // needed to send udp packet
        } catch (Exception e) {
            System.out.println("Could not connect to nEmulator host. Ensure valid hostname is entered.");
            usage();
        }
       
        // Check if output file writing is allowed 
        FileWriter output = null;
        try {
            output = new FileWriter(filename);
        } catch(IOException e) {
            System.out.println("Could not write to given output file.");
            usage();
        }

        String strToWrite;  // variable to store the string needed to write to file
        uReceiverSocket = new DatagramSocket(portReceiverFromEmulator); // reciever socket from send/receive
        byte[] receiveData = new byte[512];     // max size of receive data is 512 bytes
        DatagramPacket receiveDatagram = new DatagramPacket(receiveData, receiveData.length);
        packet receivePacket = null;    // packet to receive from emulator
        packet sendPacket = null;       // packet to send to emulator
        PrintWriter arrivalWriter = new PrintWriter(new File("arrival.log"));   // to write to arrival.log
    
        // Start receiving the data
        System.out.println("Starting to receive data");
        while(true) {
            // Recieve the datagram and parse the UDP data into a GBN packet
            try {
                uReceiverSocket.receive(receiveDatagram);    
                byte[] UDPdata = Arrays.copyOf(receiveDatagram.getData(), receiveDatagram.getLength());
                try {
                    receivePacket = packet.parseUDPdata(UDPdata);
                } catch (Exception e) {
                    System.out.println("Could not parse packet received from nEmulator");
                }
            } catch (Exception e) {
                System.out.println("Could not receieve packet. Verify port numbers.");
                System.exit(1);
            }
            
            // if EOT packet, we send the EOT packet back to sender, then exit the receiver
            if (receivePacket.getType() == 2) {
                sendPacket =  packet.createEOT(expectedSeqNum % 32);
                sendToEmulator(sendPacket);
                break;
            } else {
                arrivalWriter.println(receivePacket.getSeqNum());   // write the arrival sequence number
                // If we get an expected packet, then create an ACK packet to send back and send it
                // Also write the data from this packet to the output file
                if(receivePacket.getSeqNum() == expectedSeqNum % 32) {
                    sendPacket = packet.createACK(expectedSeqNum % 32);
                    sendToEmulator(sendPacket);
                    strToWrite = new String(receivePacket.getData()); 
                    output.write(strToWrite, 0, receivePacket.getLength());
                    expectedSeqNum++;
                } 
                // If we get an unexpected packet, send the previous correctly received packet's ACK back to
                // sender. In the case of the first packet missing, we don't send back any acks
                else if (expectedSeqNum != 0) {
                    sendPacket = packet.createACK( (expectedSeqNum-1) %32 );    // create ACK for previous correct packet
                    sendToEmulator(sendPacket);     // send the ACK
                }
            }
        }
        System.out.println("Finished receiving data");

        output.write("\r"); // need to write appropraite line seperator
                                                            // sometimes this is \r, sometimes \n
                                                            // seems to be hard to get the correct seperator to be written
        output.close();     // close the output file writer
        arrivalWriter.close();  // close the arrival.log writer
        uReceiverSocket.close();    // close reciever socket
    }

    // Main function for receiver program
    public static void main(String[] args) throws Exception {
        receiver r = new receiver();
        r.begin(args);
    }
}
