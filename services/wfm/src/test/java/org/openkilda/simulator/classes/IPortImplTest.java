package org.openkilda.simulator.classes;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openkilda.simulator.interfaces.IPort;

import static org.junit.Assert.*;

public class IPortImplTest {
    IPortImpl port;
    int portNum = 1;

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        port = new IPortImpl(PortStateType.DOWN, portNum);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testInit() throws Exception {
        int portNum = 5;
        IPortImpl port = new IPortImpl(PortStateType.DOWN, portNum);
        assertFalse(port.isActive());
        assertEquals(portNum, port.getNumber());

        port = new IPortImpl(PortStateType.UP, portNum);
        assertTrue(port.isActive());
    }

    @Test
    public void testEnableDisable() throws Exception {
        port.enable();
        assertTrue(port.isActive());

        port.disable();
        assertFalse(port.isActive());
    }

    @Test
    public void testBlockUnblock() throws Exception {
        port.block();
        assertFalse(port.isForwarding());

        port.unblock();
        assertTrue(port.isForwarding());
    }

    @Test
    public void isActiveIsl() throws Exception {
        port.enable();
        port.setPeerSwitch("00:00:00:00:00:01");
        port.setPeerPortNum(10);
        assertTrue(port.isActiveIsl());

        port.disable();
        assertFalse(port.isActiveIsl());

        port.enable();
        port.block();
        assertFalse(port.isActiveIsl());

        port.unblock();
        assertTrue(port.isActiveIsl());

        port.disable();
        port.unblock();
        assertFalse(port.isActiveIsl());
    }

    @Test
    public void getStats() throws Exception {
    }

}