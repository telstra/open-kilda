package org.bitbucket.openkilda.pce;

import org.junit.Test;

public class TopologyTest {
    StorageMock storage = new StorageMock();
    Topology topology = Topology.getTopologyManager(storage);

    @Test
    public void testTopologyManager() {
    }
}