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
import org.openkilda.wfm.share.model.FactoryBuildContext;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());
        Collection<FlowSegmentRequestFactory> commands = new ArrayList<>();

        Set<PathId> protectedPaths = Arrays.stream(
                new PathId[]{
                        flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()})
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<PathId> processed = new HashSet<>();
        for (FlowPath path : flow.getPaths()) {
            PathId pathId = path.getPathId();
            if (processed.add(pathId)) {
                FlowPath oppositePath = flow.getOppositePathId(pathId).filter(oppPathId -> !pathId.equals(oppPathId))
                        .flatMap(flow::getPath).orElse(null);
                if (oppositePath != null) {
                    processed.add(oppositePath.getPathId());
                }


                if (oppositePath != null) {
                    stateMachine.getFlowResources().add(buildResources(flow, path, oppositePath));

                    if (protectedPaths.contains(pathId)) {
                        commands.addAll(commandBuilder.buildAllExceptIngress(
                                stateMachine.getCommandContext(), flow, path, oppositePath));
                    } else {
                        FactoryBuildContext factoryBuildContext = FactoryBuildContext.builder()
                                .cleanUpIngress(isCleanUpIngress(flow, path))
                                .cleanUpOppositeIngress(isCleanUpIngress(flow, oppositePath))
                                .build();
                        commands.addAll(commandBuilder.buildAll(
                                stateMachine.getCommandContext(), flow, path, oppositePath, factoryBuildContext));
                    }
                } else {
                    log.warn("No opposite path found for {}, trying to delete as unpaired path", pathId);

                    stateMachine.getFlowResources().add(buildResources(flow, path, path));

                    if (protectedPaths.contains(pathId)) {
                        commands.addAll(commandBuilder.buildAllExceptIngress(
                                stateMachine.getCommandContext(), flow, path, null));
                    } else {
                        FactoryBuildContext factoryBuildContext = FactoryBuildContext.builder()
                                .cleanUpIngress(isCleanUpIngress(flow, path))
                                .build();
                        commands.addAll(commandBuilder.buildAll(
                                stateMachine.getCommandContext(), flow, path, null, factoryBuildContext));
                    }
                }
            }
        }

        SpeakerRemoveSegmentEmitter.INSTANCE.emitBatch(
                stateMachine.getCarrier(), commands, stateMachine.getRemoveCommands());
        stateMachine.getPendingCommands().addAll(stateMachine.getRemoveCommands().keySet());

        stateMachine.saveActionToHistory("Remove commands for rules have been sent");
    }

    private FlowResources buildResources(Flow flow, FlowPath path, FlowPath oppositePath) {
        FlowPath forwardPath;
        FlowPath reversePath;

        if (path.isForward()) {
            forwardPath = path;
            reversePath = oppositePath;
        } else {
            forwardPath = oppositePath;
            reversePath = path;
        }

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

    private boolean isCleanUpIngress(Flow flow, FlowPath path) {
        boolean isForward = flow.isForward(path);
        return isIngressCleanUpNeeded(flow, path.getSrcSwitch().getSwitchId(),
                isForward ? flow.getSrcPort() : flow.getDestPort());
    }
}
