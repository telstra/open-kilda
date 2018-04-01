package org.openkilda.pce.provider;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.configuration.BoltConnector;
import org.openkilda.messaging.model.Flow;

import java.io.File;
import java.util.List;

public class NeoDriverTest {

    private NeoDriver target = new NeoDriver(GraphDatabase.driver("bolt://localhost:7878",
            AuthTokens.basic("neo4j", "neo4j")));
    private static GraphDatabaseService graphDb;

    private static final File databaseDirectory = new File( "target/neo4j-test-db" );

    @BeforeClass
    public static void setUpOnce() throws Exception {
        FileUtils.deleteRecursively( databaseDirectory );       // delete neo db file

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


    @Test
    public void getAllFlows() {
        try ( Transaction tx = graphDb.beginTx() ) {
            Node node1, node2;
            node1 = graphDb.createNode(Label.label("switch"));
            node1.setProperty("name", "00:01");
            node2 = graphDb.createNode(Label.label("switch"));
            node2.setProperty("name", "00:02");
            Relationship rel1 = node1.createRelationshipTo(node2, RelationshipType.withName("flow"));
            rel1.setProperty("flowid","f1");
            rel1.setProperty("cookie", 3);
            rel1.setProperty("meter_id", 2);
            rel1.setProperty("transit_vlan", 1);
            rel1.setProperty("src_switch","00:01");
            rel1.setProperty("dst_switch","00:02");
            rel1.setProperty("src_port",1);
            rel1.setProperty("dst_port",2);
            rel1.setProperty("src_vlan",5);
            rel1.setProperty("dst_vlan",5);
            rel1.setProperty("bandwidth",200);
            rel1.setProperty("ignore_bandwidth", true);
            rel1.setProperty("description","description");
            rel1.setProperty("last_updated","last_updated");
            tx.success();
        }

        List<Flow> flows = target.getAllFlows();
        Flow flow = flows.get(0);
        Assert.assertEquals(3, flow.getCookie());
        Assert.assertEquals("f1", flow.getFlowId());
        Assert.assertEquals(true, flow.isIgnoreBandwidth());
    }

}
