package org.openkilda.pce.provider;

import org.junit.*;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.neo4j.driver.v1.*;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.configuration.BoltConnector;


import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.pce.RecoverableException;
import org.openkilda.pce.algo.SimpleGetShortestPath;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.model.SimpleIsl;
import org.openkilda.pce.model.SimpleSwitch;
import org.openkilda.pce.provider.PathComputer.Strategy;

/**
 * The primary goals of this test package are to emulate the Acceptance Tests in the ATDD module.
 * Those tests can be found in services/src/atdd/src/test/java/org/openkilda/atdd/PathComputationTest.java
 *
 * To
 */
public class PathComputerTest {

    private static GraphDatabaseService graphDb;

    private static final File databaseDirectory = new File( "target/neo4j-test-db" );
    private static Driver driver;
    private static NeoDriver nd;

    @BeforeClass
    public static void setUpOnce() throws Exception {
        FileUtils.deleteRecursively( databaseDirectory );       // delete neo db file
        System.out.println("Creating Elephants \uD83D\uDC18");

        // This next area enables Kilda to connect to the local db
        BoltConnector bolt = new BoltConnector("0");
        graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder( databaseDirectory )
                .setConfig( bolt.type, "BOLT" )
                .setConfig( bolt.enabled, "true" )
                .setConfig( bolt.listen_address, "localhost:7878" )
                .newGraphDatabase();

        driver = GraphDatabase.driver( "bolt://localhost:7878", AuthTokens.basic( "neo4j", "password" ) );
        nd = new NeoDriver(driver);

    }

    @AfterClass
    public static void teatDownOnce() {
        driver.close();
        graphDb.shutdown();
    }

    @Test
    public void testGetFlowInfo() {
        try ( Transaction tx = graphDb.beginTx() ) {
            Node node1, node2;
            node1 = graphDb.createNode(Label.label("switch"));
            node1.setProperty("name", "00:03");
            node2 = graphDb.createNode(Label.label("switch"));
            node2.setProperty("name", "00:04");
            Relationship rel1 = node1.createRelationshipTo(node2, RelationshipType.withName("flow"));
            rel1.setProperty("flowid","f1");
            rel1.setProperty("cookie", 3);
            rel1.setProperty("meter_id", 2);
            rel1.setProperty("transit_vlan", 1);
            rel1.setProperty("src_switch","00:03");
            tx.success();
        }

        List<FlowInfo> fi = nd.getFlowInfo();
        Assert.assertEquals(fi.get(0).getFlowId(), "f1");
        Assert.assertEquals(fi.get(0).getCookie(), 3);
        Assert.assertEquals(fi.get(0).getMeterId(), 2);
        Assert.assertEquals(fi.get(0).getTransitVlanId(), 1);
        Assert.assertEquals(fi.get(0).getSrcSwitchId(), "00:03");
    }

    private Node createNode(String name) {
        Node n = graphDb.createNode(Label.label("switch"));
        n.setProperty("name", name);
        n.setProperty("state", "active");
        return n;
    }
    private Relationship addRel (Node n1, Node n2, String status, String actual, int cost, int bw, int port){
        Relationship rel;
        rel = n1.createRelationshipTo(n2, RelationshipType.withName("isl"));
        rel.setProperty("status",status);
        rel.setProperty("actual",actual);
        if (cost >= 0) {rel.setProperty("cost", cost);}
        rel.setProperty("available_bandwidth", bw);
        rel.setProperty("latency", 5);
        rel.setProperty("src_port", port);
        rel.setProperty("dst_port", port);
        rel.setProperty("src_switch", n1.getProperty("name"));
        rel.setProperty("dst_switch", n2.getProperty("name"));
        return rel;
    }
    private Relationship addRelAsString (Node n1, Node n2, String status, String actual, String cost, int bw, int port){
        Relationship rel;
        rel = n1.createRelationshipTo(n2, RelationshipType.withName("isl"));
        rel.setProperty("status",status);
        rel.setProperty("actual",actual);
        if (cost != null && !cost.isEmpty()) {rel.setProperty("cost", cost);}
        rel.setProperty("available_bandwidth", bw);
        rel.setProperty("latency", 5);
        rel.setProperty("src_port", port);
        rel.setProperty("dst_port", port);
        rel.setProperty("src_switch", n1.getProperty("name"));
        rel.setProperty("dst_switch", n2.getProperty("name"));
        return rel;
    }


    private void createDiamond(String pathBstatus, String pathCstatus, int pathBcost, int pathCcost) {
        createDiamond(pathBstatus, pathCstatus, pathBcost, pathCcost, "00:", 1);
    }

    /**
     * This will create a diamond with cost as string.
     */
    private void createDiamondAsString(String pathBstatus, String pathCstatus, String pathBcost, String pathCcost, String switchStart, int startIndex) {
        try ( Transaction tx = graphDb.beginTx() ) {
            // A - B - D
            //   + C +
            Node nodeA, nodeB, nodeC, nodeD;
            int index = startIndex;
            nodeA = createNode(switchStart + String.format("%02X", index++));
            nodeB = createNode(switchStart + String.format("%02X", index++));
            nodeC = createNode(switchStart + String.format("%02X", index++));
            nodeD = createNode(switchStart + String.format("%02X", index++));
            String actual = (pathBstatus.equals("active") && pathCstatus.equals("active")) ? "active" : "inactive";
            addRelAsString(nodeA, nodeB, pathBstatus, actual, pathBcost, 1000, 5);
            addRelAsString(nodeA, nodeC, pathCstatus, actual, pathCcost, 1000, 6);
            addRelAsString(nodeB, nodeD, pathBstatus, actual, pathBcost, 1000, 6);
            addRelAsString(nodeC, nodeD, pathCstatus, actual, pathCcost, 1000, 5);
            addRelAsString(nodeB, nodeA, pathBstatus, actual, pathBcost, 1000, 5);
            addRelAsString(nodeC, nodeA, pathCstatus, actual, pathCcost, 1000, 6);
            addRelAsString(nodeD, nodeB, pathBstatus, actual, pathBcost, 1000, 6);
            addRelAsString(nodeD, nodeC, pathCstatus, actual, pathCcost, 1000, 5);
            tx.success();
        }
    }

    private void createDiamond(String pathBstatus, String pathCstatus, int pathBcost, int pathCcost, String switchStart, int startIndex) {
        try ( Transaction tx = graphDb.beginTx() ) {
            // A - B - D
            //   + C +
            Node nodeA, nodeB, nodeC, nodeD;
            int index = startIndex;
            nodeA = createNode(switchStart + String.format("%02X", index++));
            nodeB = createNode(switchStart + String.format("%02X", index++));
            nodeC = createNode(switchStart + String.format("%02X", index++));
            nodeD = createNode(switchStart + String.format("%02X", index++));
            String actual = (pathBstatus.equals("active") && pathCstatus.equals("active")) ? "active" : "inactive";
            addRel(nodeA, nodeB, pathBstatus, actual, pathBcost, 1000, 5);
            addRel(nodeA, nodeC, pathCstatus, actual, pathCcost, 1000, 6);
            addRel(nodeB, nodeD, pathBstatus, actual, pathBcost, 1000, 6);
            addRel(nodeC, nodeD, pathCstatus, actual, pathCcost, 1000, 5);
            addRel(nodeB, nodeA, pathBstatus, actual, pathBcost, 1000, 5);
            addRel(nodeC, nodeA, pathCstatus, actual, pathCcost, 1000, 6);
            addRel(nodeD, nodeB, pathBstatus, actual, pathBcost, 1000, 6);
            addRel(nodeD, nodeC, pathCstatus, actual, pathCcost, 1000, 5);
            tx.success();
        }
    }

    private void connectDiamonds(String aName, String bName, String status, int cost, int port) {
        try ( Transaction tx = graphDb.beginTx() ) {
            // A - B - D
            //   + C +
            Node nodeA = graphDb.findNode(Label.label("switch"),"name", aName);
            Node nodeB = graphDb.findNode(Label.label("switch"),"name", bName);
            addRel(nodeA, nodeB, status, status, cost, 1000, port);
            addRel(nodeB, nodeA, status, status, cost, 1000, port);
            tx.success();
        }
    }


    private void createTriangleTopo(String pathABstatus, int pathABcost, int pathCcost) {
        createTriangleTopo(pathABstatus, pathABcost, pathCcost, "00:", 1);
    }

    private void createTriangleTopo(String pathABstatus, int pathABcost, int pathCcost, String switchStart, int startIndex) {
        try ( Transaction tx = graphDb.beginTx() ) {
            // A - B
            // + C +
            Node nodeA, nodeB, nodeC;
            int index = startIndex;

            nodeA = createNode(switchStart + String.format("%02X", index++));
            nodeB = createNode(switchStart + String.format("%02X", index++));
            nodeC = createNode(switchStart + String.format("%02X", index++));

            addRel(nodeA, nodeB, pathABstatus, pathABstatus, pathABcost, 1000, 5);
            addRel(nodeB, nodeA, pathABstatus, pathABstatus, pathABcost, 1000, 5);
            addRel(nodeA, nodeC, "active", "active", pathCcost, 1000, 6);
            addRel(nodeC, nodeA, "active", "active", pathCcost, 1000, 6);
            addRel(nodeC, nodeB,  "active", "active", pathCcost, 1000, 7);
            addRel(nodeB, nodeC,  "active", "active", pathCcost, 1000, 7);
            tx.success();
        }
    }

    @Test
    public void testGetPathByCostActive() throws UnroutablePathException, RecoverableException {
        /*
         * simple happy path test .. everything has cost
         */
        createDiamond("active", "active", 10, 20);
        Flow f = new Flow();
        f.setSourceSwitch("00:01");
        f.setDestinationSwitch("00:04");
        f.setBandwidth(100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);
        //System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        Assert.assertEquals("00:02", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }


    @Test
    public void testGetPathByCostActiveAsStr() throws UnroutablePathException, RecoverableException {
        /*
         * simple happy path test .. everything has cost
         */
        createDiamondAsString("active", "active", "10", "20", "FF:", 1);
        Flow f = new Flow();
        f.setSourceSwitch("FF:01");
        f.setDestinationSwitch("FF:04");
        f.setBandwidth(100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);
        //System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        Assert.assertEquals("FF:02", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }


    @Test
    public void testGetPathByCostInactive() throws UnroutablePathException, RecoverableException {
        /*
         * verifies that iSL in both directions needs to be active
         */
        createDiamond("inactive", "active", 10, 20, "01:", 1);
        Flow f = new Flow();
        f.setSourceSwitch("01:01");
        f.setDestinationSwitch("01:04");
        f.setBandwidth(100);

        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);

        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        // ====> only difference is it should now have C as first hop .. since B is inactive
        Assert.assertEquals("01:03", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }

    @Test
    public void testGetPathByCostInactiveOnTriangleTopo() throws UnroutablePathException, RecoverableException {
        /*
         * simple happy path test .. but lowest path is inactive
         */
        createTriangleTopo("inactive", 5, 20, "02:", 1);
        Flow f = new Flow();
        f.setSourceSwitch("02:01");
        f.setDestinationSwitch("02:02");
        f.setBandwidth(100);

        AvailableNetwork network = nd.getAvailableNetwork(false, 100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, network, PathComputer.Strategy.COST);
        System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        // ====> only difference is it should now have C as first hop .. since B is inactive
        Assert.assertEquals("02:03", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }

    @Test
    public void testGetPathByCostNoCost() throws UnroutablePathException, RecoverableException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond("active", "active",  -1, 2000, "03:", 1);
        Flow f = new Flow();
        f.setSourceSwitch("03:01");
        f.setDestinationSwitch("03:04");
        f.setBandwidth(100);

        AvailableNetwork network = nd.getAvailableNetwork(false, 100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, network, PathComputer.Strategy.COST);
        // System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        // ====> Should choose B .. because default cost (700) cheaper than 2000
        Assert.assertEquals("03:02", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }


    @Test(expected = UnroutablePathException.class)
    public void testGetPathNoPath() throws UnroutablePathException, RecoverableException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond("inactive", "inactive", 10, 30, "04:", 1);
        Flow f = new Flow();
        f.setSourceSwitch("04:01");
        f.setDestinationSwitch("04:04");
        f.setBandwidth(100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);
    }


    /**
     * Test the mechanisms of the in memory getPath.
     */
    @Test
    public void getPathTest_InitState() {
        createDiamond("active", "active", 10, 20, "05:", 1);
        boolean ignoreBw = false;

        long time = System.currentTimeMillis();
        System.out.println("start = " + time);
        AvailableNetwork network = nd.getAvailableNetwork(ignoreBw, 0);
        System.out.println("\nNETWORK = " + network);

        System.out.println("AvailableNetwork = " + (System.currentTimeMillis() - time));
        System.out.println("network.getCounts() = " + network.getCounts());

        time = System.currentTimeMillis();
        network.removeSelfLoops().reduceByCost();
        System.out.println("network.getCounts() = " + network.getCounts());
        System.out.println("After Counts = " + (System.currentTimeMillis() - time));

        time = System.currentTimeMillis();
        network = nd.getAvailableNetwork(ignoreBw, 0);
        System.out.println("2nd AvailableNetwork = " + (System.currentTimeMillis() - time));
        SimpleSwitch[] switches = new SimpleSwitch[network.getSwitches().values().size()];
        Arrays.sort(network.getSwitches().values().toArray(switches));
        Assert.assertEquals(4, switches.length);
        Assert.assertEquals("05:01", switches[0].dpid);
        Assert.assertEquals("05:04", switches[3].dpid);
        Assert.assertEquals(2, switches[0].outbound.size());
        Assert.assertEquals(1, switches[0].outbound.get("05:02").size());
        Assert.assertEquals(10, switches[0].outbound.get("05:02").iterator().next().getCost());
        Assert.assertEquals(1, switches[0].outbound.get("05:03").size());
        Assert.assertEquals(20, switches[0].outbound.get("05:03").iterator().next().getCost());

        time = System.currentTimeMillis();
        SimpleGetShortestPath sgsp = new SimpleGetShortestPath(network, "05:01", "05:03", 35);
        LinkedList<SimpleIsl> result = sgsp.getPath();
        System.out.println("TIME: SimpleGetShortestPath.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);

        time = System.currentTimeMillis();
        sgsp = new SimpleGetShortestPath(network, "05:01", "05:04", 35);
        result = sgsp.getPath();
        System.out.println("TIME: SimpleGetShortestPath.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);
    }

    /**
     * Create a couple of islands .. try to find a path between them .. validate no path is
     * returned, and that the function completes in reasonable time ( < 10ms);
     */
    @Test
    public void getPathTest_Islands() {
        createDiamond("active", "active", 10, 20, "06:", 1);
        createDiamond("active", "active", 10, 20, "07:", 1);
        boolean ignoreBw = false;

        AvailableNetwork network = nd.getAvailableNetwork(ignoreBw, 0);
        network.removeSelfLoops().reduceByCost();

        // THIS ONE SHOULD WORK
        long time = System.currentTimeMillis();
        SimpleGetShortestPath sgsp = new SimpleGetShortestPath(network, "06:01", "06:03", 35);
        LinkedList<SimpleIsl> result = sgsp.getPath();
        System.out.println("TIME: SimpleGetShortestPath.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);

        // THIS ONE SHOULD FAIL
        time = System.currentTimeMillis();
        sgsp = new SimpleGetShortestPath(network, "06:01", "07:04", 35);
        result = sgsp.getPath();
        System.out.println("TIME: SimpleGetShortestPath.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("result = " + result);
    }

    /**
     * See how it works with a large network.
     * It takes a while to create the network .. therefore @Ignore so that it doesn't slow down unit tests.
     */
    @Ignore
    @Test
    public void getPathTest_Large() {
        createDiamond("active", "active", 10, 20, "08:", 1);

        for (int i = 0; i < 50; i++) {
            createDiamond("active", "active", 10, 20, "10:", 4 * i + 1);
            createDiamond("active", "active", 10, 20, "11:", 4 * i + 1);
            createDiamond("active", "active", 10, 20, "12:", 4 * i + 1);
            createDiamond("active", "active", 10, 20, "13:", 4 * i + 1);
        }
        for (int i = 0; i < 49; i++) {
            String prev = String.format("%02X", 4 * i + 4);
            String next = String.format("%02X", 4 * i + 5);
            connectDiamonds("10:" + prev, "10:" + next, "active", 20, 50);
            connectDiamonds("11:" + prev, "11:" + next, "active", 20, 50);
            connectDiamonds("12:" + prev, "12:" + next, "active", 20, 50);
            connectDiamonds("13:" + prev, "13:" + next, "active", 20, 50);
        }
        connectDiamonds("10:99", "11:22", "active", 20, 50);
        connectDiamonds("11:99", "12:22", "active", 20, 50);
        connectDiamonds("12:99", "13:22", "active", 20, 50);
        connectDiamonds("13:99", "10:22", "active", 20, 50);

        boolean ignoreBw = false;

        AvailableNetwork network = nd.getAvailableNetwork(ignoreBw, 0);
        network.removeSelfLoops().reduceByCost();
        System.out.println("network.getCounts() = " + network.getCounts());

        // THIS ONE SHOULD WORK
        long time = System.currentTimeMillis();
        SimpleGetShortestPath sgsp = new SimpleGetShortestPath(network, "10:01", "11:03", 200);
        LinkedList<SimpleIsl> result = sgsp.getPath();
        System.out.println("TIME: SimpleGetShortestPath.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("Path Length = " + result.size());

        // THIS ONE SHOULD FAIL
        time = System.currentTimeMillis();
        sgsp = new SimpleGetShortestPath(network, "08:01", "11:04", 100);
        result = sgsp.getPath();
        System.out.println("TIME: SimpleGetShortestPath.getPath -> " + (System.currentTimeMillis() - time));
        System.out.println("Path Length = " + result.size());
    }


    /**
     * This verifies that the getPath in NeoDriver returns what we expect.
     * Essentially, this tests the additional logic wrt taking the results of the algo and convert
     * to something installable.
     */
    @Test
    public void verifyConversionToPair() throws UnroutablePathException, RecoverableException {
        createDiamond("active", "active", 10, 20, "09:", 1);
        Flow flow = new Flow();
        String start = "09:01";
        String end = "09:04";
        flow.setSourceSwitch(start);      // getPath will find an isl port
        flow.setDestinationSwitch(end);
        flow.setIgnoreBandwidth(false);
        flow.setBandwidth(10);
        ImmutablePair<PathInfoData, PathInfoData> result = nd.getPath(flow, PathComputer.Strategy.COST);
        // ensure start/end switches match
        List<PathNode> left = result.left.getPath();
        Assert.assertEquals(start, left.get(0).getSwitchId());
        Assert.assertEquals(end, left.get(left.size() - 1).getSwitchId());
        List<PathNode> right = result.right.getPath();
        Assert.assertEquals(end, right.get(0).getSwitchId());
        Assert.assertEquals(start, right.get(right.size() - 1).getSwitchId());
    }

    /**
     * Path should be exists for created flow in case of reroute/update.
     */
    @Test
    public void shouldAlwaysFindPathForExistedFlow() throws Exception {
        Flow flow = new Flow();
        flow.setSourceSwitch("A1:01");      // getPath will find an isl port
        flow.setDestinationSwitch("A1:03");
        flow.setIgnoreBandwidth(false);
        flow.setBandwidth(1000);
        flow.setFlowId("flow-A1:01-A1:03");

        createLinearTopoWithFlowSegments(10, "A1:", 1, flow.getFlowId());
        AvailableNetwork network = nd.getAvailableNetwork(flow.isIgnoreBandwidth(), flow.getBandwidth());
        network.addIslsOccupiedByFlow(flow.getFlowId());
        ImmutablePair<PathInfoData, PathInfoData> result = nd.getPath(flow, network, Strategy.COST);

        Assert.assertEquals(4, result.getLeft().getPath().size());
        Assert.assertEquals(4, result.getRight().getPath().size());
    }

    private void createLinearTopoWithFlowSegments(int cost, String switchStart, int startIndex, String flowId) {
        try ( Transaction tx = graphDb.beginTx() ) {
            // A - B - C
            Node nodeA, nodeB, nodeC;
            int index = startIndex;
            nodeA = createNode(switchStart + String.format("%02X", index++));
            nodeB = createNode(switchStart + String.format("%02X", index++));
            nodeC = createNode(switchStart + String.format("%02X", index));
            addRel(nodeA, nodeB, "active", "active", cost, 1000, 5);
            addRel(nodeB, nodeC, "active", "active", cost, 1000, 6);
            addRel(nodeC, nodeB, "active", "active", cost, 1000, 6);
            addRel(nodeB, nodeA, "active", "active", cost, 1000, 5);

            addFlowSegment(flowId, nodeA, nodeB, 5, 5);
            addFlowSegment(flowId, nodeB, nodeA, 5, 5);
            addFlowSegment(flowId, nodeB, nodeC, 6, 6);
            addFlowSegment(flowId, nodeC, nodeB, 6, 6);
            tx.success();
        }
    }

    private void addFlowSegment(String flowId, Node src, Node dst, int srcPort, int dstPort) {
        Relationship rel;
        rel = src.createRelationshipTo(dst, RelationshipType.withName("flow_segment"));
        rel.setProperty("src_switch", src.getProperty("name"));
        rel.setProperty("dst_switch", dst.getProperty("name"));
        rel.setProperty("src_port", srcPort);
        rel.setProperty("dst_port", dstPort);
        rel.setProperty("flowid", flowId);
    }

}
