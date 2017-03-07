package org.bitbucket.openkilda.smoke;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.bitbucket.openkilda.topo.*;
import org.glassfish.jersey.client.ClientConfig;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by carmine on 3/2/17.
 */
public class TopologyEngineSmokeTest  {

    @Test
    /**
     * Several things we can test to ensure the service is up:
     * 1) The TE is off port 80, and when empty returns []
     * NB: Neo4J is off of 7474 - neo4j / temppass.  To clear everything:
     *      - ```match (n) detach delete n```
     */
    public void testServiceUp(){

        String entity = TopologyHelp.GetTopology();

        assertEquals("Default, initial, response from TopologyEngine", "{\"nodes\": []}",entity);
    }


    @Test
    /**
     * A small network of switches and links (~10 switches and 10s of links. Ensure it comes up
     * properly.
     */
    public void testSimpleTopologyDiscovery() throws InterruptedException {
        URL url = Resources.getResource("topologies/partial-topology.json");
        try {
            String doc = Resources.toString(url, Charsets.UTF_8);
//            TopologyHelp.DeleteTopology();
//            TopologyHelp.CreateTopology(doc);
            Thread.sleep(10000);
            String jsonFromTE = TopologyHelp.GetTopology();
            System.out.println("jsonFromTE = " + jsonFromTE);

            if (1==1) return;
            ITopology t = TopologyBuilder.buildTopoFromTestJson(doc);
            ITopology t1 = new Topology(doc);
            IController ctrl = new MockController(t1);
            ITopology t2 = ctrl.getTopology();
            assertTrue(t1.equivalent(t2));
        } catch (IOException e) {
            fail("Unexpected Exception:" + e.getMessage());
        }

    }


    @Test
    /**
     * A medium network of switches and links (10s of switches and ~100 links. Ensure it comes
     * up properly.
     */
    public void testFullTopologyDiscovery(){
    }



    @Test
    public void testTemp(){
        String jsonFromTE = "jsonFromTE = "
                + "{\"nodes\": "
                    + "[{\"name\": \"00:00:00:00:00:00:00:05\", \"outgoing_relationships\": "
                        +  "[\"00:00:00:00:00:00:00:04\", \"00:00:00:00:00:00:00:04\", \"00:00:00:00:00:00:00:06\", \"00:00:00:00:00:00:00:06\", \"00:00:00:00:00:00:00:07\", \"00:00:00:00:00:00:00:07\"]} "
                    + ", {\"name\": \"00:00:00:00:00:00:00:07\", \"outgoing_relationships\": "
                        +  "[\"00:00:00:00:00:00:00:04\", \"00:00:00:00:00:00:00:04\", \"00:00:00:00:00:00:00:05\", \"00:00:00:00:00:00:00:05\", \"00:00:00:00:00:00:00:06\", \"00:00:00:00:00:00:00:06\"]} "
                    + ", {\"name\": \"00:00:00:00:00:00:00:09\"} "
                    + ", {\"name\": \"00:00:00:00:00:00:00:06\", \"outgoing_relationships\": "
                        + "[\"00:00:00:00:00:00:00:05\", \"00:00:00:00:00:00:00:05\", \"00:00:00:00:00:00:00:07\", \"00:00:00:00:00:00:00:07\"]} "
                    + ", {\"name\": \"00:00:00:00:00:00:00:08\"} "
                    + ", {\"name\": \"00:00:00:00:00:00:00:01\", \"outgoing_relationships\": "
                        + "[\"00:00:00:00:00:00:00:02\", \"00:00:00:00:00:00:00:02\", \"00:00:00:00:00:00:00:03\", \"00:00:00:00:00:00:00:03\"]}"
                    + ", {\"name\": \"00:00:00:00:00:00:00:02\", \"outgoing_relationships\": "
                        + "[\"00:00:00:00:00:00:00:01\", \"00:00:00:00:00:00:00:01\", \"00:00:00:00:00:00:00:03\", \"00:00:00:00:00:00:00:03\", \"00:00:00:00:00:00:00:03\", \"00:00:00:00:00:00:00:03\"]}"
                    + ", {\"name\": \"00:00:00:00:00:00:00:04\", \"outgoing_relationships\": "
                        + "[\"00:00:00:00:00:00:00:05\", \"00:00:00:00:00:00:00:05\", \"00:00:00:00:00:00:00:07\", \"00:00:00:00:00:00:00:07\"]}"
                    + ", {\"name\": \"00:00:00:00:00:00:00:03\", \"outgoing_relationships\": " +
                "[\"00:00:00:00:00:00:00:01\", \"00:00:00:00:00:00:00:01\", \"00:00:00:00:00:00:00:02\", \"00:00:00:00:00:00:00:02\", \"00:00:00:00:00:00:00:02\", \"00:00:00:00:00:00:00:02\"]}]}";

        ITopology t = TopologyBuilder.buildTopoFromTopoEngineJson(doc);
        System.out.println("jsonFromTE = " + jsonFromTE);
    }
}
