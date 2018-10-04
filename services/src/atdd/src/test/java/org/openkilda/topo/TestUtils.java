/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.topo;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.openkilda.KafkaUtils;
import org.openkilda.topo.builders.TeTopologyParser;
import org.openkilda.topo.builders.TestTopologyBuilder;
import org.openkilda.topo.exceptions.TopologyProcessingException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.Uninterruptibles;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Created by carmine on 2/27/17.
 */
public class TestUtils {

    public static void validateTopos(ITopology expected, ITopology actual) {
        if (expected.getSwitches().keySet().size() != actual.getSwitches().keySet().size()) {
            System.out.println("TOPOLOGY DISCOVERY: The number of SWITCHES don't match");
            System.out.println("expected.getSwitches().keySet() = " + expected.getSwitches().keySet());
            System.out.println("actual.getSwitches().keySet()   = " + actual.getSwitches().keySet());

            assertEquals(expected.getSwitches().keySet().size()
                    , actual.getSwitches().keySet().size());
        }
        assertEquals("TOPOLOGY DISCOVERY: The number of LINKS don't match"
                , expected.getLinks().keySet().size()
                , actual.getLinks().keySet().size());
        assertEquals("TOPOLOGY DISCOVERY: The IDs of SWITCHES don't match"
                , toUpper(expected.getSwitches().keySet()), toUpper(actual.getSwitches().keySet()));
        simpleLinkComparison(expected.getLinks().keySet(), actual.getLinks().keySet());
    }

    /* ensure everything is uppercase .. useful for testing intent .. would prefer exactness */
    private static Set<String> toUpper(Set<String> ss) {
        Set<String> result = new TreeSet<>();
        for (String s : ss) {
            result.add(s.toUpperCase());
        }
        return result;
    }

    private static Set<String> toUpperSubstring(Set<String> ss, int start, int end) {
        Set<String> result = new TreeSet<>();
        // break apart src->dst into src and dst, truncate each, then reassemble.
        for (String s : ss) {
            String[] trunc = s.toUpperCase().split(TopoSlug.EP_DELIM);
            trunc[0] = trunc[0].substring(start, end);
            trunc[1] = trunc[1].substring(start, end);
            result.add(trunc[0] + TopoSlug.EP_DELIM + trunc[1]);
        }
        return result;
    }


    /** this method will ignore case and ignore port / queue information, if it exists */
    private static void simpleLinkComparison(Set<String> expected, Set<String> actual) {
        // TODO: remove this comparison once port/queue info is used universally
        String[] s1 = toUpperSubstring(expected, 0, 23).toArray(new String[expected.size()]);   // upper case and sorted
        String[] s2 = toUpperSubstring(actual, 0, 23).toArray(new String[actual.size()]);       // upper case and sorted
        for (int i = 0; i < s1.length; i++) {
            assertEquals(format("Link IDs - test failed[%d]: %s,%s", i, s1[i], s2[i]), s1[i], s2[i]);
        }
    }

    public static String createMininetTopoFromFile(URL url) {
        String doc;
        try {
            doc = Resources.toString(url, Charsets.UTF_8);
        } catch (IOException ex) {
            throw new TopologyProcessingException(format("Unable to read the topology file '%s'.", url), ex);
        }
        doc = doc.replaceAll("\"dpid\": \"SW", "\"dpid\": \""); // remove any SW characters
        doc = doc.replaceAll("([0-9A-Fa-f]{2}):", "$1");         // remove ':' in id
        TopologyHelp.createMininetTopology(doc);
        return doc;
    }

    /**
     * @param doc Generally, the String returned from createMininetTopoFromFile
     */
    private static ITopology translateTestTopo(String doc) {
        ITopology tDoc = TestTopologyBuilder.buildTopoFromTestJson(doc);
        return tDoc;
    }

    /**
     * Get the topology from the Topology Engine, compare against the expected (switch/link count)
     * until it matches or times out.
     */
    public static ITopology translateTopoEngTopo(ITopology expected) throws InterruptedException {
        long expectedSwitches = expected.getSwitches().keySet().size();
        long expectedLinks = expected.getLinks().keySet().size();
        return checkAndGetTopo(expectedSwitches, expectedLinks);
    }


    /**
     * Queries for the Topology from the TE, counting switches and links, until they match the
     * expected counts or it times out.
     *
     * @param expectedSwitches
     * @param expectedLinks
     * @return the topology as of the last query
     * @throws InterruptedException
     */
    public static ITopology checkAndGetTopo(long expectedSwitches, long expectedLinks) throws
            InterruptedException {
        ITopology tTE = new Topology(); // ie null topology

        // try a couple of times to get the topology;
        // TODO: this should be based off of a cucumber spec .. the cucumber tests as smoke!
        long priorSwitches = 0, priorLinks = 0;
        for (int i = 0; i < 4; i++) {
            Thread.yield(); // let other threads do something ..
            TimeUnit.SECONDS.sleep(10);
            String jsonFromTE = TopologyHelp.getTopology();
            Thread.yield(); // let other threads do something ..

            tTE = TeTopologyParser.parseTopologyEngineJson(jsonFromTE);
            long numSwitches = tTE.getSwitches().keySet().size();
            long numLinks = tTE.getLinks().keySet().size();

            System.out.print("(" + numSwitches + "," + numLinks + ") ");
            System.out.print("of (" + expectedSwitches + "," + expectedLinks + ") .. ");
            System.out.flush();

            if (numSwitches != expectedSwitches) {
                if (numSwitches > priorSwitches) {
                    priorSwitches = numSwitches;
                    i = 0; // reset the timeout
                }
                continue;
            }

            if (numLinks != expectedLinks) {
                if (numLinks > priorLinks) {
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


    /**
     * Default behavior - hit localhost
     */
    public static void clearEverything() throws InterruptedException, IOException {
        clearEverything("localhost");
    }

    /**
     * @param endpoint the kilda endpoint to clear
     */
    public static void clearEverything(String endpoint) throws InterruptedException, IOException {
        String expected = "{\"nodes\": []}";
        TopologyHelp.deleteMininetTopology();
        // Give Mininet some time to clear things naturally
        TimeUnit.MILLISECONDS.sleep(3000);

        // verify it is empty
        String entity = TopologyHelp.clearTopology();

        for (int i = 0; i < 2; i++) {
            if (!expected.equals(entity)) {
                TimeUnit.MILLISECONDS.sleep(2000);
                entity = TopologyHelp.clearTopology();
                assertEquals("Default, initial, response from TopologyEngine", expected, entity);
            } else {
                break;
            }
        }

        KafkaUtils kafkaUtils = new KafkaUtils();
        // TODO: the topology name is environment dependent and may be changed during deployment,
        // so the solution on how to send a Ctrl request should be reconsidered.
        kafkaUtils.clearTopologyComponentState("wfm", "OFELinkBolt");

        Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
    }

    /**
     * The composite test .. going from a resources/topologies/* file to Kilda and back
     */
    public static void testTheTopo(URL url) {
        try {
            clearEverything();
            String doc = createMininetTopoFromFile(url);
            ITopology expected = translateTestTopo(doc);
            ITopology actual = translateTopoEngTopo(expected);
            validateTopos(expected, actual);
        } catch (Exception e) {
            fail("Unexpected Exception:" + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Topology t = TestTopologyBuilder.buildLinearTopo(5);
        System.out.println("t1 = " + t);

        t = TestTopologyBuilder.buildTreeTopo(3, 3);
        System.out.println("t2 = " + t.printSwitchConnections());

        System.out.println("t2.size = " + t.getSwitches().keySet().size());

    }
}
