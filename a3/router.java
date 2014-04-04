import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class Globals {
    public static final int NBR_ROUTER = 5;
}

class pkt_HELLO {
    public int routerID;
    public int linkID;

    public pkt_HELLO(int routerIDIn, int linkIDIn) {
        routerID = routerIDIn;
        linkID = linkIDIn; 
    }

    public byte[] getUDPdata() {
        byte[] data = new byte[8];                                                                 
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(routerID);
        buffer.putInt(linkID);
        return buffer.array();
    }

    public static pkt_HELLO getPktHELLO(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        pkt_HELLO helloPkt = new pkt_HELLO(0, 0);
        int routerID = buffer.getInt();
        int linkID = buffer.getInt();
        helloPkt.routerID = routerID;
        helloPkt.linkID = linkID;
        return helloPkt;
    }
}

class pkt_LSPDU {
    public int sender;
    public int routerID;
    public int linkID;
    public int cost;
    public int via;

    public pkt_LSPDU(int senderIn, int routerIDIn, int linkIDIn, int costIn, int viaIn) {
        sender = senderIn;
        routerID = routerIDIn;
        linkID = linkIDIn;
        cost = costIn;
        via = viaIn;
    }

    public byte[] getUDPdata() {
        byte[] data = new byte[20];                                                                 
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(sender);
        buffer.putInt(routerID);
        buffer.putInt(linkID);
        buffer.putInt(cost);
        buffer.putInt(via);
        return buffer.array();
    }

    public static pkt_LSPDU getPktLSPDU(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        pkt_LSPDU lspduPkt = new pkt_LSPDU(0, 0, 0, 0, 0);
        lspduPkt.sender = buffer.getInt();
        lspduPkt.routerID = buffer.getInt();
        lspduPkt.linkID = buffer.getInt();
        lspduPkt.cost = buffer.getInt();
        lspduPkt.via = buffer.getInt();
        return lspduPkt;
    }
}

class pkt_INIT {
    public int routerID;

    public pkt_INIT(int routerIDIn) {
        routerID = routerIDIn;
    }

    public byte[] getUDPdata() {
        byte[] data = new byte[4];                                                                 
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(routerID);
        return buffer.array();
    }
}

class link_cost {
    public int link;
    public int cost;

    public link_cost(int linkIn, int costIn) {
        link = linkIn;
        cost = costIn;
    }
}

class circuit_DB {
    public int nbrLink;
    public link_cost[] linkCost;
    public boolean receivedHello;
    public String name;

    public circuit_DB(int routerID) {
        nbrLink = 0;
        receivedHello = false;
        linkCost = new link_cost[Globals.NBR_ROUTER];
        name = "R" + routerID;

        for(int i = 0; i < Globals.NBR_ROUTER; i++) {
            linkCost[i] = new link_cost(0, 0);
        }
    }

    public static circuit_DB getCircuitDB(byte[] bytes, int routerID) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        circuit_DB circuitDB = new circuit_DB(routerID);
        circuitDB.nbrLink = buffer.getInt();
        for(int i = 0; i < circuitDB.nbrLink; i++) {
            circuitDB.linkCost[i].link = buffer.getInt();
            circuitDB.linkCost[i].cost = buffer.getInt();
        }
        return circuitDB;
    }

    public static circuit_DB receiveCircuitDB(DatagramSocket socket, int routerID) {
        byte[] data = new byte[4 + 8*Globals.NBR_ROUTER];
        DatagramPacket receivePacket = new DatagramPacket(data, data.length);
        try {
            socket.receive(receivePacket);
        } catch(IOException e) {
            System.out.println("Unable to receive circuit_DB.");
            System.exit(1);
        }

        return circuit_DB.getCircuitDB(data, routerID);
    }
}

public class router {
    private int routerID;
    private InetAddress IPAddress;
    private int nsePort;
    private int routerPort;
    private DatagramSocket uRouterSocket;
    private circuit_DB router_circuit_DB;
    private circuit_DB[] router_network_DB;
    private PrintWriter routerLog;

    public router() {
        router_network_DB = new circuit_DB[Globals.NBR_ROUTER];
        for(int i = 0; i < router_network_DB.length; i++) {
            router_network_DB[i] = new circuit_DB(i+1);
        }
    }

    public static void main(String[] args) throws Exception {
        router r = new router();
        r.validateInput(args);
        r.begin(); 
    }

    public void begin() throws Exception {
        uRouterSocket = new DatagramSocket(routerPort);  // port router will listen on
        routerLog = createLog(routerID);     // create log file
        router_circuit_DB = new circuit_DB(routerID);

        // send init packet
        pkt_INIT pktInit = new pkt_INIT(routerID);
        sendPacket(pktInit.getUDPdata());
        routerLog.println(router_circuit_DB.name + " sends an INIT"); // log init

        router_circuit_DB = circuit_DB.receiveCircuitDB(uRouterSocket, routerID); // block until circuit db received from nse
        routerLog.println(router_circuit_DB.name + " receives a INIT circuit_DB");
        router_network_DB[routerID-1] = router_circuit_DB;
        printTopologyDatabase(); 
    }

    private void printTopologyDatabase() {
        routerLog.println("# Topology database");
        for(int i = 0; i < router_network_DB.length; i++) {
            circuit_DB currDB = router_network_DB[i];
            if (currDB.nbrLink > 0) {
                routerLog.printf("%s -> %s nbr link %d\n", 
                    router_circuit_DB.name, currDB.name, currDB.nbrLink);
            }
            for(int j = 0; j < currDB.nbrLink; j++) {
                routerLog.printf("%s -> %s link %d cost %d\n", 
                    router_circuit_DB.name, currDB.name, currDB.linkCost[j].link, currDB.linkCost[j].cost);
            }
        }
    }

    private void sendPacket(byte[] data) {
        DatagramPacket packetToSend = new DatagramPacket(data, data.length, IPAddress, nsePort);
        try {
            uRouterSocket.send(packetToSend);
        } catch (Exception e) {
            System.out.println("Could not send packet to nse. Verify hostname and port numbers.");
            System.exit(1);
        }
    }

    private PrintWriter createLog(int routerID) {
        String filename = "router" + routerID + ".log";
        PrintWriter file = null;
        try {
            file = new PrintWriter(filename);
        } catch(IOException e) {
            System.out.println("Unable to create file: " + filename);
            usage();
        }
        return file;
    }

    // Usage message
    private void usage() {
        String usage = "Usage: router <router_id> <nse_host> <nse_port> <router_port>";
        System.err.println(usage);
        System.exit(1); 
    }

    // Given input arguments, make sure they are valid
    private void validateInput(String[] args) {
        // verify arguments
        if ( args.length != 4 ) {
            System.err.println("Invalid number of arguments");
            usage();
        }
        
        String nseHost = args[1];
   
        // verify ports and router id as integers
        try {
            routerID = Integer.parseInt(args[0]);
            nsePort = Integer.parseInt(args[2]);
            routerPort = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            System.err.println("Ports and router id must be a valid integers");
            usage();
        }
        // check if hostname is valid
        try { 
            IPAddress = InetAddress.getByName(nseHost); // needed to send udp packet
        } catch (Exception e) {
            System.out.println("Could not connect to nse host. Ensure valid hostname is entered.");
            usage();
        }
    }
}

