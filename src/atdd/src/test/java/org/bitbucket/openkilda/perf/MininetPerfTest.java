package org.bitbucket.openkilda.perf;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.bitbucket.openkilda.topo.TopologyHelp;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

/**
 * Created by carmine on 5/1/17.
 */
public class MininetPerfTest {

    private void testIt(URL url) throws IOException {
        TopologyHelp.DeleteMininetTopology();  // clear out mininet
        String result = TopologyHelp.ClearTopology();   // clear out neo4j / topology-engine
        System.out.println("Just cleared the Topology Engine. Result = " + result);

        String doc = Resources.toString(url, Charsets.UTF_8);
        TopologyHelp.TestMininetCreate(doc);
    }

    @Test
    public void test5_20() throws IOException {
        testIt(Resources.getResource("topologies/rand-5-20.json"));
    }

    @Test
    public void test50_150() throws IOException {
        testIt(Resources.getResource("topologies/rand-50-150.json"));
    }

    @Test
    public void test200_500() throws IOException {
        testIt(Resources.getResource("topologies/rand-200-500.json"));
    }


}
