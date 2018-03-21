/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.pce.cache;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.pce.NetworkTopologyConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NetworkCacheTest {
    private final NetworkCache networkCache = new NetworkCache();

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
        networkCache.clear();
    }

    @Test
    public void getSwitch() throws Exception {
        networkCache.createSwitch(NetworkTopologyConstants.sw1);
        networkCache.createSwitch(NetworkTopologyConstants.sw2);
        assertEquals(NetworkTopologyConstants.sw1,
                networkCache.getSwitch(NetworkTopologyConstants.sw1.getSwitchId()));
        assertEquals(NetworkTopologyConstants.sw2,
                networkCache.getSwitch(NetworkTopologyConstants.sw2.getSwitchId()));
    }

    @Test
    public void createSwitch() throws Exception {
        networkCache.createSwitch(NetworkTopologyConstants.sw1);
        networkCache.createSwitch(NetworkTopologyConstants.sw2);
        assertEquals(2, networkCache.dumpSwitches().size());
    }

    @Test
    public void updateSwitch() throws Exception {
        String swId = "sw7";
        SwitchInfoData sw7 = new SwitchInfoData(swId, SwitchState.ACTIVATED, "", "", "", "");
        networkCache.createSwitch(sw7);
        assertEquals(sw7, networkCache.getSwitch(swId));

        SwitchInfoData sw7updated = new SwitchInfoData(swId, SwitchState.ACTIVATED, "", "", "", "");
        networkCache.updateSwitch(sw7updated);
        assertEquals(sw7updated, networkCache.getSwitch(swId));

        networkCache.deleteSwitch(swId);
        Set<SwitchInfoData> switches = networkCache.dumpSwitches();
        assertEquals(Collections.emptySet(), switches);
    }

    @Test
    public void createOrUpdateSwitch() throws Exception {
        networkCache.createOrUpdateSwitch(NetworkTopologyConstants.sw1);
        networkCache.createOrUpdateSwitch(NetworkTopologyConstants.sw2);

        assertEquals(2, networkCache.dumpSwitches().size());

        networkCache.createOrUpdateSwitch(NetworkTopologyConstants.sw1);
        networkCache.createOrUpdateSwitch(NetworkTopologyConstants.sw2);

        assertEquals(2, networkCache.dumpSwitches().size());
    }

    @Test
    public void deleteSwitch() throws Exception {
        createSwitch();
        networkCache.deleteSwitch(NetworkTopologyConstants.sw1.getSwitchId());
        networkCache.deleteSwitch(NetworkTopologyConstants.sw2.getSwitchId());
        assertEquals(0, networkCache.dumpSwitches().size());
    }

    @Test
    public void dumpSwitches() throws Exception {
        createSwitch();
        Set<SwitchInfoData> switches = networkCache.dumpSwitches();
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1,
                NetworkTopologyConstants.sw2)), switches);
    }

    @Test
    public void getStateSwitches() throws Exception {
        networkCache.createSwitch(NetworkTopologyConstants.sw1);
        networkCache.createSwitch(NetworkTopologyConstants.sw2);
        networkCache.createSwitch(NetworkTopologyConstants.sw3);
        Set<SwitchInfoData> activeSwitches = networkCache.getStateSwitches(SwitchState.ACTIVATED);
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1,
                NetworkTopologyConstants.sw2)), activeSwitches);
    }

    @Test
    public void getControllerSwitches() throws Exception {
        networkCache.createSwitch(NetworkTopologyConstants.sw1);
        networkCache.createSwitch(NetworkTopologyConstants.sw2);
        networkCache.createSwitch(NetworkTopologyConstants.sw3);
        Set<SwitchInfoData> activeSwitches = networkCache.getControllerSwitches("localhost");
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1,
                NetworkTopologyConstants.sw2)), activeSwitches);
    }

    @Test
    public void getDirectlyConnectedSwitches() throws Exception {
        networkCache.createSwitch(NetworkTopologyConstants.sw1);
        networkCache.createSwitch(NetworkTopologyConstants.sw2);
        networkCache.createSwitch(NetworkTopologyConstants.sw3);

        Set<SwitchInfoData> directlyConnected = networkCache.getDirectlyConnectedSwitches(
                NetworkTopologyConstants.sw2.getSwitchId());
        assertEquals(new HashSet<>(), directlyConnected);

        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl21);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl23);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl32);

        directlyConnected = networkCache.getDirectlyConnectedSwitches(NetworkTopologyConstants.sw2.getSwitchId());
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.sw1, NetworkTopologyConstants.sw3)), directlyConnected);
    }

    @Test
    public void createOrUpdateIsl() throws Exception {
        networkCache.createSwitch(NetworkTopologyConstants.sw1);
        networkCache.createSwitch(NetworkTopologyConstants.sw2);
        networkCache.createSwitch(NetworkTopologyConstants.sw3);

        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl21);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl23);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl32);

        assertEquals(4, networkCache.dumpIsls().size());

        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl12);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl21);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl23);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl32);

        assertEquals(4, networkCache.dumpIsls().size());
    }

    @Test
    public void deleteIsl() throws Exception {
        createOrUpdateIsl();

        networkCache.deleteIsl(NetworkTopologyConstants.isl12.getId());
        networkCache.deleteIsl(NetworkTopologyConstants.isl21.getId());
        networkCache.deleteIsl(NetworkTopologyConstants.isl23.getId());
        networkCache.deleteIsl(NetworkTopologyConstants.isl32.getId());

        assertEquals(0, networkCache.dumpIsls().size());
    }

    @Test
    public void getIsl() throws Exception {
        createOrUpdateIsl();
        assertEquals(NetworkTopologyConstants.isl12, networkCache.getIsl(NetworkTopologyConstants.isl12.getId()));
        assertEquals(NetworkTopologyConstants.isl21, networkCache.getIsl(NetworkTopologyConstants.isl21.getId()));
        assertEquals(NetworkTopologyConstants.isl23, networkCache.getIsl(NetworkTopologyConstants.isl23.getId()));
        assertEquals(NetworkTopologyConstants.isl32, networkCache.getIsl(NetworkTopologyConstants.isl32.getId()));
    }

    @Test
    public void dumpIsls() throws Exception {
        createOrUpdateIsl();
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.isl12, NetworkTopologyConstants.isl21,
                NetworkTopologyConstants.isl23, NetworkTopologyConstants.isl32)), networkCache.dumpIsls());
    }

    @Test
    public void getIslsBySwitch() throws Exception {
        createOrUpdateIsl();
        networkCache.createSwitch(NetworkTopologyConstants.sw4);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl14);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl41);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl24);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl42);
        assertEquals(new HashSet<>(Arrays.asList(
                NetworkTopologyConstants.isl12, NetworkTopologyConstants.isl21, NetworkTopologyConstants.isl23,
                NetworkTopologyConstants.isl32, NetworkTopologyConstants.isl24, NetworkTopologyConstants.isl42)),
                networkCache.getIslsBySwitch(NetworkTopologyConstants.sw2.getSwitchId()));
    }

    @Test
    public void getIslsBySource() throws Exception {
        createOrUpdateIsl();
        networkCache.createSwitch(NetworkTopologyConstants.sw4);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl14);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl41);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl24);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl42);
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.isl21,
                NetworkTopologyConstants.isl23, NetworkTopologyConstants.isl24)),
                networkCache.getIslsBySource(NetworkTopologyConstants.sw2.getSwitchId()));
    }

    @Test
    public void getIslsByDestination() throws Exception {
        createOrUpdateIsl();
        networkCache.createSwitch(NetworkTopologyConstants.sw4);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl14);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl41);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl24);
        networkCache.createOrUpdateIsl(NetworkTopologyConstants.isl42);
        assertEquals(new HashSet<>(Arrays.asList(NetworkTopologyConstants.isl12,
                NetworkTopologyConstants.isl32, NetworkTopologyConstants.isl42)),
                networkCache.getIslsByDestination(NetworkTopologyConstants.sw2.getSwitchId()));
    }
}
