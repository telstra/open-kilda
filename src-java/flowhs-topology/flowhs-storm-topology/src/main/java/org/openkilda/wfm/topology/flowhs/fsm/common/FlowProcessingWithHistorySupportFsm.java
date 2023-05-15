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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import static java.util.Collections.emptyList;

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.history.model.HaFlowDumpData;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.share.history.model.HaFlowHistoryData;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.topology.flowhs.service.common.FlowHistoryCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.NorthboundResponseCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.ProcessingEventListener;

import lombok.NonNull;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.time.Instant;
import java.util.Collection;

public abstract class FlowProcessingWithHistorySupportFsm<T extends AbstractStateMachine<T, S, E, C>, S, E, C,
        R extends NorthboundResponseCarrier & HistoryUpdateCarrier, L extends ProcessingEventListener>
        extends NbTrackableFlowProcessingFsm<T, S, E, C, R, L>
        implements FlowHistoryCarrier {
    private Instant lastHistoryEntryTime;

    protected FlowProcessingWithHistorySupportFsm(@NonNull E nextEvent, @NonNull E errorEvent,
                                                  @NonNull CommandContext commandContext, @NonNull R carrier) {
        this(nextEvent, errorEvent, commandContext, carrier, emptyList());
    }

    protected FlowProcessingWithHistorySupportFsm(@NonNull E nextEvent, @NonNull E errorEvent,
                                                  @NonNull CommandContext commandContext, @NonNull R carrier,
                                                  @NonNull Collection<L> eventListeners) {
        super(nextEvent, errorEvent, commandContext, carrier, eventListeners);
    }

    protected String[] getFlowIdsForHistory() {
        return new String[]{getFlowId()};
    }

    /**
     * Add a history record on the action.
     */
    public void saveActionToHistory(String action) {
        for (String flowId : getFlowIdsForHistory()) {
            log.debug("Flow {} action - {}", flowId, action);
            sendHistoryData(flowId, action, null);
        }
    }

    @Override
    public void saveActionToHistory(String action, String description) {
        for (String flowId : getFlowIdsForHistory()) {
            log.debug("Flow {} action - {} : {}", flowId, action, description);
            sendHistoryData(flowId, action, description);
        }
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

    @Override
    public void saveErrorToHistory(String action, String errorMessage) {
        for (String flowId : getFlowIdsForHistory()) {
            log.error("Flow {} error - {} : {}", flowId, action, errorMessage);
            sendHistoryData(flowId, action, errorMessage);
        }
    }

    @Override
    public void saveErrorToHistory(String errorMessage) {
        for (String flowId : getFlowIdsForHistory()) {
            log.error("Flow {} error - {}", flowId, errorMessage);
            sendHistoryData(flowId, errorMessage, null);
        }
    }

    /**
     * Add a history record on the error.
     */
    public void saveErrorToHistory(String errorMessage, Exception ex) {
        for (String flowId : getFlowIdsForHistory()) {
            log.error("Flow {} error - {}", flowId, errorMessage, ex);
            sendHistoryData(flowId, errorMessage, null);
        }
    }

    protected void sendHistoryData(String flowId, String action, String description) {
        sendHistoryData(flowId, action, description, getCommandContext().getCorrelationId());
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
        for (String flowId : getFlowIdsForHistory()) {
            saveNewEventToHistory(flowId, action, event, initiator, details, getCommandContext().getCorrelationId());
        }
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
        for (String flowId : getFlowIdsForHistory()) {
            log.debug("Flow {} action - {} : {}", flowId, action, description);

            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(getCommandContext().getCorrelationId())
                    .flowDumpData(flowDumpData)
                    .flowHistoryData(FlowHistoryData.builder()
                            .action(action)
                            .time(getNextHistoryEntryTime())
                            .description(description)
                            .flowId(flowId)
                            .build())
                    .build();
            getCarrier().sendHistoryUpdate(historyHolder);
        }
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

    public void saveGlobalTimeoutToHistory() {
        for (String flowId : getFlowIdsForHistory()) {
            saveErrorToHistory(String.format(
                    "Global timeout reached for %s operation on flow \"%s\"", getCrudActionName(), flowId));
        }
    }

    protected abstract String getCrudActionName();

    //region HA-flow-related history methods

    /**
     * Saves an action on HA-flow to history.
     */
    public void saveHaFlowActionWithDumpToHistory(HaFlowEventData.Event event, String description,
                                                  HaFlowDumpData flowDumpData) {
        for (String haFlowId : getFlowIdsForHistory()) {
            log.debug("Flow {} action - {} : {}", haFlowId, event, description);

            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(getCommandContext().getCorrelationId())
                    .haFlowDumpData(flowDumpData)
                    .haFlowHistoryData(HaFlowHistoryData.builder()
                            .action(event.getDescription())
                            .time(getNextHistoryEntryTime())
                            .description(description)
                            .haFlowId(haFlowId)
                            .build())
                    .build();
            getCarrier().sendHistoryUpdate(historyHolder);
        }
    }

    /**
     * Add a history record on the new HA-flow event.
     */
    public void saveNewHaFlowEventToHistory(String action, HaFlowEventData.Event event) {
        saveNewHaFlowEventToHistory(action, event, null, null);
    }

    /**
     * Add a history record on the new event.
     */
    public void saveNewHaFlowEventToHistory(String haFlowId, String action, HaFlowEventData.Event event,
                                            HaFlowEventData.Initiator initiator,
                                            String details, String taskId) {
        log.debug("HA-flow {} action - {} : {}", haFlowId, action, event);

        Instant timestamp = getNextHistoryEntryTime();
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(taskId)
                .haFlowHistoryData(HaFlowHistoryData.builder()
                        .action(action)
                        .time(timestamp)
                        .haFlowId(haFlowId)
                        .build())
                .haFlowEventData(HaFlowEventData.builder()
                        .haFlowId(haFlowId)
                        .event(event)
                        .initiator(initiator)
                        .time(timestamp)
                        .details(details)
                        .build())
                .build();
        getCarrier().sendHistoryUpdate(historyHolder);
    }

    /**
     * Add a history record on the new event.
     */
    public void saveNewHaFlowEventToHistory(String action, HaFlowEventData.Event event,
                                            HaFlowEventData.Initiator initiator,
                                            String details) {
        for (String flowId : getFlowIdsForHistory()) {
            saveNewHaFlowEventToHistory(flowId, action, event, initiator, details,
                    getCommandContext().getCorrelationId());
        }
    }

    /**
     * Add a history record on the action.
     */
    public void saveHaFlowActionToHistory(String action) {
        for (String haFlowId : getFlowIdsForHistory()) {
            log.debug("Flow {} action - {}", haFlowId, action);
            sendHaFlowHistoryData(haFlowId, action, null);
        }
    }

    public void saveHaFlowActionToHistory(String action, String description) {
        for (String flowId : getFlowIdsForHistory()) {
            log.debug("Flow {} action - {} : {}", flowId, action, description);
            sendHaFlowHistoryData(flowId, action, description);
        }
    }

    public void sendHaFlowHistoryData(String haFlowId, String action, String description) {
        sendHaFlowHistoryData(haFlowId, action, description, getCommandContext().getCorrelationId());
    }

    public void sendHaFlowHistoryData(String flowId, String action, String description, String taskId) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(taskId)
                .haFlowHistoryData(HaFlowHistoryData.builder()
                        .action(action)
                        .time(getNextHistoryEntryTime())
                        .haFlowId(flowId)
                        .description(description)
                        .build())
                .build();
        getCarrier().sendHistoryUpdate(historyHolder);
    }

    public void saveHaFlowErrorToHistory(String action, String errorDetails) {
        for (String flowId : getFlowIdsForHistory()) {
            log.error("Flow {} error - {} : {}", flowId, action, errorDetails);
            sendHaFlowHistoryData(flowId, action, errorDetails);
        }
    }

    public void saveHaFlowErrorToHistory(String action) {
        saveHaFlowErrorToHistory(action, null);
    }
    //endregion
}
