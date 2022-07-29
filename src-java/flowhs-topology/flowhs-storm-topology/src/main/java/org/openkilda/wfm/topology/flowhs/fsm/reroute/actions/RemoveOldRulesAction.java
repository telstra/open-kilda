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
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.PathSwappingRuleRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.speaker.MirrorContext;
import org.openkilda.wfm.topology.flowhs.service.speaker.SpeakerRequestBuildContext;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class RemoveOldRulesAction extends
        PathSwappingRuleRemovalAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    public RemoveOldRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager, resourcesManager);
    }

    @TimedExecution("fsm.remove_old_rules")
    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        FlowEncapsulationType encapsulationType = stateMachine.getOriginalEncapsulationType();
        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(encapsulationType);

        Collection<FlowSegmentRequestFactory> factories = new ArrayList<>();

        Flow originalFlow = getOriginalFlowWithPaths(stateMachine, stateMachine.getOriginalFlow());

        SpeakerRequestBuildContext speakerContext = SpeakerRequestBuildContext.getEmpty();
        MirrorContext mirrorContext = MirrorContext.builder().removeFlowOperation(true).build();

        if (stateMachine.getOldPrimaryForwardPath() != null) {
            FlowPath oldForward = getFlowPath(stateMachine.getOldPrimaryForwardPath());
            speakerContext.setForward(buildPathContextForRemovalIngressOnly(oldForward.getSrcSwitchId()));

            if (stateMachine.getOldPrimaryReversePath() != null) {
                FlowPath oldReverse = getFlowPath(stateMachine.getOldPrimaryReversePath());
                speakerContext.setReverse(
                        buildPathContextForRemovalIngressOnly(oldReverse.getSrcSwitchId()));

                factories.addAll(commandBuilder.buildAll(
                        stateMachine.getCommandContext(), originalFlow, oldForward, oldReverse, speakerContext,
                        mirrorContext));
            } else {
                factories.addAll(commandBuilder.buildAll(
                        stateMachine.getCommandContext(), originalFlow, oldForward, speakerContext, mirrorContext));

            }
        } else if (stateMachine.getOldPrimaryReversePath() != null) {
            FlowPath oldReverse = getFlowPath(stateMachine.getOldPrimaryReversePath());
            speakerContext.setForward(buildPathContextForRemovalIngressOnly(oldReverse.getSrcSwitchId()));

            factories.addAll(commandBuilder.buildAll(
                    stateMachine.getCommandContext(), originalFlow, oldReverse, speakerContext, mirrorContext));
        }

        if (stateMachine.getOldProtectedForwardPath() != null) {
            FlowPath oldForward = getFlowPath(stateMachine.getOldProtectedForwardPath());

            if (stateMachine.getOldProtectedReversePath() != null) {
                FlowPath oldReverse = getFlowPath(stateMachine.getOldProtectedReversePath());
                factories.addAll(commandBuilder.buildAllExceptIngress(
                        stateMachine.getCommandContext(), originalFlow, oldForward, oldReverse, mirrorContext));
            } else {
                factories.addAll(commandBuilder.buildAllExceptIngress(
                        stateMachine.getCommandContext(), originalFlow, oldForward, mirrorContext));
            }
        } else if (stateMachine.getOldProtectedReversePath() != null) {
            FlowPath oldReverse = getFlowPath(stateMachine.getOldProtectedReversePath());
            factories.addAll(commandBuilder.buildAllExceptIngress(
                    stateMachine.getCommandContext(), originalFlow, oldReverse, mirrorContext));
        }

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        if (factories.isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove old rules");

            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            Map<UUID, FlowSegmentRequestFactory> requestsStorage = stateMachine.getRemoveCommands();
            for (FlowSegmentRequestFactory factory : factories) {
                FlowSegmentRequest request = factory.makeRemoveRequest(commandIdGenerator.generate());
                // TODO ensure no conflicts
                requestsStorage.put(request.getCommandId(), factory);
                stateMachine.getCarrier().sendSpeakerRequest(request);
            }
            requestsStorage.forEach((key, value) -> stateMachine.addPendingCommand(key, value.getSwitchId()));

            stateMachine.saveActionToHistory("Remove commands for old rules have been sent");
        }
    }
}
