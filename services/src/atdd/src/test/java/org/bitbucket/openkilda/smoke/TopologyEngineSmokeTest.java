package org.bitbucket.openkilda.smoke;

import com.google.common.io.Resources;
import org.bitbucket.openkilda.topo.*;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Created by carmine on 3/2/17.
 */
public class TopologyEngineSmokeTest  {
    public static final String defaultHost = "localhost";

    @Test
    /**
     * Several things we can test to ensure the service is up:
     * 1) The TE is off port 80, and when empty returns []
     * NB: Neo4J is off of 7474 - neo4j / temppass.  To clear everything:
     *      - ```match (n) detach delete n```
     */
    public void testServiceUp(){
        String entity = TopologyHelp.ClearTopology();
        assertEquals("Default, initial, response from TopologyEngine", "{\"nodes\": []}",entity);
    }

    @Test
    /**
     * A small network of switches and links (~10 switches and 10s of links. Ensure it comes up
     * properly.
     */
    public void testSimpleTopologyDiscovery() throws InterruptedException {
        TestUtils.testTheTopo(Resources.getResource("topologies/partial-topology.json"));
    }

    @Test
    public void testMock() throws IOException {
//        ITopology t1 = new Topology(doc);
//        IController ctrl = new MockController(t1);
//        ITopology t2 = ctrl.getTopology();

    }
}
