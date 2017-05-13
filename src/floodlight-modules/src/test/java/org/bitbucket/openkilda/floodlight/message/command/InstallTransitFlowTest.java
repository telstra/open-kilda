package org.bitbucket.openkilda.floodlight.message.command;

import org.bitbucket.openkilda.messaging.command.flow.InstallTransitFlowCommandData;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class InstallTransitFlowTest {
    private InstallTransitFlowCommandData flow = new InstallTransitFlowCommandData(flowName, switchId, inputPort, outputPort, transitVlanId);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getTransitVlanId() throws Exception {
        assertEquals(transitVlanId, flow.getTransitVlanId());
    }

    @Test
    public void setTransitVlanId() throws Exception {
        flow.setTransitVlanId(transitVlanId);
        assertEquals(transitVlanId, flow.getTransitVlanId());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullTransitVlanId() throws Exception {
        flow.setTransitVlanId(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setZeroTransitVlanId() throws Exception {
        flow.setTransitVlanId(0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setTooBigTransitVlanId() throws Exception {
        flow.setTransitVlanId(4096);
    }
}
