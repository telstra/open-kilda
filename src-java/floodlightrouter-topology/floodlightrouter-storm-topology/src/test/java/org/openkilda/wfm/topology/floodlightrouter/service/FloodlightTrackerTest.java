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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.openkilda.model.SwitchId;
import org.openkilda.stubs.ManualClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapstruct.ap.internal.util.Collections;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class FloodlightTrackerTest {
    private static long DEFAULT_ALIVE_TIMEOUT = 5L;
    private static long DEFAULT_RESPONSE_TIMEOUT = 1L;
    private static String REGION_ONE = "1";
    private static String REGION_TWO = "2";

    private static SwitchId SWITCH_R1_ONE = new SwitchId(1001);
    private static SwitchId SWITCH_R1_TWO = new SwitchId(1002);

    private static SwitchId SWITCH_R2_ONE = new SwitchId(2001);
    private static SwitchId SWITCH_R2_TWO = new SwitchId(2002);

    private final ManualClock testClock = new ManualClock(Instant.EPOCH, ZoneOffset.UTC);
    private final AliveSetup aliveSetup = new AliveSetup(testClock, DEFAULT_ALIVE_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT);
    private Set<String> regions = Collections.asSet(REGION_ONE, REGION_TWO);
    private FloodlightTracker service;

    @Mock
    RegionMonitorCarrier carrier;

    @Before
    public void setUp() {
        testClock.set(Instant.EPOCH);
        service = new FloodlightTracker(carrier, regions, aliveSetup);
    }

    @Test
    public void testAliveExpiration() {
        // all regions are alive on start
        service.handleAliveExpiration();
        verifyNoMoreInteractions(carrier);

        // region 1 received an alive response, while region 2 does not
        testClock.adjust(Duration.ofSeconds(DEFAULT_ALIVE_TIMEOUT - 1));
        service.handleAliveEvidence(REGION_ONE, testClock.instant().toEpochMilli());

        testClock.set(Instant.EPOCH.plusSeconds(DEFAULT_ALIVE_TIMEOUT + 1));
        service.handleAliveExpiration();
        verify(carrier).emitRegionBecameUnavailableNotification(REGION_TWO);
        verifyNoMoreInteractions(carrier);
        reset(carrier);

        // region 1 become unavailable too
        testClock.set(Instant.EPOCH.plusSeconds(DEFAULT_ALIVE_TIMEOUT * 2));
        service.handleAliveExpiration();
        verify(carrier).emitRegionBecameUnavailableNotification(REGION_ONE);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    public void testAliveResponseHandling() {
        // force all regions to become "expired/unmanaged"
        testClock.adjust(Duration.ofSeconds(DEFAULT_ALIVE_TIMEOUT + 1));
        service.handleAliveExpiration();
        verify(carrier).emitRegionBecameUnavailableNotification(REGION_ONE);
        verify(carrier).emitRegionBecameUnavailableNotification(REGION_TWO);
        verifyNoMoreInteractions(carrier); // there is no switches, so there is no unmanaged notifications

        // 1 second later
        testClock.adjust(Duration.ofSeconds(1));
        service.handleAliveEvidence(REGION_ONE, testClock.instant().toEpochMilli());
        verify(carrier).emitNetworkDumpRequest(REGION_ONE);
        service.handleAliveEvidence(REGION_TWO, testClock.instant().toEpochMilli());
        verify(carrier).emitNetworkDumpRequest(REGION_TWO);
        reset(carrier);

        // 1 second later (do not require sync on consecutive responses)
        testClock.adjust(Duration.ofSeconds(1));
        service.handleAliveEvidence(REGION_ONE, testClock.instant().toEpochMilli());
        service.handleAliveEvidence(REGION_TWO, testClock.instant().toEpochMilli());
        verify(carrier, never()).emitNetworkDumpRequest(any(String.class));
    }

    @Test
    public void testRegionsRequireAliveRequest() {
        // need to move clock on any not zero value, because all interval overcome check use strict comparison '<'
        testClock.adjust(Duration.ofMillis(1));

        service.emitAliveRequests();
        verify(carrier, times(1)).emitSpeakerAliveRequest(REGION_ONE);
        verify(carrier, times(1)).emitSpeakerAliveRequest(REGION_TWO);

        // this call is clock independent, so it will be same in consecutive call
        service.emitAliveRequests();
        verify(carrier, times(2)).emitSpeakerAliveRequest(REGION_ONE);
        verify(carrier, times(2)).emitSpeakerAliveRequest(REGION_TWO);

        // register response from region 1
        service.handleAliveEvidence(REGION_ONE, testClock.instant().toEpochMilli());
        service.emitAliveRequests();
        verify(carrier, times(2)).emitSpeakerAliveRequest(REGION_ONE);
        verify(carrier, times(3)).emitSpeakerAliveRequest(REGION_TWO);

        // after DEFAULT_RESPONSE_TIMEOUT seconds later
        testClock.adjust(Duration.ofSeconds(DEFAULT_RESPONSE_TIMEOUT));
        service.emitAliveRequests();
        verify(carrier, times(2)).emitSpeakerAliveRequest(REGION_ONE);
        verify(carrier, times(4)).emitSpeakerAliveRequest(REGION_TWO);

        // 1 ms later
        testClock.adjust(Duration.ofMillis(1));
        service.emitAliveRequests();
        verify(carrier, times(3)).emitSpeakerAliveRequest(REGION_ONE);
        verify(carrier, times(5)).emitSpeakerAliveRequest(REGION_TWO);
    }
}
