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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.utils.SpeakerRequestHelper.keepOnlyFailedCommands;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public abstract class BaseReceivedResponseAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E,
        C extends SpeakerResponseContext> extends HistoryRecordingAction<T, S, E, C> {
    public static final String FAILED_ACTION_TEMPLATE = "Failed to %s rule";
    private final int speakerCommandRetriesLimit;
    private final E successfulEvent;
    private final FlowGenericCarrier carrier;

    public BaseReceivedResponseAction(int speakerCommandRetriesLimit, E successfulEvent, FlowGenericCarrier carrier) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        this.successfulEvent = successfulEvent;
        this.carrier = carrier;
    }

    protected void handleResponse(
            SpeakerCommandResponse response, BaseSpeakerCommandsRequest request, String actionName,
            T stateMachine) {
        UUID commandId = response.getCommandId();
        if (!stateMachine.getPendingCommands().containsKey(commandId) || request == null) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);

            stateMachine.saveActionToHistory(format("%s rules operation was successful", actionName),
                    format("%s rules operation was successful. Rules count: "
                            + "%d. Switch id: %s", actionName, request.getCommands().size(), response.getSwitchId()));
        } else {
            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit) {
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(format(FAILED_ACTION_TEMPLATE, actionName),
                                format("Failed to %s the rule: commandId %s, ruleId %s switch %s. Error: %s. "
                                                + "Retrying (attempt %d)",
                                        actionName, commandId, uuid, response.getSwitchId(), message, attempt)));

                keepOnlyFailedCommands(request, response.getFailedCommandIds().keySet());
                carrier.sendSpeakerRequest(request);
            } else {
                stateMachine.addFailedCommand(commandId, response);
                stateMachine.removePendingCommand(commandId);


                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(format(FAILED_ACTION_TEMPLATE, actionName),
                                format("Failed to %s the rule: commandId %s, ruleId %s, switch %s. Error: %s",
                                        actionName, commandId, uuid, response.getSwitchId(), message)));

            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending {} commands of the ha-flow {}",
                        actionName, stateMachine.getHaFlowId());
                stateMachine.fire(successfulEvent);
            } else {
                String errorMessage = format("Received error response(s) for %d %s commands",
                        stateMachine.getFailedCommands().size(), actionName);
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
