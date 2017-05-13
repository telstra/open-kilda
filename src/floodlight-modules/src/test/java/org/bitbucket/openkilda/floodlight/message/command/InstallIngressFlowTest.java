package org.bitbucket.openkilda.floodlight.message.command;

import org.bitbucket.openkilda.messaging.command.flow.InstallIngressFlowCommandData;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class InstallIngressFlowTest {
    private InstallIngressFlowCommandData flow = new InstallIngressFlowCommandData(flowName, switchId, inputPort,
            outputPort, inputVlanId, transitVlanId, outputVlanType, bandwidth, meterId);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getBandwidth() throws Exception {
        assertEquals(bandwidth, flow.getBandwidth());
    }

    @Test
    public void getMeterId() throws Exception {
        assertEquals(meterId, flow.getMeterId());
    }

    @Test
    public void getInputVlanId() throws Exception {
        assertEquals(inputVlanId, flow.getInputVlanId());
    }

    @Test
    public void setBandwidth() throws Exception {
        flow.setBandwidth(bandwidth);
        assertEquals(bandwidth, flow.getBandwidth());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullBandwidth() throws Exception {
        flow.setBandwidth(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeBandwidth() throws Exception {
        flow.setBandwidth(-1);
    }

    @Test
    public void setMeterId() throws Exception {
        flow.setMeterId(meterId);
        assertEquals(meterId, flow.getMeterId());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullMeterId() throws Exception {
        flow.setMeterId(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeMeterId() throws Exception {
        flow.setMeterId(-1);
    }

    @Test
    public void setInputVlanId() throws Exception {
        flow.setInputVlanId(inputVlanId);
        assertEquals(inputVlanId, flow.getInputVlanId());
    }

    @Test
    public void setNullInputVlanId() throws Exception {
        flow.setInputVlanId(null);
        assertEquals(0, flow.getInputVlanId());
    }

    @Test
    public void setZeroInputVlanId() throws Exception {
        flow.setInputVlanId(0);
        assertEquals(0, flow.getInputVlanId());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeInputVlanId() throws Exception {
        flow.setInputVlanId(-1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setTooBigInputVlanId() throws Exception {
        flow.setInputVlanId(4096);
    }
}
