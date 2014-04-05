import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

// C structs converted to Java

// global variables
class Globals {
    public static final int NBR_ROUTER = 5; // number of routers in topology
}

class pkt_HELLO {
    public int routerID;   /* id of the router who sends the HELLO PDU */
    public int linkID;     /* id of the link through which it is sent */

    // constructor    
    public pkt_HELLO(int routerIDIn, int linkIDIn) {
        routerID = routerIDIn;
        linkID = linkIDIn; 
    }

    // returns bytes for UDP transfer of packet 
    public byte[] getUDPdata() {
        byte[] data = new byte[8];                                                                 
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(routerID);
        buffer.putInt(linkID);
        return buffer.array();
    }

    // create HELLO packet from bytes
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
    public int sender;      // sender of ls pdu
    public int routerID;    // router id
    public int linkID;      // link id
    public int cost;        // cost of link
    public int via;         // id of link through which ls pdu is sent

    // constructor
    public pkt_LSPDU(int senderIn, int routerIDIn, int linkIDIn, int costIn, int viaIn) {
        sender = senderIn;
        routerID = routerIDIn;
        linkID = linkIDIn;
        cost = costIn;
        via = viaIn;
    }

    // returns bytes for UDP transfer of packet
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

    // create LSPDU packet from bytes
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
    public int routerID;        // id of router sending init pdu

    // constructor
    public pkt_INIT(int routerIDIn) {
        routerID = routerIDIn;
    }

    // returns bytes for UDP transfer of packet
    public byte[] getUDPdata() {
        byte[] data = new byte[4];                                                                 
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(routerID);
        return buffer.array();
    }
}

class link_cost {
    public int link;        // link id 
    public int cost;        // associated cost
    public boolean gotHello;     // whether this link has recevied HELLO pkt

    // constructor
    public link_cost(int linkIn, int costIn) {
        link = linkIn;
        cost = costIn;
        gotHello = false;
    }
}

class circuit_DB {
    public int nbrLink;     // number of links attached to router
    public link_cost[] linkCost;    // link costs
    public String name; // name of router, ie R1 etc

    // constructor
    public circuit_DB(int routerID) {
        nbrLink = 0;
        linkCost = new link_cost[Globals.NBR_ROUTER];
        name = "R" + routerID;

        for(int i = 0; i < Globals.NBR_ROUTER; i++) {
            linkCost[i] = new link_cost(0, 0);
        }
    }

    // received hello on link so update it
    public void updateHello(int linkID) {
        for(int i = 0; i < nbrLink; i++) {
            if(linkCost[i].link == linkID) {
                linkCost[i].gotHello = true;
                break;
            }
        }
    }

    // determine if a link has received HELLO packet or not
    public boolean checkHello(int linkID) {
        for(int i = 0; i < nbrLink; i++) {
            if(linkCost[i].link == linkID) {
                return linkCost[i].gotHello;
            }
        }
        return false;
    }

    // returns circuit_DB from bytes
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

    // receives UDP packet which captures circuit_DB data and then returns the object
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

// This is the main router class. All routers exchange information about each others links
// through LSPDU packets. HELLO and INIT packets are used for engaging the neighbors and receving
// initial circuit_DBs. 
// Whenever a router receives an LS PDU which updates its network topology, it performs dijkstras
// algorithm to find the shortest path to all other routers in the network. 
// Eventually, the router has full knowledge of the network, and is able to compute the shortest path
// to all other routers. This information is contained in the routerX.log file.

// The logic is split into various method. The begin() method contains the main logic
public class router {
    private int routerID;                       // id of router
    private InetAddress IPAddress;              // IP addr of nse
    private int nsePort;                        // port nse is listening on
    private int routerPort;                     // port router will receive from NSE on
    private DatagramSocket uRouterSocket;       // UDP socket for router
    private circuit_DB router_circuit_DB;       // contains information about the router's links
    private circuit_DB[] router_network_DB;     // contains information about links of every router in the network
    private PrintWriter routerLog;              // for writing out to routerX.log file

    // Costructor
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
        // add shutdown hook to close ports and file when we exit
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                routerLog.close();
                uRouterSocket.close();
            }
        });

        uRouterSocket = new DatagramSocket(routerPort);  // port router will listen on
        routerLog = createLog(routerID);     // create log file
        router_circuit_DB = new circuit_DB(routerID);   // give the router circuit DB a name

        // send init packet
        pkt_INIT pktInit = new pkt_INIT(routerID);
        sendPacket(pktInit.getUDPdata());
        routerLog.println(router_circuit_DB.name + " sends an INIT"); // log init

        // update router circuit DB and router network DB
        router_circuit_DB = circuit_DB.receiveCircuitDB(uRouterSocket, routerID); // block until circuit db received from nse
        routerLog.println(router_circuit_DB.name + " receives a INIT circuit_DB");
        router_network_DB[routerID-1] = router_circuit_DB;
       
        // print the topology for initial receipt 
        printTopologyDatabase(); 

        // send HELLO on each link
        sendHello();

        // wait for HELLO or LS PDU messages
        while(true) {
            // poll for message
            byte[] receiveData  = new byte[20];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                uRouterSocket.receive(receivePacket);
            } catch(IOException e) {
                System.out.println(router_circuit_DB.name + " unable to receive messages from nse.");
                System.exit(1);
            }
            byte[] realData = Arrays.copyOf(receivePacket.getData(), receivePacket.getLength());
           
            // HELLO received 
            if(receivePacket.getLength() == 8) {
                pkt_HELLO pktHello = pkt_HELLO.getPktHELLO(realData);
                receiveHello(pktHello); // perform appropriate actions with hello packet
            }
            // LSPDU received
            else if(receivePacket.getLength() == 20) {
                pkt_LSPDU pktLspdu = pkt_LSPDU.getPktLSPDU(realData);
                receiveLSPDU(pktLspdu); // perform appropriate actions with LS PDU packet
            }
            routerLog.flush();  // write out to log
        }
    }

    // First we log the receipt of the LS PDU. Then we try and update the network topology, if 
    // the topology is changed, then we log the topology, find the shortest path from the router
    // to all other routers using dikjstra's algorithm, and forward the LS PDU to neighboring links
    private void receiveLSPDU(pkt_LSPDU pktIn) {
        routerLog.printf("%s receives an LS PDU: sender: %d, router_id %d, link_id, %d, cost %d, via %d\n",
            router_circuit_DB.name, pktIn.sender, pktIn.routerID, pktIn.linkID, pktIn.cost, pktIn.via);
        // if there is new information, the network topology is updated, the topology database is printer
        // dijkstra's algorithm is run and the packet is forwarded to neighboring links
        if(updateTopology(pktIn)) {
            printTopologyDatabase(); 
            runDijkstra();
            forwardLSPDU(pktIn);
        }
    }
    
    // Sends LS PDU packet and writes logs
    private void sendLSPDU(pkt_LSPDU pktIn) {
        sendPacket(pktIn.getUDPdata());
        routerLog.printf("%s sends an LS PDU: sender: %d, router_id %d, link_id, %d, cost %d, via %d\n",
            router_circuit_DB.name, pktIn.sender, pktIn.routerID, pktIn.linkID, pktIn.cost, pktIn.via);
    }

    // Receive and log the HELLO packet. Respond by sending the router's link information as
    // LS PDU packets across all the links attached to the router
    private void receiveHello(pkt_HELLO pktIn) {
        routerLog.printf(router_circuit_DB.name + " receives a HELLO: router %d, link %d\n",
            pktIn.routerID, pktIn.linkID);
        router_circuit_DB.updateHello(pktIn.linkID);
        for (int i = 0; i < router_circuit_DB.nbrLink; i++) {
            link_cost linkInfo = router_circuit_DB.linkCost[i];
            pkt_LSPDU pktPDU = new pkt_LSPDU(routerID, routerID, linkInfo.link, linkInfo.cost, pktIn.linkID);
            sendLSPDU(pktPDU);
        }
    }

    // Sends the HELLO packet across all links
    private void sendHello() {
        for (int i = 0; i < router_circuit_DB.nbrLink; i++) {
            link_cost lc = router_circuit_DB.linkCost[i];
            pkt_HELLO pktHello = new pkt_HELLO(routerID, lc.link);
            sendPacket(pktHello.getUDPdata());
            routerLog.println(router_circuit_DB.name + " sends HELLO on link " + lc.link); 
        }
    }

    // First, we create an adjacency matrix out of the router_network_DB. Then we compute the shortest
    // path to all nodes using Dikjstra's algorithm. DistInfo is an appropriate datastructure which maintains
    // information about which neighbor was used to get the shortest path as well as the cost
    private void runDijkstra() {
        // Helper class for storing information about the router's neighbor used to reach the goal node
        class DistInfo {
            int cost;           // dist to router
            int prevRouterID;   // previous router in the shortest path to this node
            int viaNeighborID;  // first neighbor used when computing shortest path. used for printing logs
   
            // constructor 
            public DistInfo(int cost, int prevRouterID, int viaNeighborID) {
                this.cost = cost;
                this.prevRouterID = prevRouterID;
                this.viaNeighborID = viaNeighborID;
            }

            // String used for printing the RIB
            public String distString() {
                String ret;
                if(cost == 0) {
                    ret = String.format("%s", "Local, 0");
                }
                else if (cost == Integer.MAX_VALUE) {
                    ret = String.format("%s", "None, INF");
                }
                else {
                    ret = String.format("R%d, %d", viaNeighborID, cost); 
                }
                return ret;
            }
        }

        // adjacency matrix contains the cost between router u,v, Integer.MAX_VALUE if there is no edge, and 0 
        // for the cost between a router and itself
        int[][] adjacencyMatrix = new int[Globals.NBR_ROUTER][Globals.NBR_ROUTER];

        // initialize adjacency matrix
        for(int i = 0; i < Globals.NBR_ROUTER; i++) {
            for(int j = 0; j < Globals.NBR_ROUTER; j++) {
                adjacencyMatrix[i][j] = (i == j ? 0 : Integer.MAX_VALUE); 
            }
        }

        // build the adjacency matrix with appropriate costs
        for(int i = 0; i < Globals.NBR_ROUTER; i++) {
            circuit_DB cdbA = router_network_DB[i];
            // explore all links
            for(int j = 0; j < cdbA.nbrLink; j++) {
                // find other routers with matching links
                for(int k = 0; k < Globals.NBR_ROUTER; k++) {
                    if(k == i) continue;
                    circuit_DB cdbB = router_network_DB[k];
                    // if matching link found, then update the adjacency matrix
                    for(int l = 0; l < cdbB.nbrLink; l++) {
                        if (cdbA.linkCost[j].link == cdbB.linkCost[l].link) {
                            adjacencyMatrix[i][k] = cdbA.linkCost[j].cost;
                            break;
                        }
                    }
                }
            }
        }

        DistInfo[] dist = new DistInfo[Globals.NBR_ROUTER];
        Set<Integer> N = new TreeSet<Integer>();
        N.add(routerID-1);
        // initialize distances
        for(int i = 0; i < Globals.NBR_ROUTER; i++) {
            int cost = adjacencyMatrix[routerID-1][i];
            if(cost != Integer.MAX_VALUE) {
                dist[i] = new DistInfo(adjacencyMatrix[routerID-1][i], routerID, i+1);
            } else {
                dist[i] = new DistInfo(adjacencyMatrix[routerID-1][i], routerID, -1);
            }
        }
    
        // while all nodes aren't examined
        while(N.size() != Globals.NBR_ROUTER) {
            // find w not in N such that D(w) is a minimum
            int w = -1;
            int minDist = Integer.MAX_VALUE;
            for(int i = 0; i < dist.length; i++) {
                if(!N.contains(i) && dist[i].cost <= minDist) {
                    w = i;
                    minDist = dist[i].cost;
                }
            }

            N.add(w);   // add w to the set
            // update D(v) for all v adjacent to w and not in N
            // new cost to v is either old cost to v or known shortest path to w plus cost from w->v
            // ignore update if the distance of w is MAX_VALUE
            for(int v = 0; v < Globals.NBR_ROUTER; v++) {
                if(!N.contains(v) && adjacencyMatrix[w][v] != Integer.MAX_VALUE && dist[w].cost != Integer.MAX_VALUE
                        && dist[v].cost > (dist[w].cost + adjacencyMatrix[w][v])) {
                    dist[v].cost = dist[w].cost + adjacencyMatrix[w][v];
                    dist[v].prevRouterID = w+1; 
                    dist[v].viaNeighborID = dist[w].viaNeighborID;  // propagate the neighbor used to reach shortest path
                }
            }
        }

        // print RIB
        routerLog.println("# RIB");
        for(int i  = 0; i < Globals.NBR_ROUTER; i++) {
            routerLog.printf("%s -> R%d -> %s\n", router_circuit_DB.name, i+1, dist[i].distString());
        }
    }
   
    // forward LSPDU on all neighboring links except from where LSPDU was recieved or where
    // HELLO has not yet been received 
    private void forwardLSPDU(pkt_LSPDU pktIn) {
        for(int i = 0; i < router_circuit_DB.nbrLink; i++) {
            if(router_circuit_DB.linkCost[i].link != pktIn.via && router_circuit_DB.linkCost[i].gotHello) {
                pkt_LSPDU forwardPkt = 
                    new pkt_LSPDU(routerID, pktIn.routerID, pktIn.linkID, pktIn.cost, router_circuit_DB.linkCost[i].link);
                sendPacket(forwardPkt.getUDPdata());
                routerLog.printf("%s forwards an LS PDU: sender: %d, router_id %d, link_id, %d, cost %d, via %d\n",
                    router_circuit_DB.name, forwardPkt.sender, forwardPkt.routerID, forwardPkt.linkID, forwardPkt.cost, forwardPkt.via);
            }
        } 
    }

    // Updates a router's circuit DB with new link information if necessary
    // If update occurs, then return true, otherwise false
    private boolean updateTopology(pkt_LSPDU pktIn) {
        circuit_DB cdb = router_network_DB[pktIn.routerID-1];
        link_cost lc = new link_cost(pktIn.linkID, pktIn.cost);
        if(cdb.nbrLink == 0) {
            cdb.linkCost[0] = lc;         
            cdb.nbrLink++;
            return true;
        } 
        // run through the links. if we find a matching link but costs don't match, update cost.
        // if no matching link found, add the new link to the circuit_DB
        // returns true if link found and costs don't match or if link not found and new link added
        // return false if link found but costs match
        else {
            boolean found = false;
            int i;
            for(i = 0; i < cdb.nbrLink; i++) {
                if(cdb.linkCost[i].link == lc.link) {
                    found = true;
                    break;        
                }
            }

            if (found) {
                if(cdb.linkCost[i].cost == lc.cost) {
                    return false;
                } else {
                    cdb.linkCost[i].cost = lc.cost;
                }
            }
            else {
                cdb.linkCost[cdb.nbrLink] = lc;
                cdb.nbrLink++;
            }
            return true;
        }
    }
    
    
    // Logs Topology Database
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

    // Sends bytes as UDP packet to the nse port
    private void sendPacket(byte[] data) {
        DatagramPacket packetToSend = new DatagramPacket(data, data.length, IPAddress, nsePort);
        try {
            uRouterSocket.send(packetToSend);
        } catch (Exception e) {
            System.out.println("Could not send packet to nse. Verify hostname and port numbers.");
            System.exit(1);
        }
    }

    // Creates a PrintWriter to write to log
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
