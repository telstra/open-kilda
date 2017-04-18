package org.bitbucket.openkilda.floodlight.message.command;

import org.bitbucket.openkilda.floodlight.switchmanager.OutputVlanType;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class InstallEgressFlowTest {
    private static InstallEgressFlow installEgressFlow;
    private InstallEgressFlow flow;

    @Before
    public void setUp() throws Exception {
        flow = new InstallEgressFlow();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        installEgressFlow = new InstallEgressFlow(flowName, switchId, inputPort,
                outputPort, transitVlanId, outputVlanId, outputVlanType);
        System.out.println(installEgressFlow.toString());
    }

    @Test
    public void toStringTest() throws Exception {
        String flowString = installEgressFlow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getOutputVlanType() throws Exception {
        assertEquals(outputVlanType, installEgressFlow.getOutputVlanType());
    }

    @Test
    public void getOutputVlanId() throws Exception {
        assertEquals(outputVlanId, installEgressFlow.getOutputVlanId());
    }

    @Test
    public void setOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(outputVlanType);
        assertEquals(outputVlanType, flow.getOutputVlanType());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullOutputVlanType() throws Exception {
        flow.setOutputVlanType(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setInvalidOutputVlanType() throws Exception {
        flow.setOutputVlanType("");
    }

    @Test(expected=IllegalArgumentException.class)
    public void setIncorrectNoneOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(OutputVlanType.NONE.toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setIncorrectPopOutputVlanType() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        flow.setOutputVlanType(OutputVlanType.POP.toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setIncorrectPushOutputVlanType() throws Exception {
        flow.setOutputVlanId(0);
        flow.setOutputVlanType(OutputVlanType.PUSH.toString());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setIncorrectReplaceOutputVlanType() throws Exception {
        flow.setOutputVlanId(null);
        flow.setOutputVlanType(OutputVlanType.REPLACE.toString());
    }

    @Test
    public void setOutputVlanId() throws Exception {
        flow.setOutputVlanId(outputVlanId);
        assertEquals(outputVlanId, flow.getOutputVlanId());
    }

    @Test
    public void setNullOutputVlanId() throws Exception {
        flow.setOutputVlanId(null);
        assertEquals(0, flow.getOutputVlanId());
    }

    @Test
    public void setZeroOutputVlanId() throws Exception {
        flow.setOutputVlanId(0);
        assertEquals(0, flow.getOutputVlanId());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeOutputVlanId() throws Exception {
        flow.setOutputVlanId(-1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setTooBigOutputVlanId() throws Exception {
        flow.setOutputVlanId(4096);
    }
}
