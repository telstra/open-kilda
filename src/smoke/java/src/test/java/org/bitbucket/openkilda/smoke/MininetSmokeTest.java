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
 * Created by carmine on 3/10/17.
 */
public class MininetSmokeTest {

    private void testIt(URL url) throws IOException {
        TopologyHelp.DeleteTopology();  // clear out mininet
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
