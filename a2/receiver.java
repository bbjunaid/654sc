import java.io.*;
import java.net.*;
import java.util.Arrays;

public class receiver {
    private int expectedSeqNum;
    private DatagramSocket uReceiverSocket;
    private InetAddress IPAddress;
    private int portEmulatorFromReceiver;
    private int portReceiverFromEmulator;

    public receiver() {
        expectedSeqNum = 0;
        IPAddress = null;
    }

    private void usage() {
        String usage = "Usage: receiver <nEmulator host> <port: nEmulator from Receiver> "
                     + "<port: Receiver from nEmulator> <output file name>";
        System.err.println(usage);
        System.exit(1); 
    }
    
    private void sendToEmulator(packet p) {
        byte[] sendData = p.getUDPdata();
        DatagramPacket sendDatagram =
            new DatagramPacket(sendData, sendData.length, IPAddress, portEmulatorFromReceiver);

        try {
            uReceiverSocket.send(sendDatagram);
        } catch (Exception e) {
            System.out.println("Could not send packet to emulator");
            System.exit(1);
        }
    }

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
        
        File output = new File(filename);
        PrintWriter printer = null;
        try {
            printer = new PrintWriter(output);
        } catch (Exception e) {
            System.out.println("Cannot write to output file. Verify filename/permissions.");
            System.exit(1);
        }

        String strToWrite;
        uReceiverSocket = new DatagramSocket(portReceiverFromEmulator);
        byte[] receiveData = new byte[512]; 
        DatagramPacket receiveDatagram = new DatagramPacket(receiveData, receiveData.length);
        packet receivePacket = null;
        packet sendPacket = null;
        PrintWriter arrivalWriter = new PrintWriter(new File("arrival.log"));
        while(true) {
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
            
            // write sequence number to log
            System.out.printf("Received Packet with Seq Num: %d. Expected Seq Num: %d\n", receivePacket.getSeqNum(),
                               expectedSeqNum % 32);

            // if EOT packet
            if (receivePacket.getType() == 2) {
                sendPacket =  packet.createEOT(expectedSeqNum % 32);
                sendToEmulator(sendPacket);
                System.out.printf("Sending EOT packet. Seq Num: %d\n", expectedSeqNum%32);
                break;
            } else {
                arrivalWriter.println(receivePacket.getSeqNum());
                if(receivePacket.getSeqNum() == expectedSeqNum % 32) {
                    sendPacket = packet.createACK(expectedSeqNum % 32);
                    sendToEmulator(sendPacket);
                    strToWrite = new String(receivePacket.getData()); 
                    printer.write(strToWrite);
                    expectedSeqNum++;
                } 
                else if (expectedSeqNum != 0) {
                    sendPacket = packet.createACK( (expectedSeqNum-1) %32 );
                    sendToEmulator(sendPacket);
                }
            }
        }
        //uReceiverSocket.receive(receiveDatagram);    
        //System.out.println("Received a datagram");
        //byte[] UDPdata = Arrays.copyOf(receiveDatagram.getData(), receiveDatagram.getLength());
        //System.out.printf("Size of udp data: %d\n", UDPdata.length);
        //receivePacket = packet.parseUDPdata(UDPdata);
        //strToWrite = new String(receivePacket.getData()); 
        //printer.write(strToWrite);
        //sendPacket = packet.createACK(expectedSeqNum % 32);
        //sendToEmulator(sendPacket);

        printer.write("\n");
        printer.close();
        arrivalWriter.close();
        uReceiverSocket.close();
    }

    public static void main(String[] args) throws Exception {
        receiver r = new receiver();
        r.begin(args);
    }
}
