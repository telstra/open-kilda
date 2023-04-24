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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.OfCommandConverter;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
            stateMachine.saveActionToHistory("Rule was deleted",
                    format("The rule was removed: switch %s", response.getSwitchId()));
        } else {
            Optional<BaseSpeakerCommandsRequest> command = stateMachine.getSpeakerCommand(commandId);
            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit && command.isPresent()) {
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(FAILED_TO_REMOVE_RULE_ACTION,
                                format("Failed to remove the rule: commandId %s, ruleId %s, switch %s. "
                                                + "Error: %s. Retrying (attempt %d)",
                                        commandId, uuid, response.getSwitchId(), message, attempt)));

                Set<UUID> failedUuids = response.getFailedCommandIds().keySet();
                BaseSpeakerCommandsRequest request = command.get();
                List<OfCommand> failedCommands = request.getCommands().stream()
                        .filter(ifCommand -> failedUuids.contains(ifCommand.getUuid()))
                        .collect(Collectors.toList());
                request.getCommands().clear();
                request.getCommands().addAll(OfCommandConverter.INSTANCE.removeExcessDependencies(failedCommands));
                stateMachine.getCarrier().sendSpeakerRequest(request);
            } else {
                stateMachine.addFailedCommand(commandId, response);
                stateMachine.removePendingCommand(commandId);
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(FAILED_TO_REMOVE_RULE_ACTION,
                                format("Failed to remove the rule: commandId %s, ruleId %s, switch %s. Error: %s",
                                        commandId, uuid, response.getSwitchId(), message)));
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
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
