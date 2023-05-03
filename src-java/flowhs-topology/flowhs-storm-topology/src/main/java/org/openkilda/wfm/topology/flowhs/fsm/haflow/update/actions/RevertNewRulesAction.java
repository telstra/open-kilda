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
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RevertNewRulesAction extends HaFlowRuleManagerProcessingAction<
        HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public RevertNewRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();
        HaFlow haFlow = getHaFlow(haFlowId);

        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        removeNewRules(haFlow, stateMachine);
        installOldRules(stateMachine);

        if (stateMachine.getPendingCommands().isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove new rules or re-install original ingress rule");
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            stateMachine.saveActionToHistory(
                    "Commands for removing new rules and re-installing original ingress rule have been sent");
        }
    }

    private void removeNewRules(HaFlow haFlow, HaFlowUpdateFsm stateMachine) {
        List<SpeakerData> removeCommands = buildPrimaryRemoveCommands(haFlow, stateMachine);
        removeCommands.addAll(buildProtectedRemoveCommands(haFlow, stateMachine));

        stateMachine.getRemoveCommands().clear();
        buildHaFlowDeleteRequests(removeCommands, stateMachine.getCommandContext())
                .forEach(request -> {
                    stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private List<SpeakerData> buildPrimaryRemoveCommands(HaFlow haFlow, HaFlowUpdateFsm stateMachine) {
        List<SpeakerData> removeCommands = new ArrayList<>();
        if (stateMachine.getNewPrimaryPathIds() != null) {
            HaFlowPath newForward = getHaFlowPath(
                    haFlow, stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());
            HaFlowPath newReverse = getHaFlowPath(
                    haFlow, stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId());

            DataAdapter dataAdapter = buildDataAdapterForNewRulesFullUpdate(
                    haFlow, newForward, newReverse, stateMachine);
            removeCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newForward, true, false, true, true, dataAdapter));
            removeCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newReverse, true, false, true, true, dataAdapter));
        }
        return removeCommands;
    }

    private List<SpeakerData> buildProtectedRemoveCommands(HaFlow haFlow, HaFlowUpdateFsm stateMachine) {
        List<SpeakerData> revertCommands = new ArrayList<>();
        if (stateMachine.getNewProtectedPathIds() != null) {
            HaFlowPath newForward = getHaFlowPath(
                    haFlow, stateMachine.getNewProtectedPathIds().getForward().getHaPathId());
            HaFlowPath newReverse = getHaFlowPath(
                    haFlow, stateMachine.getNewProtectedPathIds().getReverse().getHaPathId());

            DataAdapter dataAdapter = buildDataAdapterForNewRulesFullUpdate(
                    haFlow, newForward, newReverse, stateMachine);

            revertCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newForward, true, false, false, true, dataAdapter));
            revertCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newReverse, true, false, false, true, dataAdapter));
        }
        return revertCommands;
    }

    private void installOldRules(HaFlowUpdateFsm stateMachine) {
        List<SpeakerData> ingressCommands = new ArrayList<>();
        // Reinstall old ingress rules that may be overridden by new ingress.

        DataAdapter dataAdapter = buildDataAdapterForOldRules(stateMachine.getOldPrimaryPathIds(), stateMachine);
        HaFlowPath oldForward = stateMachine.getOriginalHaFlow().getForwardPath();
        if (oldForward != null) {
            ingressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    oldForward, false, false, true, false, dataAdapter));
        }
        HaFlowPath oldReverse = stateMachine.getOriginalHaFlow().getReversePath();
        if (oldReverse != null) {
            ingressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    oldReverse, false, false, true, false, dataAdapter));
        }

        stateMachine.getIngressCommands().clear();  // need to clean previous requests
        buildHaFlowInstallRequests(ingressCommands, stateMachine.getCommandContext(), false)
                .forEach(request -> {
                    stateMachine.getIngressCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private DataAdapter buildDataAdapterForOldRules(
            HaPathIdsPair oldPrimaryPathIdsPair, HaFlowUpdateFsm stateMachine) {
        Set<PathId> pathIds = new HashSet<>(oldPrimaryPathIdsPair.getAllSubPathIds());

        Set<SwitchId> switchIds = stateMachine.getOriginalHaFlow().getEndpointSwitchIds();
        Map<PathId, HaFlow> additionalHaFlowMap = buildHaFlowMap(stateMachine.getOriginalHaFlow(),
                stateMachine.getOldPrimaryPathIds(), stateMachine.getOldProtectedPathIds());

        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false, additionalHaFlowMap);
    }

    private DataAdapter buildDataAdapterForNewRulesFullUpdate(
            HaFlow haFlow, HaFlowPath newForward, HaFlowPath newReverse, HaFlowUpdateFsm stateMachine) {
        Set<SwitchId> endpointSwitchIds = newForward.getEndpointSwitchIds();
        endpointSwitchIds.addAll(newReverse.getEndpointSwitchIds());

        Set<PathId> pathIds = new HashSet<>();
        for (SwitchId switchId : endpointSwitchIds) {
            flowPathRepository.findBySrcSwitch(switchId, false).stream()
                    .map(FlowPath::getPathId)
                    .forEach(pathIds::add);
        }

        List<FlowPath> subPaths = new ArrayList<>(newForward.getSubPaths());
        subPaths.addAll(newReverse.getSubPaths());

        Set<SwitchId> switchIds = new HashSet<>(endpointSwitchIds);

        for (FlowPath subPath : subPaths) {
            switchIds.addAll(getSubPathSwitchIds(subPath));
            pathIds.add(subPath.getPathId());
        }

        HaFlow updatedHaFlow = copyHaFlowWithPathIds(
                haFlow, stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
        Map<PathId, HaFlow> additionalHaFlowMap = buildHaFlowMap(updatedHaFlow,
                stateMachine.getNewPrimaryPathIds(), stateMachine.getNewProtectedPathIds());
        additionalHaFlowMap.putAll(buildHaFlowMap(stateMachine.getOriginalHaFlow(), stateMachine.getOldPrimaryPathIds(),
                stateMachine.getOldProtectedPathIds()));

        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false, additionalHaFlowMap);
    }

    private Set<SwitchId> getSubPathSwitchIds(FlowPath subPath) {
        Set<SwitchId> switchIds = new HashSet<>();
        for (PathSegment segment : subPath.getSegments()) {
            switchIds.add(segment.getSrcSwitchId());
            switchIds.add(segment.getDestSwitchId());
        }
        return switchIds;
    }
}
