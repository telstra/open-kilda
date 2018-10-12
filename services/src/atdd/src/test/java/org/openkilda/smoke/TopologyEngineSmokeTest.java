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

package org.openkilda.smoke;

import com.google.common.io.Resources;
import org.openkilda.topo.*;
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
        String entity = TopologyHelp.clearTopology();
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
