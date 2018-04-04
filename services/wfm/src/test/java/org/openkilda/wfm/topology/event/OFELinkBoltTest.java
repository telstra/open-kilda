package org.openkilda.wfm.topology.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.only;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.openkilda.messaging.command.discovery.SyncNetworkCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.discovery.NetworkSyncMarker;
import org.openkilda.wfm.AbstractStormTest;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.protocol.KafkaMessage;
import org.openkilda.wfm.topology.OutputCollectorMock;
import org.openkilda.wfm.topology.TopologyConfig;

import java.io.IOException;
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

        assertEquals(OFELinkBolt.STREAM_ID_SPEAKER, captorStreamId.getValue());

        Values values = captorTuple.getValue();
        String payload = (String) values.get(1);

        CommandMessage message = objectMapper.readValue(payload, CommandMessage.class);
        assertTrue(message.getData() instanceof SyncNetworkCommandData);

    }

    public void initBolt() throws JsonProcessingException {
        Tuple dumpTuple = getDumpTuple();
        bolt.doWork(dumpTuple);
    }

    public Tuple getDumpTuple() throws JsonProcessingException {
        NetworkSyncMarker syncMarker = new NetworkSyncMarker();

        InfoMessage info = new InfoMessage(syncMarker, 0, DEFAULT_CORRELATION_ID, Destination.WFM);
        String request = objectMapper.writeValueAsString(info);
        return new TupleImpl(context, new Values(request), TASK_ID_BOLT,
                STREAM_ID_INPUT);
    }
}
