package org.openkilda.simulator.bolts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.junit.Before;
import org.junit.Test;
import org.openkilda.simulator.classes.SimulatorCommands;
import org.openkilda.simulator.messages.LinkMessage;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SimulatorCommandBoltTest {
    SimulatorCommandBolt simulatorCommandBolt;
    ObjectMapper mapper;

    @Before
    public void setUp() throws Exception {
        simulatorCommandBolt = new SimulatorCommandBolt();
        mapper = new ObjectMapper();
    }

    @Test
    public void testLinkMessage() throws Exception {
//        String dpid = "00:00:00:00:00:00:00:01";
//        int latency = 10;
//        int localPort = 5;
//        String peerSwitch = "00:00:00:00:00:00:00:01";
//        int peerPort = 8;
//        Tuple tuple = mock(Tuple.class);
//        LinkMessage linkMessage = new LinkMessage(latency, localPort, peerSwitch, peerPort);
//        when(tuple.getString(0)).thenReturn(mapper.writeValueAsString(linkMessage));
//
//        Map<String, Object> values = simulatorCommandBolt.doCommand(tuple);
//        assertTrue(values.containsKey("stream"));
//        assertTrue(values.containsKey("values"));
//        assertThat(values.get("values"), instanceOf(List.class));
//
//        List<Values> v = (List<Values>) values.get("values");
//        assertEquals(1, v.size());
//        assertThat(v.get(0).get(1), instanceOf(LinkMessage.class));
//        assertEquals(SimulatorCommands.DO_ADD_LINK, v.get(0).get(2));

    }

}