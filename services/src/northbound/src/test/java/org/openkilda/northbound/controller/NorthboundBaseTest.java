package org.openkilda.northbound.controller;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.ogm.testutil.TestServer;

/**
 * Add common test functionality here.
 * <p/>
 * At present, a dependency on Neo4J has been added.
 */
public abstract class NorthboundBaseTest {

    /*
     * The neo4j block was added due to new dependency in FlowServiceImpl
     */
    static TestServer testServer;

    @BeforeClass
    public static void setUpOnce() {
        testServer = new TestServer(true, true, 5, 17687);
    }

    @AfterClass
    public static void teatDownOnce() {
        testServer.shutdown();
    }
    /*
     * End of special block for neo
     */
}
