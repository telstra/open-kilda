package org.bitbucket.openkilda.pce.manager;

import static org.junit.Assert.assertEquals;

import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchState;
import org.bitbucket.openkilda.pce.NetworkTopologyConstants;
import org.bitbucket.openkilda.pce.PathComputerMock;
import org.bitbucket.openkilda.pce.StateStorageMock;
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
    private final NetworkManager networkManager = new NetworkManager(storage, computer);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
        networkManager.clear();
    }

    @Test
    public void getSwitch() throws Exception {
        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        assertEquals(NetworkTopologyConstants.sw1,
                networkManager.getSwitch(NetworkTopologyConstants.sw1.getSwitchId()));
        assertEquals(NetworkTopologyConstants.sw2,
                networkManager.getSwitch(NetworkTopologyConstants.sw2.getSwitchId()));
    }

    @Test
    public void createSwitch() throws Exception {
        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        assertEquals(2, storage.getSwitchesCount());
    }

    @Test
    public void updateSwitch() throws Exception {
        String swId = "sw7";
        SwitchInfoData sw7 = new SwitchInfoData(swId, SwitchState.ACTIVATED, "", "", "", "");
        networkManager.createSwitch(sw7);
        assertEquals(sw7, networkManager.getSwitch(swId));

        SwitchInfoData sw7updated = new SwitchInfoData(swId, SwitchState.ACTIVATED, "", "", "", "");
        networkManager.updateSwitch(swId, sw7updated);
        assertEquals(sw7updated, networkManager.getSwitch(swId));

        networkManager.deleteSwitch(swId);
        Set<SwitchInfoData> switches = networkManager.dumpSwitches();
        assertEquals(Collections.emptySet(), switches);
    }

    @Test
    public void deleteSwitch() throws Exception {
        createSwitch();
        networkManager.deleteSwitch(NetworkTopologyConstants.sw1.getSwitchId());
        networkManager.deleteSwitch(NetworkTopologyConstants.sw2.getSwitchId());
        assertEquals(0, storage.getSwitchesCount());
    }

    @Test
    public void dumpSwitches() throws Exception {
        createSwitch();
        Set<SwitchInfoData> switches = networkManager.dumpSwitches();
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1,
                NetworkTopologyConstants.sw2)), switches);
    }

    @Test
    public void getStateSwitches() throws Exception {
        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        networkManager.createSwitch(NetworkTopologyConstants.sw3);
        Set<SwitchInfoData> activeSwitches = networkManager.getStateSwitches(SwitchState.ACTIVATED);
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1,
                NetworkTopologyConstants.sw2)), activeSwitches);
    }

    @Test
    public void getControllerSwitches() throws Exception {
        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        networkManager.createSwitch(NetworkTopologyConstants.sw3);
        Set<SwitchInfoData> activeSwitches = networkManager.getControllerSwitches("localhost");
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1,
                NetworkTopologyConstants.sw2)), activeSwitches);
    }

    @Test
    public void getDirectlyConnectedSwitches() throws Exception {
        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        networkManager.createSwitch(NetworkTopologyConstants.sw3);

        Set<SwitchInfoData> directlyConnected = networkManager.getDirectlyConnectedSwitches(
                NetworkTopologyConstants.sw2.getSwitchId());
        assertEquals(new HashSet<>(), directlyConnected);

        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl21);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl23);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl32);

        directlyConnected = networkManager.getDirectlyConnectedSwitches(NetworkTopologyConstants.sw2.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1, NetworkTopologyConstants.sw3)), directlyConnected);
    }

    @Test
    public void createOrUpdateIsl() throws Exception {
        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        networkManager.createSwitch(NetworkTopologyConstants.sw3);

        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl21);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl23);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl32);

        assertEquals(4, storage.getIslsCount());

        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl21);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl23);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl32);

        assertEquals(4, storage.getIslsCount());
    }

    @Test
    public void deleteIsl() throws Exception {
        createOrUpdateIsl();

        networkManager.deleteIsl(NetworkTopologyConstants.isl12.getId());
        networkManager.deleteIsl(NetworkTopologyConstants.isl21.getId());
        networkManager.deleteIsl(NetworkTopologyConstants.isl23.getId());
        networkManager.deleteIsl(NetworkTopologyConstants.isl32.getId());

        assertEquals(0, storage.getIslsCount());
    }

    @Test
    public void getIsl() throws Exception {
        createOrUpdateIsl();
        assertEquals(NetworkTopologyConstants.isl12, networkManager.getIsl(NetworkTopologyConstants.isl12.getId()));
        assertEquals(NetworkTopologyConstants.isl21, networkManager.getIsl(NetworkTopologyConstants.isl21.getId()));
        assertEquals(NetworkTopologyConstants.isl23, networkManager.getIsl(NetworkTopologyConstants.isl23.getId()));
        assertEquals(NetworkTopologyConstants.isl32, networkManager.getIsl(NetworkTopologyConstants.isl32.getId()));
    }

    @Test
    public void dumpIsls() throws Exception {
        createOrUpdateIsl();
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.isl12, NetworkTopologyConstants.isl21,
                NetworkTopologyConstants.isl23, NetworkTopologyConstants.isl32)), networkManager.dumpIsls());
    }

    @Test
    public void getIslsBySwitch() throws Exception {
        createOrUpdateIsl();
        networkManager.createSwitch(NetworkTopologyConstants.sw4);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl14);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl41);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl24);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl42);
        assertEquals(new HashSet<>(Arrays.asList(
                NetworkTopologyConstants.isl12, NetworkTopologyConstants.isl21, NetworkTopologyConstants.isl23,
                NetworkTopologyConstants.isl32, NetworkTopologyConstants.isl24, NetworkTopologyConstants.isl42)),
                networkManager.getIslsBySwitch(NetworkTopologyConstants.sw2.getSwitchId()));
    }

    @Test
    public void getIslsBySource() throws Exception {
        createOrUpdateIsl();
        networkManager.createSwitch(NetworkTopologyConstants.sw4);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl14);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl41);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl24);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl42);
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.isl21,
                NetworkTopologyConstants.isl23, NetworkTopologyConstants.isl24)),
                networkManager.getIslsBySource(NetworkTopologyConstants.sw2.getSwitchId()));
    }

    @Test
    public void getIslsByDestination() throws Exception {
        createOrUpdateIsl();
        networkManager.createSwitch(NetworkTopologyConstants.sw4);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl14);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl41);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl24);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl42);
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.isl12,
                NetworkTopologyConstants.isl32, NetworkTopologyConstants.isl42)),
                networkManager.getIslsByDestination(NetworkTopologyConstants.sw2.getSwitchId()));
    }

    @Test
    public void eventTest() {
        NetworkManager otherNetworkManager = new NetworkManager(storage, computer);
        Function<NetworkManager.SwitchChangeEvent, Void> switchChangeCallback =
                switchChangeEvent -> {
                    System.out.println(switchChangeEvent);
                    otherNetworkManager.handleSwitchChange(switchChangeEvent);
                    return null;
                };

        Function<NetworkManager.IslChangeEvent, Void> islChangeCallback =
                islChangeEvent -> {
                    System.out.println(islChangeEvent);
                    otherNetworkManager.handleIslChange(islChangeEvent);
                    return null;
                };

        networkManager.withSwitchChange(switchChangeCallback);
        networkManager.withIslChange(islChangeCallback);

        networkManager.createSwitch(NetworkTopologyConstants.sw1);
        networkManager.createSwitch(NetworkTopologyConstants.sw2);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkManager.createOrUpdateIsl(NetworkTopologyConstants.isl21);

        assertEquals(networkManager.dumpIsls(), otherNetworkManager.dumpIsls());
        assertEquals(networkManager.dumpSwitches(), otherNetworkManager.dumpSwitches());
    }
}
