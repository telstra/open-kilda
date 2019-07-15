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

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestProxiedFactory;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class RollbackInstalledRulesAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final SpeakerCommandFsm.Builder speakerCommandFsmBuilder;
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public RollbackInstalledRulesAction(SpeakerCommandFsm.Builder fsmBuilder, PersistenceManager persistenceManager,
                                        FlowResourcesManager resourcesManager) {
        super(persistenceManager);

        this.speakerCommandFsmBuilder = fsmBuilder;
        this.commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        stateMachine.getPendingRequests().clear();

        Flow flow = getFlow(stateMachine.getFlowId());
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());

        final List<FlowSegmentRequestProxiedFactory> removeRequests = new ArrayList<>();
        if (!stateMachine.getNonIngressCommands().isEmpty()) {
            removeRequests.addAll(commandBuilder.buildAllExceptIngress(stateMachine.getCommandContext(), flow));
        }
        if (!stateMachine.getIngressCommands().isEmpty()) {
            removeRequests.addAll(commandBuilder.buildIngressOnly(stateMachine.getCommandContext(), flow));
        }

        Map<UUID, FlowSegmentRequestProxiedFactory> commandPerId = new HashMap<>(removeRequests.size());
        final Map<UUID, SpeakerCommandObserver> pendingRequests = stateMachine.getPendingRequests();
        for (FlowSegmentRequestProxiedFactory factory : removeRequests) {
            FlowSegmentRequest request = factory.makeRemoveRequest(commandIdGenerator.generate());
            // TODO ensure no conflicts
            commandPerId.put(request.getCommandId(), factory);
            pendingRequests.put(request.getCommandId(), new SpeakerCommandObserver(speakerCommandFsmBuilder, request));
        }

        stateMachine.setRemoveCommands(commandPerId);
        log.debug("Commands to rollback installed rules have been sent. Total amount: {}", removeRequests.size());
    }
}
