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

import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.rules.NonIngressRulesValidator;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class ValidateNonIngressRuleAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    public ValidateNonIngressRuleAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        UUID commandId = context.getSpeakerFlowResponse().getCommandId();

        InstallTransitRule expected = stateMachine.getNonIngressCommands().get(commandId);
        FlowRuleResponse response = (FlowRuleResponse) context.getSpeakerFlowResponse();
        if (new NonIngressRulesValidator(expected, response).validate()) {
            stateMachine.saveActionToHistory("Rule was validated",
                    format("The non ingress rule has been validated successfully: switch %s, cookie %s",
                            expected.getSwitchId(), expected.getCookie()));
        } else {
            stateMachine.saveErrorToHistory("Rule is missing or invalid",
                    format("Non ingress rule is missing or invalid: switch %s, cookie %s",
                            expected.getSwitchId(), expected.getCookie()));
            stateMachine.getFailedValidationResponses().put(commandId, response);
        }

        stateMachine.getPendingCommands().remove(commandId);
        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedValidationResponses().isEmpty()) {
                log.debug("Non ingress rules have been validated for flow {}", stateMachine.getFlowId());
                stateMachine.fire(Event.NEXT);
            } else {
                String errorMessage = "Found missing rules or received error response(s) on validation commands";
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
