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

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.model.Flow;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.rules.IngressRulesValidator;
import org.openkilda.wfm.topology.flowhs.validation.rules.RulesValidator;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;

@Slf4j
public class ValidateIngressRuleAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        String commandId = context.getFlowResponse().getCommandId();

        InstallIngressRule expected = stateMachine.getIngressCommands().get(commandId);

        RulesValidator validator = new IngressRulesValidator(expected, (FlowRuleResponse) context.getFlowResponse());
        if (!validator.validate()) {
            stateMachine.fire(Event.ERROR);
        } else {
            saveHistory(stateMachine, expected);
            stateMachine.getPendingCommands().remove(commandId);
            if (stateMachine.getPendingCommands().isEmpty()) {
                log.debug("Ingress rules have been validated for flow {}", stateMachine.getFlow().getFlowId());
                stateMachine.fire(Event.NEXT);
            }
        }
    }

    private void saveHistory(FlowCreateFsm stateMachine, InstallIngressRule expected) {
        Flow flow = stateMachine.getFlow();

        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(format("Rule is valid: switch %s, cookie %s",
                                expected.getSwitchId().toString(), expected.getCookie()))
                        .description(format("Ingress rule has been validated successfully: switch %s, cookie %s",
                                expected.getSwitchId().toString(), expected.getCookie()))
                        .time(Instant.now())
                        .flowId(flow.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
