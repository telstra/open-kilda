package org.bitbucket.openkilda.floodlight.message.command;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class InstallTransitFlowTest {
    private static InstallTransitFlow installTransitFlow;
    private InstallTransitFlow flow;

    @Before
    public void setUp() throws Exception {
        flow = new InstallTransitFlow();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        installTransitFlow = new InstallTransitFlow(flowName, switchId, inputPort, outputPort, transitVlanId);
        System.out.println(installTransitFlow.toString());
    }

    @Test
    public void toStringTest() throws Exception {
        String flowString = installTransitFlow.toString();
        assertNotNull(flowString);
        assertFalse(flowString.isEmpty());
    }

    @Test
    public void getTransitVlanId() throws Exception {
        assertEquals(transitVlanId, installTransitFlow.getTransitVlanId());
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
