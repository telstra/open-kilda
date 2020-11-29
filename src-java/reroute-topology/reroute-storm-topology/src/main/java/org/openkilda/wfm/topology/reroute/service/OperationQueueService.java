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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.wfm.topology.reroute.bolts.OperationQueueCarrier;

import com.google.common.annotations.VisibleForTesting;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j
public class OperationQueueService {
    private final OperationQueueCarrier carrier;

    private final Map<String, FlowQueueData> flowCommands = new HashMap<>();
    private boolean active;


    public OperationQueueService(OperationQueueCarrier carrier) {
        this.carrier = carrier;
    }

    /**
     * Add command to the front of the queue.
     */
    public void addFirst(String flowId, String correlationId, CommandData data) {
        log.info("Flow command {} has been added to the queue for flow {} with correlation id {}",
                data, flowId, correlationId);
        FlowQueueData queueData = getFlowQueueData(flowId);
        queueData.getQueue().addFirst(new OperationData(correlationId, data));
        processQueue(flowId, queueData);
    }

    /**
     * Add command to the end of the queue.
     */
    public void addLast(String flowId, String correlationId, CommandData data) {
        log.info("Flow command {} has been added to the queue for flow {} with correlation id {}",
                data, flowId, correlationId);
        FlowQueueData queueData = getFlowQueueData(flowId);
        queueData.getQueue().addLast(new OperationData(correlationId, data));
        processQueue(flowId, queueData);
    }

    /**
     * Handle timeout.
     */
    public void handleTimeout(String correlationId) {
        log.warn("Operation with correlationId {} has timed out", correlationId);
        Collection<String> flowIds = flowCommands.entrySet().stream()
                .filter(entry -> correlationId.equals(entry.getValue().getTaskInProgress()))
                .map(Entry::getKey)
                .collect(Collectors.toList());

        if (flowIds.isEmpty()) {
            log.info("No queue data with correlationId {} found. Timeout event skipped.", correlationId);
        } else if (flowIds.size() > 1) {
            log.error("Found more than one queue data with correlationId {}. Timed out all of them.", correlationId);
        }

        flowIds.forEach(flowId -> operationCompleted(flowId, null));
    }

    /**
     * Operation completed.
     */
    public void operationCompleted(String flowId, InfoData response) {
        FlowQueueData queueData = getFlowQueueData(flowId);
        log.info("Flow command {} has been completed with response {}", queueData.getTaskInProgress(), response);
        queueData.setTaskInProgress(null);
        processQueue(flowId, queueData);
    }

    private void processQueue(String flowId, FlowQueueData queueData) {
        if (!queueData.getQueue().isEmpty() && !queueData.isOperationInProgress()) {
            OperationData operationData = queueData.getQueue().pop();
            carrier.emitRequest(operationData.getCorrelationId(), operationData.getCommandData());
            queueData.setTaskInProgress(operationData.getCorrelationId());
            log.info("Flow command {} has been sent", operationData.getCommandData());
        } else if (queueData.getQueue().isEmpty() && !queueData.isOperationInProgress()) {
            flowCommands.remove(flowId);
            if (!active && flowCommands.isEmpty()) {
                carrier.sendInactive();
            }
        }
    }

    private FlowQueueData getFlowQueueData(String flowId) {
        return flowCommands.computeIfAbsent(flowId, v -> new FlowQueueData());
    }

    @VisibleForTesting
    Map<String, FlowQueueData> getFlowCommands() {
        return flowCommands;
    }

    /**
     * Handles activate command.
     */
    public void activate() {
        active = true;
    }

    /**
     * Handles deactivate command.
     */
    public boolean deactivate() {
        active = false;
        if (flowCommands.isEmpty()) {
            return true;
        }
        return false;
    }


    @Getter
    @Setter
    @VisibleForTesting
    static class FlowQueueData {
        private String taskInProgress;
        private Deque<OperationData> queue = new LinkedList<>();

        public boolean isOperationInProgress() {
            return taskInProgress != null;
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    private static class OperationData {
        @NonNull
        private String correlationId;
        @NonNull
        private CommandData commandData;
    }
}
