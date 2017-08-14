package org.bitbucket.openkilda.pce.manager;

import static org.junit.Assert.assertEquals;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.PathComputerMock;
import org.bitbucket.openkilda.pce.StateStorageMock;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;

public class FlowManagerTest {
    private final StateStorageMock storage = new StateStorageMock();
    private final PathComputer computer = new PathComputerMock();
    private final NetworkManager networkManager = new NetworkManager(storage);
    private final FlowManager flowManager = new FlowManager(storage, networkManager, computer);

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

    @Test
    public void getFlow() throws Exception {
    }

    @Test
    public void createFlow() throws Exception {
    }

    @Test
    public void deleteFlow() throws Exception {
    }

    @Test
    public void updateFlow() throws Exception {
    }

    @Test
    public void dumpFlows() throws Exception {
    }

    @Test
    public void getFlowPath() throws Exception {
    }

    @Test
    public void getAffectedBySwitchFlows() throws Exception {
    }

    @Test
    public void getAffectedByIslFlows() throws Exception {
    }

    @Test
    public void getPath() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createSwitch(sw3);
        networkManager.createSwitch(sw4);
        networkManager.createSwitch(sw5);

        networkManager.createOrUpdateIsl(isl12);
        networkManager.createOrUpdateIsl(isl21);
        networkManager.createOrUpdateIsl(isl24);
        networkManager.createOrUpdateIsl(isl42);
        networkManager.createOrUpdateIsl(isl52);
        networkManager.createOrUpdateIsl(isl25);
        networkManager.createOrUpdateIsl(isl53);
        networkManager.createOrUpdateIsl(isl35);
        networkManager.createOrUpdateIsl(isl54);
        networkManager.createOrUpdateIsl(isl45);

        LinkedList<Isl> path = flowManager.getPath(sw1, sw3, 0);

        assertEquals(new LinkedList<>(Arrays.asList(isl12, isl25, isl53)), path);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getPathIntersection() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createSwitch(sw3);
        networkManager.createSwitch(sw4);
        networkManager.createSwitch(sw5);

        networkManager.createOrUpdateIsl(isl12);
        networkManager.createOrUpdateIsl(isl21);
        networkManager.createOrUpdateIsl(isl14);
        networkManager.createOrUpdateIsl(isl41);
        networkManager.createOrUpdateIsl(isl24);
        networkManager.createOrUpdateIsl(isl42);
        networkManager.createOrUpdateIsl(isl52);
        networkManager.createOrUpdateIsl(isl25);
        networkManager.createOrUpdateIsl(isl53);
        networkManager.createOrUpdateIsl(isl35);
        networkManager.createOrUpdateIsl(isl54);
        networkManager.createOrUpdateIsl(isl45);

        LinkedList<Isl> path1 = flowManager.getPath(sw4, sw3, 5);
        assertEquals(new LinkedList<>(Arrays.asList(isl45, isl53)), path1);

        LinkedList<Isl> path2 = flowManager.getPath(sw2, sw3, 5);
        assertEquals(new LinkedList<>(Arrays.asList(isl25, isl53)), path2);

        assertEquals((Collections.singleton(isl53)), flowManager.getPathIntersection(path1, path2));
        assertEquals((Collections.singleton(isl53)), flowManager.getPathIntersection(path2, path1));

        flowManager.getPath(sw5, sw3, 1);
    }

    @Test
    public void allocateFlow() throws Exception {
    }

    @Test
    public void deallocateFlow() throws Exception {
    }

    @Test
    public void buildFlow() throws Exception {
    }

    @Test
    public void buildForwardFlow() throws Exception {
    }

    @Test
    public void buildReverseFlow() throws Exception {
    }
}
