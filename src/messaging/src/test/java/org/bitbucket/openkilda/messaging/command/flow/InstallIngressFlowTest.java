package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.bandwidth;
import static org.bitbucket.openkilda.messaging.command.Constants.flowName;
import static org.bitbucket.openkilda.messaging.command.Constants.inputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.inputVlanId;
import static org.bitbucket.openkilda.messaging.command.Constants.meterId;
import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.outputVlanType;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.bitbucket.openkilda.messaging.command.Constants.transitVlanId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Created by atopilin on 10/04/2017.
 */
public class InstallIngressFlowTest {
    private InstallIngressFlow flow = new InstallIngressFlow(0L, flowName, 0L, switchId, inputPort,
            outputPort, inputVlanId, transitVlanId, outputVlanType, bandwidth, meterId);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getBandwidth() throws Exception {
        assertEquals(bandwidth, flow.getBandwidth().longValue());
    }

    @Test
    public void getMeterId() throws Exception {
        assertEquals(meterId, flow.getMeterId().longValue());
    }

    @Test
    public void getInputVlanId() throws Exception {
        assertEquals(inputVlanId, flow.getInputVlanId().intValue());
    }

    @Test
    public void setBandwidth() throws Exception {
        flow.setBandwidth(bandwidth);
        assertEquals(bandwidth, flow.getBandwidth().intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullBandwidth() throws Exception {
        flow.setBandwidth(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeBandwidth() throws Exception {
        flow.setBandwidth(-1L);
    }

    @Test
    public void setMeterId() throws Exception {
        flow.setMeterId(meterId);
        assertEquals(meterId, flow.getMeterId().longValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullMeterId() throws Exception {
        flow.setMeterId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeMeterId() throws Exception {
        flow.setMeterId(-1L);
    }

    @Test
    public void setInputVlanId() throws Exception {
        flow.setInputVlanId(inputVlanId);
        assertEquals(inputVlanId, flow.getInputVlanId().intValue());
    }

    @Test
    public void setNullInputVlanId() throws Exception {
        flow.setInputVlanId(null);
        assertEquals(0, flow.getInputVlanId().intValue());
    }

    @Test
    public void setZeroInputVlanId() throws Exception {
        flow.setInputVlanId(0);
        assertEquals(0, flow.getInputVlanId().intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeInputVlanId() throws Exception {
        flow.setInputVlanId(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setTooBigInputVlanId() throws Exception {
        flow.setInputVlanId(4096);
    }
}
