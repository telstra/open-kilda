package org.openkilda.wfm.topology.event;

import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.topology.OutputCollectorMock;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;
import org.mockito.Mockito;

public class OFELinkBoltTest extends AbstractStormTest {

    private static final Integer TASK_ID_BOLT = 0;
    private static final String STREAM_ID_INPUT = "input";
    private TopologyContext context;
    private OFELinkBolt bolt;
    private OutputCollectorMock outputDelegate;
    private OFEventWfmTopologyConfig config;

    @Before
    public void before() throws CmdLineException, ConfigurationException {
        OFEventWFMTopology manager = new OFEventWFMTopology(
                AbstractStormTest.makeLaunchEnvironment());
        config = manager.getConfig();
        bolt = new OFELinkBolt(config);

        context = Mockito.mock(TopologyContext.class);

        Mockito.when(context.getComponentId(TASK_ID_BOLT))
                .thenReturn(OFEventWFMTopology.DISCO_SPOUT_ID);
        Mockito.when(context.getComponentOutputFields(OFEventWFMTopology.DISCO_SPOUT_ID, STREAM_ID_INPUT))
                .thenReturn(KafkaMessage.FORMAT);

        outputDelegate = Mockito.spy(new OutputCollectorMock());
        OutputCollector output = new OutputCollector(outputDelegate);

        bolt.prepare(stormConfig(), context, output);
        bolt.initState(new InMemoryKeyValueState<>());
    }

    @Test
    public void invalidJsonForDiscoveryFilter() throws JsonProcessingException {
        Tuple tuple = new TupleImpl(context, new Values("{\"corrupted-json"), TASK_ID_BOLT,
                STREAM_ID_INPUT);
        bolt.doWork(tuple);

        Mockito.verify(outputDelegate).ack(tuple);
    }
}
