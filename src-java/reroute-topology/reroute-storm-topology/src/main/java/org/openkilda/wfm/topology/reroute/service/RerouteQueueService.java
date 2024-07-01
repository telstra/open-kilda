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

package org.openkilda.wfm.topology.reroute.service;

import static java.lang.String.format;
import static org.openkilda.model.PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.FlowType;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.messaging.info.reroute.error.FlowInProgressError;
import org.openkilda.messaging.info.reroute.error.NoPathFoundError;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.messaging.info.reroute.error.SpeakerRequestError;
import org.openkilda.model.Flow;
import org.openkilda.model.HaFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.model.RerouteQueue;

import com.google.common.annotations.VisibleForTesting;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RerouteQueueService {

    private final int defaultFlowPriority;
    private final int maxRetry;
    private final FlowRepository flowRepository;
    private final YFlowRepository yFlowRepository;
    private final HaFlowRepository haFlowRepository;

    private final Map<String, RerouteQueue> reroutes = new HashMap<>();
    private final IRerouteQueueCarrier carrier;

    public RerouteQueueService(IRerouteQueueCarrier carrier, PersistenceManager persistenceManager,
                               int defaultFlowPriority, int maxRetry) {
        this.carrier = carrier;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        this.haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        this.defaultFlowPriority = defaultFlowPriority;
        this.maxRetry = maxRetry;
    }

    /**
     * Put reroute request to throttling.
     *
     * @param flowId flow id
     * @param throttlingData reroute request params
     */
    public void processAutomaticRequest(String flowId, FlowThrottlingData throttlingData) {
        FlowInfo flowInfo = getFlowInfo(flowId, throttlingData.getFlowType());
        if (!flowInfo.isPresent()) {
            log.warn(format("%s %s not found. Skip the reroute operation of this flow.", flowInfo.getType(), flowId));
            return;
        } else if (flowInfo.isPinned()) {
            log.info(format("%s %s is pinned. Skip the reroute operation of this flow.", flowInfo.getType(), flowId));
            return;
        }

        log.info("Puts reroute request for flow {} with correlationId {}.", flowId, throttlingData.getCorrelationId());
        RerouteQueue rerouteQueue = getRerouteQueue(flowId);
        rerouteQueue.putToThrottling(throttlingData);
        carrier.sendExtendTimeWindowEvent();
    }

    /**
     * Process manual reroute request.
     *
     * @param flowId flow id
     * @param throttlingData reroute request params
     */
    public void processManualRequest(String flowId, FlowThrottlingData throttlingData) {
        FlowInfo flowInfo = getFlowInfo(flowId, throttlingData.getFlowType());
        if (!flowInfo.isPresent()) {
            String description = format("%s %s not found", flowInfo.getType(), flowId);
            ErrorData errorData = new ErrorData(
                    ErrorType.NOT_FOUND, format("Could not reroute %s", flowInfo.getType().toLowerCase()), description);
            carrier.emitFlowRerouteError(errorData);
            return;
        } else if (flowInfo.isPinned()) {
            String description = format("Can't reroute pinned %s", flowInfo.getType().toLowerCase());
            ErrorData errorData =
                    new ErrorData(ErrorType.UNPROCESSABLE_REQUEST,
                            format("Could not reroute %s", flowInfo.getType().toLowerCase()), description);
            carrier.emitFlowRerouteError(errorData);
            return;
        }

        RerouteQueue rerouteQueue = getRerouteQueue(flowId);
        if (rerouteQueue.hasInProgress()) {
            String description = format("Flow %s is in reroute process", flowId);
            ErrorData errorData = new ErrorData(ErrorType.UNPROCESSABLE_REQUEST, "Could not reroute flow",
                    description);
            carrier.emitFlowRerouteError(errorData);
        } else {
            rerouteQueue.putToInProgress(throttlingData);
            sendRerouteRequest(flowId, throttlingData);
        }
    }

    /**
     * Process manual reroute flush request.
     *
     * @param flowId flow id
     */
    public void processManualFlushRequest(String flowId, FlowThrottlingData throttlingData) {
        RerouteQueue rerouteQueue = getRerouteQueue(flowId);
        log.info("Process manual Flush Request for flow: {}", flowId);
        rerouteQueue.putToInProgress(null);
    }

    /**
     * Process reroute result. Check fail reason, decide if retry is needed and schedule it if yes.
     *
     * @param rerouteResultInfoData reroute result
     * @param correlationId correlation id
     */
    public void processRerouteResult(RerouteResultInfoData rerouteResultInfoData,
                                     String correlationId) {
        String flowId = rerouteResultInfoData.getFlowId();
        RerouteQueue rerouteQueue = getRerouteQueue(flowId);
        FlowThrottlingData inProgress = rerouteQueue.getInProgress();
        if (inProgress == null || !Objects.equals(inProgress.getCorrelationId(), correlationId)) {
            log.error("Skipped unexpected reroute result for flow {} with correlation id {}.", flowId, correlationId);
            return;
        }
        carrier.cancelTimeout(correlationId);

        if (rerouteResultInfoData.isSuccess()) {
            FlowThrottlingData toSend = rerouteQueue.processPending();
            sendRerouteRequest(flowId, toSend);
        } else {
            RerouteError rerouteError = rerouteResultInfoData.getRerouteError();
            if (isRetryRequired(flowId, rerouteError, rerouteResultInfoData.getFlowType())) {
                injectRetry(flowId, rerouteQueue, rerouteError instanceof NoPathFoundError);
            } else {
                FlowThrottlingData toSend = rerouteQueue.processPending();
                sendRerouteRequest(flowId, toSend);
            }
        }
    }

    /**
     * Move reroute requests form throttling to pending/in-progress.
     */
    public void flushThrottling() {
        Map<String, FlowThrottlingData> requestsToSend = new HashMap<>();
        reroutes.forEach((flowId, rerouteQueue) -> rerouteQueue.flushThrottling()
                .ifPresent(flowThrottlingData -> requestsToSend.put(flowId, flowThrottlingData)));
        log.info("Send reroute requests for flows {}", requestsToSend.keySet());
        Comparator<FlowThrottlingData> comparator = ((Comparator<FlowThrottlingData>) this::comparePriority)
                .thenComparing(this::compareAvailableBandwidth)
                .thenComparing(this::compareTimeCreate);
        requestsToSend.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(comparator))
                .forEach(es -> {
                    String flowId = es.getKey();
                    FlowThrottlingData flowThrottlingData = es.getValue();
                    sendRerouteRequest(flowId, flowThrottlingData);
                });
    }

    /**
     * Handle timeout event for reroute with correlation id.
     *
     * @param correlationId reroute correlationId
     */
    public void handleTimeout(String correlationId) {
        log.warn("Reroute request with correlation id {} timed out.", correlationId);
        List<Entry<String, RerouteQueue>> foundReroutes = reroutes.entrySet().stream()
                .filter(es -> es.getValue().getInProgress() != null)
                .filter(es -> correlationId.equals(es.getValue().getInProgress().getCorrelationId()))
                .collect(Collectors.toList());
        if (foundReroutes.isEmpty()) {
            log.warn("No reroute with correlationId {} found. Timeout event skipped.", correlationId);
        } else if (foundReroutes.size() > 1) {
            log.error("Found more than one reroute with correlationId {}. Timed out all of them.", correlationId);
        }
        foundReroutes.forEach(entry -> injectRetry(entry.getKey(), entry.getValue(), false));
    }

    private boolean isRetryRequired(String flowId, RerouteError rerouteError, FlowType flowType) {
        if (rerouteError instanceof NoPathFoundError) {
            log.info("Received no path found error for flow {}", flowId);
            return true;
        } else if (rerouteError instanceof FlowInProgressError) {
            log.info("Received reroute in progress error for flow {}", flowId);
            return true;
        } else if (rerouteError instanceof SpeakerRequestError) {
            SpeakerRequestError ruleFailedError = (SpeakerRequestError) rerouteError;
            FlowInfo flowInfo = getFlowInfo(flowId, flowType);
            log.info("Received speaker request error for {} {}", flowInfo.getType(), flowId);
            if (!flowInfo.isPresent()) {
                log.error("{} {} not found", flowType, flowId);
                return false;
            }

            for (SwitchId switchId : flowInfo.endpointSwitchIds) {
                if (ruleFailedError.getSwitches().contains(switchId)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void injectRetry(String flowId, RerouteQueue rerouteQueue, boolean ignoreBandwidth) {
        log.info("Injecting retry for flow {} wuth ignore b/w flag {}", flowId, ignoreBandwidth);
        FlowThrottlingData retryRequest = rerouteQueue.getInProgress();
        if (retryRequest == null) {
            throw new IllegalStateException(format("Can not retry 'null' reroute request for flow %s.", flowId));
        }
        retryRequest.setIgnoreBandwidth(computeIgnoreBandwidth(retryRequest, ignoreBandwidth));
        if (retryRequest.getRetryCounter() < maxRetry) {
            retryRequest.increaseRetryCounter();
            String retryCorrelationId = new CommandContext(retryRequest.getCorrelationId())
                    .fork(format("retry #%d ignore_bw %b", retryRequest.getRetryCounter(),
                            retryRequest.isIgnoreBandwidth()))
                    .getCorrelationId();
            retryRequest.setCorrelationId(retryCorrelationId);
            FlowThrottlingData toSend = rerouteQueue.processRetryRequest(retryRequest, carrier);
            sendRerouteRequest(flowId, toSend);
        } else {
            log.error("No more retries available for reroute request {}.", retryRequest);
            FlowThrottlingData toSend = rerouteQueue.processPending();
            if (toSend != null) {
                toSend.setIgnoreBandwidth(computeIgnoreBandwidth(toSend, ignoreBandwidth));
            }
            sendRerouteRequest(flowId, toSend);
        }
    }

    @VisibleForTesting
    boolean computeIgnoreBandwidth(FlowThrottlingData data, boolean ignoreBandwidth) {
        return !data.isStrictBandwidth() && (data.isIgnoreBandwidth() || ignoreBandwidth);
    }

    private void sendRerouteRequest(String flowId, FlowThrottlingData throttlingData) {
        if (throttlingData != null) {
            switch (throttlingData.getFlowType()) {
                case Y_FLOW:
                    YFlowRerouteRequest yFlowRequest = new YFlowRerouteRequest(flowId, throttlingData.getAffectedIsl(),
                            throttlingData.getReason(), throttlingData.isIgnoreBandwidth());
                    carrier.sendRerouteRequest(throttlingData.getCorrelationId(), yFlowRequest);
                    break;
                
                case HA_FLOW:
                    HaFlowRerouteRequest haFlowRequest = new HaFlowRerouteRequest(flowId,
                            throttlingData.getAffectedIsl(), throttlingData.isEffectivelyDown(), 
                            throttlingData.getReason(), throttlingData.isIgnoreBandwidth(), false);
                    carrier.sendRerouteRequest(throttlingData.getCorrelationId(), haFlowRequest);
                    break;
                
                case FLOW: 
                    FlowRerouteRequest flowRequest = new FlowRerouteRequest(flowId,
                            throttlingData.isEffectivelyDown(), throttlingData.isIgnoreBandwidth(),
                            throttlingData.getAffectedIsl(), throttlingData.getReason(), false);
                    carrier.sendRerouteRequest(throttlingData.getCorrelationId(), flowRequest);
                    break;
                default:
                    logUnknownType(throttlingData.getFlowType());
            }
        }
    }

    private RerouteQueue getRerouteQueue(String flowId) {
        return reroutes.computeIfAbsent(flowId, key -> RerouteQueue.empty());
    }

    @VisibleForTesting
    Map<String, RerouteQueue> getReroutes() {
        return reroutes;
    }

    private int comparePriority(FlowThrottlingData throttlingDataA, FlowThrottlingData throttlingDataB) {
        Integer priorityA = Optional.ofNullable(throttlingDataA.getPriority()).orElse(defaultFlowPriority);
        Integer priorityB = Optional.ofNullable(throttlingDataB.getPriority()).orElse(defaultFlowPriority);
        return Integer.compare(priorityA, priorityB);
    }

    private int compareAvailableBandwidth(FlowThrottlingData throttlingDataA, FlowThrottlingData throttlingDataB) {
        PathComputationStrategy pathComputationStrategyA = throttlingDataA.getPathComputationStrategy();
        PathComputationStrategy pathComputationStrategyB = throttlingDataB.getPathComputationStrategy();
        long bandwidthA = throttlingDataA.getBandwidth();
        long bandwidthB = throttlingDataB.getBandwidth();

        if (pathComputationStrategyA == COST_AND_AVAILABLE_BANDWIDTH
                && pathComputationStrategyB == COST_AND_AVAILABLE_BANDWIDTH) {
            if (bandwidthA != bandwidthB) {
                return Long.compare(bandwidthB, bandwidthA);
            }
        } else {
            if (pathComputationStrategyA == COST_AND_AVAILABLE_BANDWIDTH) {
                return 1;
            }
            if (pathComputationStrategyB == COST_AND_AVAILABLE_BANDWIDTH) {
                return -1;
            }
        }
        return 0;
    }

    private int compareTimeCreate(FlowThrottlingData throttlingDataA, FlowThrottlingData throttlingDataB) {
        Instant timeCreateA = throttlingDataA.getTimeCreate();
        Instant timeCreateB = throttlingDataB.getTimeCreate();

        if (timeCreateA != null || timeCreateB != null) {
            if (timeCreateA == null) {
                return -1;
            }
            if (timeCreateB == null) {
                return 1;
            }
            return timeCreateA.compareTo(timeCreateB);
        }
        return 0;
    }

    private static void logUnknownType(FlowType flowType) {
        log.error("Unknown flow type {}", flowType);
    }

    private FlowInfo getFlowInfo(String flowId, FlowType type) {
        switch (type) {
            case Y_FLOW:
                Optional<YFlow> yFlow = yFlowRepository.findById(flowId);
                return new FlowInfo(yFlow.isPresent(), "Y-flow", yFlow.map(YFlow::isPinned).orElse(false),
                        getEndpointSwitches(yFlow.orElse(null)));
            case HA_FLOW:
                Optional<HaFlow> haFlow = haFlowRepository.findById(flowId);
                return new FlowInfo(haFlow.isPresent(), "HA-flow", haFlow.map(HaFlow::isPinned).orElse(false),
                        getEndpointSwitches(haFlow.orElse(null)));
            case FLOW:
                Optional<Flow> flow = flowRepository.findById(flowId);
                return new FlowInfo(flow.isPresent(), "Flow", flow.map(Flow::isPinned).orElse(false),
                        getEndpointSwitches(flow.orElse(null)));
            default:
                logUnknownType(type);
                return new FlowInfo(false, "unknown", false, new HashSet<>());
        }
    }

    private Set<SwitchId> getEndpointSwitches(Flow flow) {
        Set<SwitchId> result = new HashSet<>();
        if (flow != null) {
            result.add(flow.getSrcSwitchId());
            result.add(flow.getDestSwitchId());
        }
        return result;
    }

    private Set<SwitchId> getEndpointSwitches(YFlow yFlow) {
        Set<SwitchId> result = new HashSet<>();
        if (yFlow != null) {
            yFlow.getSubFlows().stream()
                    .map(YSubFlow::getEndpointSwitchId)
                    .forEach(result::add);
            result.add(yFlow.getSharedEndpoint().getSwitchId());
        }
        return result;
    }

    private Set<SwitchId> getEndpointSwitches(HaFlow haFlow) {
        if (haFlow != null) {
            return haFlow.getEndpointSwitchIds();
        }
        return new HashSet<>();
    }

    @Value
    private static class FlowInfo {
        boolean present;
        String type;
        boolean pinned;
        Set<SwitchId> endpointSwitchIds;
    }
}
