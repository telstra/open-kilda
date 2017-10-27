package org.openkilda.simulator.classes;

import org.junit.Before;
import org.junit.Test;
import org.openkilda.messaging.info.stats.PortStatsEntry;

import static org.junit.Assert.*;

public class PortTest {
    private int number = 5;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void disable() throws Exception {
        Port port = new Port(number);
        assertEquals(port.getNumber(), number);
        assertTrue(port.isActive);
        assertTrue(port.isForwarding);

        port.disable(true);
        assertFalse(port.isActive);
        assertFalse(port.isForwarding);

        port.disable(false);
        assertTrue(port.isActive);
        assertFalse(port.isForwarding);
    }

    @Test
    public void block() throws Exception {
        Port port = new Port(number);

        assertTrue(port.isForwarding);
        port.block();
        assertFalse(port.isForwarding);
        assertTrue(port.isActive);
    }

    @Test
    public void unblock() throws Exception {
        Port port = new Port(number);

        assertTrue(port.isForwarding);
        port.block();
        assertFalse(port.isForwarding);
        assertTrue(port.isActive);

        port.disable(true);
        port.unblock();
        assertTrue(port.isForwarding);
        assertTrue(port.isActive);
    }

    @Test
    public void getStats() throws Exception {
        Port port = new Port(number);

        PortStatsEntry portStatsEntry = port.getStats();
        long rxBytes = portStatsEntry.getRxBytes();
        assertNotEquals(rxBytes, 0);

        portStatsEntry = port.getStats();
        assertTrue(portStatsEntry.getRxBytes() > rxBytes);
        rxBytes = portStatsEntry.getRxBytes();

        port.block();
        assertEquals(port.getStats().getRxBytes(), rxBytes);

        port.disable(true);
        assertEquals(port.getStats().getRxBytes(), rxBytes);
    }

}