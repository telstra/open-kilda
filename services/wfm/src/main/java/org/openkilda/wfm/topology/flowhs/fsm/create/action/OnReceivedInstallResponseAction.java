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

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestProxiedFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedInstallResponseAction extends OnReceivedVerifyResponseAction {

    public OnReceivedInstallResponseAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        super.perform(from, to, event, context, stateMachine);

        if (stateMachine.getPendingRequests().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Successfully executed all pending commands");
                stateMachine.fire(Event.NEXT);
            } else {
                log.debug("Received responses of all pending commands. Total failed commands: {}",
                        stateMachine.getFailedCommands().size());
                stateMachine.getFailedCommands().clear();
                stateMachine.fireError();
            }
        }
    }

    @Override
    void handleResponse(FlowCreateFsm stateMachine, FlowCreateContext context) {
        SpeakerFlowSegmentResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();

        FlowSegmentRequestProxiedFactory factory;
        factory = stateMachine.getNonIngressCommands().get(commandId);
        if (factory == null) {
            factory = stateMachine.getIngressCommands().get(commandId);
        }
        if (factory == null) {
            throw new IllegalStateException(format("Failed to find install rule command with id %s", commandId));
        }

        if (response.isSuccess()) {
            log.debug("Rule {} was installed on switch {}", factory.getCookie(), response.getSwitchId());
            saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(), "Rule installed",
                    format("Rule was installed successfully: cookie %s, switch %s",
                           factory.getCookie(), response.getSwitchId()));
        } else {
            handleError(stateMachine, response, factory);
        }
    }

    private void handleError(
            FlowCreateFsm stateMachine, SpeakerFlowSegmentResponse response, FlowSegmentRequestProxiedFactory factory) {
        stateMachine.getFailedCommands().add(factory.getCommandId());
        FlowErrorResponse errorResponse = (FlowErrorResponse) response;
        String message = format("Failed to install rule %s, on the switch %s: %s. Description: %s",
                factory.getCookie(), errorResponse.getSwitchId(), errorResponse.getErrorCode(),
                errorResponse.getDescription());
        log.error(message);
        saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(), "Rule not installed",
                message);
    }
}
