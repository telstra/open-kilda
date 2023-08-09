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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.utils.SpeakerRequestHelper.keepOnlyFailedCommands;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
public class OnReceivedResponseAction extends HistoryRecordingAction<
        HaFlowDeleteFsm, State, Event, HaFlowDeleteContext> {
    private static final String FAILED_TO_REMOVE_RULE_ACTION = "Failed to remove rule";

    private final int speakerCommandRetriesLimit;

    public OnReceivedResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowDeleteContext context, HaFlowDeleteFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        if (!stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);
            HaFlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                    .withTaskId(stateMachine.getHaFlowId())
                    .withAction("Rule was deleted")
                    .withDescription(format("The rule with command ID %s has been removed: switch %s",
                            commandId, response.getSwitchId()))
                    .withHaFlowId(stateMachine.getHaFlowId()));

        } else {
            Optional<BaseSpeakerCommandsRequest> command = stateMachine.getSpeakerCommand(commandId);
            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit && command.isPresent()) {
                response.getFailedCommandIds().forEach((uuid, message) ->
                        HaFlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                                .withTaskId(stateMachine.getHaFlowId())
                                .withAction(FAILED_TO_REMOVE_RULE_ACTION)
                                .withDescription(format("Failed to remove the rule: commandId %s, ruleId %s,"
                                                + " switch %s. Error: %s. Retrying (attempt %d)",
                                                commandId, uuid, response.getSwitchId(), message, attempt))
                                .withHaFlowId(stateMachine.getHaFlowId())));

                BaseSpeakerCommandsRequest request = command.get();
                keepOnlyFailedCommands(request, response.getFailedCommandIds().keySet());
                stateMachine.getCarrier().sendSpeakerRequest(request);
            } else {
                stateMachine.addFailedCommand(commandId, response);
                stateMachine.removePendingCommand(commandId);
                response.getFailedCommandIds().forEach((uuid, message) ->
                        HaFlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                                .withTaskId(stateMachine.getHaFlowId())
                                .withAction(FAILED_TO_REMOVE_RULE_ACTION)
                                .withDescription(
                                    format("Failed to remove the rule: commandId %s, ruleId %s, switch %s. Error: %s",
                                        commandId, uuid, response.getSwitchId(), message))
                                .withHaFlowId(stateMachine.getHaFlowId())));
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending remove commands of ha-flow {}",
                        stateMachine.getHaFlowId());
                stateMachine.fire(Event.RULES_REMOVED);
            } else {
                String errorMessage = format("Received error response(s) for %d remove commands",
                        stateMachine.getFailedCommands().size());
                HaFlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                        .withTaskId(stateMachine.getHaFlowId())
                        .withAction(errorMessage)
                        .withDescription(stateMachine.getErrorReason())
                        .withHaFlowId(stateMachine.getHaFlowId()));

                stateMachine.fireError(errorMessage);
            }
        }
    }
}
