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
import org.openkilda.model.SharedOfFlow;
import org.openkilda.model.SharedOfFlow.SharedOfFlowType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.model.FlowPathSnapshot;
import org.openkilda.wfm.share.model.FlowPathSnapshot.FlowPathSnapshotBuilder;
import org.openkilda.wfm.share.model.SharedOfFlowStatus;
import org.openkilda.wfm.share.service.SharedOfFlowManager;
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

        SharedOfFlowManager sharedOfFlowManager = new SharedOfFlowManager(persistenceManager, flow.getFlowPathIds());

        for (FlowPath path : flow.getPaths()) {
            PathId pathId = path.getPathId();
            if (processed.add(pathId)) {
                FlowPath oppositePath = flow.getOppositePathId(pathId).filter(oppPathId -> !pathId.equals(oppPathId))
                        .flatMap(flow::getPath).orElse(null);
                if (oppositePath != null) {
                    processed.add(oppositePath.getPathId());
                }

                FlowResources pathPairResources;
                if (oppositePath != null) {
                    pathPairResources = buildResources(flow, path, oppositePath);
                } else {
                    log.warn("No opposite path found for {}, trying to delete as unpaired path", pathId);
                    pathPairResources = buildResources(flow, path, path);
                }

                stateMachine.getFlowResources().add(pathPairResources);

                if (protectedPaths.contains(pathId)) {
                    commands.addAll(commandBuilder.buildAllExceptIngress(
                            stateMachine.getCommandContext(), flow,
                            makeProtectedPathSnapshot(pathPairResources, path),
                            oppositePath != null
                                    ? makeProtectedPathSnapshot(pathPairResources, oppositePath)
                                    : null));
                } else {
/*
                    SpeakerRequestBuildContext speakerRequestBuildContext = SpeakerRequestBuildContext.builder()
                            .removeCustomerPortRule(isRemoveCustomerPortSharedCatchRule(flow, path))
                            .removeOppositeCustomerPortRule(
                                    isRemoveCustomerPortSharedCatchRule(flow, oppositePath))
                            .build();
*/
                    commands.addAll(commandBuilder.buildAll(
                            stateMachine.getCommandContext(), flow,
                            makePrimaryPathSnapshot(sharedOfFlowManager, path, pathPairResources),
                            oppositePath != null
                                    ? makePrimaryPathSnapshot(sharedOfFlowManager, oppositePath, pathPairResources)
                                    : null));
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

/*
    private boolean isRemoveCustomerPortSharedCatchRule(Flow flow, FlowPath path) {
        boolean isForward = flow.isForward(path);
        return isRemoveCustomerPortSharedCatchRule(flow.getFlowId(), path.getSrcSwitch().getSwitchId(),
                isForward ? flow.getSrcPort() : flow.getDestPort());
    }
*/

    private FlowPathSnapshot makePrimaryPathSnapshot(
            SharedOfFlowManager sharedOfFlowManager, FlowPath path, FlowResources resources) {
        FlowPathSnapshot.FlowPathSnapshotBuilder pathSnapshot = FlowPathSnapshot.builder(path)
                .resources(extractPathResources(resources, path));
        addSharedOfFlowsReferences(sharedOfFlowManager, pathSnapshot, path);
        return pathSnapshot.build();
    }

    private FlowPathSnapshot makeProtectedPathSnapshot(FlowResources resources, FlowPath path) {
        return FlowPathSnapshot.builder(path)
                .resources(extractPathResources(resources, path))
                .build();
    }

    private PathResources extractPathResources(FlowResources resources, FlowPath path) {
        if (path.isForward()) {
            return resources.getForward();
        } else {
            return resources.getReverse();
        }
    }

    private void addSharedOfFlowsReferences(
            SharedOfFlowManager sharedOfFlowManager, FlowPathSnapshotBuilder pathSnapshot, FlowPath path) {
        Set<SharedOfFlow> pathSharedFlowReferences = new HashSet<>(path.getSharedOfFlows());
        for (SharedOfFlow reference : pathSharedFlowReferences) {
            SharedOfFlowStatus status = sharedOfFlowManager.removeBinding(reference, path);
            if (reference.getType() == SharedOfFlowType.INGRESS_OUTER_VLAN_MATCH) {
                pathSnapshot.sharedIngressSegmentOuterVlanMatchStatus(status);
            } else {
                log.error("Unknown shared OF Flow type {} - {}", reference.getType(), reference);
            }
        }
    }
}
