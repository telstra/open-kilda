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

package org.openkilda.perf;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.openkilda.topo.TopologyHelp;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

/**
 * Created by carmine on 5/1/17.
 */
public class MininetPerfTest {

    public static final String defaultHost = "localhost";


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
