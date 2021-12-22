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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedInstallResponseAction extends
        HistoryRecordingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private static final String FAILED_TO_INSTALL_RULE_ACTION = "Failed to install rule";

    private final int speakerCommandRetriesLimit;

    public OnReceivedInstallResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        SpeakerResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        if (!stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);
            stateMachine.saveActionToHistory("Rule was installed",
                    format("The rule was installed: switch %s", response.getSwitchId()));
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;

            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit) {
                stateMachine.saveErrorToHistory(FAILED_TO_INSTALL_RULE_ACTION,
                        format("Failed to install the rule: commandId %s, switch %s. Error %s. Retrying (attempt %d)",
                                commandId, errorResponse.getSwitchId(), errorResponse, attempt));

                //TODO: stateMachine.getCarrier().sendSpeakerRequest(command.makeInstallRequest(commandId));
            } else {
                stateMachine.addFailedCommand(commandId, errorResponse);
                stateMachine.removePendingCommand(commandId);

                stateMachine.saveErrorToHistory(FAILED_TO_INSTALL_RULE_ACTION,
                        format("Failed to install the rule: commandId %s, switch %s. Error: %s",
                                commandId, errorResponse.getSwitchId(), errorResponse));
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending install commands of y-flow {}",
                        stateMachine.getYFlowId());
                stateMachine.fire(Event.ALL_YFLOW_METERS_INSTALLED);
            } else {
                String errorMessage = format("Received error response(s) for %d install commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}