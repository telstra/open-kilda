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
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandFactory;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RollbackInstalledRulesAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final FlowCommandFactory flowCommandFactory;

    public RollbackInstalledRulesAction(PersistenceManager persistenceManager) {
        this.flowCommandFactory = new FlowCommandFactory(
                persistenceManager.getRepositoryFactory().createTransitVlanRepository());
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Set<RemoveRule> removeCommands = new HashSet<>();

        if (!stateMachine.getNonIngressCommands().isEmpty()) {
            List<RemoveRule> removeNonIngress = flowCommandFactory.createRemoveNonIngressRules(
                    stateMachine.getCommandContext(), stateMachine.getFlow());
            removeCommands.addAll(removeNonIngress);
        }

        if (!stateMachine.getIngressCommands().isEmpty()) {
            List<RemoveRule> removeIngress = flowCommandFactory.createRemoveIngressRules(
                    stateMachine.getCommandContext(), stateMachine.getFlow());
            removeCommands.addAll(removeIngress);
        }

        Map<String, RemoveRule> commandPerId = new HashMap<>(removeCommands.size());
        for (RemoveRule command : removeCommands) {
            commandPerId.put(command.getCommandId(), command);
            stateMachine.getCarrier().sendSpeakerRequest(command);
        }

        stateMachine.setPendingCommands(commandPerId.keySet());
        stateMachine.setRemoveCommands(commandPerId);

        log.debug("Commands to rollback installed rules have been sent. Total amount: {}", removeCommands.size());
    }
}
