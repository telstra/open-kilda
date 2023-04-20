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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.OfCommandConverter;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class OnReceivedResponseAction
        extends HistoryRecordingAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private static final String FAILED_TO_APPLY_RULE_MESSAGE = "Failed to %s rule";

    private final int speakerCommandRetriesLimit;
    private final String actionName;
    private final Event finishEvent;

    public OnReceivedResponseAction(int speakerCommandRetriesLimit, String actionName, Event finishEvent) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        this.actionName = actionName;
        this.finishEvent = finishEvent;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        Optional<BaseSpeakerCommandsRequest> command = stateMachine.getSpeakerCommand(commandId);
        if (!command.isPresent() || !stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);
            stateMachine.saveActionToHistory(format("%s rules were %sed", command.get().getCommands().size(),
                            actionName),
                    format("%s rules were %sed: switch %s", command.get().getCommands().size(),
                            actionName, response.getSwitchId()));
        } else {
            BaseSpeakerCommandsRequest request = stateMachine.getSpeakerCommand(commandId).orElse(null);
            int retries = stateMachine.doRetryForCommand(commandId);
            if (retries <= speakerCommandRetriesLimit && request != null) {
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(format(FAILED_TO_APPLY_RULE_MESSAGE, actionName), format(
                                "Failed to %s the rule: commandId %s, switch %s, uuid %s. Error %s. "
                                        + "Retrying (attempt %d)",
                                actionName, commandId, response.getSwitchId(), uuid, message, retries)));

                List<OfCommand> failedCommands = request.getCommands().stream()
                        .filter(ofCommand -> response.getFailedCommandIds().containsKey(ofCommand.getUuid()))
                        .collect(Collectors.toList());
                request.getCommands().clear();
                request.getCommands().addAll(OfCommandConverter.INSTANCE.removeExcessDependencies(failedCommands));
                stateMachine.getCarrier().sendSpeakerRequest(request);
            } else {
                stateMachine.removePendingCommand(commandId);

                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(format(FAILED_TO_APPLY_RULE_MESSAGE, actionName), format(
                                "Failed to %s the rule: commandId %s, switch %s, uuid %s. Error: %s",
                                actionName, commandId, response.getSwitchId(), uuid, message)));

                stateMachine.addFailedCommand(commandId, response);
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending {} commands of the flow {}",
                        actionName, stateMachine.getFlowId());
                stateMachine.fire(finishEvent);
            } else {
                String errorMessage = format("Received error response(s) for %d %s commands",
                        stateMachine.getFailedCommands().size(), actionName);
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
