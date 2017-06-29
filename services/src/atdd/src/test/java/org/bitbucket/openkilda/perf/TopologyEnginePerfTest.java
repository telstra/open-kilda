package org.bitbucket.openkilda.perf;

import com.google.common.io.Resources;
import org.bitbucket.openkilda.topo.TestUtils;
import org.junit.Test;

/**
 * Created by carmine on 5/2/17.
 */
public class TopologyEnginePerfTest {

    @Test
    /**
     * A medium network of switches and links (10s of switches and ~100 links. Ensure it comes
     * up properly.
     */
    public void testFullTopologyDiscovery(){
        TestUtils.testTheTopo(Resources.getResource("topologies/full-topology.json"));
    }


}
