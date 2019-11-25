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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerInstallSegmentEmitter;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerRemoveSegmentEmitter;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class RevertNewRulesAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C>
        extends FlowProcessingAction<T, S, E, C> {
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public RevertNewRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());

        FlowEncapsulationType encapsulationType = stateMachine.getNewEncapsulationType() != null
                ? stateMachine.getNewEncapsulationType() : flow.getEncapsulationType();
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(encapsulationType);

        Collection<FlowSegmentRequestFactory> installRequests = new ArrayList<>();

        // Reinstall old ingress rules that may be overridden by new ingress.
        if (stateMachine.hasOldPrimaryForwardPath() && stateMachine.hasOldPrimaryReversePath()) {
            FlowPath oldForward = getFlowPath(flow, stateMachine.getOldPrimaryForwardPath());
            FlowPath oldReverse = getFlowPath(flow, stateMachine.getOldPrimaryReversePath());
            installRequests.addAll(commandBuilder.buildIngressOnly(
                    stateMachine.getCommandContext(), flow, oldForward, oldReverse));
        }

        Map<UUID, FlowSegmentRequestFactory> sentInstallRequests =
                SpeakerInstallSegmentEmitter.INSTANCE.emitBatch(stateMachine.getCarrier(), installRequests);
        stateMachine.setIngressCommands(sentInstallRequests);

        // Remove possible installed flow segments
        Collection<FlowSegmentRequestFactory> removeRequests = new ArrayList<>();
        if (stateMachine.hasNewPrimaryPaths()) {
            FlowPath newForward = getFlowPath(flow, stateMachine.getNewPrimaryForwardPath());
            FlowPath newReverse = getFlowPath(flow, stateMachine.getNewPrimaryReversePath());
            removeRequests.addAll(commandBuilder.buildAll(
                    stateMachine.getCommandContext(), flow, newForward, newReverse));
        }
        if (stateMachine.hasNewProtectedPaths()) {
            FlowPath newForward = getFlowPath(flow, stateMachine.getNewProtectedForwardPath());
            FlowPath newReverse = getFlowPath(flow, stateMachine.getNewProtectedReversePath());
            removeRequests.addAll(commandBuilder.buildAllExceptIngress(
                    stateMachine.getCommandContext(), flow, newForward, newReverse));
        }

        Map<UUID, FlowSegmentRequestFactory> sentRemoveRequests =
                SpeakerRemoveSegmentEmitter.INSTANCE.emitBatch(stateMachine.getCarrier(), removeRequests);
        stateMachine.setRemoveCommands(sentRemoveRequests);

        Set<UUID> pendingRequests = new HashSet<>(sentInstallRequests.keySet());
        pendingRequests.addAll(sentRemoveRequests.keySet());
        stateMachine.setPendingCommands(pendingRequests);

        stateMachine.resetFailedCommandsAndRetries();

        stateMachine.saveActionToHistory(
                "Commands for removing new rules and re-installing original ingress rule have been sent");
    }
}
