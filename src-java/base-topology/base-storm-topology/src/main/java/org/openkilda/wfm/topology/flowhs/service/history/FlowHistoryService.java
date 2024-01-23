/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.service.history;

import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.share.history.model.HaFlowHistoryData;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * For detailed guide please see <b>ha-flow-history.md</b> under the docs directory.
 * <p>
 * This class is used on the caller side for saving history events for simple and HA flows. It saves events, actions,
 * dumps, and errors grouped together using a task ID (correlation ID).
 * An event is an operation or an attempt to perform an operation.
 * Supported events are listed in the {@link HaFlowEventData.Event} and {@link FlowEventData.Event}.
 * An action is a specific step performed when some event happened.
 * A dump is a state of the flow before or after the event.
 * An error is a special kind of action (not successful action).</p>
 */
@Slf4j
public final class FlowHistoryService {

    private final HistoryUpdateCarrier carrier;

    private FlowHistoryService(HistoryUpdateCarrier carrier) {
        this.carrier = Objects.requireNonNull(carrier);
    }

    public static FlowHistoryService using(HistoryUpdateCarrier carrier) {
        return new FlowHistoryService(carrier);
    }

    /**
     * Saves an HA-flow action to history.
     * @param parameters a holder class for the action parameters. Task ID (correlation ID) is mandatory.
     */
    public void save(HaFlowHistory parameters) {
        try {
            String haFlowId = ObjectUtils.getIfNull(parameters.getHaFlowId(), () ->
                    parameters.getFlowDumpData() != null ? parameters.getFlowDumpData().getHaFlowId() : null);
            if (haFlowId == null) {
                log.debug("An attempt to save history without providing HA-flow ID. Parameters: {}. Stacktrace: {}",
                        parameters, Arrays.toString(new RuntimeException().getStackTrace()));
            }

            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(Objects.requireNonNull(parameters.getTaskId()))
                    .haFlowDumpData(parameters.getFlowDumpData())
                    .haFlowHistoryData(HaFlowHistoryData.builder()
                            .action(parameters.getAction())
                            .time(Instant.now())
                            .description(parameters.getDescription())
                            .haFlowId(haFlowId)
                            .build())
                    .build();

            carrier.sendHistoryUpdate(historyHolder);
        } catch (RuntimeException e) {
            log.error("An error occurred when trying to save a History Action", e);
        }
    }

    /**
     * Saves an action performed on a simple flow to history.
     * @param parameters a holder class for the action parameters. Task ID (correlation ID) is mandatory.
     */
    public void save(FlowHistory parameters) {
        try {
            String flowId = ObjectUtils.getIfNull(parameters.getFlowId(), () ->
                    parameters.getFlowDumpData() != null ? parameters.getFlowDumpData().getFlowId() : null);
            if (flowId == null) {
                log.debug("An attempt to save history without providing HA-flow ID. Parameters: {}. Stacktrace: {}",
                        parameters, Arrays.toString(new RuntimeException().getStackTrace()));
            }

            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(Objects.requireNonNull(parameters.getTaskId()))
                    .flowDumpData(parameters.getFlowDumpData())
                    .flowHistoryData(FlowHistoryData.builder()
                            .action(parameters.getAction())
                            .time(Instant.now())
                            .description(parameters.getDescription())
                            .flowId(flowId)
                            .build())
                    .build();

            carrier.sendHistoryUpdate(historyHolder);
        } catch (RuntimeException e) {
            log.error("An error occurred when trying to save a History Action", e);
        }
    }

    /**
     * Saves a new HA flow event to history. The <i>eventData</i> might be augmented with some additional information.
     * @param eventData data about the event
     * @return true if the event is created successfully and sent for being stored, false otherwise.
     */
    public boolean saveNewHaFlowEvent(HaFlowEventData eventData) {
        try {
            Instant timestamp = Instant.now();
            String haFlowId = Objects.requireNonNull(eventData.getHaFlowId());
            String taskId = Objects.requireNonNull(eventData.getTaskId());

            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(taskId)
                    .haFlowHistoryData(HaFlowHistoryData.builder()
                            .action(eventData.getEvent().getDescription())
                            .time(timestamp)
                            .haFlowId(haFlowId)
                            .build())
                    .haFlowEventData(HaFlowEventData.builder()
                            .haFlowId(haFlowId)
                            .action(eventData.getAction())
                            .event(eventData.getEvent())
                            .initiator(eventData.getInitiator())
                            .time(timestamp)
                            .details(eventData.getDetails())
                            .taskId(taskId)
                            .build())
                    .build();
            carrier.sendHistoryUpdate(historyHolder);
            return true;
        } catch (RuntimeException e) {
            log.error("An error occurred when trying to save a new History Event", e);
            return false;
        }
    }

    /**
     * Saves a new simple flow event to history.
     * The <i>eventData</i> might be augmented with some additional information.
     * @param eventData data about the event
     * @return true if the event is created successfully and sent for being stored, false otherwise.
     */
    public boolean saveNewFlowEvent(FlowEventData eventData) {
        try {
            Instant timestamp = Instant.now();
            String flowId = Objects.requireNonNull(eventData.getFlowId());
            String taskId = Objects.requireNonNull(eventData.getTaskId());

            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(taskId)
                    .flowHistoryData(FlowHistoryData.builder()
                            .action(eventData.getEvent().getDescription())
                            .time(timestamp)
                            .flowId(flowId)
                            .build())
                    .flowEventData(FlowEventData.builder()
                            .flowId(flowId)
                            .event(eventData.getEvent())
                            .initiator(eventData.getInitiator())
                            .time(timestamp)
                            .details(eventData.getDetails())
                            .build())
                    .build();
            carrier.sendHistoryUpdate(historyHolder);
            return true;
        } catch (RuntimeException e) {
            log.error("An error occurred when trying to save a new History Event", e);
            return false;
        }
    }

    /**
     * Saves an error to history. This error will be available in the list of actions of this HA flow.
     * @param parameters parameters of the error to save.
     */
    public void saveError(HaFlowHistory parameters) {
        try {
            save(parameters);
        } catch (RuntimeException e) {
            log.error("An error occurred when trying to save an error to History", e);
        }
    }

    /**
     * Saves an error to history. This error will be available in the list of actions of this simple flow.
     * @param parameters parameters of the error to save.
     */
    public void saveError(FlowHistory parameters) {
        try {
            save(parameters);
        } catch (RuntimeException e) {
            log.error("An error occurred when trying to save an error to History", e);
        }
    }
}
