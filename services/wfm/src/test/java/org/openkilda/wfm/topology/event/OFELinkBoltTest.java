package org.openkilda.wfm.topology.event;

import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.topology.OutputCollectorMock;
import org.openkilda.wfm.topology.TopologyConfig;

import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;
import org.mockito.Mockito;

public class OFELinkBoltTest extends AbstractStormTest {
    private static final Integer TASK_ID_BOLT = 0;
    private static final String COMPONENT_ID_SOURCE = OFEventWFMTopology.SPOUT_ID_INPUT;
    private static final String STREAM_ID_INPUT = "input";

    @Test
    public void invalidJsonForDiscoveryFilter() throws CmdLineException, ConfigurationException {
        OFEventWFMTopology manager = new OFEventWFMTopology(makeLaunchEnvironment());
        TopologyConfig config = manager.getConfig();
        OFELinkBolt bolt = new OFELinkBolt(config);

        TopologyContext context = Mockito.mock(TopologyContext.class);

        Mockito.when(context.getComponentId(TASK_ID_BOLT))
                .thenReturn(COMPONENT_ID_SOURCE);
        Mockito.when(context.getComponentOutputFields(COMPONENT_ID_SOURCE, STREAM_ID_INPUT))
                .thenReturn(KafkaMessage.FORMAT);

        OutputCollectorMock outputDelegate = Mockito.spy(new OutputCollectorMock());
        OutputCollector output = new OutputCollector(outputDelegate);

        bolt.prepare(stormConfig(), context, output);
        bolt.initState(new InMemoryKeyValueState<>());

        Tuple tuple = new TupleImpl(context, new Values("{\"corrupted-json"), TASK_ID_BOLT, STREAM_ID_INPUT);
        bolt.doWork(tuple);

        Mockito.verify(outputDelegate).ack(tuple);
    }
}
