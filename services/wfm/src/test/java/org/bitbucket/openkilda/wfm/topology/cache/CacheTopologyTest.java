package org.bitbucket.openkilda.wfm.topology.cache;

import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.wfm.AbstractStormTest;
import org.bitbucket.openkilda.wfm.topology.flow.FlowTopology;
import org.bitbucket.openkilda.wfm.topology.flow.FlowTopologyTest;

import org.apache.storm.Config;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.utils.Utils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CacheTopologyTest extends AbstractStormTest {
    private static CacheTopology cacheTopology;

    @BeforeClass
    public static void setupOnce() throws Exception {
        AbstractStormTest.setupOnce();

        cacheTopology = new CacheTopology();
        StormTopology stormTopology = cacheTopology.createTopology();
        Config config = stormConfig();
        cluster.submitTopology(CacheTopologyTest.class.getSimpleName(), config, stormTopology);

        Utils.sleep(10000);
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        AbstractStormTest.teardownOnce();
    }

    @Test
    public void someTest() throws Exception {
        System.out.println("Some Test");
        assertTrue(true);
    }
}
