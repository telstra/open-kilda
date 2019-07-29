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

import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandObserver;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class InstallIngressRulesAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final SpeakerCommandFsm.Builder speakerCommandFsmBuilder;
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public InstallIngressRulesAction(SpeakerCommandFsm.Builder speakerCommandFsmBuilder,
                                     PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);

        this.speakerCommandFsmBuilder = speakerCommandFsmBuilder;
        this.commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());
        List<InstallIngressRule> commands = commandBuilder.createInstallIngressRules(
                stateMachine.getCommandContext(), flow);

        commands.forEach(command -> {
            stateMachine.getIngressCommands().put(command.getCommandId(), command);
            SpeakerCommandObserver commandObserver = new SpeakerCommandObserver(speakerCommandFsmBuilder, command);
            commandObserver.start();
            stateMachine.getPendingCommands().put(command.getCommandId(), commandObserver);
        });

        log.debug("Commands for installing ingress rules have been sent");
        saveHistory(stateMachine, stateMachine.getCarrier(), stateMachine.getFlowId(),
                "Install ingress commands have been sent.");
    }
}
