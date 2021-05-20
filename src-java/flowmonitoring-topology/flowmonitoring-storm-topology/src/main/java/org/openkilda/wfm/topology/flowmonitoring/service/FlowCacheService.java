/* Copyright 2021 Telstra Open Source
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

import static org.openkilda.server42.messaging.FlowDirection.FORWARD;

import org.openkilda.messaging.info.flow.UpdateFlowInfo;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.topology.flowmonitoring.mapper.FlowMapper;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowState;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FlowCacheService {

    private Clock clock;
    private long flowRttStatsExpirationTime;
    private FlowCacheBoltCarrier carrier;

    private Map<String, FlowState> flowStates;

    public FlowCacheService(PersistenceManager persistenceManager, Clock clock,
                            long flowRttStatsExpirationTime, FlowCacheBoltCarrier carrier) {
        this.clock = clock;
        this.flowRttStatsExpirationTime = flowRttStatsExpirationTime;
        this.carrier = carrier;

        FlowRepository flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();

        flowStates = flowRepository.findAll().stream()
                .filter(flow -> !flow.isOneSwitchFlow())
                .collect(Collectors.toMap(Flow::getFlowId, FlowMapper.INSTANCE::toFlowState));
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
        if (FORWARD.name().equals(flowRttStatsData.getDirection())) {
            flowState.setForwardPathLatency(FlowMapper.INSTANCE.toFlowPathLatency(flowRttStatsData));
        } else {
            flowState.setReversePathLatency(FlowMapper.INSTANCE.toFlowPathLatency(flowRttStatsData));
        }
    }

    /**
     * Update flow info.
     */
    public void updateFlowInfo(UpdateFlowInfo info) {
        if (info.getFlowPath().getForwardPath() == null
                || info.getFlowPath().getForwardPath().isEmpty()) {
            flowStates.remove(info.getFlowId());
        } else {
            flowStates.put(info.getFlowId(), FlowMapper.INSTANCE.toFlowState(info));
        }
    }

    /**
     * Start latency check for all flows.
     */
    public void processFlowLatencyCheck() {
        flowStates.forEach(this::checkFlowLatency);
    }

    private void checkFlowLatency(String flowId, FlowState flowState) {
        long currentTimeMillis = clock.millis();
        if (currentTimeMillis > flowState.getForwardPathLatency().getTimestamp() + flowRttStatsExpirationTime) {
            carrier.emitCalculateFlowLatencyRequest(flowId, FlowDirection.FORWARD, flowState.getForwardPath());
        } else {
            carrier.emitCheckFlowLatencyRequest(flowId, FlowDirection.FORWARD,
                    flowState.getForwardPathLatency().getLatency());
        }
        if (currentTimeMillis > flowState.getReversePathLatency().getTimestamp() + flowRttStatsExpirationTime) {
            carrier.emitCalculateFlowLatencyRequest(flowId, FlowDirection.REVERSE, flowState.getReversePath());
        } else {
            carrier.emitCheckFlowLatencyRequest(flowId, FlowDirection.REVERSE,
                    flowState.getReversePathLatency().getLatency());
        }
    }
}
