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
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import java.time.Instant;

public abstract class WithHistorySupportFsm<T extends WithCommandContextFsm<T, S, E, C>, S, E, C>
        extends WithCommandContextFsm<T, S, E, C> {

    private Instant lastHistoryEntryTime;

    public WithHistorySupportFsm(CommandContext commandContext) {
        super(commandContext);
    }

    public abstract String getFlowId();

    public abstract FlowGenericCarrier getCarrier();

    public abstract void fireError(String errorReason);

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
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(getNextHistoryEntryTime())
                        .flowId(getFlowId())
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
    public void saveNewEventToHistory(String action, FlowEventData.Event event,
                                      FlowEventData.Initiator initiator,
                                      String details) {
        log.debug("Flow {} action - {} : {}", getFlowId(), action, event);

        Instant timestamp = getNextHistoryEntryTime();
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(timestamp)
                        .flowId(getFlowId())
                        .build())
                .flowEventData(FlowEventData.builder()
                        .flowId(getFlowId())
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
}
