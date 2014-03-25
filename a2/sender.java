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
    // Go Back N variables
    private final int N = 10;                   // window size
    private int base;                           // position of first packet sent for which ACK not yet received
    private int nextSeqNum;                     // position of next packet to send
    private static final int TIMEOUT = 200;     // time out to resend all unack'ed packets
    private Timer timer;                        // needed to create timers
    private packet[] packets;

    // Packet sending and receiving
    private DatagramSocket uSenderSocket;       // socket to send and receive packets on
    private Queue<packet> receivedQueue;        // this queue is populated by a seperate thread which
                                                // polls for incoming packets
    
    // user input varaibles
    private InetAddress IPAddress;              // ip-address of nEmulator
    private int portEmulatorFromSender;         // port emulator expects to receive data from sender
    private int portSenderFromEmulator;         // port sender expects to receive data from nEmulator

    // logging variables
    private PrintWriter seqNumWriter;           // log sequence numbers of sent packets

    // Constructor
    public sender() throws Exception {
        base = 0;
        nextSeqNum = 0;
        receivedQueue = new ConcurrentLinkedQueue<packet>();    
        IPAddress = null;
    }

    // Usage message
    public void usage() {
        String usage = "Usage: sender <nEmulator host> <port: nEmulator from Sender> "
                     + "<port: Sender from nEmulator> <input file name>";
        System.err.println(usage);
        System.exit(1); 
    }
   
    // Reads a file and returns its contents in a Java String
    // Input: filename
    // Output: Java String containing file contents
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

    // Converts a GBN packet to a UDP DatagramPacket
    // Input: GBN packet
    // Output: DatagramPacket containing GBN packet data
    private DatagramPacket packetToUDP(packet p) {
        byte[] sendData = p.getUDPdata();
        return new DatagramPacket(sendData, sendData.length, IPAddress, portEmulatorFromSender);
    }

    // Sends a GBN packet to the emulator and writes to the seqnum.log
    // Input: absolute packet number
    // Output: void
    private void sendPacket(int packetNum) {
        try {
            uSenderSocket.send(packetToUDP(packets[packetNum]));    // convert GBN packet to UDP datagram and send
            seqNumWriter.println(packetNum % 32);   // write to seqnum.log
        } catch (Exception e) {
            System.out.println("Could not send packet to nEmulator. Verify hostname and port numbers.");
            System.exit(1);
        }
    }

    // This resents all the unack'ed packets when the timeout occurs, which are from base to nextSeqNum-1
    // It runs in a seperate Timer thread
    // Input: None
    // Output: None
    class TimeoutTask extends TimerTask {
        public void run() {
            // Resent all unack'ed packets
            for(int i = base; i < nextSeqNum; i++) {
                sendPacket(i);
            }
            //cancel(); // cancel timer task so it doesn't run again
            timer.schedule(new TimeoutTask(), TIMEOUT); // restart the timer
        }
    }

    // This function runs in a seperate thread and receives packets on the uSenderSocket from
    // the nEmulator. When the sender has received the EOT packet, it closes uSenderSocket
    // which is received as a IOException casuing this function to end
    private void receivePackets() {
        byte[] receiveData = new byte[512]; // max size of the received packet is 512 bytes
        DatagramPacket receiveDatagram = new DatagramPacket(receiveData, receiveData.length);
        packet receivePacket;

        while (base < packets.length) {
            try {
                uSenderSocket.receive(receiveDatagram); // receive the datagram packet first 

                // convert UDP data to a GBN packet
                byte[] UDPdata = Arrays.copyOf(receiveDatagram.getData(), receiveDatagram.getLength());
                try {
                    receivePacket = packet.parseUDPdata(UDPdata); // convert udp bytes to GBN packet
                    receivedQueue.offer(receivePacket); // add the packet to the receive queue
                } catch (Exception e) {
                    System.out.println("Could not parse packet received from nEmulator");
                }
            }
            catch (PortUnreachableException e) {
                System.out.println("Could not receieve packet. Verify port: Sender from nEmulator.");
                System.exit(1);
            }
            // Thread ends here when uSenderSocket gets closed
            catch (IOException e) {
                break;
            }
        }
    }

    // This contains most of the logic for the sender
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
        // check if hostname is valid
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
        packet p;
        for ( i = 0; i < numPackets-1; i++ ) {
            p = packet.createPacket( i % 32, fileContent.substring(i*500, (i+1)*500) );
            packets[i] = p;
        }
        // last packet may not contain 500 bytes
        p = packet.createPacket( i % 32, fileContent.substring(i*500, fileContent.length()) );
        packets[i] = p;

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
        packet packetFromEmulator = null;                               // contains packet read from emulator
        seqNumWriter = new PrintWriter(new File("seqnum.log"));         // to write in seqnum.log
        PrintWriter ackWriter = new PrintWriter(new File("ack.log"));   // to write in ack.log
        int wrapAround = 0;     // everytime we cover a full range of 0-31, this increments
                                // helps keep track of the overall base 
        int prevSequence = 0;   // previous sequence number (mod 32)
        int currSequence = 0;   // current sequence number of received ACK (mod 32)
        timer = new Timer();    // create a Timer to be scheduled

        System.out.println("Sending file data"); 
        while (base < numPackets) {
            // send a packet and start timer if needed
            if (nextSeqNum < numPackets && nextSeqNum < (base + N)) {
                sendPacket(nextSeqNum);

                // start the timer once a newly sent packet which has not been ack'ed yet is sent
                if ( base == nextSeqNum ) {
                    timer.schedule(new TimeoutTask(), TIMEOUT);
                }

                nextSeqNum++;
            } 

            packetFromEmulator = receivedQueue.poll(); // poll the receiver queue for any packets from emulator
            if ( packetFromEmulator != null ) {
                ackWriter.println(packetFromEmulator.getSeqNum()); // write the ACK seqnum to the log
                prevSequence = currSequence;
                currSequence = packetFromEmulator.getSeqNum();
                // To detect the wraparound, the idea case would be the prevSeqeunce to be 31 and
                // currSequence to be 0. However, consider the scenario:
                // prevSequence = 28. Then acks 29 30 31 and 0 are discarded and we receive ack1.
                // We need to take into account that acks may be discarded so we cannot hardcode
                // 31 and 0. The logic here assumes that a max of 3 packets can be lost, which
                // is resonable. So if our prevSequence is 30 and we receive ack1, we acknowledge
                // that as a wrap around
                if (prevSequence > 29 && currSequence < 2) {
                    wrapAround++;
                // We need to ignore delayed ack's as they don't help us advance our base
                } else if (currSequence >= prevSequence) {
                    base = wrapAround*32 + packetFromEmulator.getSeqNum() + 1;
                    // everytime we receive a valid ack and there are more packet's waiting to be ack'ed
                    // we restart the timer
                    timer.cancel();
                    timer = new Timer();
                    if ( base < nextSeqNum ) {
                        timer.schedule(new TimeoutTask(), TIMEOUT);
                    }
                }
            }
        }
        System.out.println("Finished sending file Data");
        // Close the writers and cancel remaining timers as we are done sending the data
        seqNumWriter.close();
        ackWriter.close();
        timer.cancel();

        // Send the EOT packet to close the receiver program
        System.out.println("Sending the EOT packet");
        packet eotPacket = packet.createEOT(nextSeqNum % 32);
        byte[] sendData = eotPacket.getUDPdata();
        DatagramPacket eotSend = new DatagramPacket(sendData, sendData.length, IPAddress, portEmulatorFromSender);
        uSenderSocket.send(eotSend);
      
        // Wait to receive the EOT back from the emulator to shut down 
        System.out.printf("Waiting to receive EOT packet"); 
        while(packetFromEmulator == null || packetFromEmulator.getType() != 2) {
            packetFromEmulator = receivedQueue.poll();
        }

        System.out.println("Received EOT packet. Finishing sender");
        uSenderSocket.close();  // close socket, which implicitly closes the thread which is receiving
                                // packets from the emulator
    }

    // starts the sender
    public static void main(String[] args) throws Exception {
        sender s = new sender(); // needed to output usage message
        s.begin(args);
    }        
}
