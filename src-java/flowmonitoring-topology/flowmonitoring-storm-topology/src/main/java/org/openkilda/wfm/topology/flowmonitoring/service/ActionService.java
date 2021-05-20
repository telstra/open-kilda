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

import static java.lang.String.format;

import org.openkilda.messaging.info.flow.UpdateFlowInfo;
import org.openkilda.model.Flow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowmonitoring.bolt.FlowOperationsCarrier;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Context;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Event;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.FlowLatencyMonitoringFsmFactory;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ActionService implements FlowSlaMonitoringCarrier {

    private static final String FORWARD = FlowDirection.FORWARD.name().toLowerCase();
    private static final String REVERSE = FlowDirection.REVERSE.name().toLowerCase();
    private static final Set<PathComputationStrategy> LATENCY_BASED_STRATEGIES =
            Sets.newHashSet(PathComputationStrategy.LATENCY, PathComputationStrategy.MAX_LATENCY);

    private FlowOperationsCarrier carrier;
    private FlowRepository flowRepository;
    private FlowLatencyMonitoringFsmFactory fsmFactory;
    private FsmExecutor<FlowLatencyMonitoringFsm, State, Event, Context> fsmExecutor;

    private float threshold;

    private Map<String, FlowLatencyMonitoringFsm> fsms = new HashMap<>();

    public ActionService(FlowOperationsCarrier carrier, PersistenceManager persistenceManager,
                         Clock clock, Duration timeout, float threshold) {
        this.carrier = carrier;
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        fsmFactory = FlowLatencyMonitoringFsm.factory(clock, timeout);
        fsmExecutor = fsmFactory.produceExecutor();
        this.threshold = threshold;
    }

    /**
     * Update flow info.
     */
    public void updateFlowInfo(UpdateFlowInfo flowInfo) {
        String flowId = flowInfo.getFlowId();
        if (flowInfo.getFlowPath().getForwardPath() == null
                || flowInfo.getFlowPath().getForwardPath().isEmpty()) {
            fsms.remove(getFsmKey(flowId, FORWARD));
        } else {
            long maxLatency = flowInfo.getMaxLatency() == null || flowInfo.getMaxLatency() == 0
                    ? Long.MAX_VALUE : flowInfo.getMaxLatency();
            long maxLatencyTier2 = flowInfo.getMaxLatencyTier2() == null || flowInfo.getMaxLatencyTier2() == 0
                    ? Long.MAX_VALUE : flowInfo.getMaxLatencyTier2();
            fsms.put(getFsmKey(flowId, FORWARD), fsmFactory.produce(flowId, FORWARD, maxLatency, maxLatencyTier2));
        }
        if (flowInfo.getFlowPath().getReversePath() == null
                || flowInfo.getFlowPath().getReversePath().isEmpty()) {
            fsms.remove(getFsmKey(flowId, REVERSE));
        } else {
            long maxLatency = flowInfo.getMaxLatency() == null || flowInfo.getMaxLatency() == 0
                    ? Long.MAX_VALUE : flowInfo.getMaxLatency();
            long maxLatencyTier2 = flowInfo.getMaxLatencyTier2() == null || flowInfo.getMaxLatencyTier2() == 0
                    ? Long.MAX_VALUE : flowInfo.getMaxLatencyTier2();
            fsms.put(getFsmKey(flowId, REVERSE), fsmFactory.produce(flowId, REVERSE, maxLatency, maxLatencyTier2));
        }
    }

    /**
     * Check flow SLA is violated.
     */
    public void checkFlowSla(String flowId, FlowDirection direction, long latency) {
        String key = getFsmKey(flowId, direction.name().toLowerCase());
        FlowLatencyMonitoringFsm fsm = fsms.get(key);
        if (fsm == null) {
            Flow flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new IllegalStateException(format("Flow %s not found.", flowId)));
            long maxLatency = flow.getMaxLatency() == null || flow.getMaxLatency() == 0
                    ? Long.MAX_VALUE : flow.getMaxLatency();
            long maxLatencyTier2 = flow.getMaxLatencyTier2() == null || flow.getMaxLatencyTier2() == 0
                    ? Long.MAX_VALUE : flow.getMaxLatencyTier2();
            fsm = fsmFactory.produce(flowId, direction.name().toLowerCase(), maxLatency, maxLatencyTier2);
            fsms.put(key, fsm);
        }

        Event event = getCorrespondingEvent(fsm, latency);
        Context context = Context.builder()
                .latency(latency)
                .carrier(this)
                .build();
        fsmExecutor.fire(fsm, event, context);
    }

    private String getFsmKey(String flowId, String direction) {
        return format("%s_%s", flowId, direction);
    }

    private Event getCorrespondingEvent(FlowLatencyMonitoringFsm fsm, long latency) {
        long maxLatency = fsm.getMaxLatency();
        long maxLatencyTier2 = fsm.getMaxLatencyTier2();
        switch (fsm.getLastStableState()) {
            case HEALTHY:
                maxLatency = (long) (maxLatency * (1 + threshold));
                break;
            case TIER_1_FAILED:
                maxLatency = (long) (maxLatency * (1 - threshold));
                maxLatencyTier2 = (long) (maxLatencyTier2 * (1 + threshold));
                break;
            case TIER_2_FAILED:
                maxLatencyTier2 = (long) (maxLatencyTier2 * (1 + threshold));
                break;
            case _INIT:
                break;
            default:
                throw new IllegalStateException(format("Unknown last stable state %s", fsm.getLastStableState()));
        }

        if (latency > maxLatency) {
            if (latency > maxLatencyTier2) {
                return Event.TIER_2_FAILED;
            }

            return Event.TIER_1_FAILED;
        }
        return Event.HEALTHY;
    }

    @Override
    public void saveFlowLatency(String flowId, String direction, long latency) {
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalStateException(format("Flow %s not found.", flowId)));
        if (FlowDirection.FORWARD.name().toLowerCase().equals(direction)) {
            flow.setForwardLatency(latency);
        } else {
            flow.setReverseLatency(latency);
        }
    }

    @Override
    public void sendFlowSyncRequest(String flowId) {
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalStateException(format("Flow %s not found.", flowId)));
        if (LATENCY_BASED_STRATEGIES.contains(flow.getPathComputationStrategy())) {
            carrier.sendFlowSyncRequest(flowId);
        }
    }

    @Override
    public void sendFlowRerouteRequest(String flowId) {
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalStateException(format("Flow %s not found.", flowId)));
        if (LATENCY_BASED_STRATEGIES.contains(flow.getPathComputationStrategy())) {
            carrier.sendFlowRerouteRequest(flowId);
        }
    }
}
