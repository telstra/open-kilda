package org.openkilda.simulator.classes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;

import static org.junit.Assert.*;

public class SwitchTest {
    private final String DPID = "00:00:00:00:00:00:00:01";
    private final int NUM_OF_INTS = 10;

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void portStats() throws Exception {
        Switch sw = new Switch(DatapathId.of(DPID), NUM_OF_INTS);

        assertEquals(sw.dpid.toString(), DPID);
        assertEquals(NUM_OF_INTS, sw.numOfPorts());

        InfoMessage message = sw.portStats();
        assertEquals("simulator", message.getCorrelationId());
        assertEquals(Destination.WFM, message.getDestination());
        assertNotNull(message.getTimestamp());

        assertNotNull(message.getData());
        PortStatsData portStatsData = (PortStatsData) message.getData();
        assertEquals(DPID, portStatsData.getSwitchId().toString());
        List<PortStatsReply> portStatsReplies = portStatsData.getStats();
        assertEquals(1, portStatsReplies.size());
        assertEquals(NUM_OF_INTS, portStatsReplies.get(0).getEntries().size());
    }

    @Test
    public void addPort() throws Exception {
        Switch sw = new Switch(DatapathId.of(DPID));

        assertEquals(0, sw.numOfPorts());
        sw.addPort(true, true);
        assertEquals(1, sw.numOfPorts());
    }

}