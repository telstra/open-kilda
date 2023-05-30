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


import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;

import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowRuleManagerProcessingAction;
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

@Slf4j
public class BuildNewRulesAction
        extends HaFlowRuleManagerProcessingAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public BuildNewRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to,
                           Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getFlowId());
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(haFlow);
        Map<PathId, HaFlow> additionalHaFlowMap = buildAdditionalHaFlowMap(haFlow, stateMachine);

        List<SpeakerData> ingressCommands = new ArrayList<>();
        List<SpeakerData> nonIngressCommands = new ArrayList<>();

        HaFlowPath forwardPath = getHaFlowPath(haFlow, stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());
        List<SpeakerData> forwardCommands = buildPrimaryCommands(overlappingPathIds, additionalHaFlowMap, forwardPath);
        groupCommands(forwardCommands, ingressCommands, nonIngressCommands,
                newHashSet(forwardPath.getSharedSwitchId()));

        HaFlowPath reversePath = getHaFlowPath(haFlow, stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId());
        List<SpeakerData> reverseCommands = buildPrimaryCommands(overlappingPathIds, additionalHaFlowMap, reversePath);
        groupCommands(reverseCommands, ingressCommands, nonIngressCommands, reversePath.getSubFlowSwitchIds());

        if (stateMachine.getNewProtectedPathIds() != null) {
            List<HaFlowPath> protectedPaths = newArrayList(
                    getHaFlowPath(haFlow, stateMachine.getNewProtectedPathIds().getForward().getHaPathId()),
                    getHaFlowPath(haFlow, stateMachine.getNewProtectedPathIds().getReverse().getHaPathId()));

            for (HaFlowPath protectedPath : protectedPaths) {
                DataAdapter dataAdapter = buildDataAdapter(protectedPath, overlappingPathIds, additionalHaFlowMap);
                nonIngressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                        protectedPath, true, false, false, true, dataAdapter));
            }
        }

        buildHaFlowInstallRequests(ingressCommands, stateMachine.getCommandContext(), true)
                .forEach(request -> stateMachine.getIngressCommands().put(request.getCommandId(), request));
        buildHaFlowInstallRequests(nonIngressCommands, stateMachine.getCommandContext(), true)
                .forEach(request -> stateMachine.getNonIngressCommands().put(request.getCommandId(), request));
    }

    private List<SpeakerData> buildPrimaryCommands(
            Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap, HaFlowPath forwardPath) {
        DataAdapter dataAdapter = buildDataAdapter(forwardPath, overlappingPathIds, additionalHaFlowMap);
        return ruleManager.buildRulesHaFlowPath(forwardPath, true, false, true, true, dataAdapter);
    }

    private DataAdapter buildDataAdapter(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap) {
        Set<SwitchId> switchIds = haFlowPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(haFlowPath.getSubPathIds());
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false, additionalHaFlowMap);
    }

    private Map<PathId, HaFlow> buildAdditionalHaFlowMap(HaFlow haFlow, HaFlowUpdateFsm stateMachine) {
        HaFlow updatedHaFlow = copyHaFlowWithPathIds(
                haFlow, stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
        Map<PathId, HaFlow> additionalHaFlowMap = buildHaFlowMap(updatedHaFlow,
                stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
        additionalHaFlowMap.putAll(buildHaFlowMap(stateMachine.getOriginalHaFlow(), stateMachine.getOldPrimaryPathIds(),
                stateMachine.getOldProtectedPathIds()));
        return additionalHaFlowMap;
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
