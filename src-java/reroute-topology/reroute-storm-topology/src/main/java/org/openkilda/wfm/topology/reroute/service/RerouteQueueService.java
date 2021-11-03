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
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.messaging.info.reroute.error.NoPathFoundError;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.messaging.info.reroute.error.RerouteInProgressError;
import org.openkilda.messaging.info.reroute.error.SpeakerRequestError;
import org.openkilda.model.Flow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.model.RerouteQueue;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RerouteQueueService {

    private int defaultFlowPriority;
    private int maxRetry;
    private FlowRepository flowRepository;
    private YFlowRepository yFlowRepository;

    private Map<String, RerouteQueue> reroutes = new HashMap<>();
    private IRerouteQueueCarrier carrier;

    public RerouteQueueService(IRerouteQueueCarrier carrier, PersistenceManager persistenceManager,
                               int defaultFlowPriority, int maxRetry) {
        this.carrier = carrier;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
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
        if (throttlingData.isYFlow()) {
            Optional<YFlow> flow = yFlowRepository.findById(flowId);
            if (!flow.isPresent()) {
                log.warn(format("Y-flow %s not found. Skip the reroute operation of this flow.", flowId));
                return;
            } else if (flow.get().isPinned()) {
                log.info(format("Y-flow %s is pinned. Skip the reroute operation of this flow.", flowId));
                return;
            }
        } else {
            Optional<Flow> flow = flowRepository.findById(flowId);
            if (!flow.isPresent()) {
                log.warn(format("Flow %s not found. Skip the reroute operation of this flow.", flowId));
                return;
            } else if (flow.get().isPinned()) {
                log.info(format("Flow %s is pinned. Skip the reroute operation of this flow.", flowId));
                return;
            }
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
        if (throttlingData.isYFlow()) {
            Optional<YFlow> yFlow = yFlowRepository.findById(flowId);
            if (!yFlow.isPresent()) {
                String description = format("Y-flow %s not found", flowId);
                ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, "Could not reroute y-flow", description);
                carrier.emitFlowRerouteError(errorData);
                return;
            } else if (yFlow.get().isPinned()) {
                String description = "Can't reroute pinned y-flow";
                ErrorData errorData =
                        new ErrorData(ErrorType.UNPROCESSABLE_REQUEST, "Could not reroute y-flow", description);
                carrier.emitFlowRerouteError(errorData);
                return;
            }
        } else {
            Optional<Flow> flow = flowRepository.findById(flowId);
            if (!flow.isPresent()) {
                String description = format("Flow %s not found", flowId);
                ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, "Could not reroute flow", description);
                carrier.emitFlowRerouteError(errorData);
                return;
            } else if (flow.get().isPinned()) {
                String description = "Can't reroute pinned flow";
                ErrorData errorData =
                        new ErrorData(ErrorType.UNPROCESSABLE_REQUEST, "Could not reroute flow", description);
                carrier.emitFlowRerouteError(errorData);
                return;
            }
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
            if (isRetryRequired(flowId, rerouteError, rerouteResultInfoData.isYFlow())) {
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

    private boolean isRetryRequired(String flowId, RerouteError rerouteError, boolean isYFlow) {
        if (rerouteError instanceof NoPathFoundError) {
            log.info("Received no path found error for flow {}", flowId);
            return true;
        } else if (rerouteError instanceof RerouteInProgressError) {
            log.info("Received reroute in progress error for flow {}", flowId);
            return true;
        } else if (rerouteError instanceof SpeakerRequestError) {
            if (isYFlow) {
                log.info("Received speaker request error for y-flow {}", flowId);
                YFlow yFlow = yFlowRepository.findById(flowId).orElse(null);
                if (yFlow == null) {
                    log.error("Y-flow {} not found", flowId);
                    return false;
                }
                Set<SwitchId> yFlowSwitchIds = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getEndpointSwitchId).collect(Collectors.toSet());
                yFlowSwitchIds.add(yFlow.getSharedEndpoint().getSwitchId());

                boolean isRetryRequired = true;
                SpeakerRequestError ruleFailedError = (SpeakerRequestError) rerouteError;
                for (SwitchId switchId : yFlowSwitchIds) {
                    isRetryRequired &= !ruleFailedError.getSwitches().contains(switchId);
                }
                return isRetryRequired;
            } else {
                log.info("Received speaker request error for flow {}", flowId);
                Flow flow = flowRepository.findById(flowId).orElse(null);
                if (flow == null) {
                    log.error("Flow {} not found", flowId);
                    return false;
                }
                SpeakerRequestError ruleFailedError = (SpeakerRequestError) rerouteError;
                return !ruleFailedError.getSwitches().contains(flow.getSrcSwitchId())
                        && !ruleFailedError.getSwitches().contains(flow.getDestSwitchId());
            }
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
            if (throttlingData.isYFlow()) {
                YFlowRerouteRequest request = new YFlowRerouteRequest(flowId, throttlingData.getAffectedIsl(),
                        throttlingData.isForce(), throttlingData.getReason(), throttlingData.isIgnoreBandwidth());
                carrier.sendRerouteRequest(throttlingData.getCorrelationId(), request);
            } else {
                FlowRerouteRequest request = new FlowRerouteRequest(flowId, throttlingData.isForce(),
                        throttlingData.isEffectivelyDown(), throttlingData.isIgnoreBandwidth(),
                        throttlingData.getAffectedIsl(), throttlingData.getReason(), false);
                carrier.sendRerouteRequest(throttlingData.getCorrelationId(), request);
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
}
