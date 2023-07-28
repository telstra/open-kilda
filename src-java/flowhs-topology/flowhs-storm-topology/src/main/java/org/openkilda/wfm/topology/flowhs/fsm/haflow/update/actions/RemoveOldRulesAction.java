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

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
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
public class RemoveOldRulesAction extends
        HaFlowRuleManagerProcessingAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public RemoveOldRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        HaFlow originalHaFlow = stateMachine.getOriginalHaFlow();
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(originalHaFlow);
        Map<PathId, HaFlow> additionalHaFlowMap = buildHaFlowMap(
                originalHaFlow, stateMachine.getOldPrimaryPathIds(), stateMachine.getOldProtectedPathIds());

        List<SpeakerData> commands = new ArrayList<>();
        List<HaFlowPath> primaryPaths = newArrayList(
                getHaFlowPath(originalHaFlow, originalHaFlow.getForwardPathId()),
                getHaFlowPath(originalHaFlow, originalHaFlow.getReversePathId()));
        for (HaFlowPath primaryPath : primaryPaths) {
            commands.addAll(buildPrimaryRules(primaryPath, overlappingPathIds, additionalHaFlowMap, stateMachine));
        }

        if (originalHaFlow.isAllocateProtectedPath()) {
            List<HaFlowPath> protectedPaths = newArrayList(
                    getHaFlowPath(originalHaFlow, originalHaFlow.getProtectedForwardPathId()),
                    getHaFlowPath(originalHaFlow, originalHaFlow.getProtectedReversePathId()));
            for (HaFlowPath protectedPath : protectedPaths) {
                commands.addAll(buildProtectedRules(
                        protectedPath, overlappingPathIds, additionalHaFlowMap, stateMachine));
            }
        }

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove old rules");
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            Collection<DeleteSpeakerCommandsRequest> deleteRequests = buildHaFlowDeleteRequests(
                    commands, stateMachine.getCommandContext());

            for (DeleteSpeakerCommandsRequest request : deleteRequests) {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            }
            stateMachine.saveActionToHistory("Commands for removing old rules have been sent");
        }
    }

    private List<SpeakerData> buildPrimaryRules(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap,
            HaFlowUpdateFsm stateMachine) {
        DataAdapter dataAdapter = buildDataAdapter(haFlowPath, overlappingPathIds, additionalHaFlowMap, stateMachine);
        return ruleManager.buildRulesHaFlowPath(
                haFlowPath, true, false, true, true, dataAdapter);
    }

    private List<SpeakerData> buildProtectedRules(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap,
            HaFlowUpdateFsm stateMachine) {
        DataAdapter dataAdapter = buildDataAdapter(haFlowPath, overlappingPathIds, additionalHaFlowMap, stateMachine);
        return ruleManager.buildRulesHaFlowPath(
                haFlowPath, true, false, false, true, dataAdapter);
    }

    private DataAdapter buildDataAdapter(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap,
            HaFlowUpdateFsm stateMachine) {
        Set<SwitchId> switchIds = haFlowPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(haFlowPath.getSubPathIds());
        pathIds.addAll(stateMachine.getNewPrimaryPathIds().getAllSubPathIds());
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, additionalHaFlowMap);
    }
}
