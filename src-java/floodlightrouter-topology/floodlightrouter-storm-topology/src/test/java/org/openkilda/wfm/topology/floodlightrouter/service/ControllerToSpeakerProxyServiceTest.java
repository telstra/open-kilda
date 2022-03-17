/* Copyright 2020 Telstra Open Source
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.openkilda.messaging.command.BroadcastWrapper;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.stubs.ManualClock;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingAdd;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class ControllerToSpeakerProxyServiceTest {
    private static final String REGION_STATS = "stats";
    private static final String REGION_MANAGEMENT = "management";

    private static final SwitchId SWITCH_ALPHA = new SwitchId(1);
    private static final SwitchId SWITCH_BETA = new SwitchId(2);
    private static final SwitchId SWITCH_GAMMA = new SwitchId(3);

    private final ManualClock clock = new ManualClock();
    private final Duration switchMappingRemoveDelay = Duration.ofSeconds(5);

    @Mock
    private ControllerToSpeakerProxyCarrier carrier;

    @Test
    public void verifyStatsPreferRoRegions() {
        ControllerToSpeakerProxyService subject = makeSubject();

        // stats/RO only region
        subject.switchMappingUpdate(new RegionMappingAdd(SWITCH_ALPHA, REGION_STATS, false));
        subject.switchMappingUpdate(new RegionMappingAdd(SWITCH_BETA, REGION_STATS, false));

        // management/RW region
        subject.switchMappingUpdate(new RegionMappingAdd(SWITCH_BETA, REGION_MANAGEMENT, false));
        subject.switchMappingUpdate(new RegionMappingAdd(SWITCH_BETA, REGION_MANAGEMENT, true));
        subject.switchMappingUpdate(new RegionMappingAdd(SWITCH_GAMMA, REGION_MANAGEMENT, false));
        subject.switchMappingUpdate(new RegionMappingAdd(SWITCH_GAMMA, REGION_MANAGEMENT, true));

        verifyNoMoreInteractions(carrier);

        String correlationId = "dummy-request";
        StatsRequest request = new StatsRequest();
        subject.statsRequest(request, correlationId);

        // RO only region
        verify(carrier).sendToSpeaker(makeStatsRegionRequest(
                request, ImmutableSet.of(SWITCH_ALPHA, SWITCH_BETA), correlationId), REGION_STATS);
        verify(carrier).sendToSpeaker(
                makeStatsRegionRequest(request, ImmutableSet.of(SWITCH_GAMMA), correlationId), REGION_MANAGEMENT);
        verifyNoMoreInteractions(carrier);
    }

    private ControllerToSpeakerProxyService makeSubject() {
        Set<String> allRegions = ImmutableSet.of(REGION_STATS, REGION_MANAGEMENT);
        return new ControllerToSpeakerProxyService(clock, carrier, allRegions, switchMappingRemoveDelay);
    }

    private CommandMessage makeStatsRegionRequest(StatsRequest seed, Set<SwitchId> scope, String correlationId) {
        BroadcastWrapper wrapper = new BroadcastWrapper(scope, seed);
        return new CommandMessage(wrapper, clock.instant().toEpochMilli(), correlationId);
    }
}
