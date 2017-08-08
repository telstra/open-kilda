package org.bitbucket.openkilda.pce;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.path.PathComputer;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class TopologyTest {
    private final StorageMock storage = new StorageMock();
    private final PathComputer computer = new PathComputerMock();
    private final Topology topology = Topology.getTopologyManager(storage, computer);

    private final Switch sw1 = new Switch("sw1", "", "", "", SwitchState.ACTIVATED, "localhost");
    private final Switch sw2 = new Switch("sw2", "", "", "", SwitchState.ACTIVATED, "localhost");
    private final Switch sw3 = new Switch("sw3", "", "", "", SwitchState.ADDED, "remote");
    private final Switch sw4 = new Switch("sw4", "", "", "", SwitchState.ADDED, "remote");
    private final Switch sw5 = new Switch("sw5", "", "", "", SwitchState.REMOVED, "remote");

    private final Isl isl12 = new Isl("sw1", "sw2", 1, 2, 1L, 0L, 10);
    private final Isl isl21 = new Isl("sw2", "sw1", 2, 1, 1L, 0L, 10);
    private final Isl isl23 = new Isl("sw2", "sw3", 1, 2, 1L, 0L, 10);
    private final Isl isl32 = new Isl("sw3", "sw2", 2, 1, 1L, 0L, 10);

    private final Isl isl14 = new Isl("sw1", "sw4", 2, 1, 1L, 0L, 10);
    private final Isl isl41 = new Isl("sw4", "sw1", 1, 2, 1L, 0L, 10);
    private final Isl isl24 = new Isl("sw2", "sw4", 3, 2, 1L, 0L, 10);
    private final Isl isl42 = new Isl("sw4", "sw2", 2, 3, 1L, 0L, 10);

    private final Isl isl54 = new Isl("sw5", "sw4", 1, 3, 1L, 0L, 10);
    private final Isl isl45 = new Isl("sw4", "sw5", 3, 1, 1L, 0L, 10);
    private final Isl isl52 = new Isl("sw5", "sw2", 2, 4, 1L, 0L, 10);
    private final Isl isl25 = new Isl("sw2", "sw5", 4, 2, 1L, 0L, 10);
    private final Isl isl53 = new Isl("sw5", "sw3", 3, 1, 1L, 0L, 10);
    private final Isl isl35 = new Isl("sw3", "sw5", 1, 3, 1L, 0L, 10);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
        Topology.clear();
    }

    @Test
    public void getTopologyManager() throws Exception {
        Topology newTopology = Topology.getTopologyManager(storage, computer);
        assertTrue(topology == newTopology);
        assertEquals(topology, newTopology);
    }

    @Test
    public void getSwitch() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        assertEquals(sw1, topology.getSwitch(sw1.getSwitchId()));
        assertEquals(sw2, topology.getSwitch(sw2.getSwitchId()));
    }

    @Test
    public void createSwitch() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        assertEquals(2, storage.switches);
    }

    @Test
    public void updateSwitch() throws Exception {
        String swId = "sw7";
        Switch sw7 = new Switch(swId, "", "", "", SwitchState.ADDED, "");
        topology.createSwitch(sw7);
        assertEquals(sw7, topology.getSwitch(swId));

        Switch sw7updated = new Switch(swId, "", "", "", SwitchState.ACTIVATED, "");
        topology.updateSwitch(swId, sw7updated);
        assertEquals(sw7updated, topology.getSwitch(swId));

        topology.deleteSwitch(swId);
        Set<Switch> switches = topology.dumpSwitches();
        assertEquals(Collections.emptySet(), switches);
    }

    @Test
    public void deleteSwitch() throws Exception {
        createSwitch();
        topology.deleteSwitch(sw1.getSwitchId());
        topology.deleteSwitch(sw2.getSwitchId());
        assertEquals(0, storage.switches);
    }

    @Test
    public void dumpSwitches() throws Exception {
        createSwitch();
        Set<Switch> switches = topology.dumpSwitches();
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw2)), switches);
    }

    @Test
    public void getStateSwitches() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        topology.createSwitch(sw3);
        Set<Switch> activeSwitches = topology.getStateSwitches(SwitchState.ACTIVATED);
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw2)), activeSwitches);
    }

    @Test
    public void getControllerSwitches() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        topology.createSwitch(sw3);
        Set<Switch> activeSwitches = topology.getControllerSwitches("localhost");
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw2)), activeSwitches);
    }

    @Test
    public void getDirectlyConnectedSwitches() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        topology.createSwitch(sw3);

        Set<Switch> directlyConnected = topology.getDirectlyConnectedSwitches(sw2.getSwitchId());
        assertEquals(new HashSet<>(), directlyConnected);

        topology.createOrUpdateIsl(isl12);
        topology.createOrUpdateIsl(isl21);
        topology.createOrUpdateIsl(isl23);
        topology.createOrUpdateIsl(isl32);

        directlyConnected = topology.getDirectlyConnectedSwitches(sw2.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw3)), directlyConnected);
    }

    @Test
    public void createOrUpdateIsl() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        topology.createSwitch(sw3);

        topology.createOrUpdateIsl(isl12);
        topology.createOrUpdateIsl(isl21);
        topology.createOrUpdateIsl(isl23);
        topology.createOrUpdateIsl(isl32);

        assertEquals(4, storage.isls);

        topology.createOrUpdateIsl(isl12);
        topology.createOrUpdateIsl(isl21);
        topology.createOrUpdateIsl(isl23);
        topology.createOrUpdateIsl(isl32);

        assertEquals(4, storage.isls);
    }

    @Test
    public void deleteIsl() throws Exception {
        createOrUpdateIsl();

        topology.deleteIsl(isl12.getId());
        topology.deleteIsl(isl21.getId());
        topology.deleteIsl(isl23.getId());
        topology.deleteIsl(isl32.getId());

        assertEquals(0, storage.isls);
    }

    @Test
    public void getIsl() throws Exception {
        createOrUpdateIsl();
        assertEquals(isl12, topology.getIsl(isl12.getId()));
        assertEquals(isl21, topology.getIsl(isl21.getId()));
        assertEquals(isl23, topology.getIsl(isl23.getId()));
        assertEquals(isl32, topology.getIsl(isl32.getId()));
    }

    @Test
    public void dumpIsls() throws Exception {
        createOrUpdateIsl();
        assertEquals(new HashSet<>(Arrays.asList(isl12, isl21, isl23, isl32)), topology.dumpIsls());
    }

    @Test
    public void getIslsBySwitch() throws Exception {
        createOrUpdateIsl();
        topology.createSwitch(sw4);
        topology.createOrUpdateIsl(isl14);
        topology.createOrUpdateIsl(isl41);
        topology.createOrUpdateIsl(isl24);
        topology.createOrUpdateIsl(isl42);
        assertEquals(new HashSet<>(Arrays.asList(isl12, isl21, isl23, isl32, isl24, isl42)),
                topology.getIslsBySwitch(sw2.getSwitchId()));
    }

    @Test
    public void getIslsBySource() throws Exception {
        createOrUpdateIsl();
        topology.createSwitch(sw4);
        topology.createOrUpdateIsl(isl14);
        topology.createOrUpdateIsl(isl41);
        topology.createOrUpdateIsl(isl24);
        topology.createOrUpdateIsl(isl42);
        assertEquals(new HashSet<>(Arrays.asList(isl21, isl23, isl24)),
                topology.getIslsBySource(sw2.getSwitchId()));
    }

    @Test
    public void getIslsByDestination() throws Exception {
        createOrUpdateIsl();
        topology.createSwitch(sw4);
        topology.createOrUpdateIsl(isl14);
        topology.createOrUpdateIsl(isl41);
        topology.createOrUpdateIsl(isl24);
        topology.createOrUpdateIsl(isl42);
        assertEquals(new HashSet<>(Arrays.asList(isl12, isl32, isl42)),
                topology.getIslsByDestination(sw2.getSwitchId()));
    }

    @Test
    public void getPath() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        topology.createSwitch(sw3);
        topology.createSwitch(sw4);
        topology.createSwitch(sw5);

        topology.createOrUpdateIsl(isl12);
        topology.createOrUpdateIsl(isl21);
        topology.createOrUpdateIsl(isl24);
        topology.createOrUpdateIsl(isl42);
        topology.createOrUpdateIsl(isl52);
        topology.createOrUpdateIsl(isl25);
        topology.createOrUpdateIsl(isl53);
        topology.createOrUpdateIsl(isl35);
        topology.createOrUpdateIsl(isl54);
        topology.createOrUpdateIsl(isl45);

        LinkedList<Isl> path = topology.getPath(sw1, sw3, 0);

        assertEquals(new LinkedList<>(Arrays.asList(isl12, isl25, isl53)), path);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getPathIntersection() throws Exception {
        topology.createSwitch(sw1);
        topology.createSwitch(sw2);
        topology.createSwitch(sw3);
        topology.createSwitch(sw4);
        topology.createSwitch(sw5);

        topology.createOrUpdateIsl(isl12);
        topology.createOrUpdateIsl(isl21);
        topology.createOrUpdateIsl(isl14);
        topology.createOrUpdateIsl(isl41);
        topology.createOrUpdateIsl(isl24);
        topology.createOrUpdateIsl(isl42);
        topology.createOrUpdateIsl(isl52);
        topology.createOrUpdateIsl(isl25);
        topology.createOrUpdateIsl(isl53);
        topology.createOrUpdateIsl(isl35);
        topology.createOrUpdateIsl(isl54);
        topology.createOrUpdateIsl(isl45);

        LinkedList<Isl> path1 = topology.getPath(sw4, sw3, 5);
        assertEquals(new LinkedList<>(Arrays.asList(isl45, isl53)), path1);

        LinkedList<Isl> path2 = topology.getPath(sw2, sw3, 5);
        assertEquals(new LinkedList<>(Arrays.asList(isl25, isl53)), path2);

        assertEquals((Collections.singleton(isl53)), topology.getPathIntersection(path1, path2));
        assertEquals((Collections.singleton(isl53)), topology.getPathIntersection(path2, path1));

        topology.getPath(sw5, sw3, 1);
    }
}
