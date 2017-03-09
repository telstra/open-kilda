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
        String entity = TopologyHelp.ClearTopology();

        assertEquals("Default, initial, response from TopologyEngine", "{\"nodes\": []}",entity);
    }


    private String createTopo(URL url) throws IOException {
        String doc = Resources.toString(url, Charsets.UTF_8);
        doc = doc.replaceAll("\"dpid\": \"SW", "\"dpid\": \""); // remove any SW characters
        doc = doc.replaceAll("([0-9A-Fa-f]{2}):","$1");         // remove ':' in id
        TopologyHelp.DeleteTopology();  // clear out mininet
        String result = TopologyHelp.ClearTopology();   // clear out neo4j / topology-engine
        System.out.println("Just cleared the Topology Engine. Result = " + result);
        TopologyHelp.CreateTopology(doc);
        return doc;
    }


    private ITopology translateTestTopo(String doc) throws IOException {
        ITopology tDoc = TopologyBuilder.buildTopoFromTestJson(doc);
        System.out.println("tDoc = " + tDoc);
        System.out.println("tDoc.switchs.size = " + tDoc.getSwitches().keySet().size());
        System.out.println("tDoc.links  .size = " + tDoc.getLinks().keySet().size());
        return tDoc;
    }


    private ITopology translateTopoEngTopo(ITopology expected) throws InterruptedException,
            IOException {
        ITopology tTE = new Topology(""); // ie null topology

        // try a couple of times to get the topology;
        // TODO: this should be based off of a cucumber spec .. the cucumber tests as smoke!
        long priorSwitches = 0, priorLinks = 0;
        long expectedSwitches = expected.getSwitches().keySet().size();
        long expectedLinks = expected.getLinks().keySet().size();
        for (int i = 0; i < 4; i++) {
            Thread.yield(); // let other threads do something ..
            Thread.sleep(10000);
            String jsonFromTE = TopologyHelp.GetTopology();
            Thread.yield(); // let other threads do something ..

            tTE = TopologyBuilder.buildTopoFromTopoEngineJson(jsonFromTE);
            long numSwitches = tTE.getSwitches().keySet().size();
            long numLinks = tTE.getLinks().keySet().size();

            System.out.print("(" + numSwitches + "," + numLinks + ") ");
            System.out.flush();

            if (numSwitches != expectedSwitches){
                if (numSwitches > priorSwitches){
                    priorSwitches = numSwitches;
                    i = 0; // reset the timeout
                }
                continue;
            }

            if (numLinks != expectedLinks){
                if (numLinks > priorLinks){
                    priorLinks = numLinks;
                    i = 0; // reset the timeout
                }
                continue;
            }
            break;
        }
        System.out.println("");
        return tTE;
    }


    private void validateTopos(ITopology expected, ITopology actual){
        assertEquals("TOPOLOGY DISCOVERY: The number of SWITCHES don't match"
                ,expected.getSwitches().keySet().size()
                , actual.getSwitches().keySet().size());
        assertEquals("TOPOLOGY DISCOVERY: The number of LINKS don't match"
                ,expected.getLinks().keySet().size()
                , actual.getLinks().keySet().size());
        assertEquals("TOPOLOGY DISCOVERY: The IDs of Switches and/or Links don't match"
                , expected, actual);

    }


    private void testTheTopo(URL url){
        try {
            String doc = createTopo(url);
            ITopology expected = translateTestTopo(doc);
            ITopology actual = translateTopoEngTopo(expected);
            validateTopos(expected,actual);
        } catch (Exception e) {
            fail("Unexpected Exception:" + e.getMessage());
        }
    }


    @Test
    /**
     * A small network of switches and links (~10 switches and 10s of links. Ensure it comes up
     * properly.
     */
    public void testSimpleTopologyDiscovery() throws InterruptedException {
        testTheTopo(Resources.getResource("topologies/partial-topology.json"));
    }

    @Test
    /**
     * A medium network of switches and links (10s of switches and ~100 links. Ensure it comes
     * up properly.
     */
    public void testFullTopologyDiscovery(){
        testTheTopo(Resources.getResource("topologies/full-topology.json"));
    }

    @Test
    public void testMock() throws IOException {
//        ITopology t1 = new Topology(doc);
//        IController ctrl = new MockController(t1);
//        ITopology t2 = ctrl.getTopology();

    }
}
