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

package org.openkilda.wfm.topology.reroute.service;

import static java.lang.String.format;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.messaging.info.reroute.error.RerouteInProgressError;
import org.openkilda.messaging.info.reroute.error.RuleFailedError;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.model.RerouteQueue;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class RerouteQueueService {

    private int defaultFlowPriority;
    private int maxRetry;
    private FlowRepository flowRepository;

    private Map<String, RerouteQueue> reroutes = new HashMap<>();
    private IRerouteQueueCarrier carrier;

    public RerouteQueueService(IRerouteQueueCarrier carrier, PersistenceManager persistenceManager,
                               int defaultFlowPriority, int maxRetry) {
        this.carrier = carrier;
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.defaultFlowPriority = defaultFlowPriority;
        this.maxRetry = maxRetry;
    }

    /**
     * Put reroute request to throttling.
     *
     * @param flowId flow id
     * @param throttlingData reroute request params
     */
    public void putRequestToThrottling(String flowId, FlowThrottlingData throttlingData) {
        RerouteQueue rerouteQueue = reroutes.get(flowId);
        if (rerouteQueue == null) {
            rerouteQueue = RerouteQueue.builder()
                    .throttling(throttlingData)
                    .build();
            reroutes.put(flowId, rerouteQueue);
        } else {
            FlowThrottlingData merged = merge(flowId, rerouteQueue.getThrottling(), throttlingData);
            rerouteQueue.setThrottling(merged);
        }
        carrier.sendExtendTimeWindowEvent();
    }

    /**
     * Process manual reroute request.
     *
     * @param flowId flow id
     * @param throttlingData reroute request params
     */
    public void processManualRequest(String flowId, FlowThrottlingData throttlingData) {
        Optional<Flow> flow = flowRepository.findById(flowId);
        if (!flow.isPresent()) {
            String description = format("Flow '%s' not found.", flowId);
            ErrorData errorData = new ErrorData(ErrorType.NOT_FOUND, "Flow not found.", description);
            carrier.emitFlowRerouteError(errorData);
            return;
        }
        RerouteQueue rerouteQueue = reroutes.get(flowId);
        if (rerouteQueue == null) {
            reroutes.put(flowId, RerouteQueue.builder().build());
            sendRerouteRequest(flowId, throttlingData, throttlingData.getCorrelationId());
        } else {
            if (rerouteQueue.getInProgress() == null) {
                sendRerouteRequest(flowId, throttlingData, throttlingData.getCorrelationId());
            } else {
                String description = format("Flow '%s' is in reroute process.", flowId);
                ErrorData errorData = new ErrorData(ErrorType.UNPROCESSABLE_REQUEST, "Reroute is in progress.",
                        description);
                carrier.emitFlowRerouteError(errorData);
            }
        }
    }

    /**
     * Process reroute result. Check fail reason, decide if retry is needed and schedule it if yes.
     *
     * @param flowId flow id
     * @param rerouteResultInfoData reroute result
     * @param correlationId correlation id
     */
    public void processRerouteResult(String flowId, RerouteResultInfoData rerouteResultInfoData,
                                     String correlationId) {
        RerouteQueue rerouteQueue = reroutes.get(flowId);
        String key = rerouteQueue.getKey();
        if (key == null || !key.equals(correlationId)) {
            log.error("Skipped unexpected reroute result for flow {} with correlation id {}.",
                    flowId, correlationId);
            return;
        }

        if (rerouteResultInfoData.isSuccess()) {
            processNextRerouteInQueue(flowId, rerouteQueue);
        } else {
            RerouteError rerouteError = rerouteResultInfoData.getRerouteError();
            if (isRetryRequired(flowId, rerouteError)) {
                injectRetry(flowId, rerouteQueue);
            } else {
                processNextRerouteInQueue(flowId, rerouteQueue);
            }
        }
    }

    /**
     * Move reroute requests form throttling to pending/in-progress.
     */
    public void flushThrottling() {
        Map<String, FlowThrottlingData> requestsToSend = new HashMap<>();
        reroutes.entrySet().stream()
                .filter(es -> es.getValue().getThrottling() != null)
                .forEach(es -> {
                    if (es.getValue().getInProgress() == null) {
                        es.getValue().setInProgress(es.getValue().getThrottling());
                        requestsToSend.put(es.getKey(), es.getValue().getInProgress());
                    } else {
                        FlowThrottlingData merged = merge(es.getKey(), es.getValue().getPending(),
                                es.getValue().getThrottling());
                        es.getValue().setPending(merged);
                    }
                    es.getValue().setThrottling(null);
                });
        requestsToSend.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(new FlowPriorityComparator()))
                .forEach(es -> {
                    String correlationId = new CommandContext(es.getValue().getCorrelationId())
                            .fork(es.getKey()).getCorrelationId();
                    sendRerouteRequest(es.getKey(), es.getValue(), correlationId);
                });
    }

    /**
     * Handle timeout event for reroute with key.
     *
     * @param key reroute key
     */
    public void handleTimeout(String key) {
        log.warn("Reroute retry with correlation id {} timed out", key);
        List<Entry<String, RerouteQueue>> foundReroutes = reroutes.entrySet().stream()
                .filter(es -> key.equals(es.getValue().getKey()))
                .collect(Collectors.toList());
        if (foundReroutes.isEmpty()) {
            log.warn("No reroute with key {} found. Timeout event skipped.", key);
        } else if (foundReroutes.size() > 1) {
            log.error("Found more than one reroute with key {}. Timed out all of them.", key);
        }
        foundReroutes.forEach(entry -> injectRetry(entry.getKey(), entry.getValue()));
    }

    private void processNextRerouteInQueue(String flowId, RerouteQueue rerouteQueue) {
        FlowThrottlingData pending = rerouteQueue.getPending();
        if (pending == null) {
            rerouteQueue.setInProgress(null);
            rerouteQueue.setKey(null);
        } else {
            sendRerouteRequest(flowId, pending, pending.getCorrelationId());
            rerouteQueue.setPending(null);
        }
    }

    private boolean isRetryRequired(String flowId, RerouteError rerouteError) {
        if (rerouteError instanceof RerouteInProgressError) {
            return true;
        } else if (rerouteError instanceof RuleFailedError) {
            Flow flow = flowRepository.findById(flowId).orElse(null);
            if (flow == null) {
                log.error("Flow {} not found", flowId);
                return false;
            }
            RuleFailedError ruleFailedError = (RuleFailedError) rerouteError;
            return !ruleFailedError.getSwitches().contains(flow.getSrcSwitch().getSwitchId())
                    && !ruleFailedError.getSwitches().contains(flow.getDestSwitch().getSwitchId());
        }
        return false;
    }

    private void injectRetry(String flowId, RerouteQueue rerouteQueue) {
        if (rerouteQueue.getInProgress().getRetryCounter() < maxRetry) {
            FlowThrottlingData retryRequest = rerouteQueue.getInProgress();
            retryRequest.increaseRetryCounter();
            String retryCorrelationId = new CommandContext(retryRequest.getCorrelationId())
                    .fork(format("retry #%d", retryRequest.getRetryCounter()))
                    .getCorrelationId();
            retryRequest.setCorrelationId(retryCorrelationId);
            if (rerouteQueue.getPending() == null) {
                putRequestToThrottling(flowId, retryRequest);
                rerouteQueue.setInProgress(null);
                rerouteQueue.setKey(null);
            } else {
                FlowThrottlingData nextRequest = merge(flowId, rerouteQueue.getPending(), retryRequest);
                sendRerouteRequest(flowId, nextRequest, nextRequest.getCorrelationId());
                rerouteQueue.setPending(null);
            }
        } else {
            processNextRerouteInQueue(flowId, rerouteQueue);
        }
    }

    private void sendRerouteRequest(String flowId, FlowThrottlingData throttlingData, String correlationId) {
        FlowRerouteRequest request = new FlowRerouteRequest(flowId, throttlingData.isForce(),
                throttlingData.isEffectivelyDown(), throttlingData.getAffectedIsl(), throttlingData.getReason());
        carrier.sendRerouteRequest(correlationId, request);
        RerouteQueue rerouteQueue = reroutes.get(flowId);
        rerouteQueue.setInProgress(throttlingData);
        rerouteQueue.setKey(correlationId);
    }

    private FlowThrottlingData merge(String flowId, FlowThrottlingData first, FlowThrottlingData second) {
        log.info("Puts reroute request for flow {} with correlationId {}.", flowId, second.getCorrelationId());
        if (first == null) {
            return second;
        }
        log.info("Merge reroute request for flow {}. Previous correlationId {}. New correlationId {} and reason {}.",
                flowId, first.getCorrelationId(), second.getCorrelationId(), second.getReason());

        if (first.getAffectedIsl().isEmpty()) {
            second.setAffectedIsl(Collections.emptySet());
        } else if (!second.getAffectedIsl().isEmpty()) {
            second.getAffectedIsl().addAll(first.getAffectedIsl());
        }
        second.setForce(first.isForce() || second.isForce());
        second.setEffectivelyDown(first.isEffectivelyDown() || second.isEffectivelyDown());
        second.setReason(first.getReason());
        second.setRetryCounter(Math.min(first.getRetryCounter(), second.getRetryCounter()));

        return second;
    }

    @VisibleForTesting
    Map<String, RerouteQueue> getReroutes() {
        return reroutes;
    }

    private class FlowPriorityComparator implements Comparator<FlowThrottlingData> {
        @Override
        public int compare(FlowThrottlingData throttlingDataA, FlowThrottlingData throttlingDataB) {
            int priorityA = throttlingDataA.getPriority() == null ? defaultFlowPriority : throttlingDataA.getPriority();
            int priorityB = throttlingDataB.getPriority() == null ? defaultFlowPriority : throttlingDataB.getPriority();
            Instant timeCreateA = throttlingDataA.getTimeCreate();
            Instant timeCreateB = throttlingDataB.getTimeCreate();

            if (priorityA == priorityB && (timeCreateA != null || timeCreateB != null)) {
                if (timeCreateA == null) {
                    return -1;
                }
                if (timeCreateB == null) {
                    return 1;
                }
                return timeCreateA.compareTo(timeCreateB);
            }

            return Integer.compare(priorityA, priorityB);
        }
    }
}
