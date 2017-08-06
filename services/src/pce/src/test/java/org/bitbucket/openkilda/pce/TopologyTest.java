package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.path.PathComputer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TopologyTest {
    StorageMock storage = new StorageMock();
    PathComputer computer = new PathComputerMock();
    Topology topology = Topology.getTopologyManager(storage, computer);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getTopologyManager() throws Exception {
    }

    @Test
    public void getSwitch() throws Exception {
    }

    @Test
    public void createSwitch() throws Exception {
    }

    @Test
    public void updateSwitch() throws Exception {
    }

    @Test
    public void deleteSwitch() throws Exception {
    }

    @Test
    public void dumpSwitches() throws Exception {
    }

    @Test
    public void getStateSwitches() throws Exception {
    }

    @Test
    public void getControllerSwitches() throws Exception {
    }

    @Test
    public void getDirectlyConnectedSwitches() throws Exception {
    }

    @Test
    public void createOrUpdateIsl() throws Exception {
    }

    @Test
    public void deleteIsl() throws Exception {
    }

    @Test
    public void getIsl() throws Exception {
    }

    @Test
    public void dumpIsls() throws Exception {
    }

    @Test
    public void getIslsBySwitch() throws Exception {
    }

    @Test
    public void getIslsBySource() throws Exception {
    }

    @Test
    public void getIslsByDestination() throws Exception {
    }

    @Test
    public void getPath() throws Exception {
    }

    @Test
    public void getPath1() throws Exception {
    }

    @Test
    public void createFlow() throws Exception {
    }

    @Test
    public void deleteFlow() throws Exception {
    }

    @Test
    public void getFlow() throws Exception {
    }

    @Test
    public void updateFlow() throws Exception {
    }

    @Test
    public void dumpFlows() throws Exception {
    }

    @Test
    public void getAffectedFlows() throws Exception {
    }

    @Test
    public void getSwitchPathBetweenSwitches() throws Exception {
    }

    @Test
    public void getIslPathBetweenSwitches() throws Exception {
    }

    @Test
    public void rerouteFlow() throws Exception {
    }

    @Test
    public void testCreateOrUpdateIsl() {
        Switch sw1 = new Switch("sw1", "" , "", "", SwitchState.ACTIVATED, "");
        Switch sw2 = new Switch("sw2", "" , "", "", SwitchState.ACTIVATED, "");
        Isl isl1 = new Isl("sw1", "sw2", 1, 2, 1L, 0L, 0);
        Isl isl2 = new Isl("sw1", "sw2", 1, 2, 2L, 0L, 0);
        Isl isl3 = new Isl("sw1", "sw2", 1, 2, 3L, 0L, 0);

        topology.createSwitch(sw1);
        topology.createSwitch(sw2);

        topology.createOrUpdateIsl(isl1);
        topology.createOrUpdateIsl(isl2);
        topology.createOrUpdateIsl(isl3);

        //topology.getIslsWithSource("sw1");
    }
}