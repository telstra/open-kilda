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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedInstallResponseAction extends OnReceivedValidationResponseAction {
    public OnReceivedInstallResponseAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        super.perform(from, to, event, context, stateMachine);

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending install commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.NEXT);
            } else {
                String errorMessage = format("Received error response(s) for %d install commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.getFailedCommands().clear();
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }

    @Override
    void handleResponse(FlowCreateFsm stateMachine, FlowCreateContext context) {
        FlowResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();

        InstallFlowRule installRule;
        if (stateMachine.getNonIngressCommands().containsKey(commandId)) {
            installRule = stateMachine.getNonIngressCommands().get(commandId);
        } else if (stateMachine.getIngressCommands().containsKey(commandId)) {
            installRule = stateMachine.getIngressCommands().get(commandId);
        } else {
            throw new IllegalStateException(format("Failed to find an install rule command with id %s", commandId));
        }

        if (response.isSuccess()) {
            stateMachine.saveActionToHistory("Rule was installed",
                    format("The rule was installed: switch %s, cookie %s",
                            response.getSwitchId(), installRule.getCookie()));
        } else {
            handleError(stateMachine, response, installRule);
        }
    }

    private void handleError(FlowCreateFsm stateMachine, FlowResponse response, InstallFlowRule command) {
        FlowErrorResponse errorResponse = (FlowErrorResponse) response;
        stateMachine.getFailedCommands().put(command.getCommandId(), errorResponse);
        stateMachine.saveErrorToHistory("Failed to install rule",
                format("Failed to install the rule: switch %s, cookie %s. Error: %s",
                        errorResponse.getSwitchId(), command.getCookie(), errorResponse));
    }
}
