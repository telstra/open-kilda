package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.flowName;
import static org.bitbucket.openkilda.messaging.command.Constants.inputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.bitbucket.openkilda.messaging.command.Constants.transitVlanId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class InstallTransitFlowTest {
    private InstallTransitFlow flow = new InstallTransitFlow(0L,
            flowName, 0L, switchId, inputPort, outputPort, transitVlanId);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getTransitVlanId() throws Exception {
        assertEquals(transitVlanId, flow.getTransitVlanId().intValue());
    }

    @Test
    public void setTransitVlanId() throws Exception {
        flow.setTransitVlanId(transitVlanId);
        assertEquals(transitVlanId, flow.getTransitVlanId().intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullTransitVlanId() throws Exception {
        flow.setTransitVlanId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setZeroTransitVlanId() throws Exception {
        flow.setTransitVlanId(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setTooBigTransitVlanId() throws Exception {
        flow.setTransitVlanId(4096);
    }
}
