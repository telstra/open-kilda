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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.UUID;

@Slf4j
public class OnReceivedRemoveOrRevertResponseAction extends BaseOnReceivedResponseAction {
    public OnReceivedRemoveOrRevertResponseAction(int speakerCommandRetriesLimit) {
        super(speakerCommandRetriesLimit);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowPathSwapContext context,
                           FlowPathSwapFsm stateMachine) {
        SpeakerResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        if (!stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        boolean isInstallCommand = stateMachine.getInstallCommand(commandId) != null
                || stateMachine.getInstallSpeakerCommand(commandId).isPresent();

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);
            String commandName = isInstallCommand ? "re-installed (reverted)" : "deleted";
            if (response instanceof SpeakerFlowSegmentResponse) {
                stateMachine.saveActionToHistory("Rule was " + commandName,
                        format("The rule was %s: switch %s, cookie %s", commandName,
                                response.getSwitchId(), ((SpeakerFlowSegmentResponse) response).getCookie()));
            } else {
                stateMachine.saveActionToHistory("Rule was " + commandName,
                        format("The rule was %s: switch %s", commandName, response.getSwitchId()));
            }
        } else {
            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit) {
                if (response instanceof FlowErrorResponse) {
                    FlowErrorResponse errorResponse = (FlowErrorResponse) response;
                    if (isInstallCommand) {
                        FlowSegmentRequestFactory installCommand = stateMachine.getInstallCommand(commandId);
                        stateMachine.saveErrorToHistory("Failed to re-install (revert) rule",
                                format("Failed to install the rule: commandId %s, switch %s, cookie %s. Error %s. "
                                                + "Retrying (attempt %d)",
                                        commandId, errorResponse.getSwitchId(), installCommand.getCookie(),
                                        errorResponse, attempt));
                        stateMachine.getCarrier().sendSpeakerRequest(installCommand.makeInstallRequest(commandId));
                    } else {
                        FlowSegmentRequestFactory removeCommand = stateMachine.getRemoveCommand(commandId);
                        stateMachine.saveErrorToHistory("Failed to delete rule",
                                format("Failed to remove the rule: commandId %s, switch %s, cookie %s. Error %s. "
                                                + "Retrying (attempt %d)",
                                        commandId, errorResponse.getSwitchId(), removeCommand.getCookie(),
                                        errorResponse, attempt));
                        stateMachine.getCarrier().sendSpeakerRequest(removeCommand.makeRemoveRequest(commandId));
                    }
                } else if (response instanceof SpeakerCommandResponse) {
                    String commandName = isInstallCommand ? "re-install (revert)" : "delete";
                    SpeakerCommandResponse speakerCommandResponse = (SpeakerCommandResponse) response;
                    speakerCommandResponse.getFailedCommandIds().forEach((uuid, message) ->
                            stateMachine.saveErrorToHistory("Failed to " + commandName + " rule",
                                    format("Failed to %s the rule: commandId %s, ruleId %s, switch %s. "
                                                    + "Error %s. Retrying (attempt %d)", commandName,
                                            commandId, uuid, response.getSwitchId(), message, attempt)));

                    Set<UUID> failedUuids = speakerCommandResponse.getFailedCommandIds().keySet();
                    stateMachine.getInstallSpeakerCommand(commandId)
                            .ifPresent(command -> stateMachine.getCarrier()
                                    .sendSpeakerRequest(command.toBuilder()
                                            .commands(filterOfCommands(command.getCommands(), failedUuids)).build()));
                    stateMachine.getDeleteSpeakerCommand(commandId)
                            .ifPresent(command -> stateMachine.getCarrier()
                                    .sendSpeakerRequest(command.toBuilder()
                                            .commands(filterOfCommands(command.getCommands(), failedUuids)).build()));
                } else {
                    log.warn("Received a unknown response: {}", response);
                    return;
                }
            } else {
                stateMachine.addFailedCommand(commandId, response);
                stateMachine.removePendingCommand(commandId);

                String commandName = isInstallCommand ? "re-install (revert)" : "delete";
                if (response instanceof FlowErrorResponse) {
                    stateMachine.saveErrorToHistory("Failed to " + commandName + " rule",
                            format("Failed to %s the rule: commandId %s, switch %s, cookie %s. Error %s.",
                                    commandName, commandId, response.getSwitchId(),
                                    ((FlowErrorResponse) response).getCookie(), response));
                } else if (response instanceof SpeakerCommandResponse) {
                    ((SpeakerCommandResponse) response).getFailedCommandIds().forEach((uuid, message) ->
                            stateMachine.saveErrorToHistory("Failed to " + commandName + " rule",
                                    format("Failed to %s the rule: commandId %s, ruleId %s, switch %s. Error %s.",
                                            commandName, commandId, uuid, response.getSwitchId(), message)));
                } else {
                    log.warn("Received a unknown response: {}", response);
                    return;
                }
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending install / delete commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_REMOVED);
            } else {
                String errorMessage = format("Received error response(s) for %d install / delete commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
