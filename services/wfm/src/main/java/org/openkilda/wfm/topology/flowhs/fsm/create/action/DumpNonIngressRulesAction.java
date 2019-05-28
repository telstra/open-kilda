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

import org.openkilda.floodlight.flow.request.FlowRequest;
import org.openkilda.floodlight.flow.request.GetInstalledRule;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class DumpNonIngressRulesAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        log.debug("Started validation of installed non ingress rules for the flow {}", stateMachine.getFlowId());

        List<GetInstalledRule> dumpFlowRules = stateMachine.getNonIngressCommands().values()
                .stream()
                .map(command -> new GetInstalledRule(command.getMessageContext(), command.getCommandId(),
                        command.getFlowId(), command.getSwitchId(), command.getCookie()))
                .collect(Collectors.toList());

        dumpFlowRules.forEach(command -> stateMachine.getCarrier().sendSpeakerRequest(command));
        Set<UUID> commandIds = dumpFlowRules.stream()
                .map(FlowRequest::getCommandId)
                .collect(Collectors.toSet());
        stateMachine.setPendingCommands(commandIds);
    }
}
