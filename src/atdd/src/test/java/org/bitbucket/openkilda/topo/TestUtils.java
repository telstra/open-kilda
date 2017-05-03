package org.bitbucket.openkilda.topo;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by carmine on 2/27/17.
 */
public class TestUtils {

    public static void validateTopos(ITopology expected, ITopology actual){
        if (expected.getSwitches().keySet().size() != actual.getSwitches().keySet().size()) {
            System.out.println("TOPOLOGY DISCOVERY: The number of SWITCHES don't match");
            System.out.println("expected.getSwitches().keySet() = " + expected.getSwitches().keySet());
            System.out.println("actual.getSwitches().keySet()   = " +   actual.getSwitches().keySet());

            assertEquals(expected.getSwitches().keySet().size()
                    , actual.getSwitches().keySet().size());
        }
        assertEquals("TOPOLOGY DISCOVERY: The number of LINKS don't match"
                ,expected.getLinks().keySet().size()
                , actual.getLinks().keySet().size());
        assertEquals("TOPOLOGY DISCOVERY: The IDs of Switches and/or Links don't match"
                , expected, actual);
    }

    public static String createMininetTopoFromFile(URL url) throws IOException {
        String doc = Resources.toString(url, Charsets.UTF_8);
        doc = doc.replaceAll("\"dpid\": \"SW", "\"dpid\": \""); // remove any SW characters
        doc = doc.replaceAll("([0-9A-Fa-f]{2}):","$1");         // remove ':' in id
        TopologyHelp.CreateMininetTopology(doc);
        return doc;
    }

    /**
     * @param doc Generally, the String returned from createMininetTopoFromFile
     */
    private static ITopology translateTestTopo(String doc) throws IOException {
        ITopology tDoc = TopologyBuilder.buildTopoFromTestJson(doc);
        return tDoc;
    }

    /**
     * Get the topology from the Topology Engine, with some heuristics related to how long it might
     * take to propagate. This could be fed in as part of a cucumber test (ie converge within..)
     */
    public static ITopology translateTopoEngTopo(ITopology expected) throws InterruptedException,
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

    public static void clearEverything(){
        TopologyHelp.DeleteMininetTopology();

        // verify it is empty
        String entity = TopologyHelp.ClearTopology();
        assertEquals("Default, initial, response from TopologyEngine", "{\"nodes\": []}",entity);

        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
    }

    /**
     * The composite test .. going from a resources/topologies/* file to Kilda and back
     */
    public static void testTheTopo(URL url){
        try {
            clearEverything();
            String doc = createMininetTopoFromFile(url);
            ITopology expected = translateTestTopo(doc);
            ITopology actual = translateTopoEngTopo(expected);
            validateTopos(expected,actual);
        } catch (Exception e) {
            fail("Unexpected Exception:" + e.getMessage());
        }
    }





    public static void main(String[] args) {
        ITopology t = TopologyBuilder.buildLinearTopo(5);
        System.out.println("t1 = " + t);

        t = TopologyBuilder.buildTreeTopo(3,3);
        System.out.println("t2 = " + t.printSwitchConnections());

        System.out.println("t2.size = " + t.getSwitches().keySet().size());

    }
}
