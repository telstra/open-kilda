package org.bitbucket.openkilda.floodlight.message.command;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 11/04/2017.
 */
public class AbstractInstallFlowTest {
    private static AbstractInstallFlow abstractInstallFlow;
    private AbstractInstallFlow flow;

    @Before
    public void setUp() throws Exception {
        flow = new AbstractInstallFlow();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        abstractInstallFlow = new AbstractInstallFlow(flowName, switchId, inputPort, outputPort);
        System.out.println(abstractInstallFlow.toString());
    }

    @Test
    public void getFlowName() throws Exception {
        assertEquals(flowName, abstractInstallFlow.getFlowName());
    }

    @Test
    public void getSwitchId() throws Exception {
        assertEquals(switchId, abstractInstallFlow.getSwitchId());
    }

    @Test
    public void getInputPort() throws Exception {
        assertEquals(inputPort, abstractInstallFlow.getInputPort());
    }

    @Test
    public void getOutputPort() throws Exception {
        assertEquals(outputPort, abstractInstallFlow.getOutputPort());
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
