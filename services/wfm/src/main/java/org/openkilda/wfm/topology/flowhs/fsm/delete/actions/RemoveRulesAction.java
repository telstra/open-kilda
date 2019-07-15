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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerRemoveSegmentEmitter;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class RemoveRulesAction extends FlowProcessingAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {
    private final FlowResourcesManager resourcesManager;
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public RemoveRulesAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowDeleteContext context, FlowDeleteFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        Set<PathId> pathIds = new HashSet<>(flow.getFlowPathIds());

        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());
        Collection<FlowSegmentRequestFactory> commands = new ArrayList<>();

        FlowPath forward = flow.getForwardPath();
        FlowPath reverse = flow.getReversePath();
        if (forward != null && reverse != null) {
            pathIds.remove(forward.getPathId());
            pathIds.remove(reverse.getPathId());

            stateMachine.getFlowResources().add(buildResources(flow, forward, reverse));
            commands.addAll(commandBuilder.buildAll(stateMachine.getCommandContext(), flow, forward, reverse));
        }

        FlowPath protectedForward = flow.getProtectedForwardPath();
        FlowPath protectedReverse = flow.getProtectedReversePath();
        if (protectedForward != null && protectedReverse != null) {
            pathIds.remove(protectedForward.getPathId());
            pathIds.remove(protectedReverse.getPathId());

            stateMachine.getFlowResources().add(buildResources(flow, protectedForward, protectedReverse));
            commands.addAll(commandBuilder.buildAllExceptIngress(
                    stateMachine.getCommandContext(), flow, protectedForward, protectedReverse));
        }

        Set<PathId> processed = new HashSet<>();
        for (PathId pathId : pathIds) {
            if (!processed.contains(pathId)) {
                PathId reversePathId = flow.getOppositePathId(pathId);
                Optional<FlowPath> path = flow.getPath(pathId);
                Optional<FlowPath> reversePath = flow.getPath(reversePathId);
                if (path.isPresent() && reversePath.isPresent()) {
                    processed.add(pathId);
                    processed.add(reversePathId);

                    stateMachine.getFlowResources().add(buildResources(flow, path.get(), reversePath.get()));

                    commands.addAll(commandBuilder.buildAll(
                            stateMachine.getCommandContext(), flow, path.get(), reversePath.get()));
                }
            }
        }
        pathIds.removeAll(processed);

        for (PathId pathId : pathIds) {
            flow.getPath(pathId).ifPresent(path -> log.warn("Failed to remove path {}", path));
        }

        SpeakerRemoveSegmentEmitter.INSTANCE.emitBatch(
                stateMachine.getCarrier(), commands, stateMachine.getRemoveCommands());
        stateMachine.getPendingCommands().addAll(stateMachine.getRemoveCommands().keySet());

        stateMachine.saveActionToHistory("Remove commands for old rules have been sent");
    }

    private FlowResources buildResources(Flow flow, FlowPath forwardPath, FlowPath reversePath) {
        EncapsulationResources encapsulationResources;
        if (!flow.isOneSwitchFlow()) {
            encapsulationResources = resourcesManager.getEncapsulationResources(
                    forwardPath.getPathId(), reversePath.getPathId(), flow.getEncapsulationType()).orElse(null);
        } else {
            encapsulationResources = null;
        }

        return FlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                .forward(PathResources.builder()
                        .pathId(forwardPath.getPathId())
                        .meterId(forwardPath.getMeterId())
                        .encapsulationResources(encapsulationResources)
                        .build())
                .reverse(PathResources.builder()
                        .pathId(reversePath.getPathId())
                        .meterId(reversePath.getMeterId())
                        .encapsulationResources(encapsulationResources)
                        .build())
                .build();
    }
}
