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

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class InstallNonIngressRulesAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public InstallNonIngressRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Flow flow = getFlow(flowId);

        FlowEncapsulationType encapsulationType = stateMachine.getNewEncapsulationType() != null
                ? stateMachine.getNewEncapsulationType() : flow.getEncapsulationType();
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(encapsulationType);

        Collection<FlowSegmentRequestFactory> requestFactories = new ArrayList<>();

        if (stateMachine.getNewPrimaryForwardPath() != null && stateMachine.getNewPrimaryReversePath() != null) {
            FlowPath newForward = getFlowPath(flow, stateMachine.getNewPrimaryForwardPath());
            FlowPath newReverse = getFlowPath(flow, stateMachine.getNewPrimaryReversePath());
            requestFactories.addAll(commandBuilder.buildAllExceptIngress(
                    stateMachine.getCommandContext(), flow, newForward, newReverse));
        }
        if (stateMachine.getNewProtectedForwardPath() != null && stateMachine.getNewProtectedReversePath() != null) {
            FlowPath newForward = getFlowPath(flow, stateMachine.getNewProtectedForwardPath());
            FlowPath newReverse = getFlowPath(flow, stateMachine.getNewProtectedReversePath());
            requestFactories.addAll(commandBuilder.buildAllExceptIngress(
                    stateMachine.getCommandContext(), flow, newForward, newReverse));
        }

        Map<UUID, FlowSegmentRequestFactory> requestsStorage = stateMachine.getNonIngressCommands();
        if (requestFactories.isEmpty()) {
            stateMachine.saveActionToHistory("No need to install non ingress rules");

            stateMachine.fire(Event.RULES_INSTALLED);
        } else {
            for (FlowSegmentRequestFactory factory : requestFactories) {
                FlowSegmentRequest request = factory.makeInstallRequest(commandIdGenerator.generate());
                // TODO ensure no conflicts
                requestsStorage.put(request.getCommandId(), factory);
                stateMachine.getCarrier().sendSpeakerRequest(request);
            }
            stateMachine.saveActionToHistory("Commands for installing non ingress rules have been sent");
            stateMachine.getRetriedCommands().clear();
        }

        stateMachine.getPendingCommands().addAll(new HashSet<>(requestsStorage.keySet()));
    }
}
