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

import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
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
public class RemoveOldRulesAction extends FlowProcessingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public RemoveOldRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        FlowEncapsulationType oldEncapsulationType = stateMachine.getOriginalFlow().getFlowEncapsulationType();
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(oldEncapsulationType);

        Collection<RemoveRule> commands = new ArrayList<>();

        FlowPath oldPrimaryForward = getFlowPath(stateMachine.getOldPrimaryForwardPath());
        FlowPath oldPrimaryReverse = getFlowPath(stateMachine.getOldPrimaryReversePath());
        Flow flow = oldPrimaryForward.getFlow();
        commands.addAll(commandBuilder.createRemoveNonIngressRules(
                stateMachine.getCommandContext(), flow, oldPrimaryForward, oldPrimaryReverse));
        commands.addAll(commandBuilder.createRemoveIngressRules(
                stateMachine.getCommandContext(), flow, oldPrimaryForward, oldPrimaryReverse));

        if (stateMachine.getOldProtectedForwardPath() != null && stateMachine.getOldProtectedReversePath() != null) {
            FlowPath oldForward = getFlowPath(stateMachine.getOldProtectedForwardPath());
            FlowPath oldReverse = getFlowPath(stateMachine.getOldProtectedReversePath());
            commands.addAll(commandBuilder.createRemoveNonIngressRules(
                    stateMachine.getCommandContext(), flow, oldForward, oldReverse));
        }

        stateMachine.getRemoveCommands().putAll(commands.stream()
                .collect(Collectors.toMap(RemoveRule::getCommandId, Function.identity())));

        Set<UUID> commandIds = commands.stream()
                .peek(command -> stateMachine.getCarrier().sendSpeakerRequest(command))
                .map(SpeakerFlowRequest::getCommandId)
                .collect(Collectors.toSet());
        stateMachine.getPendingCommands().addAll(commandIds);
        stateMachine.getRetriedCommands().clear();

        stateMachine.saveActionToHistory("Remove commands for old rules have been sent");
    }
}
