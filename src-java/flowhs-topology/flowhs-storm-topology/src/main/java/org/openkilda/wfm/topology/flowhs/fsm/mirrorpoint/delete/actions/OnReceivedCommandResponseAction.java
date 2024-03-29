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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.utils.SpeakerRequestHelper.keepOnlyFailedCommands;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
public class OnReceivedCommandResponseAction
        extends HistoryRecordingAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private static final String FAILED_TO_REMOVE_GROUP_ACTION = "Failed to remove group id";

    private final int speakerCommandRetriesLimit;

    public OnReceivedCommandResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event,
                           FlowMirrorPointDeleteContext context, FlowMirrorPointDeleteFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        Optional<BaseSpeakerCommandsRequest> command = stateMachine.getSpeakerCommand(commandId);
        if (!command.isPresent() || !stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);

            stateMachine.saveActionToHistory(format("%s mirror point entities were removed",
                            command.get().getCommands().size()),
                    format("%s mirror point entities were removed: switch %s", command.get().getCommands().size(),
                            response.getSwitchId()));
        } else {
            BaseSpeakerCommandsRequest request = stateMachine.getSpeakerCommand(commandId).orElse(null);
            int retries = stateMachine.doRetryForCommand(commandId);
            if (retries <= speakerCommandRetriesLimit && request != null) {
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(FAILED_TO_REMOVE_GROUP_ACTION, format(
                                "Failed to remove mirror point entity: commandId %s, switch %s, uuid %s. Error %s. "
                                        + "Retrying (attempt %d)",
                                commandId, response.getSwitchId(), uuid, message, retries)));

                keepOnlyFailedCommands(request, response.getFailedCommandIds().keySet());
                stateMachine.getCarrier().sendSpeakerRequest(request);
            } else {
                stateMachine.removePendingCommand(commandId);

                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory(FAILED_TO_REMOVE_GROUP_ACTION, format(
                                "Failed to remove the mirror point entities: commandId %s, switch %s, uuid %s. "
                                        + "Error: %s", commandId, response.getSwitchId(), uuid, message)));

                stateMachine.getFailedCommands().put(commandId, response);
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending remove commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.GROUP_REMOVED);
            } else {
                String errorMessage = format("Received error response(s) for %d remove commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
