package org.openkilda.northbound.controller;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.configuration.BoltConnector;

import java.io.File;

/**
 * Add common test functionality here.
 *
 * At present, a dependency on Neo4J has been added.
 */
public class NorthboundBaseTest {

    /*
     * The neo4j block was added due to new dependency in FlowServiceImpl
     */
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
    /*
     * End of special block for neo
     */

}
