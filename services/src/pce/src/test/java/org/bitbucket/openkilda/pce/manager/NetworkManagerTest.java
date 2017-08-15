package org.bitbucket.openkilda.pce.manager;

import static org.junit.Assert.assertEquals;

import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.PathComputerMock;
import org.bitbucket.openkilda.pce.StateStorageMock;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.PathComputer;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class NetworkManagerTest {
    private final StateStorageMock storage = new StateStorageMock();
    private final PathComputer computer = new PathComputerMock();
    private final NetworkManager networkManager = new NetworkManager(storage);

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
        networkManager.clear();
    }

    @Test
    public void getSwitch() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        assertEquals(sw1, networkManager.getSwitch(sw1.getSwitchId()));
        assertEquals(sw2, networkManager.getSwitch(sw2.getSwitchId()));
    }

    @Test
    public void createSwitch() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        assertEquals(2, storage.getSwitchesCount());
    }

    @Test
    public void updateSwitch() throws Exception {
        String swId = "sw7";
        Switch sw7 = new Switch(swId, "", "", "", SwitchState.ADDED, "");
        networkManager.createSwitch(sw7);
        assertEquals(sw7, networkManager.getSwitch(swId));

        Switch sw7updated = new Switch(swId, "", "", "", SwitchState.ACTIVATED, "");
        networkManager.updateSwitch(swId, sw7updated);
        assertEquals(sw7updated, networkManager.getSwitch(swId));

        networkManager.deleteSwitch(swId);
        Set<Switch> switches = networkManager.dumpSwitches();
        assertEquals(Collections.emptySet(), switches);
    }

    @Test
    public void deleteSwitch() throws Exception {
        createSwitch();
        networkManager.deleteSwitch(sw1.getSwitchId());
        networkManager.deleteSwitch(sw2.getSwitchId());
        assertEquals(0, storage.getSwitchesCount());
    }

    @Test
    public void dumpSwitches() throws Exception {
        createSwitch();
        Set<Switch> switches = networkManager.dumpSwitches();
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw2)), switches);
    }

    @Test
    public void getStateSwitches() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createSwitch(sw3);
        Set<Switch> activeSwitches = networkManager.getStateSwitches(SwitchState.ACTIVATED);
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw2)), activeSwitches);
    }

    @Test
    public void getControllerSwitches() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createSwitch(sw3);
        Set<Switch> activeSwitches = networkManager.getControllerSwitches("localhost");
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw2)), activeSwitches);
    }

    @Test
    public void getDirectlyConnectedSwitches() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createSwitch(sw3);

        Set<Switch> directlyConnected = networkManager.getDirectlyConnectedSwitches(sw2.getSwitchId());
        assertEquals(new HashSet<>(), directlyConnected);

        networkManager.createOrUpdateIsl(isl12);
        networkManager.createOrUpdateIsl(isl21);
        networkManager.createOrUpdateIsl(isl23);
        networkManager.createOrUpdateIsl(isl32);

        directlyConnected = networkManager.getDirectlyConnectedSwitches(sw2.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(sw1, sw3)), directlyConnected);
    }

    @Test
    public void createOrUpdateIsl() throws Exception {
        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createSwitch(sw3);

        networkManager.createOrUpdateIsl(isl12);
        networkManager.createOrUpdateIsl(isl21);
        networkManager.createOrUpdateIsl(isl23);
        networkManager.createOrUpdateIsl(isl32);

        assertEquals(4, storage.getIslsCount());

        networkManager.createOrUpdateIsl(isl12);
        networkManager.createOrUpdateIsl(isl21);
        networkManager.createOrUpdateIsl(isl23);
        networkManager.createOrUpdateIsl(isl32);

        assertEquals(4, storage.getIslsCount());
    }

    @Test
    public void deleteIsl() throws Exception {
        createOrUpdateIsl();

        networkManager.deleteIsl(isl12.getId());
        networkManager.deleteIsl(isl21.getId());
        networkManager.deleteIsl(isl23.getId());
        networkManager.deleteIsl(isl32.getId());

        assertEquals(0, storage.getIslsCount());
    }

    @Test
    public void getIsl() throws Exception {
        createOrUpdateIsl();
        assertEquals(isl12, networkManager.getIsl(isl12.getId()));
        assertEquals(isl21, networkManager.getIsl(isl21.getId()));
        assertEquals(isl23, networkManager.getIsl(isl23.getId()));
        assertEquals(isl32, networkManager.getIsl(isl32.getId()));
    }

    @Test
    public void dumpIsls() throws Exception {
        createOrUpdateIsl();
        assertEquals(new HashSet<>(Arrays.asList(isl12, isl21, isl23, isl32)), networkManager.dumpIsls());
    }

    @Test
    public void getIslsBySwitch() throws Exception {
        createOrUpdateIsl();
        networkManager.createSwitch(sw4);
        networkManager.createOrUpdateIsl(isl14);
        networkManager.createOrUpdateIsl(isl41);
        networkManager.createOrUpdateIsl(isl24);
        networkManager.createOrUpdateIsl(isl42);
        assertEquals(new HashSet<>(Arrays.asList(isl12, isl21, isl23, isl32, isl24, isl42)),
                networkManager.getIslsBySwitch(sw2.getSwitchId()));
    }

    @Test
    public void getIslsBySource() throws Exception {
        createOrUpdateIsl();
        networkManager.createSwitch(sw4);
        networkManager.createOrUpdateIsl(isl14);
        networkManager.createOrUpdateIsl(isl41);
        networkManager.createOrUpdateIsl(isl24);
        networkManager.createOrUpdateIsl(isl42);
        assertEquals(new HashSet<>(Arrays.asList(isl21, isl23, isl24)),
                networkManager.getIslsBySource(sw2.getSwitchId()));
    }

    @Test
    public void getIslsByDestination() throws Exception {
        createOrUpdateIsl();
        networkManager.createSwitch(sw4);
        networkManager.createOrUpdateIsl(isl14);
        networkManager.createOrUpdateIsl(isl41);
        networkManager.createOrUpdateIsl(isl24);
        networkManager.createOrUpdateIsl(isl42);
        assertEquals(new HashSet<>(Arrays.asList(isl12, isl32, isl42)),
                networkManager.getIslsByDestination(sw2.getSwitchId()));
    }

    @Test
    public void eventTest() {
        NetworkManager otherNetworkManager = new NetworkManager(storage);
        Function<NetworkManager.SwitchChangeEvent, Void> switchChangeCallback =
                new Function<NetworkManager.SwitchChangeEvent, Void>() {
                    @Override
                    public Void apply(NetworkManager.SwitchChangeEvent switchChangeEvent) {
                        System.out.println(switchChangeEvent);
                        otherNetworkManager.handleSwitchChange(switchChangeEvent);
                        return null;
                    }
                };

        Function<NetworkManager.IslChangeEvent, Void> islChangeCallback =
                new Function<NetworkManager.IslChangeEvent, Void>() {
                    @Override
                    public Void apply(NetworkManager.IslChangeEvent islChangeEvent) {
                        System.out.println(islChangeEvent);
                        otherNetworkManager.handleIslChange(islChangeEvent);
                        return null;
                    }
                };

        networkManager.withSwitchChange(switchChangeCallback);
        networkManager.withIslChange(islChangeCallback);

        networkManager.createSwitch(sw1);
        networkManager.createSwitch(sw2);
        networkManager.createOrUpdateIsl(isl12);
        networkManager.createOrUpdateIsl(isl21);

        assertEquals(networkManager.dumpIsls(), otherNetworkManager.dumpIsls());
        assertEquals(networkManager.dumpSwitches(), otherNetworkManager.dumpSwitches());
    }
}
