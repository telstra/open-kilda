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
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandFactory;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class InstallIngressRulesAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final FlowCommandFactory flowCommandFactory;

    public InstallIngressRulesAction(PersistenceManager persistenceManager) {
        this.flowCommandFactory = new FlowCommandFactory(
                persistenceManager.getRepositoryFactory().createTransitVlanRepository());
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        List<InstallIngressRule> commands = flowCommandFactory.createInstallIngressRules(
                stateMachine.getCommandContext(), stateMachine.getFlow());
        commands.forEach(command -> stateMachine.getCarrier().sendSpeakerRequest(command));

        stateMachine.setIngressCommands(new HashSet<>(commands));
        Set<String> commandIds = commands.stream()
                .map(FlowRequest::getCommandId)
                .collect(Collectors.toSet());
        stateMachine.setPendingCommands(commandIds);
        log.debug("Commands for installing ingress rules have been sent");
    }
}
