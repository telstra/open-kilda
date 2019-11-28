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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class InstallNonIngressRulesAction extends FlowProcessingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public InstallNonIngressRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        RequestedFlow requestedFlow = stateMachine.getTargetFlow();
        Flow flow = getFlow(flowId);

        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(requestedFlow.getFlowEncapsulationType());

        Collection<InstallTransitRule> commands = new ArrayList<>();

        FlowPath newPrimaryForward = getFlowPath(flow, stateMachine.getNewPrimaryForwardPath());
        FlowPath newPrimaryReverse = getFlowPath(flow, stateMachine.getNewPrimaryReversePath());
        commands.addAll(commandBuilder.createInstallNonIngressRules(
                stateMachine.getCommandContext(), flow, newPrimaryForward, newPrimaryReverse));

        if (stateMachine.getNewProtectedForwardPath() != null && stateMachine.getNewProtectedReversePath() != null) {
            FlowPath newProtectedForward = getFlowPath(flow, stateMachine.getNewProtectedForwardPath());
            FlowPath newProtectedReverse = getFlowPath(flow, stateMachine.getNewProtectedReversePath());
            commands.addAll(commandBuilder.createInstallNonIngressRules(
                    stateMachine.getCommandContext(), flow, newProtectedForward, newProtectedReverse));
        }

        stateMachine.getNonIngressCommands().putAll(commands.stream()
                .collect(Collectors.toMap(InstallTransitRule::getCommandId, Function.identity())));

        Set<UUID> commandIds = commands.stream()
                .peek(command -> stateMachine.getCarrier().sendSpeakerRequest(command))
                .map(SpeakerFlowRequest::getCommandId)
                .collect(Collectors.toSet());
        stateMachine.setPendingCommands(commandIds);
        stateMachine.resetFailedCommandsAndRetries();

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to install non ingress rules");

            stateMachine.fire(Event.RULES_INSTALLED);
        } else {
            stateMachine.saveActionToHistory("Commands for installing non ingress rules have been sent");
        }
    }
}
