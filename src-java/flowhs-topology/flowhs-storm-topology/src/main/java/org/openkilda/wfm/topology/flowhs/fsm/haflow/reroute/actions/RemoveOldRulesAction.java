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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

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
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class RemoveOldRulesAction extends
        HaFlowRuleManagerProcessingAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {

    public RemoveOldRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @TimedExecution("fsm.remove_old_rules")
    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        HaFlow originalHaFlow = stateMachine.getOriginalHaFlow();
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(originalHaFlow);
        Map<PathId, HaFlow> additionalHaFlowMap = buildHaFlowMap(
                originalHaFlow, stateMachine.getOldPrimaryPathIds(), stateMachine.getOldProtectedPathIds());

        List<SpeakerData> commands = new ArrayList<>();
        if (stateMachine.getOldPrimaryPathIds() != null) {
            List<HaFlowPath> primaryPaths = new ArrayList<>();
            Optional.ofNullable(originalHaFlow.getForwardPath()).ifPresent(primaryPaths::add);
            Optional.ofNullable(originalHaFlow.getReversePath()).ifPresent(primaryPaths::add);
            for (HaFlowPath primaryPath : primaryPaths) {
                commands.addAll(buildPrimaryRules(primaryPath, overlappingPathIds, additionalHaFlowMap, stateMachine));
            }
        }


        if (stateMachine.getOldProtectedPathIds() != null) {
            List<HaFlowPath> protectedPaths = new ArrayList<>();
            Optional.ofNullable(originalHaFlow.getProtectedForwardPath()).ifPresent(protectedPaths::add);
            Optional.ofNullable(originalHaFlow.getProtectedReversePath()).ifPresent(protectedPaths::add);

            for (HaFlowPath protectedPath : protectedPaths) {
                commands.addAll(buildProtectedRules(
                        protectedPath, overlappingPathIds, additionalHaFlowMap, stateMachine));
            }
        }

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        if (commands.isEmpty()) {
            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("No need to remove old rules")
                    .withHaFlowId(stateMachine.getHaFlowId()));
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            Collection<DeleteSpeakerCommandsRequest> deleteRequests = buildHaFlowDeleteRequests(
                    commands, stateMachine.getCommandContext());

            for (DeleteSpeakerCommandsRequest request : deleteRequests) {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            }
            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("Remove commands for old rules have been sent")
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
    }

    private List<SpeakerData> buildPrimaryRules(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap,
            HaFlowRerouteFsm stateMachine) {
        DataAdapter dataAdapter = buildDataAdapter(haFlowPath, overlappingPathIds, additionalHaFlowMap, stateMachine);
        return ruleManager.buildRulesHaFlowPath(
                haFlowPath, true, false, true, true, dataAdapter);
    }

    private List<SpeakerData> buildProtectedRules(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap,
            HaFlowRerouteFsm stateMachine) {
        DataAdapter dataAdapter = buildDataAdapter(haFlowPath, overlappingPathIds, additionalHaFlowMap, stateMachine);
        return ruleManager.buildRulesHaFlowPath(
                haFlowPath, true, false, false, true, dataAdapter);
    }

    private DataAdapter buildDataAdapter(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap,
            HaFlowRerouteFsm stateMachine) {
        Set<SwitchId> switchIds = haFlowPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(haFlowPath.getSubPathIds());
        if (stateMachine.getNewPrimaryPathIds() != null) {
            pathIds.addAll(stateMachine.getNewPrimaryPathIds().getAllSubPathIds());
        }
        return new PersistenceDataAdapter(
                persistenceManager, pathIds, switchIds, false, additionalHaFlowMap);
    }
}
