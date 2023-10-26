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

import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RevertNewRulesAction
        extends HaFlowRuleManagerProcessingAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    public RevertNewRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();
        HaFlow haFlow = getHaFlow(haFlowId);

        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        installOldRules(haFlow, stateMachine);
        removeNewRules(haFlow, stateMachine);

        if (stateMachine.getPendingCommands().isEmpty()) {
            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("No need to remove new rules or re-install original ingress rule")
                    .withHaFlowId(stateMachine.getHaFlowId()));
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction(
                            "Commands for removing new rules and re-installing original ingress rule have been sent")
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
    }

    private void installOldRules(HaFlow haFlow, HaFlowRerouteFsm stateMachine) {
        // Reinstall old ingress rules that may be overridden by new ingress.
        List<SpeakerData> ingressCommands = new ArrayList<>();

        if (stateMachine.getOldPrimaryPathIds() != null) {
            List<HaFlowPath> oldPaths = getHaFlowPaths(haFlow, stateMachine.getOldPrimaryPathIds());
            for (HaFlowPath oldPath : oldPaths) {
                DataAdapter dataAdapter = buildDataAdapterForOldRules(oldPath);
                ingressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                        oldPath, false, false, true, false, dataAdapter));
            }
        }

        stateMachine.getIngressCommands().clear();  // need to clean previous requests
        buildHaFlowInstallRequests(ingressCommands, stateMachine.getCommandContext(), false)
                .forEach(request -> {
                    stateMachine.getIngressCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private List<HaFlowPath> getHaFlowPaths(HaFlow haFlow, HaPathIdsPair haFlowPathIds) {
        List<HaFlowPath> paths = new ArrayList<>();
        for (PathId haFlowPathId : haFlowPathIds.getAllHaFlowPathIds()) {
            paths.add(getHaFlowPath(haFlow, haFlowPathId));
        }
        return paths;
    }

    private void removeNewRules(HaFlow haFlow, HaFlowRerouteFsm stateMachine) {
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(haFlow);
        Map<PathId, HaFlow> haFlowMap = buildHaFlowMapForNewPaths(haFlow, stateMachine);

        List<SpeakerData> removeCommands = buildPrimaryRemoveCommands(
                haFlow, overlappingPathIds, haFlowMap, stateMachine);
        removeCommands.addAll(buildProtectedRemoveCommands(haFlow, overlappingPathIds, haFlowMap, stateMachine));

        stateMachine.getRemoveCommands().clear();
        buildHaFlowDeleteRequests(removeCommands, stateMachine.getCommandContext())
                .forEach(request -> {
                    stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private List<SpeakerData> buildPrimaryRemoveCommands(
            HaFlow haFlow, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> haFlowMap,
            HaFlowRerouteFsm stateMachine) {
        List<SpeakerData> removeCommands = new ArrayList<>();
        if (stateMachine.getNewPrimaryPathIds() != null) {
            List<HaFlowPath> primaryPaths = getHaFlowPaths(haFlow, stateMachine.getNewPrimaryPathIds());
            for (HaFlowPath primaryPath : primaryPaths) {
                DataAdapter dataAdapter = buildDataAdapterForNewRules(
                        primaryPath, overlappingPathIds, haFlowMap, stateMachine);
                removeCommands.addAll(ruleManager.buildRulesHaFlowPath(
                        primaryPath, true, false, true, true, dataAdapter));
            }
        }
        return removeCommands;
    }

    private List<SpeakerData> buildProtectedRemoveCommands(
            HaFlow haFlow, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> haFlowMap,
            HaFlowRerouteFsm stateMachine) {
        List<SpeakerData> revertCommands = new ArrayList<>();
        if (stateMachine.getNewProtectedPathIds() != null) {
            List<HaFlowPath> protectedPaths = getHaFlowPaths(haFlow, stateMachine.getNewProtectedPathIds());
            for (HaFlowPath protectedPath : protectedPaths) {
                DataAdapter dataAdapter = buildDataAdapterForNewRules(
                        protectedPath, overlappingPathIds, haFlowMap, stateMachine);
                revertCommands.addAll(ruleManager.buildRulesHaFlowPath(
                        protectedPath, true, false, false, true, dataAdapter));
            }
        }
        return revertCommands;
    }


    private DataAdapter buildDataAdapterForOldRules(HaFlowPath haFlowPath) {
        Set<PathId> pathIds = new HashSet<>(haFlowPath.getSubPathIds());
        Set<SwitchId> switchIds = haFlowPath.getEndpointSwitchIds();
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds);
    }

    private DataAdapter buildDataAdapterForNewRules(
            HaFlowPath haFlowPath, Set<PathId> overlappingPathIds, Map<PathId, HaFlow> additionalHaFlowMap,
            HaFlowRerouteFsm stateMachine) {
        Set<SwitchId> switchIds = haFlowPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(haFlowPath.getSubPathIds());
        pathIds.addAll(stateMachine.getOldPrimaryPathIds().getAllSubPathIds());
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, additionalHaFlowMap);
    }

    private Map<PathId, HaFlow> buildHaFlowMapForNewPaths(HaFlow haFlow, HaFlowRerouteFsm stateMachine) {
        HaFlow updatedHaFlow = copyHaFlowWithPathIds(
                haFlow, stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
        if (stateMachine.getNewEncapsulationType() != null) {
            updatedHaFlow.setEncapsulationType(stateMachine.getNewEncapsulationType());
        }
        return buildHaFlowMap(updatedHaFlow,
                stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
    }
}
