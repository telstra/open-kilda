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

package org.openkilda.wfm.topology.flowhs.fsm.action.create;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.rules.NonIngressRulesValidator;
import org.openkilda.wfm.topology.flowhs.validation.rules.RulesValidator;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class ValidateNonIngressRuleAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        String commandId = context.getFlowResponse().getCommandId();

        InstallTransitRule expected = stateMachine.getNonIngressCommands().stream()
                .filter(rule -> rule.getCommandId().equals(commandId))
                .findFirst()
                .map(InstallTransitRule.class::cast)
                .orElseThrow(() -> new IllegalStateException(format("Failed to find non ingress command with id %s",
                        commandId)));

        RulesValidator validator = new NonIngressRulesValidator(expected, (FlowRuleResponse) context.getFlowResponse());
        if (!validator.validate()) {
            stateMachine.fire(Event.Error);
        } else {
            stateMachine.getPendingCommands().remove(commandId);
            if (stateMachine.getPendingCommands().isEmpty()) {
                log.info("Non ingress rules have been validated for flow {}", stateMachine.getFlow().getFlowId());
                stateMachine.fire(Event.Next);
            }
        }
    }
}
