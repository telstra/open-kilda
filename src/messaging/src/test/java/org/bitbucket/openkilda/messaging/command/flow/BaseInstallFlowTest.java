package org.bitbucket.openkilda.messaging.command.flow;


import static org.bitbucket.openkilda.messaging.command.Constants.flowName;
import static org.bitbucket.openkilda.messaging.command.Constants.inputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.outputPort;
import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BaseInstallFlowTest {
    private static BaseInstallFlow flow = new BaseInstallFlow(0L, flowName, 0L, switchId, inputPort, outputPort);

    @Test
    public void getFlowName() throws Exception {
        assertEquals(flowName, flow.getId());
    }

    @Test
    public void getSwitchId() throws Exception {
        assertEquals(switchId, flow.getSwitchId());
    }

    @Test
    public void getInputPort() throws Exception {
        assertEquals(inputPort, flow.getInputPort().intValue());
    }

    @Test
    public void getOutputPort() throws Exception {
        assertEquals(outputPort, flow.getOutputPort().intValue());
    }

    @Test
    public void setFlowName() throws Exception {
        flow.setId(flowName);
        assertEquals(flowName, flow.getId());
    }

    @Test
    public void setSwitchId() throws Exception {
        flow.setSwitchId(switchId);
        assertEquals(switchId, flow.getSwitchId());
    }

    @Test
    public void setInputPort() throws Exception {
        flow.setInputPort(inputPort);
        assertEquals(inputPort, flow.getInputPort().intValue());
    }

    @Test
    public void setOutputPort() throws Exception {
        flow.setOutputPort(outputPort);
        assertEquals(outputPort, flow.getOutputPort().intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullFlowName() throws Exception {
        flow.setId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullSwitchId() throws Exception {
        flow.setSwitchId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setIncorrectSwitchId() throws Exception {
        flow.setSwitchId("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullInputPort() throws Exception {
        flow.setInputPort(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNullOutputPort() throws Exception {
        flow.setOutputPort(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeInputPort() throws Exception {
        flow.setInputPort(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setNegativeOutputPort() throws Exception {
        flow.setOutputPort(-1);
    }
}
