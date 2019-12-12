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
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class RevertNewRulesAction extends FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public RevertNewRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        Flow originalFlow = stateMachine.getOriginalFlow();

        final CommandContext commandContext = stateMachine.getCommandContext();
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(originalFlow.getEncapsulationType());

        Collection<FlowSegmentRequestFactory> installRequests = new ArrayList<>();

        // Reinstall old ingress rules that may be overridden by new ingress.
        FlowPath oldForward = getFlowPath(originalFlow.getForwardPathId());
        FlowPath oldReverse = getFlowPath(originalFlow.getReversePathId());
        installRequests.addAll(commandBuilder.buildIngressOnly(commandContext, originalFlow, oldForward, oldReverse));

        // Remove possible installed flow segments
        Flow flow = getFlow(stateMachine.getFlowId());
        Collection<FlowSegmentRequestFactory> removeRequests = new ArrayList<>();
        removeRequests.addAll(makePathPairRequests(
                commandBuilder, commandContext, flow,
                stateMachine.getNewPrimaryForwardPath(), stateMachine.getNewPrimaryReversePath()));
        if (stateMachine.getNewProtectedForwardPath() != null && stateMachine.getNewProtectedReversePath() != null) {
            removeRequests.addAll(makePathPairRequests(
                    commandBuilder, commandContext, flow,
                    stateMachine.getNewProtectedForwardPath(), stateMachine.getNewProtectedReversePath()));
        }

        stateMachine.getIngressCommands().clear();
        sendRequests(stateMachine.getCarrier(), installRequests, stateMachine.getIngressCommands());
        sendRequests(stateMachine.getCarrier(), removeRequests, stateMachine.getRemoveCommands());

        Set<UUID> pendingRequests = stateMachine.getPendingCommands();
        pendingRequests.addAll(stateMachine.getIngressCommands().keySet());
        pendingRequests.addAll(stateMachine.getRemoveCommands().keySet());

        stateMachine.getRetriedCommands().clear();

        stateMachine.saveActionToHistory(
                "Commands for removing new rules and re-installing original ingress rule have been sent");
    }

    private List<FlowSegmentRequestFactory> makePathPairRequests(
            FlowCommandBuilder commandBuilder, CommandContext commandContext,
            Flow flow, PathId forwardId, PathId reverseId) {
        FlowPath newForward = getFlowPath(forwardId);
        FlowPath newReverse = getFlowPath(reverseId);
        return commandBuilder.buildAll(commandContext, flow, newForward, newReverse);
    }

    private void sendRequests(
            FlowRerouteHubCarrier carrier, Collection<FlowSegmentRequestFactory> factories,
            Map<UUID, FlowSegmentRequestFactory> requestsStorage) {

        for (FlowSegmentRequestFactory factory : factories) {
            FlowSegmentRequest request = factory.makeRemoveRequest(commandIdGenerator.generate());
            // TODO ensure no conflicts
            requestsStorage.put(request.getCommandId(), factory);
            carrier.sendSpeakerRequest(request);
        }
    }
}
