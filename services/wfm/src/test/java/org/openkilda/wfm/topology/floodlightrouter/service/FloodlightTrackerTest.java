/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.messaging.Message;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.bolts.RouterBolt;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FloodlightTrackerTest {
    private static long DEFAULT_ALIVE_TIMEOUT = 5L;
    private static String REGION_ONE = "1";
    private static String REGION_TWO = "2";

    private static SwitchId SWITCH_ID_ONE = new SwitchId(1);
    private static SwitchId SWITCH_ID_TWO = new SwitchId(2);
    private Set<String> floodlights;

    public FloodlightTrackerTest() {
        floodlights = new HashSet<>();
        floodlights.add("1");
        floodlights.add("2");
    }

    @Test
    public void testUpdateSwitchRegion() {
        FloodlightTracker floodlightTracker = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        floodlightTracker.updateSwitchRegion(SWITCH_ID_ONE, REGION_ONE);
        assertTrue(floodlightTracker.switchRegionMap.containsKey(SWITCH_ID_ONE));

    }

    @Test
    public void testLookupRegion() {
        FloodlightTracker floodlightTracker = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        floodlightTracker.switchRegionMap.putIfAbsent(SWITCH_ID_ONE, REGION_ONE);
        String actualRegion = floodlightTracker.lookupRegion(SWITCH_ID_ONE);
        assertEquals(REGION_ONE, actualRegion);
    }

    @Test
    public void testGetActiveRegions() {
        FloodlightTracker floodlightTracker  = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        FloodlightInstance floodlightInstance = new FloodlightInstance(REGION_ONE);
        floodlightInstance.setAlive(true);

        floodlightTracker.floodlightStatus.put(REGION_ONE, floodlightInstance);

        Set<String> actualActiveRegions = floodlightTracker.getActiveRegions();
        assertTrue(actualActiveRegions.contains(REGION_ONE));
        assertTrue(actualActiveRegions.size() == 1);

    }

    @Test
    public void testInActiveRegions() {
        FloodlightTracker floodlightTracker  = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        Set<String> actualInActiveRegions = floodlightTracker.getInActiveRegions();
        assertTrue(actualInActiveRegions.contains(REGION_ONE));
        assertTrue(actualInActiveRegions.contains(REGION_TWO));
        assertTrue(actualInActiveRegions.size() == 2);
    }

    @Test
    public void testGetUnmanageableSwitches() {
        FloodlightTracker floodlightTracker  = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        floodlightTracker.switchRegionMap.put(SWITCH_ID_ONE, REGION_ONE);
        floodlightTracker.switchRegionMap.put(SWITCH_ID_TWO, REGION_TWO);
        List<SwitchId> unmanagedSwitches = floodlightTracker.getUnmanageableSwitches();
        assertTrue(unmanagedSwitches.size() == 2);
        assertTrue(unmanagedSwitches.contains(SWITCH_ID_ONE));
        assertTrue(unmanagedSwitches.contains(SWITCH_ID_TWO));
    }

    @Test
    public void testHandleAliveResponseNeedDiscovery() {
        FloodlightTracker floodlightTracker  = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        long responseTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(2);
        assertTrue(floodlightTracker.handleAliveResponse(REGION_ONE, responseTime));
    }

    @Test
    public void testHandleAliveResponseNoNeedDiscovery() {
        FloodlightTracker floodlightTracker  = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        floodlightTracker.floodlightStatus.get(REGION_ONE).setAlive(true);
        long responseTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(2);
        assertFalse(floodlightTracker.handleAliveResponse(REGION_ONE, responseTime));
    }

    @Test
    public void testHandleAliveResponseNoNeedDiscoveryOffline() {
        FloodlightTracker floodlightTracker  = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        floodlightTracker.floodlightStatus.get(REGION_ONE).setAlive(true);
        long responseTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(20);
        assertFalse(floodlightTracker.handleAliveResponse(REGION_ONE, responseTime));
    }

    @Test
    public void testHandleUnmanagedSwitches() {
        FloodlightTracker floodlightTracker  = new FloodlightTracker(floodlights, DEFAULT_ALIVE_TIMEOUT);
        floodlightTracker.switchRegionMap.put(SWITCH_ID_ONE, REGION_ONE);
        floodlightTracker.switchRegionMap.put(SWITCH_ID_TWO, REGION_TWO);
        RouterBolt.RouterMessageSender sender = mock(RouterBolt.RouterMessageSender.class);
        floodlightTracker.handleUnmanagedSwitches(sender);
        verify(sender, times(2)).send((Message) any(), (String) any());
    }
}
