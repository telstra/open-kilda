package org.openkilda.wfm.topology.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.only;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.info.event.PortChangeType.UP;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkInfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.messaging.model.DiscoveryNode;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.topology.OutputCollectorMock;
import org.openkilda.wfm.topology.TopologyConfig;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class OFELinkBoltTest extends AbstractStormTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Integer TASK_ID_BOLT = 0;
    private static final String COMPONENT_ID_SOURCE = OFEventWFMTopology.SPOUT_ID_INPUT;
    private static final String STREAM_ID_INPUT = "input";
    private TopologyContext context;
    private OFELinkBolt bolt;
    private OutputCollectorMock outputDelegate;
    private TopologyConfig config;

    @Before
    public void before() throws CmdLineException, ConfigurationException {
        OFEventWFMTopology manager = new OFEventWFMTopology(
                AbstractStormTest.makeLaunchEnvironment());
        config = manager.getConfig();
        bolt = new OFELinkBolt(config);

        context = Mockito.mock(TopologyContext.class);

        Mockito.when(context.getComponentId(TASK_ID_BOLT))
                .thenReturn(COMPONENT_ID_SOURCE);
        Mockito.when(context.getComponentOutputFields(COMPONENT_ID_SOURCE, STREAM_ID_INPUT))
                .thenReturn(KafkaMessage.FORMAT);

        outputDelegate = Mockito.spy(new OutputCollectorMock());
        OutputCollector output = new OutputCollector(outputDelegate);

        bolt.prepare(stormConfig(), context, output);
        bolt.initState(new InMemoryKeyValueState<>());
    }


    @Test
    public void invalidJsonForDiscoveryFilter() throws JsonProcessingException {

        initBolt();

        Tuple tuple = new TupleImpl(context, new Values("{\"corrupted-json"), TASK_ID_BOLT,
                STREAM_ID_INPUT);
        bolt.doWork(tuple);

        Mockito.verify(outputDelegate).ack(tuple);
    }

    /**
     * Basic warm mechanism check. We push tuple to not initialized bolt and check that tuple is
     * not acked then we push init tuple to bolt and verify then next tuple will be ack.
     */
    @Test
    public void preventNotInitializedUsage() throws JsonProcessingException {

        // send tuple to non init bolt
        Tuple tuple = new TupleImpl(context, new Values(""), TASK_ID_BOLT, STREAM_ID_INPUT);
        bolt.doWork(tuple);

        // we don't process tuples while bolt not init
        Mockito.verify(outputDelegate).fail(tuple);

        // let we send dump data to bolt
        Tuple dumpTuple = getDumpTuple();
        bolt.doWork(dumpTuple);

        // we don't process tuples while bolt not init
        Mockito.verify(outputDelegate).ack(dumpTuple);

        // bolt is initialized so we can push out old tuple
        bolt.doWork(tuple);

        // and now it must be ack
        Mockito.verify(outputDelegate).ack(tuple);
    }

    /**
     * Part of warm mechanism checks. That test verifies doTick sends init message to FL and
     * second tick does nothing.
     */
    @Test
    public void cacheRequestCheck() throws IOException {
        // do Tick and verify that bolt send message with cache request to right topic

        Tuple tuple = new TupleImpl(context, new Values(""), TASK_ID_BOLT, STREAM_ID_INPUT);
        bolt.doTick(tuple);
        bolt.doTick(tuple);

        ArgumentCaptor<String> captorStreamId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<Tuple>> captorAnchor = ArgumentCaptor.forClass((Class) List.class);
        ArgumentCaptor<Values> captorTuple = ArgumentCaptor.forClass(Values.class);

        Mockito.verify(outputDelegate, only()).emit(captorStreamId.capture(),
                captorAnchor.capture(),
                captorTuple.capture());

        assertEquals(config.getKafkaSpeakerTopic(), captorStreamId.getValue());

        Values values = captorTuple.getValue();
        String payload = (String) values.get(1);

        CommandMessage commandMessage = objectMapper.readValue(payload, CommandMessage.class);
        assertTrue(commandMessage.getData() instanceof NetworkCommandData);

    }

    /**
     * Part of warm mechanism checks. That test verifies appropriately unpack and fill inner
     * cache after getting network dump information from FL.
     */
    @Test
    public void cacheLoadCheck() throws IOException {
        // Send cache data and verify is inner state is ok

        SwitchInfoData sw1 = new SwitchInfoData("sw1", SwitchState.ADDED, "127.0.0.1", "localhost",
                "test switch", "kilda");
        SwitchInfoData sw2 = new SwitchInfoData("sw2", SwitchState.ADDED, "127.0.0.1", "localhost",
                "test switch", "kilda");

        PortInfoData sw1Port1 = new PortInfoData(sw1.getSwitchId(), 1, null, UP);
        PortInfoData sw2Port1 = new PortInfoData(sw2.getSwitchId(), 1, null, UP);

        NetworkInfoData dump = new NetworkInfoData(
                "test",
                new HashSet<>(Arrays.asList(sw1, sw2)),
                new HashSet<>(Arrays.asList(sw1Port1, sw2Port1)),
                Collections.emptySet(),
                Collections.emptySet());

        InfoMessage info = new InfoMessage(dump, 0, DEFAULT_CORRELATION_ID, Destination.WFM);
        String request = objectMapper.writeValueAsString(info);
        Tuple dumpTuple = new TupleImpl(context, new Values(request), TASK_ID_BOLT,
                STREAM_ID_INPUT);
        bolt.doWork(dumpTuple);

        List<DiscoveryNode> discoveryQueue = bolt.getDiscoveryQueue();

        // I don't check discoveryQueue contents because that should be done in a test for
        // handleSwitchEvent and handlePortEvent.
        assertEquals(2, discoveryQueue.size());
    }


    public void initBolt() throws JsonProcessingException {
        Tuple dumpTuple = getDumpTuple();
        bolt.doWork(dumpTuple);
    }

    public Tuple getDumpTuple() throws JsonProcessingException {
        NetworkInfoData dump = new NetworkInfoData(
                "test", Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptySet());

        InfoMessage info = new InfoMessage(dump, 0, DEFAULT_CORRELATION_ID, Destination.WFM);
        String request = objectMapper.writeValueAsString(info);
        return new TupleImpl(context, new Values(request), TASK_ID_BOLT,
                STREAM_ID_INPUT);
    }
}
