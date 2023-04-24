/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.flow.resources.HaFlowResources.HaPathResources;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RemoveRulesAction extends HaFlowRuleManagerProcessingAction<
        HaFlowDeleteFsm, State, Event, HaFlowDeleteContext> {
    private final FlowResourcesManager resourcesManager;
    private final FlowPathRepository flowPathRepository;

    public RemoveRulesAction(
            PersistenceManager persistenceManager, FlowResourcesManager resourcesManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
        this.resourcesManager = resourcesManager;
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowDeleteContext context, HaFlowDeleteFsm stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getFlowId());

        Set<PathId> haFlowPathIds = new HashSet<>();
        for (HaFlowPath path : haFlow.getPaths()) {
            PathId pathId = path.getHaPathId();
            if (haFlowPathIds.add(pathId)) {
                HaFlowPath oppositePath = haFlow.getOppositePathId(pathId)
                        .filter(oppPathId -> !pathId.equals(oppPathId))
                        .flatMap(haFlow::getPath).orElse(null);
                if (oppositePath != null) {
                    haFlowPathIds.add(oppositePath.getHaPathId());
                    stateMachine.getHaFlowResources().add(buildResources(haFlow, path, oppositePath));
                } else {
                    log.warn("No opposite ha-path found for {}, trying to delete as ha-unpaired path", pathId);
                    stateMachine.getHaFlowResources().add(buildResources(haFlow, path, path));
                }
            }
        }

        DataAdapter adapter = buildDataAdapter(haFlow);
        List<SpeakerData> commands = buildSpeakerCommands(haFlow.getPaths(), adapter);
        Collection<DeleteSpeakerCommandsRequest> deleteRequests = buildHaFlowDeleteRequests(
                commands, stateMachine.getCommandContext());

        if (deleteRequests.isEmpty()) {
            stateMachine.saveActionToHistory("No requests to remove ha-flow rules");
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            // emitting
            deleteRequests.forEach(request -> {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.addSpeakerCommand(request.getCommandId(), request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            });

            stateMachine.saveActionToHistory("Commands for removing ha-flow rules have been sent");
        }
    }

    private HaFlowResources buildResources(HaFlow flow, HaFlowPath path, HaFlowPath oppositePath) {
        HaFlowPath forwardPath;
        HaFlowPath reversePath;

        if (path.isForward()) {
            forwardPath = path;
            reversePath = oppositePath;
        } else {
            forwardPath = oppositePath;
            reversePath = path;
        }

        EncapsulationResources encapsulationResources = resourcesManager.getEncapsulationResources(
                    forwardPath.getHaPathId(), reversePath.getHaPathId(), flow.getEncapsulationType()).orElse(null);
        return HaFlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getFlowEffectiveId())
                .forward(HaPathResources.builder()
                        .pathId(forwardPath.getHaPathId())
                        .sharedMeterId(forwardPath.getSharedPointMeterId())
                        .yPointMeterId(forwardPath.getYPointMeterId())
                        .yPointGroupId(forwardPath.getYPointGroupId())
                        .encapsulationResources(encapsulationResources)
                        .subPathIds(buildSubPathIdMap(forwardPath.getSubPaths()))
                        .subPathMeters(buildSubPathMeterIdMap(forwardPath.getSubPaths()))
                        .build())
                .reverse(HaPathResources.builder()
                        .pathId(reversePath.getHaPathId())
                        .sharedMeterId(reversePath.getSharedPointMeterId())
                        .yPointMeterId(reversePath.getYPointMeterId())
                        .yPointGroupId(reversePath.getYPointGroupId())
                        .encapsulationResources(encapsulationResources)
                        .subPathIds(buildSubPathIdMap(reversePath.getSubPaths()))
                        .subPathMeters(buildSubPathMeterIdMap(reversePath.getSubPaths()))
                        .build())
                .build();
    }

    private Map<String, PathId> buildSubPathIdMap(List<FlowPath> subPaths) {
        if (subPaths == null) {
            return new HashMap<>();
        }
        return subPaths.stream().collect(Collectors.toMap(FlowPath::getHaSubFlowId, FlowPath::getPathId));
    }

    private Map<String, MeterId> buildSubPathMeterIdMap(List<FlowPath> subPaths) {
        if (subPaths == null) {
            return new HashMap<>();
        }
        return subPaths.stream()
                .filter(path -> path.getMeterId() != null)
                .collect(Collectors.toMap(FlowPath::getHaSubFlowId, FlowPath::getMeterId));
    }

    private DataAdapter buildDataAdapter(HaFlow haFlow) {
        Set<PathId> pathIds = new HashSet<>();
        for (SwitchId switchId : haFlow.getEndpointSwitchIds()) {
            pathIds.addAll(flowPathRepository.findByEndpointSwitch(switchId, false).stream()
                    .map(FlowPath::getPathId)
                    .collect(Collectors.toSet()));
        }

        Set<SwitchId> switchIds = haFlow.getEndpointSwitchIds();
        for (HaFlowPath haFlowPath : haFlow.getPaths()) {
            for (FlowPath subPath : haFlowPath.getSubPaths()) {
                for (PathSegment segment : subPath.getSegments()) {
                    switchIds.add(segment.getSrcSwitchId());
                    switchIds.add(segment.getDestSwitchId());
                }
            }
        }
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
    }

    private List<SpeakerData> buildSpeakerCommands(
            Collection<HaFlowPath> paths, DataAdapter adapter) {
        List<SpeakerData> result = new ArrayList<>();
        for (HaFlowPath path : paths) {
            result.addAll(ruleManager.buildRulesHaFlowPath(path, true, adapter));
        }
        return result;
    }
}
