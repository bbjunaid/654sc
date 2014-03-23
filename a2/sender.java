import java.io.*;
import java.net.*;
import java.util.Queue;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.lang.Thread;
import java.awt.*;
import java.awt.event.*;

public class sender {
    private final int N = 10;
    private static final int TIMEOUT = 200;
    private Queue<packet> receivedQueue;
    private Timer timer;
    private DatagramSocket uSenderSocket;
    private packet[] packets;
    private InetAddress IPAddress;
    private int portEmulatorFromSender;
    private int portSenderFromEmulator;
    private int base;
    private int nextSeqNum;
    private PrintWriter seqNumWriter;
    private int numReceived;

    public sender() throws Exception {
        base = 0;
        nextSeqNum = 0;
        receivedQueue = new ConcurrentLinkedQueue<packet>();    
        IPAddress = null;
    }

    public void usage() {
        String usage = "Usage: sender <nEmulator host> <port: nEmulator from Sender> "
                     + "<port: Sender from nEmulator> <input file name>";
        System.err.println(usage);
        System.exit(1); 
    }
    
    static String readStringFromFile(String filename) throws IOException
    {
        Scanner scanner = new Scanner(new File(filename));
        String content = scanner.useDelimiter("\\Z").next();
        IOException io = scanner.ioException();
        scanner.close();

        if ( io != null ) {
            throw io;
        } 

        return content;
    }

    private DatagramPacket packetToUDP(packet p) {
        byte[] sendData = p.getUDPdata();
        return new DatagramPacket(sendData, sendData.length, IPAddress, portEmulatorFromSender);
    }

    private void sendPacket(int packetNum) {
        try {
            System.out.printf("Sending packet: %d\n", packets[packetNum].getSeqNum());
            uSenderSocket.send(packetToUDP(packets[packetNum]));
            seqNumWriter.println(packetNum % 32);
        } catch (Exception e) {
            System.out.println("Could not send packet to nEmulator. Verify hostname and port numbers.");
            System.exit(1);
        }
    }

    class TimeoutTask extends TimerTask {
        public void run() {
            System.out.printf("Resending all packets from base-nextSeqNum-1: %d-%d\n", base, nextSeqNum-1);
            for(int i = base; i < nextSeqNum; i++) {
                sendPacket(i);
            }
            cancel();
            timer.schedule(new TimeoutTask(), TIMEOUT);
        }
    }

    private void receivePackets() {
        numReceived = 0;
        System.out.println("Inside receivePackets function!");
        byte[] receiveData = new byte[512]; 
        DatagramPacket receiveDatagram = new DatagramPacket(receiveData, receiveData.length);
        packet receivePacket;

        while (base < packets.length) {
            try {
                uSenderSocket.receive(receiveDatagram);    
                byte[] UDPdata = Arrays.copyOf(receiveDatagram.getData(), receiveDatagram.getLength());
                numReceived++;
                try {
                    receivePacket = packet.parseUDPdata(UDPdata);
                    receivedQueue.offer(receivePacket);
                } catch (Exception e) {
                    System.out.println("Could not parse packet received from nEmulator");
                }
            } 
            catch (PortUnreachableException e) {
                System.out.println("Could not receieve packet. Verify port: Sender from nEmulator.");
                System.exit(1);
            }
            catch (IOException e) {
                System.out.println("Breaking out of loop");
                break;
            }             
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
        String fileContent = null;
   
        // verify filename
        if (filename.length() == 0) {
            System.out.println("Enter a valid filename");
            usage();
        }   

        // verify ports as integers
        try {
            portEmulatorFromSender = Integer.parseInt(args[1]);
            portSenderFromEmulator = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("Ports must be a valid integers");
            usage();
        }
        // check if hostname 
        try { 
            IPAddress = InetAddress.getByName(hostname); // needed to send udp packet
        } catch (Exception e) {
            System.out.println("Could not connect to nEmulator host. Ensure valid hostname is entered.");
            usage();
        }
       
        // verify file exists and can be read 
        File f = new File(filename);
        if ( !f.exists() || !f.canRead() ) {
            System.out.println("File does not exist or cannot be read. Enter valid input file.");
            usage();
        }
        try {
            fileContent = readStringFromFile(filename);
        } catch (IOException e) {
            System.out.println("Could not properly read file content. Supply a valid file");
            usage();
        }

        // create array of packets to be sent and populate these packets
        // each packet can only hold a string of length 500
        int numPackets = (int)Math.ceil(fileContent.length()/(double)500);
        packets = new packet[numPackets];
        int i;
        for ( i = 0; i < numPackets-1; i++ ) {
            packets[i] = packet.createPacket( i % 32, fileContent.substring(i*500, (i+1)*500) );
        }
        packets[i] = packet.createPacket( i % 32, fileContent.substring(i*500, fileContent.length()) );

        // Setup DatagramSocket to receieve on the sender listening port
        uSenderSocket = new DatagramSocket(portSenderFromEmulator);

        // Receive packets in seperate thread
        Thread receiveThread = new Thread(new Runnable() {
            public void run()
            {
                receivePackets();
            }
        });
        receiveThread.start();

        System.out.printf("Number of Packets: %d\n", numPackets);
        packet packetFromEmulator = null;
        seqNumWriter = new PrintWriter(new File("seqnum.log"));
        PrintWriter ackWriter = new PrintWriter(new File("ack.log"));
        int wrapAround = 0;
        int prevSequence = 0;
        int currSequence = 0;
        timer = new Timer();
        while (base < numPackets) {
            // send a packet and start timer if needed
            if (nextSeqNum < numPackets && nextSeqNum < (base + N)) {
                System.out.printf("Sending packet: %d\n", nextSeqNum);
                sendPacket(nextSeqNum);
                if ( base == nextSeqNum ) {
                    timer.schedule(new TimeoutTask(), TIMEOUT);
                }

                nextSeqNum++;
            } 

            packetFromEmulator = receivedQueue.poll(); 
            if ( packetFromEmulator != null ) {
                // restart timer for ACK packets received unless our base is same as nextSeqNum
                if ( packetFromEmulator.getType() == 0 ) {
                    prevSequence = currSequence;
                    currSequence = packetFromEmulator.getSeqNum();
                    if (currSequence < prevSequence)
                        wrapAround++;
                    base = wrapAround*32 + packetFromEmulator.getSeqNum() + 1;
                    System.out.printf("Base is: %d\n", base);
                    System.out.printf("Received packed with Seq Num: %d\n", packetFromEmulator.getSeqNum());
                    ackWriter.println(packetFromEmulator.getSeqNum());
                    timer.cancel();
                    timer = new Timer();
                    if ( base < nextSeqNum ) {
                        timer.schedule(new TimeoutTask(), TIMEOUT);
                    }
                }
            }
        }
        seqNumWriter.close();
        ackWriter.close();
        packet eotPacket = packet.createEOT(nextSeqNum % 32);
        byte[] sendData = eotPacket.getUDPdata();
        DatagramPacket eotSend = new DatagramPacket(sendData, sendData.length, IPAddress, portEmulatorFromSender);
        uSenderSocket.send(eotSend);
        System.out.printf("Sending packet: %d\n", eotPacket.getSeqNum());
       
        System.out.printf("Polling for EOT packet. Expected Seq Num: %d\n", nextSeqNum%32); 
        while(packetFromEmulator == null || packetFromEmulator.getType() != 2) {
            packetFromEmulator = receivedQueue.poll();
        }
        System.out.printf("Received packed with Seq Num: %d\n", packetFromEmulator.getSeqNum());
        System.out.println("Finishing sender");
        uSenderSocket.close();  // close socket, which implicitly closes the receiver thread as well
        timer.cancel();
    

        //System.out.printf("Num packets: %d\n", numPackets);
        //sendPacket(nextSeqNum);

        //byte[] receiveData = new byte[512]; 
        //DatagramPacket receiveDatagram = new DatagramPacket(receiveData, receiveData.length);
        //packet receivePacket;
        //uSenderSocket.receive(receiveDatagram);    
        //System.out.println("Received a datagram!");
        //byte[] UDPdata = Arrays.copyOf(receiveDatagram.getData(), receiveDatagram.getLength());
        //receivePacket = packet.parseUDPdata(UDPdata);

        
        //if ( receivePacket != null && receivePacket.getType() == 0 ) {
        //    System.out.printf("Recieved ACK: %d\n", receivePacket.getSeqNum());
        //}
        //while(receivedQueue.isEmpty());
        //packetFromEmulator = receivedQueue.poll();
        //if( packetFromEmulator.getType() == 0)
        //    System.out.printf("Recieved ACK: %d\n", packetFromEmulator.getSeqNum());
    }

    public static void main(String[] args) throws Exception {
        sender s = new sender(); // needed to output usage message
        s.begin(args);
        System.out.println("Exiting begin in main");
    }        
}
