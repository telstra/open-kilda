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
public class InstallOneSwitchFlowTest {
    private static InstallOneSwitchFlow installOneSwitchFlow;
    private InstallOneSwitchFlow flow;

    @Before
    public void setUp() throws Exception {
        flow = new InstallOneSwitchFlow();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        installOneSwitchFlow = new InstallOneSwitchFlow(flowName, switchId, inputPort, outputPort,
                inputVlanId, outputVlanId, outputVlanType, bandwidth, meterId, meterId+1);
        System.out.println(installOneSwitchFlow.toString());
    }

    @Test
    public void toStringTest() throws Exception {
        String flowString = installOneSwitchFlow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getOutputVlanType() throws Exception {
        assertEquals(outputVlanType, installOneSwitchFlow.getOutputVlanType());
    }

    @Test
    public void getBandwidth() throws Exception {
        assertEquals(bandwidth, installOneSwitchFlow.getBandwidth());
    }

    @Test
    public void getInputMeterId() throws Exception {
        assertEquals(meterId, installOneSwitchFlow.getInputMeterId());
    }

    @Test
    public void getOutputMeterId() throws Exception {
        assertEquals(meterId+1, installOneSwitchFlow.getOutputMeterId());
    }

    @Test
    public void getInputVlanId() throws Exception {
        assertEquals(inputVlanId, installOneSwitchFlow.getInputVlanId());
    }

    @Test
    public void getOutputVlanId() throws Exception {
        assertEquals(outputVlanId, installOneSwitchFlow.getOutputVlanId());
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
    public void setInputMeterId() throws Exception {
        flow.setInputMeterId(meterId);
        assertEquals(meterId, flow.getInputMeterId());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullInputMeterId() throws Exception {
        flow.setInputMeterId(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeInputMeterId() throws Exception {
        flow.setInputMeterId(-1);
    }

    @Test
    public void setOutputMeterId() throws Exception {
        flow.setOutputMeterId(meterId);
        assertEquals(meterId, flow.getOutputMeterId());
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNullOutputMeterId() throws Exception {
        flow.setOutputMeterId(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setNegativeOutputMeterId() throws Exception {
        flow.setOutputMeterId(-1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void setSameMeterIds() throws Exception {
        flow.setInputMeterId(meterId);
        flow.setOutputMeterId(meterId);
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
}
