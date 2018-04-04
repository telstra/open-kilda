package org.openkilda.pce.provider;

import static org.junit.Assert.assertEquals;

import org.junit.*;

import java.io.File;
import java.util.List;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.graphalgo.impl.shortestpath.Dijkstra;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.configuration.BoltConnector;


import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.ImmutablePair;
import org.openkilda.neo.NeoUtils;
import org.openkilda.neo.OkNode;
import org.openkilda.neo.NeoUtils.OkRels;

/**
 * The primary goals of this test package are to emulate the Acceptance Tests in the ATDD module.
 * Those tests can be found in services/src/atdd/src/test/java/org/openkilda/atdd/PathComputationTest.java
 *
 * To
 */
public class PathComputerTest {

    private static GraphDatabaseService graphDb;

    private static final File databaseDirectory = new File( "target/neo4j-test-db" );

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
    }

    @AfterClass
    public static void teatDownOnce() {
        graphDb.shutdown();
    }

    @Before
    public void setUp() {
        /*
         * Make sure we start from a known state
         */
//        try ( Transaction tx = graphDb.beginTx() )
//        {
//            Node firstNode = graphDb.createNode();
//            firstNode.setProperty("name","00:03");
//            Node secondNode = graphDb.createNode();
//            secondNode.setProperty("name","00:03");
//            Relationship relationship;
//
//            relationship = firstNode.createRelationshipTo( secondNode, RelTypes.KNOWS );
//            relationship.setProperty( "message", "brave Neo4j " );
//            tx.success();
//        }

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

        Driver driver = GraphDatabase.driver( "bolt://localhost:7878", AuthTokens.basic( "neo4j", "password" ) );
        NeoDriver nd = new NeoDriver(driver);
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
    private Relationship addRel (Node n1, Node n2, String status, int cost, int bw){
        Relationship rel;
        rel = n1.createRelationshipTo(n2, RelationshipType.withName("isl"));
        rel.setProperty("status",status);
        if (cost >= 0) {rel.setProperty("cost", cost);}
        rel.setProperty("available_bandwidth", bw);
        rel.setProperty("latency", 5);
        rel.setProperty("src_port", 5);
        rel.setProperty("dst_port", 5);
        rel.setProperty("src_switch", n1.getProperty("name"));
        rel.setProperty("dst_switch", n2.getProperty("name"));
        return rel;
    }

    private void createDiamond(String pathBstatus, String pathCstatus, int pathBcost, int pathCcost) {
        try ( Transaction tx = graphDb.beginTx() ) {
            // A - B - D
            //   + C +
            Node nodeA, nodeB, nodeC, nodeD;
            nodeA = createNode("00:01");
            nodeB = createNode("00:02");
            nodeC = createNode("00:03");
            nodeD = createNode("00:04");
            addRel(nodeA, nodeB, pathBstatus, pathBcost, 1000);
            addRel(nodeA, nodeC, pathCstatus, pathCcost, 1000);
            addRel(nodeB, nodeD, pathBstatus, pathBcost, 1000);
            addRel(nodeC, nodeD, pathCstatus, pathCcost, 1000);
            addRel(nodeB, nodeA, pathBstatus, pathBcost, 1000);
            addRel(nodeC, nodeA, pathCstatus, pathCcost, 1000);
            addRel(nodeD, nodeB, pathBstatus, pathBcost, 1000);
            addRel(nodeD, nodeC, pathCstatus, pathCcost, 1000);
            tx.success();
        }
    }

    private void createTriangleTopo(String pathABstatus, int pathABcost, int pathCcost) {
        try ( Transaction tx = graphDb.beginTx() ) {
            // A - B
            // + C +
            Node nodeA, nodeB, nodeC, nodeD;
            nodeA = createNode("00:01");
            nodeB = createNode("00:02");
            nodeC = createNode("00:03");
            addRel(nodeA, nodeB, pathABstatus, pathABcost, 1000);
            addRel(nodeA, nodeC, "active", pathCcost, 1000);
            addRel(nodeC, nodeB,  "active", pathCcost, 1000);
            tx.success();
        }
    }

    @Test
    public void testGetPathByCostActive() throws UnroutablePathException {
        /*
         * simple happy path test .. everything has cost
         */
        createDiamond("active", "active", 10, 20);
        Driver driver = GraphDatabase.driver( "bolt://localhost:7878", AuthTokens.basic( "neo4j", "password" ) );
        NeoDriver nd = new NeoDriver(driver);
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
    public void testGetPathByCostInactive() throws UnroutablePathException {
        /*
         * simple happy path test .. but lowest path is inactive
         */
        createDiamond("inactive", "active", 10, 20);
        Driver driver = GraphDatabase.driver( "bolt://localhost:7878", AuthTokens.basic( "neo4j", "password" ) );
        NeoDriver nd = new NeoDriver(driver);
        Flow f = new Flow();
        f.setSourceSwitch("00:01");
        f.setDestinationSwitch("00:04");
        f.setBandwidth(100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);
        // System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        // ====> only difference is it should now have C as first hop .. since B is inactive
        Assert.assertEquals("00:03", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }

    @Test
    public void testGetPathByCostInactiveOnTriangleTopo() throws UnroutablePathException {
        /*
         * simple happy path test .. but lowest path is inactive
         */
        createTriangleTopo("inactive", 10, 20);
        Driver driver = GraphDatabase.driver( "bolt://localhost:7878", AuthTokens.basic( "neo4j", "password" ) );
        NeoDriver nd = new NeoDriver(driver);
        Flow f = new Flow();
        f.setSourceSwitch("00:01");
        f.setDestinationSwitch("00:02");
        f.setBandwidth(100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);
        // System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        // ====> only difference is it should now have C as first hop .. since B is inactive
        Assert.assertEquals("00:03", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }

    @Test
    public void testGetPathByCostNoCost() throws UnroutablePathException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond("active", "active",  -1, 2000);
        Driver driver = GraphDatabase.driver( "bolt://localhost:7878", AuthTokens.basic( "neo4j", "password" ) );
        NeoDriver nd = new NeoDriver(driver);
        Flow f = new Flow();
        f.setSourceSwitch("00:01");
        f.setDestinationSwitch("00:04");
        f.setBandwidth(100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);
        // System.out.println("path = " + path);
        Assert.assertNotNull(path);
        Assert.assertEquals(4, path.left.getPath().size());
        // ====> Should choose B .. because default cost (700) cheaper than 2000
        Assert.assertEquals("00:02", path.left.getPath().get(1).getSwitchId()); // chooses path B
    }


    @Test(expected = UnroutablePathException.class)
    public void testGetPathNoPath() throws UnroutablePathException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond("inactive", "inactive", 10, 30);
        Driver driver = GraphDatabase.driver( "bolt://localhost:7878", AuthTokens.basic( "neo4j", "password" ) );
        NeoDriver nd = new NeoDriver(driver);
        Flow f = new Flow();
        f.setSourceSwitch("00:01");
        f.setDestinationSwitch("00:04");
        f.setBandwidth(100);
        ImmutablePair<PathInfoData, PathInfoData> path = nd.getPath(f, PathComputer.Strategy.COST);
    }

    /* ==========> TESTING DIJKSTRA
     * THE FOLLOWING CAN BE USED DIRECTLY IN THE NEO4J BROWSER.

     MERGE (A:switch {name:'00:01', state:'active'})
     MERGE (B:switch {name:'00:02', state:'active'})
     MERGE (C:switch {name:'00:03', state:'active'})
     MERGE (D:switch {name:'00:04', state:'active'})
     MERGE (A)-[ab:isl {available_bandwidth:1000, cost:10, status:'inactive'}]->(B)
     MERGE (A)-[ac:isl {available_bandwidth:1000, cost:20, status:'active'}]->(C)
     MERGE (B)-[bd:isl {available_bandwidth:1000, cost:10, status:'inactive'}]->(D)
     MERGE (C)-[cd:isl {available_bandwidth:1000, cost:20, status:'active'}]->(D)
     MERGE (B)-[ba:isl {available_bandwidth:1000, cost:10, status:'inactive'}]->(A)
     MERGE (C)-[ca:isl {available_bandwidth:1000, cost:20, status:'active'}]->(A)
     MERGE (D)-[db:isl {available_bandwidth:1000, cost:10, status:'inactive'}]->(B)
     MERGE (D)-[dc:isl {available_bandwidth:1000, cost:20, status:'active'}]->(C)
     return A,B,C,D

    TESTING with no_cost:

     MERGE (A:switch {name:'00:01', state:'active'})
     MERGE (B:switch {name:'00:02', state:'active'})
     MERGE (C:switch {name:'00:03', state:'active'})
     MERGE (D:switch {name:'00:04', state:'active'})
     MERGE (A)-[ab:isl {available_bandwidth:1000, status:'inactive'}]->(B)
     MERGE (A)-[ac:isl {available_bandwidth:1000, status:'active'}]->(C)
     MERGE (B)-[bd:isl {available_bandwidth:1000, status:'inactive'}]->(D)
     MERGE (C)-[cd:isl {available_bandwidth:1000, status:'active'}]->(D)
     MERGE (B)-[ba:isl {available_bandwidth:1000, status:'inactive'}]->(A)
     MERGE (C)-[ca:isl {available_bandwidth:1000, status:'active'}]->(A)
     MERGE (D)-[db:isl {available_bandwidth:1000, status:'inactive'}]->(B)
     MERGE (D)-[dc:isl {available_bandwidth:1000, status:'active'}]->(C)
     return A,B,C,D

==> WORKS
     MATCH (from:switch{name:"00:01"}), (to:switch{name:"00:04"})
     CALL apoc.algo.dijkstraWithDefaultWeight(from, to, 'isl', 'cost', 700) YIELD path AS p, weight AS weight
     RETURN p, weight


     MATCH (from:switch{name:"00:01"}), (to:switch{name:"00:04"})
     CALL apoc.algo.dijkstraWithDefaultWeight(from, to, 'isl', 'cost', 700) YIELD path AS p, weight AS weight
     WHERE ALL(y in rels(p) WHERE y.status = 'active')
     RETURN p, weight


     MATCH (from:switch), (to:switch)
     CALL apoc.algo.dijkstraWithDefaultWeight(from, to, 'isl', 'cost', 700) YIELD path AS p, weight AS weight
     WHERE from.name = "00:01" AND to.name = "00:04" AND ALL(r IN rels(path) WHERE r.state = 'active')
     RETURN p, weight


MATCH (from:switch{name:"00:01"}), (to:switch{name:"00:04"}), paths = allShortestPaths((from)-[r:isl*]->(to))
WITH REDUCE(cost = 0, rel in rels(paths) | cost + rel.cost) AS cost, paths
WHERE ALL (x in r WHERE x.status = 'active')
RETURN paths, cost
ORDER BY cost
LIMIT 1

==> WORKS
MATCH (from:switch{name:"00:01"}), (to:switch{name:"00:04"}), paths = allShortestPaths((from)-[r:isl*..100]->(to))
WITH REDUCE(cost = 0, rel in rels(paths) | cost + rel.cost) AS cost, paths
WHERE ALL (x in r WHERE x.status = 'active')
RETURN paths ORDER BY cost LIMIT 1

==> WORKING WITH NO COST
MATCH (from:switch{name:"00:01"}), (to:switch{name:"00:04"}), paths = allShortestPaths((from)-[r:isl*..100]->(to))
WITH REDUCE(cost = 0, rel in rels(paths) | cost + rel.cost) AS cost, paths
WHERE ALL (x in r WHERE x.status = 'active')
RETURN paths ORDER BY cost LIMIT 1


MATCH (a:switch{name:"00:01"}),(b:switch{name:"00:04"}), p = shortestPath((a)-[r:isl*..100]->(b))
where ALL(x in nodes(p) WHERE x.state = 'active')
    AND ALL(y in r WHERE y.status = 'active' AND y.available_bandwidth >= 100)
RETURN p

==> WORKS
MATCH (a:switch{name:"00:01"}),(b:switch{name:"00:04"}), p = shortestPath((a)-[r:isl*..100]->(b))
WHERE ALL(y in r WHERE y.status = 'active' AND y.available_bandwidth >= 100)
return p

        StringJoiner where = new StringJoiner("\n    AND ", "where ", "");
        where.add("ALL(x in nodes(p) WHERE x.state = 'active')");
        if (flow.isIgnoreBandwidth()) {
            where.add("ALL(y in r WHERE y.status = 'active')");




        StringJoiner where = new StringJoiner("\n    AND ", "where ", "");
        where.add("ALL(x in nodes(p) WHERE x.state = 'active')");
        if (flow.isIgnoreBandwidth()) {
            where.add("ALL(y in r WHERE y.status = 'active')");
        } else {
            where.add("ALL(y in r WHERE y.status = 'active' AND y.available_bandwidth >= {bandwidth})");
            parameters.put("bandwidth", Values.value(flow.getBandwidth()));
        }

        String result = "RETURN p";


     */

        /**
         * Current status of this test is .. in alpha:
         * - it works with standard graph node
         * - but we want to evolve it to work with an oknode, so that we can filter relationships
         */
    @Ignore
    @Test
    public void dijkstraAlgorithm() {
        NeoUtils nuts = new NeoUtils(graphDb);
        OkNode nodeA,nodeB,nodeC;
        try ( Transaction tx = graphDb.beginTx() ) {
            nodeA = nuts.node( "A" );
            nodeB = nuts.node( "B" );
            nodeC = nuts.node( "C" );
            nodeA.edge(OkRels.isl, nodeB).property("length", 2d);
            nodeB.edge(OkRels.isl, nodeC).property("length", 3d);
            nodeA.edge(OkRels.isl, nodeC).property("length", 10d);
            tx.success();
        }

        try ( Transaction tx = graphDb.beginTx() ) {
            Dijkstra<Double> dike = nuts.getDijkstra(0d, OkRels.isl, "length", nodeA, nodeC);
            //assertEquals(5.0d,  dike.getCost().doubleValue(), 0.01);
            assertEquals(3,  dike.getPathAsNodes().size());
            tx.success();
        }
    }

    @After
    public void tearDown() {
        /*
         * Delete Everything
         */
        try ( Transaction tx = graphDb.beginTx() )
        {
//            graphDb.findNodes().
            tx.success();
        }
    }


}
