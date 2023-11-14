/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.service;

import static java.lang.String.format;
import static org.openkilda.server42.messaging.FlowDirection.FORWARD;

import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.messaging.info.haflow.UpdateHaSubFlowCommand;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.model.Flow;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.topology.flowmonitoring.mapper.FlowMapper;
import org.openkilda.wfm.topology.flowmonitoring.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowState;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class FlowCacheService {

    private final Clock clock;
    private final Duration flowRttStatsExpirationTime;
    private final FlowCacheBoltCarrier carrier;
    private boolean active;
    private Map<String, FlowState> flowStates;
    private final FlowRepository flowRepository;
    private final HaSubFlowRepository haSubFlowRepository;

    public FlowCacheService(PersistenceManager persistenceManager, Clock clock,
                            Duration flowRttStatsExpirationTime, FlowCacheBoltCarrier carrier) {
        this.clock = clock;
        this.flowRttStatsExpirationTime = flowRttStatsExpirationTime;
        this.carrier = carrier;
        flowStates = createNewFlowStateInstance();
        active = false;
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        haSubFlowRepository = persistenceManager.getRepositoryFactory().createHaSubFlowRepository();
    }

    private void initCache() {
        flowStates.putAll(flowRepository.findAll().stream()
                .filter(flow -> !flow.isOneSwitchFlow())
                .filter(this::isCompletedFlow)
                .collect(Collectors.toMap(Flow::getFlowId, FlowMapper.INSTANCE::toFlowState)));

        flowStates.putAll(haSubFlowRepository.findAll().stream()
                .filter(this::isCompletedHaFlow)
                .filter(haSubFlow -> !haSubFlow.isOneSwitch())
                .collect(Collectors.toMap(HaSubFlow::getHaSubFlowId, HaFlowMapper.INSTANCE::toFlowState)));

        log.info("Flow cache initialized successfully.");
    }

    /**
     * Update flow RTT latency.
     */
    public void processFlowRttStatsData(FlowRttStatsData flowRttStatsData) {
        FlowState flowState = flowStates.get(flowRttStatsData.getFlowId());
        if (flowState == null) {
            log.warn("Skipping flow RTT stats for an unknown flow '{}'.", flowRttStatsData.getFlowId());
            return;
        }
        if (FORWARD.name().toLowerCase().equals(flowRttStatsData.getDirection())) {
            flowState.setForwardPathLatency(FlowMapper.INSTANCE.toFlowPathLatency(flowRttStatsData));
        } else {
            flowState.setReversePathLatency(FlowMapper.INSTANCE.toFlowPathLatency(flowRttStatsData));
        }
    }

    /**
     * Update flow info.
     */
    public void updateFlowInfo(UpdateFlowCommand info) {
        flowStates.put(info.getFlowId(), FlowMapper.INSTANCE.toFlowState(info));
    }

    /**
     * Update HA-Flow info.
     */
    public void updateHaFlowInfo(UpdateHaSubFlowCommand info) {
        Optional<HaSubFlow> optionalHaSubFlow = haSubFlowRepository.findById(info.getFlowId());
        if (optionalHaSubFlow.isPresent()) {
            HaSubFlow haSubFlow = optionalHaSubFlow.get();
            if (haSubFlow.getHaFlow() != null) {
                flowStates.put(info.getFlowId(), HaFlowMapper.INSTANCE.toFlowState(haSubFlow));
            } else {
                log.error(format("HA-Flow for HA-Sub-Flow %s is not found.", haSubFlow.getHaSubFlowId()));
            }
        } else {
            log.error(format("HA-Sub-Flow %s is not found.", info.getFlowId()));
        }
    }

    /**
     * Remove flow info.
     */
    public void removeFlowInfo(String flowId) {
        flowStates.remove(flowId);
    }

    /**
     * Start latency check for flow with flowId.
     */
    public void processFlowLatencyCheck(String flowId) {
        FlowState flowState = flowStates.get(flowId);
        if (flowState != null) {
            checkFlowLatency(flowId, flowState);
        } else {
            log.warn("Process flow latency check for unknown flow {}", flowId);
        }
    }

    /**
     * Activate the service. Init cache.
     */
    public void activate() {
        if (!active) {
            initCache();
            active = true;
        }
    }

    /**
     * Deactivate the service. Clears cache.
     */
    public void deactivate() {
        if (active) {
            flowStates = createNewFlowStateInstance();
            log.info("Flow cache cleared.");
            active = false;
        }
    }

    private void checkFlowLatency(String flowId, FlowState flowState) {
        Instant current = clock.instant();
        if (isExpired(flowState.getForwardPathLatency().getTimestamp(), current)) {
            carrier.emitCalculateFlowLatencyRequest(flowId, FlowDirection.FORWARD, flowState.getForwardPath(),
                    flowState.getHaFlowId());
        } else {
            carrier.emitCheckFlowLatencyRequest(flowId, FlowDirection.FORWARD,
                    flowState.getForwardPathLatency().getLatency());
        }
        if (isExpired(flowState.getReversePathLatency().getTimestamp(), current)) {
            carrier.emitCalculateFlowLatencyRequest(flowId, FlowDirection.REVERSE, flowState.getReversePath(),
                    flowState.getHaFlowId());
        } else {
            carrier.emitCheckFlowLatencyRequest(flowId, FlowDirection.REVERSE,
                    flowState.getReversePathLatency().getLatency());
        }
    }

    public boolean isExpired(Instant timestamp, Instant current) {
        return timestamp == null || current.isAfter(timestamp.plus(flowRttStatsExpirationTime));
    }

    private boolean isCompletedFlow(Flow flow) {
        if (flow.getForwardPathId() == null || flow.getReversePathId() == null) {
            log.warn("Flow is incomplete, do not put it into flow cache (flow_id: {}, ctime: {}, mtime: {}",
                    flow.getFlowId(), flow.getTimeCreate(), flow.getTimeModify());
            return false;
        }
        return true;
    }

    private boolean isCompletedHaFlow(HaSubFlow haSubFlow) {
        HaFlow haFlow = haSubFlow.getHaFlow();
        if (haFlow == null || haFlow.getForwardPathId() == null || haFlow.getReversePathId() == null) {
            log.warn("HA-flow is incomplete, do not put it into flow cache (flow_id: {}, ctime: {}, mtime: {}",
                    haSubFlow.getHaFlowId(), haSubFlow.getTimeCreate(), haSubFlow.getTimeModify());
            return false;
        }
        return true;
    }

    /**
     * Check if flowState is empty.
     */
    @VisibleForTesting
    protected boolean flowStatesIsEmpty() {
        return flowStates.isEmpty();
    }

    /**
     * Instead of map.clear() we are creating a new map here.
     * We need it because map.clear() doesn't shrink already allocated map capacity, size of which can be significant.
     */
    private static Map<String, FlowState> createNewFlowStateInstance() {
        return new HashMap<>();
    }
}
