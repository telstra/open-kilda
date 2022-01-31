/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.GroupCommand;
import org.openkilda.floodlight.api.request.rulemanager.MeterCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class OnReceivedRemoveResponseAction extends
        HistoryRecordingAction<YFlowDeleteFsm, State, Event, YFlowDeleteContext> {
    private static final String FAILED_TO_REMOVE_RULE_ACTION = "Failed to remove rule";

    private final int speakerCommandRetriesLimit;

    public OnReceivedRemoveResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowDeleteContext context, YFlowDeleteFsm stateMachine) {
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
            Optional<DeleteSpeakerCommandsRequest> request = stateMachine.getDeleteSpeakerCommand(commandId);
            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit && request.isPresent()) {
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(FAILED_TO_REMOVE_RULE_ACTION,
                                format("Failed to remove the rule: commandId %s, ruleId %s, switch %s. "
                                                + "Error: %s. Retrying (attempt %d)",
                                        commandId, uuid, response.getSwitchId(), message, attempt)));

                Set<UUID> failedUuids = response.getFailedCommandIds().keySet();
                DeleteSpeakerCommandsRequest deleteRequest = request.get();
                List<OfCommand> commands = deleteRequest.getCommands().stream()
                        .filter(command -> command instanceof FlowCommand
                                && failedUuids.contains(((FlowCommand) command).getData().getUuid())
                                || command instanceof MeterCommand
                                && failedUuids.contains(((MeterCommand) command).getData().getUuid())
                                || command instanceof GroupCommand
                                && failedUuids.contains(((GroupCommand) command).getData().getUuid()))
                        .collect(Collectors.toList());
                stateMachine.getCarrier().sendSpeakerRequest(deleteRequest.toBuilder().commands(commands).build());
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
                log.debug("Received responses for all pending remove commands of y-flow {}",
                        stateMachine.getYFlowId());
                stateMachine.fire(Event.ALL_YFLOW_METERS_REMOVED);
            } else {
                String errorMessage = format("Received error response(s) for %d remove commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
