package org.bitbucket.openkilda.floodlight.message.command;

import org.bitbucket.openkilda.messaging.command.flow.AbstractInstallFlowCommandData;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 11/04/2017.
 */
public class AbstractInstallFlowTest {
    private static AbstractInstallFlowCommandData flow = new AbstractInstallFlowCommandData(flowName, switchId, inputPort, outputPort);

    @Test
    public void getFlowName() throws Exception {
        assertEquals(flowName, flow.getCookie());
    }

    @Test
    public void getSwitchId() throws Exception {
        assertEquals(switchId, flow.getSwitchId());
    }

    @Test
    public void getInputPort() throws Exception {
        assertEquals(inputPort, flow.getInputPort());
    }

    @Test
    public void getOutputPort() throws Exception {
        assertEquals(outputPort, flow.getOutputPort());
    }

    @Test
    public void setFlowName() throws Exception {
        flow.setFlowName(flowName);
        assertEquals(flowName, flow.getFlowName());
    }

    @Test
    public void setSwitchId() throws Exception {
        flow.setSwitchId(switchId);
        assertEquals(switchId, flow.getSwitchId());
    }

    @Test
    public void setInputPort() throws Exception {
        flow.setInputPort(inputPort);
        assertEquals(inputPort, flow.getInputPort());
    }

    @Test
    public void setOutputPort() throws Exception {
        flow.setOutputPort(outputPort);
        assertEquals(outputPort, flow.getOutputPort());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullFlowName() throws Exception {
        flow.setFlowName(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullSwitchId() throws Exception {
        flow.setSwitchId(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setIncorrectSwitchId() throws Exception {
        flow.setSwitchId("");
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullInputPort() throws Exception {
        flow.setInputPort(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullOutputPort() throws Exception {
        flow.setOutputPort(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeInputPort() throws Exception {
        flow.setInputPort(-1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeOutputPort() throws Exception {
        flow.setOutputPort(-1);
    }
}
