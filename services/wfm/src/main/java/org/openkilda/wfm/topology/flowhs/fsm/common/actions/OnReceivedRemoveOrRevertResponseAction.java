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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowInstallingFsm;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedRemoveOrRevertResponseAction
        <T extends FlowInstallingFsm<T, S, E, C>, S, E, C extends FlowContext>
        extends HistoryRecordingAction<T, S, E, C> {
    private final int speakerCommandRetriesLimit;
    private final E completeEvent;

    public OnReceivedRemoveOrRevertResponseAction(int speakerCommandRetriesLimit, @NonNull E completeEvent) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        this.completeEvent = completeEvent;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        SpeakerFlowSegmentResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();
        FlowSegmentRequestFactory removeCommand = stateMachine.getRemoveCommands().get(commandId);
        FlowSegmentRequestFactory installCommand = stateMachine.getInstallCommand(commandId);
        if (!stateMachine.isPendingCommand(commandId)
                || (removeCommand == null && installCommand == null)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);

            if (removeCommand != null) {
                stateMachine.saveActionToHistory("Rule was deleted",
                        format("The rule was removed: switch %s, cookie %s", response.getSwitchId(),
                                removeCommand.getCookie()));
            } else {
                stateMachine.saveActionToHistory("Rule was re-installed (reverted)",
                        format("The rule was installed: switch %s, cookie %s",
                                response.getSwitchId(), installCommand.getCookie()));
            }
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;

            int retries = stateMachine.getCommandRetries(commandId);
            if (retries < speakerCommandRetriesLimit) {
                stateMachine.setCommandRetries(commandId, ++retries);

                if (removeCommand != null) {
                    stateMachine.saveErrorToHistory("Failed to remove rule", format(
                            "Failed to remove the rule: commandId %s, switch %s, cookie %s. Error %s. "
                                    + "Retrying (attempt %d)",
                            commandId, errorResponse.getSwitchId(), removeCommand.getCookie(), errorResponse, retries));

                    stateMachine.getCarrier().sendSpeakerRequest(removeCommand.makeRemoveRequest(commandId));
                } else {
                    stateMachine.saveErrorToHistory("Failed to re-install (revert) rule", format(
                            "Failed to install the rule: commandId %s, switch %s, cookie %s. Error %s. "
                                    + "Retrying (attempt %d)", commandId, errorResponse.getSwitchId(),
                            installCommand.getCookie(), errorResponse, retries));

                    stateMachine.getCarrier().sendSpeakerRequest(installCommand.makeInstallRequest(commandId));
                }
            } else {
                stateMachine.removePendingCommand(commandId);

                if (removeCommand != null) {
                    stateMachine.saveErrorToHistory("Failed to remove rule", format(
                            "Failed to remove the rule: commandId %s, switch %s, cookie %s. Error: %s",
                            commandId, errorResponse.getSwitchId(), removeCommand.getCookie(), errorResponse));
                } else {
                    stateMachine.saveErrorToHistory("Failed to re-install rule", format(
                            "Failed to install the rule: commandId %s, switch %s, cookie %s. Error: %s",
                            commandId, errorResponse.getSwitchId(), installCommand.getCookie(), errorResponse));
                }
                stateMachine.addFailedCommand(commandId, errorResponse);
            }
        }

        if (!stateMachine.hasPendingCommands()) {
            if (!stateMachine.hasFailedCommands()) {
                log.debug("Received responses for all pending remove / re-install commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(completeEvent);
            } else {
                String errorMessage = format("Received error response(s) for %d remove / re-install commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
