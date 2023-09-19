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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions;

import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class EmitSyncRuleRequestsAction extends HaFlowRuleManagerProcessingAction<
        HaFlowSyncFsm, State, Event, HaFlowSyncContext> {

    public EmitSyncRuleRequestsAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, HaFlowSyncContext context, HaFlowSyncFsm stateMachine) {
        Collection<InstallSpeakerCommandsRequest> installRequests = buildSpeakerRequests(stateMachine);

        if (installRequests.isEmpty()) {
            stateMachine.saveActionToHistory("No requests to sync HA-flow rules");
            stateMachine.fire(Event.SKIP_RULES_SYNC);
        } else {
            // emitting
            installRequests.forEach(request -> {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.addSpeakerCommand(request.getCommandId(), request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            });

            stateMachine.saveActionToHistory("Commands for syncing HA-flow rules have been sent");
        }
    }

    private List<SpeakerData> buildRules(HaFlowPath haPath) {
        Set<SwitchId> switchIds = haPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(haPath.getSubPathIds());
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
        return ruleManager.buildRulesHaFlowPath(haPath, true, dataAdapter);
    }

    private Collection<HaFlowPath> getPaths(String haFlowId) {
        HaFlow haFlow = getHaFlow(haFlowId);
        Collection<HaFlowPath> paths = haFlow.getPrimaryPaths();

        if (haFlow.getProtectedForwardPath() != null && haFlow.getProtectedReversePath() != null) {
            paths.add(haFlow.getProtectedForwardPath());
            paths.add(haFlow.getProtectedReversePath());
        }
        return paths;
    }

    private Collection<InstallSpeakerCommandsRequest> buildSpeakerRequests(HaFlowSyncFsm stateMachine) {
        Collection<InstallSpeakerCommandsRequest> result = new ArrayList<>();
        for (HaFlowPath haFlowPath : getPaths(stateMachine.getHaFlowId())) {
            List<SpeakerData> speakerCommands = buildRules(haFlowPath);
            Collection<InstallSpeakerCommandsRequest> installRequests = buildHaFlowInstallRequests(
                    speakerCommands, stateMachine.getCommandContext(), false);

            for (InstallSpeakerCommandsRequest installRequest : installRequests) {
                result.add(installRequest);
                Map<SwitchId, Set<PathId>> subPathMap = buildSubPathBySwitchIdMap(haFlowPath.getSubPaths());

                stateMachine.putPathRequest(installRequest.getCommandId(), haFlowPath.getHaPathId());
                for (PathId subPathId : subPathMap.get(installRequest.getSwitchId())) {
                    stateMachine.putPathRequest(installRequest.getCommandId(), subPathId);
                }
            }
        }
        return result;
    }

    private Map<SwitchId, Set<PathId>> buildSubPathBySwitchIdMap(Collection<FlowPath> subPaths) {
        Map<SwitchId, Set<PathId>> map = new HashMap<>();

        for (FlowPath subPath : subPaths) {
            putPathId(subPath.getSrcSwitchId(), subPath.getPathId(), map);
            putPathId(subPath.getDestSwitchId(), subPath.getPathId(), map);
            for (PathSegment segment : subPath.getSegments()) {
                putPathId(segment.getSrcSwitchId(), subPath.getPathId(), map);
            }
        }
        return map;
    }

    private void putPathId(SwitchId switchId, PathId pathId, Map<SwitchId, Set<PathId>> map) {
        map.putIfAbsent(switchId, new HashSet<>());
        map.get(switchId).add(pathId);
    }
}

