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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import org.openkilda.floodlight.flow.request.FlowRequest;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.AbstractFlowCommandFactory;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class InstallNonIngressRulesAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final AbstractFlowCommandFactory commandFactory;

    public InstallNonIngressRulesAction(PersistenceManager persistenceManager) {
        super(persistenceManager);

        commandFactory = new AbstractFlowCommandFactory(persistenceManager);
    }

    @Override
    protected void perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                           FlowRerouteFsm.Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        FlowCommandFactory flowCommandFactory = commandFactory.getFactory(flow.getEncapsulationType());

        Collection<InstallTransitRule> commands = new ArrayList<>();

        if (stateMachine.getNewPrimaryForwardPath() != null && stateMachine.getNewPrimaryReversePath() != null) {
            FlowPath newForward = getFlowPath(flow, stateMachine.getNewPrimaryForwardPath());
            FlowPath newReverse = getFlowPath(flow, stateMachine.getNewPrimaryReversePath());
            commands.addAll(flowCommandFactory.createInstallNonIngressRules(
                    stateMachine.getCommandContext(), flow, newForward, newReverse));
        }
        if (stateMachine.getNewProtectedForwardPath() != null && stateMachine.getNewProtectedReversePath() != null) {
            FlowPath newForward = getFlowPath(flow, stateMachine.getNewProtectedForwardPath());
            FlowPath newReverse = getFlowPath(flow, stateMachine.getNewProtectedReversePath());
            commands.addAll(flowCommandFactory.createInstallNonIngressRules(
                    stateMachine.getCommandContext(), flow, newForward, newReverse));
        }

        stateMachine.setNonIngressCommands(commands.stream()
                .collect(Collectors.toMap(InstallTransitRule::getCommandId, Function.identity())));

        if (commands.isEmpty()) {
            log.debug("No need to install non ingress rules for one switch flow {}", stateMachine.getFlowId());

            saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(),
                    "Skip installation of non ingress commands.");

            stateMachine.fire(Event.RULES_INSTALLED);
        } else {
            Set<UUID> commandIds = commands.stream()
                    .peek(command -> stateMachine.getCarrier().sendSpeakerRequest(command))
                    .map(FlowRequest::getCommandId)
                    .collect(Collectors.toSet());
            stateMachine.setPendingCommands(commandIds);

            log.debug("Commands for installing non ingress rules have been sent for the flow {}",
                    stateMachine.getFlowId());

            saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(),
                    "Install non ingress commands have been sent.");
        }
    }
}
