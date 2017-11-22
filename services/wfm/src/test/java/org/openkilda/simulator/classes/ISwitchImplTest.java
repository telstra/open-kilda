package org.openkilda.simulator.classes;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openkilda.messaging.info.event.SwitchState;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;

import static org.junit.Assert.*;

public class ISwitchImplTest {
    private ISwitchImpl sw;
    private String dpid = "00:00:00:00:00:01";
    private int numOfPorts = 10;
    private PortStateType portState = PortStateType.DOWN;

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        sw = new ISwitchImpl(dpid, numOfPorts, portState);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testCreators() throws Exception {
            ISwitchImpl sw1 = new ISwitchImpl();
            assertEquals("00:00:00:00:00:00:00:00", sw1.getDpidAsString());
            assertEquals(0, sw1.getMaxPorts());

            ISwitchImpl sw2 = new ISwitchImpl(dpid);
            assertEquals("00:00:" + dpid, sw2.getDpidAsString());
            assertEquals(DatapathId.of(dpid), sw2.getDpid());
            assertEquals(0, sw2.getMaxPorts());

            ISwitchImpl sw3 = new ISwitchImpl(dpid, numOfPorts, portState);
            List<IPortImpl> ports = sw3.getPorts();
            assertEquals(numOfPorts, ports.size());
            for (IPortImpl port : ports) {
                assertFalse(port.isActive());
            }
    }

    @Test
    public void modState() throws Exception {
        sw.modState(SwitchState.ACTIVATED);
        assertTrue(sw.isActive());

        sw.modState(SwitchState.DEACTIVATED);
        assertFalse(sw.isActive());
    }

    @Test
    public void testActivateDeactivate() throws Exception {
        sw.deactivate();
        assertFalse(sw.isActive());

        sw.activate();
        assertTrue(sw.isActive());
    }

    @Test
    public void testSetDpid() throws Exception {
        DatapathId dpid = sw.getDpid();

        String newDpid = "01:02:03:04:05:06";
        sw.setDpid(newDpid);
        assertEquals("00:00:" + newDpid, sw.getDpidAsString());
        assertEquals(DatapathId.of(newDpid), sw.getDpid());

        sw.setDpid(dpid);
        assertEquals(dpid, sw.getDpid());
    }

    @Test
    public void addPort() throws Exception {
        int portNum = sw.getPorts().size();
        IPortImpl port = new IPortImpl(sw, PortStateType.UP, portNum);

        thrown.expect(SimulatorException.class);
        thrown.expectMessage("Switch already has reached maxPorts");
        sw.addPort(port);

    }

    @Test
    public void getPort() throws Exception {
       int numOfPorts = sw.getPorts().size();
       assertEquals(1, sw.getPort(1).getNumber());

       thrown.expect(SimulatorException.class);
       thrown.expectMessage(String.format("Port %d is not defined on %s", numOfPorts, sw.getDpidAsString()));
       sw.getPort(numOfPorts);

    }

    @Test
    public void getFlow() throws Exception {
    }

    @Test
    public void addFlow() throws Exception {
    }

    @Test
    public void modFlow() throws Exception {
    }

    @Test
    public void delFlow() throws Exception {
    }

    @Test
    public void getPortStats() throws Exception {
    }

    @Test
    public void getPortStats1() throws Exception {
    }

}