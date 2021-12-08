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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedValidateResponseAction extends
        HistoryRecordingAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    private static final String RULE_VALIDATION_FAILED_ACTION = "Rule validation failed";

    private final int speakerCommandRetriesLimit;

    public OnReceivedValidateResponseAction(int speakerCommandRetriesLimit) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        SpeakerResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        if (!stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);
            stateMachine.saveActionToHistory("Rule was validated",
                    format("The rule was validated successfully: switch %s", response.getSwitchId()));
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;

            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit
                    && errorResponse.getErrorCode() != FlowErrorResponse.ErrorCode.MISSING_OF_FLOWS) {
                stateMachine.saveErrorToHistory(RULE_VALIDATION_FAILED_ACTION,
                        format("Failed to validate the rule: commandId %s, switch %s. Error %s. Retrying (attempt %d)",
                                commandId, errorResponse.getSwitchId(), errorResponse, attempt));

                //TODO: stateMachine.getCarrier().sendSpeakerRequest(command.makeInstallRequest(commandId));
            } else {
                stateMachine.addFailedCommand(commandId, response);
                stateMachine.removePendingCommand(commandId);

                stateMachine.saveErrorToHistory(RULE_VALIDATION_FAILED_ACTION,
                        format("Failed to validate the rule: commandId %s, switch %s. Error %s",
                                commandId, errorResponse.getSwitchId(), errorResponse));
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedValidationResponses().isEmpty()) {
                log.debug("Rules have been validated for y-flow {}", stateMachine.getYFlowId());
                stateMachine.fire(Event.YPOINT_METER_VALIDATED);
            } else {
                String errorMessage = format("Received error response(s) for %d validation commands",
                        stateMachine.getFailedValidationResponses().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
