package org.bitbucket.openkilda.wfm.topology.stats;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.bitbucket.openkilda.wfm.AbstractStormTest;
import org.bitbucket.openkilda.wfm.KafkaUtils;
import org.bitbucket.openkilda.wfm.topology.OutputCollectorMock;
import org.bitbucket.openkilda.wfm.topology.islstats.IslStatsTopology;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IslStatsTopologyTest extends AbstractStormTest {
    private long messagesExpected;
    private long messagesReceived;
    private KafkaUtils kutils;

    @Mock
    private TopologyContext topologyContext;
    private OutputCollectorMock outputCollectorMock = new OutputCollectorMock();
    private OutputCollector outputCollector = new OutputCollector(outputCollectorMock);

    // Leaving these here as a tickler if needed.
    @Before
    public void setupEach() {
    }

    @After
    public void teardownEach() {
    }

    @Test
    public void IslStatsTopologyTest() throws Exception {

    }
}
