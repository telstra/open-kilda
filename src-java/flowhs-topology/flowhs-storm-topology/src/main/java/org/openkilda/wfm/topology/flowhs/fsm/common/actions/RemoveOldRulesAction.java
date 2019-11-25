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
import org.openkilda.wfm.topology.flowhs.utils.SpeakerRemoveSegmentEmitter;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class RemoveOldRulesAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C>
        extends FlowProcessingAction<T, S, E, C> {
    private final FlowCommandBuilderFactory commandBuilderFactory;
    private final E skipEvent;

    public RemoveOldRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager,
                                E skipEvent) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
        this.skipEvent = skipEvent;
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        FlowEncapsulationType encapsulationType = stateMachine.getOriginalEncapsulationType();
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(encapsulationType);

        Collection<FlowSegmentRequestFactory> requestFactories = new ArrayList<>();

        if (stateMachine.hasOldPrimaryForwardPath()) {
            FlowPath oldForward = getFlowPath(stateMachine.getOldPrimaryForwardPath());
            Flow flow = oldForward.getFlow();

            if (stateMachine.hasOldPrimaryReversePath()) {
                FlowPath oldReverse = getFlowPath(stateMachine.getOldPrimaryReversePath());
                requestFactories.addAll(commandBuilder.buildAll(
                        stateMachine.getCommandContext(), flow, oldForward, oldReverse));
            } else {
                requestFactories.addAll(commandBuilder.buildAll(
                        stateMachine.getCommandContext(), flow, oldForward));

            }
        } else if (stateMachine.hasOldPrimaryReversePath()) {
            FlowPath oldReverse = getFlowPath(stateMachine.getOldPrimaryReversePath());
            Flow flow = oldReverse.getFlow();
            requestFactories.addAll(commandBuilder.buildAll(
                    stateMachine.getCommandContext(), flow, oldReverse));
        }

        if (stateMachine.hasOldProtectedForwardPath()) {
            FlowPath oldForward = getFlowPath(stateMachine.getOldProtectedForwardPath());
            Flow flow = oldForward.getFlow();

            if (stateMachine.hasOldProtectedReversePath()) {
                FlowPath oldReverse = getFlowPath(stateMachine.getOldProtectedReversePath());
                requestFactories.addAll(commandBuilder.buildAllExceptIngress(
                        stateMachine.getCommandContext(), flow, oldForward, oldReverse));
            } else {
                requestFactories.addAll(commandBuilder.buildAllExceptIngress(
                        stateMachine.getCommandContext(), flow, oldForward));
            }
        } else if (stateMachine.hasOldProtectedReversePath()) {
            FlowPath oldReverse = getFlowPath(stateMachine.getOldProtectedReversePath());
            Flow flow = oldReverse.getFlow();
            requestFactories.addAll(commandBuilder.buildAllExceptIngress(
                    stateMachine.getCommandContext(), flow, oldReverse));
        }

        Map<UUID, FlowSegmentRequestFactory> sentRemoveRequests =
                SpeakerRemoveSegmentEmitter.INSTANCE.emitBatch(stateMachine.getCarrier(), requestFactories);
        stateMachine.setRemoveCommands(sentRemoveRequests);
        stateMachine.setPendingCommands(sentRemoveRequests.keySet());
        stateMachine.resetFailedCommandsAndRetries();

        if (requestFactories.isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove old rules");

            stateMachine.fire(skipEvent);
        } else {
            stateMachine.saveActionToHistory("Remove commands for old rules have been sent");
        }
    }
}
