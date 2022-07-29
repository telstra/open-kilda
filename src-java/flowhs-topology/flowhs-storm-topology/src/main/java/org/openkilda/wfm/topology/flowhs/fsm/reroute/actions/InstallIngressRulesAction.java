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
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.speaker.SpeakerRequestBuildContext;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class InstallIngressRulesAction extends
        FlowProcessingWithHistorySupportAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public InstallIngressRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Flow flow = getFlow(flowId);

        // Detach the entity to avoid propagation to the database.
        flowRepository.detach(flow);
        if (stateMachine.getNewEncapsulationType() != null) {
            // This is for commandBuilder.buildIngressOnly() to use proper (updated) encapsulation type.
            flow.setEncapsulationType(stateMachine.getNewEncapsulationType());
        }

        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());

        Collection<FlowSegmentRequestFactory> requestFactories = new ArrayList<>();
        if (stateMachine.getNewPrimaryForwardPath() != null && stateMachine.getNewPrimaryReversePath() != null) {
            FlowPath newForward = getFlowPath(flow, stateMachine.getNewPrimaryForwardPath());
            FlowPath newReverse = getFlowPath(flow, stateMachine.getNewPrimaryReversePath());

            SpeakerRequestBuildContext speakerContext = buildBaseSpeakerContextForInstall(
                    newForward.getSrcSwitchId(), newReverse.getSrcSwitchId());

            requestFactories.addAll(commandBuilder.buildIngressOnly(
                    stateMachine.getCommandContext(), flow, newForward, newReverse, speakerContext));
        }

        // Installation of ingress rules for protected paths is skipped. These paths are activated on swap.

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        if (requestFactories.isEmpty()) {
            stateMachine.saveActionToHistory("No need to install ingress rules");
            stateMachine.fire(Event.INGRESS_IS_SKIPPED);
        } else {
            Map<UUID, FlowSegmentRequestFactory> requestsStorage = stateMachine.getIngressCommands();
            for (FlowSegmentRequestFactory factory : requestFactories) {
                FlowSegmentRequest request = factory.makeInstallRequest(commandIdGenerator.generate());
                requestsStorage.put(request.getCommandId(), factory);
                stateMachine.getCarrier().sendSpeakerRequest(request);
            }
            requestsStorage.forEach((key, value) -> stateMachine.addPendingCommand(key, value.getSwitchId()));

            stateMachine.saveActionToHistory("Commands for installing ingress rules have been sent");
        }
    }
}
