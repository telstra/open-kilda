/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.reroute.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReroutesThrottlingTest {

    private ReroutesThrottling reroutesThrottling;

    private Clock clock;

    private static final long minDelay = 10;

    private static final long maxDelay = 100;

    private static final String FLOW_ID_1 = "flow1";

    private static final String FLOW_ID_2 = "flow2";

    private static final String FLOW_ID_3 = "flow3";

    private static final FlowThrottlingData THROTTLING_DATA_1 = new FlowThrottlingData("corrId1", 1, null);

    private static final FlowThrottlingData THROTTLING_DATA_2 = new FlowThrottlingData("corrId2", 1, null);

    @Before
    public void init() {
        clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        reroutesThrottling = new ReroutesThrottling(new ExtendableTimeWindow(minDelay, maxDelay, clock));
    }

    @Test
    public void emptyTest() {
        assertTrue(reroutesThrottling.getReroutes().isEmpty());
    }

    @Test
    public void basicTest() {
        Instant event = Instant.now();
        Instant beforeTimeout = event.plusSeconds(minDelay);
        Instant afterTimeout = event.plusSeconds(minDelay + 1);

        when(clock.instant()).thenReturn(event, beforeTimeout, afterTimeout);

        reroutesThrottling.putRequest(FLOW_ID_1, THROTTLING_DATA_1);
        assertTrue(reroutesThrottling.getReroutes().isEmpty());

        List<Map.Entry<String, FlowThrottlingData>> expected = new ArrayList<>(
                ImmutableMap.of(FLOW_ID_1, THROTTLING_DATA_1).entrySet());
        assertEquals(expected, reroutesThrottling.getReroutes());
    }

    @Test
    public void unique() {
        Instant event = Instant.now();
        Instant afterTimeout = event.plusSeconds(minDelay + 1);

        when(clock.instant()).thenReturn(event, event, event, afterTimeout);

        reroutesThrottling.putRequest(FLOW_ID_1, THROTTLING_DATA_1);
        reroutesThrottling.putRequest(FLOW_ID_2, THROTTLING_DATA_1);
        reroutesThrottling.putRequest(FLOW_ID_1, THROTTLING_DATA_2);

        List<Map.Entry<String, FlowThrottlingData>> expected = new ArrayList<>(
                ImmutableMap.of(FLOW_ID_1, THROTTLING_DATA_2, FLOW_ID_2, THROTTLING_DATA_1).entrySet());
        assertEquals(expected, reroutesThrottling.getReroutes());
    }

    @Test
    public void hardTimeout() {
        Instant lastEvent = Instant.now();
        when(clock.instant()).thenReturn(lastEvent);
        FlowThrottlingData throttlingData = new FlowThrottlingData("corrId0", 1, null);
        reroutesThrottling.putRequest(FLOW_ID_1, throttlingData);

        long overallDelay = 0;
        //reroute of the same flow appears multiple times before reroute window closes
        while (overallDelay < maxDelay) {
            overallDelay += minDelay;
            lastEvent = lastEvent.plusSeconds(minDelay);
            when(clock.instant()).thenReturn(lastEvent, lastEvent);
            throttlingData.setCorrelationId("corrId" + overallDelay);
            reroutesThrottling.putRequest(FLOW_ID_1, throttlingData);
            assertTrue(reroutesThrottling.getReroutes().isEmpty());
        }
        Instant beforeHardTimeout = lastEvent;
        Instant afterHardTimeout = lastEvent.plusSeconds(1);
        when(clock.instant()).thenReturn(beforeHardTimeout, afterHardTimeout);
        //before hard timeout reroute still did not happen
        assertTrue(reroutesThrottling.getReroutes().isEmpty());
        //after hard timeout the reroute is finally released
        assertEquals(1, reroutesThrottling.getReroutes().size());
    }


    @Test
    public void priority() {
        Instant event = Instant.now();
        Instant afterTimeout = event.plusSeconds(minDelay + 1);

        when(clock.instant()).thenReturn(event, event, event, afterTimeout);

        //three flows with different priorities
        FlowThrottlingData throttlingData1 = new FlowThrottlingData("corrId1", 1, null);
        FlowThrottlingData throttlingData2 = new FlowThrottlingData("corrId1", 2, null);
        FlowThrottlingData throttlingData3 = new FlowThrottlingData("corrId1", 3, null);

        //add them in a non-priority order
        reroutesThrottling.putRequest(FLOW_ID_3, throttlingData3);
        reroutesThrottling.putRequest(FLOW_ID_1, throttlingData1);
        reroutesThrottling.putRequest(FLOW_ID_2, throttlingData2);

        //expect sorted reroutes, with higher priority flows going first
        List<Map.Entry<String, FlowThrottlingData>> expected = new ArrayList<>(
                ImmutableMap.of(FLOW_ID_1, throttlingData1,
                        FLOW_ID_2, throttlingData2,
                        FLOW_ID_3, throttlingData3).entrySet());
        assertEquals(expected, reroutesThrottling.getReroutes());
    }

    @Test
    public void extendWindow() {
        Instant event = Instant.now();
        Instant check = event.plusSeconds(minDelay);
        Instant secondEvent = event.plusSeconds(minDelay);
        Instant secondCheck = secondEvent.plusSeconds(minDelay);
        Instant finalCheck = secondEvent.plusSeconds(minDelay + 1);

        when(clock.instant()).thenReturn(event, check, secondEvent, secondCheck, finalCheck);

        reroutesThrottling.putRequest(FLOW_ID_1, THROTTLING_DATA_1);
        assertTrue(reroutesThrottling.getReroutes().isEmpty());
        reroutesThrottling.putRequest(FLOW_ID_2, THROTTLING_DATA_1);
        assertTrue(reroutesThrottling.getReroutes().isEmpty());
        assertFalse(reroutesThrottling.getReroutes().isEmpty());
    }
}
