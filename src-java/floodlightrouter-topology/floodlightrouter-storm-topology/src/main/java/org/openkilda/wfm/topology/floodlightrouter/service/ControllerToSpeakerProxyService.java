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

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.BroadcastWrapper;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMapping;
import org.openkilda.wfm.topology.floodlightrouter.model.RegionMappingUpdate;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class ControllerToSpeakerProxyService {
    private final Clock clock;
    private final ControllerToSpeakerProxyCarrier carrier;

    private final Set<String> allRegions;
    private final RegionMapping switchMapping;

    public ControllerToSpeakerProxyService(
            ControllerToSpeakerProxyCarrier carrier, Set<String> allRegions, Duration switchMappingRemoveDelay) {
        this(Clock.systemUTC(), carrier, allRegions, switchMappingRemoveDelay);
    }

    public ControllerToSpeakerProxyService(
            Clock clock, ControllerToSpeakerProxyCarrier carrier, Set<String> allRegions,
            Duration switchMappingRemoveDelay) {
        this.clock = clock;
        this.carrier = carrier;

        this.allRegions = ImmutableSet.copyOf(allRegions);
        switchMapping = new RegionMapping(clock, switchMappingRemoveDelay);
    }

    public void unicastRequest(Message message) {
        SwitchId switchId = RouterUtils.lookupSwitchId(message);
        proxyUnicastRequest(new ProxyMessageWrapper(message), switchId);
    }

    public void unicastHsRequest(AbstractMessage message) {
        SwitchId switchId = RouterUtils.lookupSwitchId(message);
        proxyUnicastRequest(new ProxyHsMessageWrapper(message), switchId);
    }

    /**
     * Route request into all known regions.
     */
    public void broadcastRequest(CommandMessage message) {
        Map<String, Set<SwitchId>> population = switchMapping.organizeReadWritePopulationPerRegion();
        for (String region : allRegions) {
            Set<SwitchId> scope = population.getOrDefault(region, Collections.emptySet());
            CommandMessage broadcastRequest = makeBroadcastRequest(
                    clock.instant(), message.getData(), scope, message.getCorrelationId());
            carrier.sendToSpeaker(broadcastRequest, region);
        }
    }

    /**
     * Route {@link StatsRequest}. Prefer to RO only regions.
     */
    public void statsRequest(StatsRequest request, String correlationId) {
        Map<String, Set<SwitchId>> rwPopulation = switchMapping.organizeReadWritePopulationPerRegion();
        Map<String, Set<SwitchId>> roPopulation = switchMapping.organizeReadOnlyPopulationPerRegion();

        Set<String> roRegions = new HashSet<>(roPopulation.keySet());
        roRegions.removeAll(rwPopulation.keySet());

        Instant now = clock.instant();
        Set<SwitchId> processed = new HashSet<>();
        for (String region : roRegions) {
            sendStatsRequest(now, region, roPopulation.get(region), request, correlationId, processed);
        }
        for (Map.Entry<String, Set<SwitchId>> entry : rwPopulation.entrySet()) {
            sendStatsRequest(now, entry.getKey(), entry.getValue(), request, correlationId, processed);
        }
    }

    public void switchMappingUpdate(RegionMappingUpdate update) {
        switchMapping.apply(update);
    }

    private void proxyUnicastRequest(ProxyPayloadWrapper wrapper, SwitchId switchId) {
        Optional<String> region = switchMapping.lookupReadWriteRegion(switchId);
        if (region.isPresent()) {
            wrapper.sendToSpeaker(carrier, region.get());
        } else {
            wrapper.regionNotFound(carrier, switchId);
        }
    }

    private void sendStatsRequest(
            Instant now, String region, Set<SwitchId> scope, StatsRequest seed, String correlationId,
            Set<SwitchId> processed) {
        scope = new HashSet<>(scope);
        scope.removeAll(processed);

        processed.addAll(scope);
        carrier.sendToSpeaker(makeBroadcastRequest(now, seed, scope, correlationId), region);
    }

    private CommandMessage makeBroadcastRequest(
            Instant now, CommandData payload, Set<SwitchId> scope, String correlationId) {
        BroadcastWrapper wrapper = new BroadcastWrapper(scope, payload);
        return new CommandMessage(wrapper, now.toEpochMilli(), correlationId);
    }
}
