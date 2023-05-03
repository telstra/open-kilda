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

        removeNewRules(stateMachine, haFlow);
        installOldRules(stateMachine, haFlow);

        if (stateMachine.getPendingCommands().isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove new rules or re-install original ingress rule");
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            stateMachine.saveActionToHistory(
                    "Commands for removing new rules and re-installing original ingress rule have been sent");
        }
    }

    private void removeNewRules(HaFlowUpdateFsm stateMachine, HaFlow haFlow) {
        List<SpeakerData> removeCommands = buildPrimaryRemoveCommands(stateMachine, haFlow);
        removeCommands.addAll(buildProtectedRemoveCommands(stateMachine, haFlow));

        stateMachine.getRemoveCommands().clear();
        buildHaFlowDeleteRequests(removeCommands, stateMachine.getCommandContext())
                .forEach(request -> {
                    stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private List<SpeakerData> buildPrimaryRemoveCommands(HaFlowUpdateFsm stateMachine, HaFlow haFlow) {
        List<SpeakerData> removeCommands = new ArrayList<>();
        if (stateMachine.getNewPrimaryPathIds() != null) {
            HaFlowPath newForward = getHaFlowPath(
                    haFlow, stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());
            HaFlowPath newReverse = getHaFlowPath(
                    haFlow, stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId());

            boolean ignoreUnknownSwitches;
            DataAdapter dataAdapter;
            if (stateMachine.getPartialUpdateEndpoints().isEmpty()) {
                ignoreUnknownSwitches = false;
                dataAdapter = buildDataAdapterForNewRulesFullUpdate(newForward, newReverse);
            } else {
                ignoreUnknownSwitches = true;
                dataAdapter = buildDataAdapterForNewRulesWithPartialUpdate(
                        stateMachine.getNewPrimaryPathIds(), stateMachine);
            }
            removeCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newForward, true, ignoreUnknownSwitches, true, true, dataAdapter));
            removeCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newReverse, true, ignoreUnknownSwitches, true, true, dataAdapter));
        }
        return removeCommands;
    }

    private List<SpeakerData> buildProtectedRemoveCommands(HaFlowUpdateFsm stateMachine, HaFlow haFlow) {
        List<SpeakerData> revertCommands = new ArrayList<>();
        if (stateMachine.getNewProtectedPathIds() != null) {
            HaFlowPath newForward = getHaFlowPath(
                    haFlow, stateMachine.getNewProtectedPathIds().getForward().getHaPathId());
            HaFlowPath newReverse = getHaFlowPath(
                    haFlow, stateMachine.getNewProtectedPathIds().getReverse().getHaPathId());

            boolean ignoreUnknownSwitches;
            DataAdapter dataAdapter;
            if (stateMachine.getPartialUpdateEndpoints().isEmpty()) {
                ignoreUnknownSwitches = false;
                dataAdapter = buildDataAdapterForNewRulesFullUpdate(newForward, newReverse);
            } else {
                ignoreUnknownSwitches = true;
                dataAdapter = buildDataAdapterForNewRulesWithPartialUpdate(
                        stateMachine.getNewProtectedPathIds(), stateMachine);
            }

            revertCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newForward, true, ignoreUnknownSwitches, false, true, dataAdapter));
            revertCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    newReverse, true, ignoreUnknownSwitches, false, true, dataAdapter));
        }
        return revertCommands;
    }

    private void installOldRules(HaFlowUpdateFsm stateMachine, HaFlow haFlow) {
        List<SpeakerData> ingressCommands = new ArrayList<>();
        // Reinstall old ingress rules that may be overridden by new ingress.
        if (stateMachine.getOldPrimaryPathIds() != null) {
            HaFlowPath oldForward = getHaFlowPath(haFlow,
                    stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());
            HaFlowPath oldReverse = getHaFlowPath(haFlow,
                    stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());

            DataAdapter dataAdapter = buildDataAdapterForOldRules(stateMachine.getOldPrimaryPathIds(), stateMachine);
            ingressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    oldForward, false, false, true, false, dataAdapter));
            ingressCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    oldReverse, false, false, true, false, dataAdapter));
        }

        stateMachine.getIngressCommands().clear();  // need to clean previous requests
        buildHaFlowInstallRequests(ingressCommands, stateMachine.getCommandContext())
                .forEach(request -> {
                    stateMachine.getIngressCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private DataAdapter buildDataAdapterForOldRules(
            HaPathIdsPair oldPrimaryPathIdsPair, HaFlowUpdateFsm stateMachine) {
        Set<PathId> pathIds = newHashSet(oldPrimaryPathIdsPair.getForward().getSubPathIds().values());
        pathIds.addAll(oldPrimaryPathIdsPair.getReverse().getSubPathIds().values());

        Set<SwitchId> switchIds = stateMachine.getOriginalHaFlow().getEndpointSwitchIds();
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
    }

    private DataAdapter buildDataAdapterForNewRulesWithPartialUpdate(
            HaPathIdsPair newPathIdsPair, HaFlowUpdateFsm stateMachine) {
        Set<PathId> pathIds = newHashSet(newPathIdsPair.getForward().getSubPathIds().values());
        pathIds.addAll(newPathIdsPair.getReverse().getSubPathIds().values());
        for (SwitchId switchId : stateMachine.getPartialUpdateEndpoints()) {
            flowPathRepository.findByEndpointSwitch(switchId, false).stream()
                    .map(FlowPath::getPathId)
                    .forEach(pathIds::add);
        }

        return new PersistenceDataAdapter(persistenceManager, pathIds, stateMachine.getPartialUpdateEndpoints(), false);
    }

    private DataAdapter buildDataAdapterForNewRulesFullUpdate(HaFlowPath newForward, HaFlowPath newReverse) {
        Set<SwitchId> endpointSwitchIds = newForward.getEndpointSwitchIds();
        endpointSwitchIds.addAll(newReverse.getEndpointSwitchIds());

        Set<PathId> pathIds = new HashSet<>();
        for (SwitchId switchId : endpointSwitchIds) {
            flowPathRepository.findByEndpointSwitch(switchId, false).stream()
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
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
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
