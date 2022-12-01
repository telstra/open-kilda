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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.OfCommandConverter;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class OnReceivedInstallResponseAction
        extends HistoryRecordingAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private static final String FAILED_TO_INSTALL_RULE_ACTION = "Failed to install rule";

    private final int speakerCommandRetriesLimit;

    public OnReceivedInstallResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event,
                           FlowMirrorPointCreateContext context, FlowMirrorPointCreateFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        Optional<BaseSpeakerCommandsRequest> command = stateMachine.getSpeakerCommand(commandId);
        if (!command.isPresent() || !stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);

            stateMachine.saveActionToHistory(format("%s rules were installed", command.get().getCommands().size()),
                    format("%s rules were installed: switch %s",
                            command.get().getCommands().size(), response.getSwitchId()));
        } else {
            BaseSpeakerCommandsRequest request = stateMachine.getSpeakerCommand(commandId).orElse(null);
            int retries = stateMachine.doRetryForCommand(commandId);
            if (retries <= speakerCommandRetriesLimit && request != null) {
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(FAILED_TO_INSTALL_RULE_ACTION, format(
                                "Failed to install the rule: commandId %s, switch %s, uuid %s. Error %s. "
                                        + "Retrying (attempt %d)",
                                commandId, response.getSwitchId(), uuid, message, retries)));

                List<OfCommand> failedCommands = request.getCommands().stream()
                        .filter(ofCommand -> response.getFailedCommandIds().containsKey(ofCommand.getUuid()))
                        .collect(Collectors.toList());
                request.getCommands().clear();
                request.getCommands().addAll(OfCommandConverter.INSTANCE.removeExcessDependencies(failedCommands));
                stateMachine.getCarrier().sendSpeakerRequest(request);
            } else {
                stateMachine.removePendingCommand(commandId);

                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(FAILED_TO_INSTALL_RULE_ACTION, format(
                                "Failed to install the rule: commandId %s, switch %s, uuid %s. Error: %s",
                                commandId, response.getSwitchId(), uuid, message)));

                stateMachine.addFailedCommand(commandId, response);
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending install commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_UPDATED);
            } else {
                String errorMessage = format("Received error response(s) for %d install commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
