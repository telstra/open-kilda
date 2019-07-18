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

import org.openkilda.floodlight.flow.request.RemoveRule;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class RollbackInstalledRulesAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final TransitVlanCommandFactory flowCommandFactory;

    public RollbackInstalledRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.flowCommandFactory = new TransitVlanCommandFactory(
                persistenceManager.getRepositoryFactory().createTransitVlanRepository());
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Set<RemoveRule> removeCommands = new HashSet<>();
        stateMachine.getPendingCommands().clear();

        Flow flow = getFlow(stateMachine.getFlowId());

        if (!stateMachine.getNonIngressCommands().isEmpty()) {
            List<RemoveRule> removeNonIngress = flowCommandFactory.createRemoveNonIngressRules(
                    stateMachine.getCommandContext(), flow);
            removeCommands.addAll(removeNonIngress);
        }

        if (!stateMachine.getIngressCommands().isEmpty()) {
            List<RemoveRule> removeIngress = flowCommandFactory.createRemoveIngressRules(
                    stateMachine.getCommandContext(), flow);
            removeCommands.addAll(removeIngress);
        }

        Map<UUID, RemoveRule> commandPerId = new HashMap<>(removeCommands.size());
        for (RemoveRule command : removeCommands) {
            commandPerId.put(command.getCommandId(), command);
            stateMachine.getCarrier().sendSpeakerRequest(command);
            stateMachine.getPendingCommands().add(command.getCommandId());
        }

        stateMachine.setRemoveCommands(commandPerId);

        log.debug("Commands to rollback installed rules have been sent. Total amount: {}", removeCommands.size());
    }
}
