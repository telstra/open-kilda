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
import static org.openkilda.server42.messaging.FlowDirection.FORWARD;
import static org.openkilda.server42.messaging.FlowDirection.REVERSE;

import org.openkilda.messaging.info.flow.UpdateFlowInfo;
import org.openkilda.model.Flow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.server42.messaging.FlowDirection;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.flowmonitoring.bolt.FlowOperationsCarrier;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Context;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.Event;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.FlowLatencyMonitoringFsmFactory;
import org.openkilda.wfm.topology.flowmonitoring.fsm.FlowLatencyMonitoringFsm.State;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class ActionService implements FlowSlaMonitoringCarrier {

    private static final Set<PathComputationStrategy> LATENCY_BASED_STRATEGIES =
            Sets.newHashSet(PathComputationStrategy.LATENCY, PathComputationStrategy.MAX_LATENCY);

    private FlowOperationsCarrier carrier;
    private FlowRepository flowRepository;
    private FeatureTogglesRepository featureTogglesRepository;
    private TransactionManager transactionManager;
    private FlowLatencyMonitoringFsmFactory fsmFactory;
    private FsmExecutor<FlowLatencyMonitoringFsm, State, Event, Context> fsmExecutor;

    private float threshold;

    @VisibleForTesting
    protected Map<String, FlowLatencyMonitoringFsm> fsms = new HashMap<>();

    public ActionService(FlowOperationsCarrier carrier, PersistenceManager persistenceManager,
                         Clock clock, Duration timeout, float threshold) {
        this.carrier = carrier;
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        transactionManager = persistenceManager.getTransactionManager();
        fsmFactory = FlowLatencyMonitoringFsm.factory(clock, timeout, threshold);
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
            fsms.put(getFsmKey(flowId, FORWARD), fsmFactory.produce(flowId, FORWARD.name().toLowerCase(),
                    flowInfo.getMaxLatency(), flowInfo.getMaxLatencyTier2()));
        }
        if (flowInfo.getFlowPath().getReversePath() == null
                || flowInfo.getFlowPath().getReversePath().isEmpty()) {
            fsms.remove(getFsmKey(flowId, REVERSE));
        } else {
            fsms.put(getFsmKey(flowId, REVERSE), fsmFactory.produce(flowId, REVERSE.name().toLowerCase(),
                    flowInfo.getMaxLatency(), flowInfo.getMaxLatencyTier2()));
        }
    }

    /**
     * Check flow SLA is violated.
     */
    public void processFlowLatencyMeasurement(String flowId, FlowDirection direction, Duration latency) {
        String key = getFsmKey(flowId, direction);
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

        Context context = Context.builder()
                .latency(latency.toNanos())
                .carrier(this)
                .build();
        fsm.processLatencyMeasurement(context);
    }

    /**
     * Process tick.
     */
    public void processTick() {
        Context context = Context.builder()
                .carrier(this)
                .build();
        fsms.values().forEach(fsm -> fsmExecutor.fire(fsm, Event.TICK, context));
    }

    private String getFsmKey(String flowId, FlowDirection direction) {
        return format("%s_%s", flowId, direction.name().toLowerCase());
    }

    @Override
    public void saveFlowLatency(String flowId, String direction, long latency) {
        transactionManager.doInTransaction(() -> {
            Optional<Flow> flow = flowRepository.findById(flowId);
            if (flow.isPresent()) {
                if (FORWARD.name().toLowerCase().equals(direction)) {
                    flow.get().setForwardLatency(latency);
                } else {
                    flow.get().setReverseLatency(latency);
                }
            } else {
                log.warn("Can't save latency for flow '{}'. Flow not found.", flowId);
            }
        });
    }

    @Override
    public void sendFlowSyncRequest(String flowId) {
        Optional<Flow> flow = flowRepository.findById(flowId);
        if (flow.isPresent()) {
            if (LATENCY_BASED_STRATEGIES.contains(flow.get().getPathComputationStrategy()) && isReactionsEnabled()) {
                log.info("Sending flow '{}' sync request.", flowId);
                carrier.sendFlowSyncRequest(flowId);
            }
        } else {
            log.warn("Can't send flow '{}' sync request. Flow not found.", flowId);
        }
    }

    @Override
    public void sendFlowRerouteRequest(String flowId) {
        Optional<Flow> flow = flowRepository.findById(flowId);
        if (flow.isPresent()) {
            if (LATENCY_BASED_STRATEGIES.contains(flow.get().getPathComputationStrategy()) && isReactionsEnabled()) {
                log.info("Sending flow '{}' reroute request.", flowId);
                carrier.sendFlowRerouteRequest(flowId);
            }
        } else {
            log.warn("Can't send flow '{}' reroute request. Flow is not found.", flowId);
        }
    }

    private boolean isReactionsEnabled() {
        return featureTogglesRepository.getOrDefault().getFlowLatencyMonitoringReactions();
    }
}
