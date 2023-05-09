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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class BuildNewRulesAction
        extends HaFlowRuleManagerProcessingAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    private final FlowPathRepository flowPathRepository;

    public BuildNewRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    protected void perform(State from, State to,
                           Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getFlowId());
        DataAdapter dataAdapter = buildDataAdapter(haFlow, stateMachine);

        List<SpeakerData> ingressCommands = new ArrayList<>();
        List<SpeakerData> nonIngressCommands = new ArrayList<>();

        HaFlowPath forwardPath = getHaFlowPath(haFlow, stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());
        List<SpeakerData> forwardCommands = ruleManager.buildRulesHaFlowPath(
                forwardPath, true, false, true, true, dataAdapter);
        groupCommands(forwardCommands, ingressCommands, nonIngressCommands,
                newHashSet(forwardPath.getSharedSwitchId()));

        HaFlowPath reversePath = getHaFlowPath(haFlow, stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId());
        List<SpeakerData> reverseCommands = ruleManager.buildRulesHaFlowPath(
                reversePath, true, false, true, true, dataAdapter);
        groupCommands(reverseCommands, ingressCommands, nonIngressCommands, reversePath.getSubFlowSwitchIds());

        if (stateMachine.getNewProtectedPathIds() != null) {
            HaFlowPath protectedForwardPath = getHaFlowPath(
                    haFlow, stateMachine.getNewProtectedPathIds().getForward().getHaPathId());
            nonIngressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    protectedForwardPath, true, false, false, true, dataAdapter));

            HaFlowPath protectedReversePath = getHaFlowPath(
                    haFlow, stateMachine.getNewProtectedPathIds().getReverse().getHaPathId());
            nonIngressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    protectedReversePath, true, false, false, true, dataAdapter));
        }

        buildHaFlowInstallRequests(ingressCommands, stateMachine.getCommandContext(), true)
                .forEach(request -> stateMachine.getIngressCommands().put(request.getCommandId(), request));
        buildHaFlowInstallRequests(nonIngressCommands, stateMachine.getCommandContext(), true)
                .forEach(request -> stateMachine.getNonIngressCommands().put(request.getCommandId(), request));
    }


    private DataAdapter buildDataAdapter(HaFlow haFlow, HaFlowUpdateFsm stateMachine) {
        Set<PathId> pathIds = newHashSet(haFlow.getSubPathIds());
        for (SwitchId switchId : haFlow.getEndpointSwitchIds()) {
            pathIds.addAll(flowPathRepository.findBySrcSwitch(switchId, false).stream()
                    .map(FlowPath::getPathId)
                    .collect(Collectors.toSet()));
        }

        Set<SwitchId> switchIds = new HashSet<>();
        switchIds.addAll(haFlow.getEndpointSwitchIds());
        switchIds.addAll(getSubPathSwitchIds(haFlow, stateMachine.getNewPrimaryPathIds().getAllSubPathIds()));
        if (stateMachine.getNewProtectedPathIds() != null) {
            switchIds.addAll(getSubPathSwitchIds(haFlow, stateMachine.getNewProtectedPathIds().getAllSubPathIds()));
        }

        HaFlow updatedHaFlow = copyHaFlowWithPathIds(
                haFlow, stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
        Map<PathId, HaFlow> additionalHaFlowMap = buildHaFlowMap(updatedHaFlow,
                stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
        additionalHaFlowMap.putAll(buildHaFlowMap(stateMachine.getOriginalHaFlow(), stateMachine.getOldPrimaryPathIds(),
                stateMachine.getOldProtectedPathIds()));

        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false, additionalHaFlowMap);
    }

    private Set<SwitchId> getSubPathSwitchIds(HaFlow haFlow, Collection<PathId> subPathIds) {
        Set<SwitchId> switchIds = new HashSet<>();
        for (PathId subPathId : subPathIds) {
            FlowPath subPath = haFlow.getSubPath(subPathId)
                    .orElseThrow(() -> new IllegalStateException(format("New ha-sub path %s of ha-flow %s not found",
                            subPathId, haFlow.getHaFlowId())));
            for (PathSegment segment : subPath.getSegments()) {
                switchIds.add(segment.getSrcSwitchId());
                switchIds.add(segment.getDestSwitchId());
            }
        }
        return switchIds;
    }

    private void groupCommands(
            Collection<SpeakerData> commands, Collection<SpeakerData> ingressCommands, Collection<SpeakerData>
            nonIngressCommands, Set<SwitchId> ingressSwitches) {
        for (SpeakerData command : commands) {
            if (ingressSwitches.contains(command.getSwitchId())) {
                ingressCommands.add(command);
            } else {
                nonIngressCommands.add(command);
            }
        }
    }
}
