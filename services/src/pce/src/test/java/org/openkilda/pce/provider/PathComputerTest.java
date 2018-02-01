package org.openkilda.pce.provider;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.*;

import java.io.File;

import org.neo4j.graphalgo.impl.shortestpath.Dijkstra;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;

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
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( databaseDirectory );

        // Shuts down nicely when the VM exits
        Runtime.getRuntime().addShutdownHook( new Thread(() -> {
            System.out.println("Killing Elephants \uD83D\uDC18");
            graphDb.shutdown();
        }));
    }

    @AfterClass
    public static void teatDownOnce() {
    }

    @Before
    public void setUp() {
        /*
         * Make sure we start from a known state
         */
        try ( Transaction tx = graphDb.beginTx() )
        {
            Node firstNode = graphDb.createNode();
            firstNode.setProperty("name","00:03");
            Node secondNode = graphDb.createNode();
            secondNode.setProperty("name","00:03");
            Relationship relationship;

//            relationship = firstNode.createRelationshipTo( secondNode, RelTypes.KNOWS );
//            relationship.setProperty( "message", "brave Neo4j " );
            tx.success();
        }

    }


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
            assertEquals(5.0d,  dike.getCost().doubleValue(), 0.01);
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
