package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.bandwidth;
import static org.bitbucket.openkilda.messaging.command.Constants.flowName;
import static org.bitbucket.openkilda.messaging.command.Constants.inputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.inputVlanId;
import static org.bitbucket.openkilda.messaging.command.Constants.meterId;
import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.outputVlanId;
import static org.bitbucket.openkilda.messaging.command.Constants.outputVlanType;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.bitbucket.openkilda.messaging.payload.flow.OutputVlanType;

import org.junit.Test;

public class InstallOneSwitchFlowTest {
    private InstallOneSwitchFlow flow = new InstallOneSwitchFlow(0L, flowName, 0L, switchId, inputPort, outputPort,
            inputVlanId, outputVlanId, outputVlanType, bandwidth, meterId, meterId + 1);

    @Test
    public void toStringTest() throws Exception {
        String flowString = flow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getOutputVlanType() throws Exception {
        assertEquals(outputVlanType, flow.getOutputVlanType());
    }

    @Test
    public void getBandwidth() throws Exception {
        assertEquals(bandwidth, flow.getBandwidth().longValue());
    }

    @Test
    public void getInputMeterId() throws Exception {
        assertEquals(meterId, flow.getSourceMeterId().longValue());
    }

    @Test
    public void getOutputMeterId() throws Exception {
        assertEquals(meterId + 1, flow.getDestinationMeterId().longValue());
    }

    @Test
    public void getInputVlanId() throws Exception {
        assertEquals(inputVlanId, flow.getInputVlanId().intValue());
    }

    @Test
    public void getOutputVlanId() throws Exception {
        assertEquals(outputVlanId, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setBandwidth() throws Exception {
        flow.setBandwidth(bandwidth);
        assertEquals(bandwidth, flow.getBandwidth().longValue());
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
    public void setInputMeterId() throws Exception {
        flow.setSourceMeterId(meterId);
        assertEquals(meterId, flow.getSourceMeterId().longValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullInputMeterId() throws Exception {
        flow.setSourceMeterId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeInputMeterId() throws Exception {
        flow.setInputVlanId(-1);
    }

    @Test
    public void setOutputMeterId() throws Exception {
        flow.setDestinationMeterId(meterId + 2);
        assertEquals(meterId + 2, flow.getDestinationMeterId().longValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullOutputMeterId() throws Exception {
        flow.setDestinationMeterId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeOutputMeterId() throws Exception {
        flow.setDestinationMeterId(-1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setSameMeterIds() throws Exception {
        flow.setSourceMeterId(meterId);
        flow.setDestinationMeterId(meterId);
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

    @Test
    public void setOutputVlanId() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        assertEquals(outputVlanId, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setNullOutputVlanId() throws Exception {
        flow.setOutputVlanId(null);
        assertEquals(0, flow.getOutputVlanId().intValue());
    }

    @Test
    public void setZeroOutputVlanId() throws Exception {
        flow.setOutputVlanId(0);
        assertEquals(0, flow.getOutputVlanId().intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeOutputVlanId() throws Exception {
        flow.setOutputVlanId(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setTooBigOutputVlanId() throws Exception {
        flow.setOutputVlanId(4096);
    }

    @Test
    public void setOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(outputVlanType);
        assertEquals(outputVlanType, flow.getOutputVlanType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullOutputVlanType() throws Exception {
        flow.setOutputVlanType(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setInvalidOutputVlanType() throws Exception {
        flow.setOutputVlanType(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectNoneOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(OutputVlanType.NONE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectPopOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(OutputVlanType.POP);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectPushOutputVlanType() throws Exception {
        flow.setOutputVlanId(0);
        flow.setOutputVlanType(OutputVlanType.PUSH);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectReplaceOutputVlanType() throws Exception {
        flow.setOutputVlanId(null);
        flow.setOutputVlanType(OutputVlanType.REPLACE);
    }
}
