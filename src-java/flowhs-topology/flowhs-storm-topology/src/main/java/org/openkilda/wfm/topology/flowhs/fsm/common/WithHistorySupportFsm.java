/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import org.squirrelframework.foundation.fsm.StateMachine;

import java.time.Instant;

public abstract class WithHistorySupportFsm<T extends StateMachine<T, S, E, C>, S, E, C, R extends FlowGenericCarrier>
        extends WithCommandContextFsm<T, S, E, C> {

    private Instant lastHistoryEntryTime;

    public WithHistorySupportFsm(CommandContext commandContext) {
        super(commandContext);
    }

    public abstract String getFlowId();

    public abstract R getCarrier();

    /**
     * Add a history record on the action.
     */
    public void saveActionToHistory(String action) {
        log.debug("Flow {} action - {}", getFlowId(), action);
        sendHistoryData(action, null);
    }

    /**
     * Add a history record on the action.
     */
    public void saveActionToHistory(String action, String description) {
        log.debug("Flow {} action - {} : {}", getFlowId(), action, description);
        sendHistoryData(action, description);
    }

    /**
     * Add a history record on the action.
     */
    public void saveFlowActionToHistory(String flowId, String action) {
        log.debug("Flow {} action - {}", flowId, action);
        String taskId = KeyProvider.joinKeys(flowId, getCommandContext().getCorrelationId());
        sendHistoryData(flowId, action, null, taskId);
    }

    /**
     * Add a history record on the action.
     */
    public void saveFlowActionToHistory(String flowId, String action, String description) {
        log.debug("Flow {} action - {} : {}", flowId, action, description);
        String taskId = KeyProvider.joinKeys(flowId, getCommandContext().getCorrelationId());
        sendHistoryData(flowId, action, description, taskId);
    }

    /**
     * Add a history record on the error.
     */
    public void saveErrorToHistory(String action, String errorMessage) {
        log.error("Flow {} error - {} : {}", getFlowId(), action, errorMessage);
        sendHistoryData(action, errorMessage);
    }

    /**
     * Add a history record on the error.
     */
    public void saveErrorToHistory(String errorMessage) {
        log.error("Flow {} error - {}", getFlowId(), errorMessage);
        sendHistoryData(errorMessage, null);
    }

    /**
     * Add a history record on the error.
     */
    public void saveErrorToHistory(String errorMessage, Exception ex) {
        log.error("Flow {} error - {}", getFlowId(), errorMessage, ex);
        sendHistoryData(errorMessage, null);
    }

    protected void sendHistoryData(String action, String description) {
        sendHistoryData(getFlowId(), action, description, getCommandContext().getCorrelationId());
    }

    protected void sendHistoryData(String flowId, String action, String description, String taskId) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(taskId)
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(getNextHistoryEntryTime())
                        .flowId(flowId)
                        .description(description)
                        .build())
                .build();
        getCarrier().sendHistoryUpdate(historyHolder);
    }

    /**
     * Add a history record on the new event.
     */
    public void saveNewEventToHistory(String action, FlowEventData.Event event) {
        saveNewEventToHistory(action, event, null, null);
    }

    /**
     * Add a history record on the new event.
     */
    public void saveNewEventToHistory(String flowId, String action, FlowEventData.Event event) {
        String taskId = KeyProvider.joinKeys(flowId, getCommandContext().getCorrelationId());
        saveNewEventToHistory(flowId, action, event, null, null, taskId);
    }

    /**
     * Add a history record on the new event.
     */
    public void saveNewEventToHistory(String action, FlowEventData.Event event,
                                      FlowEventData.Initiator initiator,
                                      String details) {
        saveNewEventToHistory(getFlowId(), action, event, initiator, details, getCommandContext().getCorrelationId());
    }

    /**
     * Add a history record on the new event.
     */
    public void saveNewEventToHistory(String flowId, String action, FlowEventData.Event event,
                                      FlowEventData.Initiator initiator,
                                      String details, String taskId) {
        log.debug("Flow {} action - {} : {}", flowId, action, event);

        Instant timestamp = getNextHistoryEntryTime();
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(taskId)
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(timestamp)
                        .flowId(flowId)
                        .build())
                .flowEventData(FlowEventData.builder()
                        .flowId(flowId)
                        .event(event)
                        .initiator(initiator)
                        .time(timestamp)
                        .details(details)
                        .build())
                .build();
        getCarrier().sendHistoryUpdate(historyHolder);
    }

    /**
     * Add a history record on the action.
     */
    public void saveActionWithDumpToHistory(String action, String description,
                                            FlowDumpData flowDumpData) {
        log.debug("Flow {} action - {} : {}", getFlowId(), action, description);

        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(getCommandContext().getCorrelationId())
                .flowDumpData(flowDumpData)
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(getNextHistoryEntryTime())
                        .description(description)
                        .flowId(getFlowId())
                        .build())
                .build();
        getCarrier().sendHistoryUpdate(historyHolder);
    }

    public final Instant getNextHistoryEntryTime() {
        Instant now = Instant.now();
        if (lastHistoryEntryTime == null || lastHistoryEntryTime.isBefore(now)) {
            lastHistoryEntryTime = now;
        } else {
            // To maintain the ordering of history records, each next record must be at least 1 ms later
            // than the previous one. In a case of subsequent calls that receive the same value of Instant.now(),
            // we have to manually increment the timestamp by adding 1 ms.
            lastHistoryEntryTime = lastHistoryEntryTime.plusMillis(1);
        }
        return lastHistoryEntryTime;
    }

    public abstract void reportError(E event);

    protected void reportGlobalTimeout() {
        saveErrorToHistory(String.format(
                "Global timeout reached for %s operation on flow \"%s\"", getCrudActionName(), getFlowId()));
    }

    protected abstract String getCrudActionName();
}
