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
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.TransitVlanCommandFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class InstallNonIngressRulesAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private TransitVlanCommandFactory commandFactory;

    public InstallNonIngressRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.commandFactory =
                new TransitVlanCommandFactory(persistenceManager.getRepositoryFactory().createTransitVlanRepository());
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        List<InstallTransitRule> commands =
                commandFactory.createInstallNonIngressRules(stateMachine.getCommandContext(), flow);

        if (commands.isEmpty()) {
            log.debug("No need to install non ingress rules for one switch flow");
        } else {
            commands.forEach(command -> stateMachine.getCarrier().sendSpeakerRequest(command));

            stateMachine.setNonIngressCommands(commands.stream()
                    .collect(Collectors.toMap(InstallTransitRule::getCommandId, Function.identity())));
            Set<UUID> commandIds = commands.stream()
                    .map(FlowRequest::getCommandId)
                    .collect(Collectors.toSet());
            stateMachine.setPendingCommands(commandIds);
            log.debug("Commands for installing non ingress rules have been sent");
            saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(),
                    "Install non ingress commands have been sent.");
        }
    }
}
