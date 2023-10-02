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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions;

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
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class RevertNewRulesAction extends HaFlowRuleManagerProcessingAction<
        HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> {

    public RevertNewRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, HaFlowPathSwapContext context,
                           HaFlowPathSwapFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();
        HaFlow haFlow = getHaFlow(haFlowId);
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(haFlow);

        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        installOldRules(stateMachine, haFlow, overlappingPathIds);
        removeNewRules(stateMachine, haFlow, overlappingPathIds);

        FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .of(stateMachine.getCommandContext().getCorrelationId())
                .withAction("Commands for removing new rules and re-installing original ingress rule have been sent")
                .withHaFlowId(stateMachine.getHaFlowId()));
    }

    private void installOldRules(HaFlowPathSwapFsm stateMachine, HaFlow haFlow, Set<PathId> overlappingPathIds) {
        List<SpeakerData> installCommands = new ArrayList<>();
        for (HaFlowPath primaryPath : haFlow.getPrimaryPaths()) {
            DataAdapter dataAdapter = buildDataAdapter(primaryPath, primaryPath.getSubPathIds(), overlappingPathIds);
            installCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    primaryPath, false, false, true, false, dataAdapter));
        }
        stateMachine.getIngressCommands().clear();
        buildHaFlowInstallRequests(installCommands, stateMachine.getCommandContext(), false)
                .forEach(request -> {
                    stateMachine.getIngressCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private void removeNewRules(HaFlowPathSwapFsm stateMachine, HaFlow haFlow, Set<PathId> overlappingPathIds) {
        Set<PathId> subPathIds = new HashSet<>(haFlow.getPrimarySubPathIds());
        subPathIds.addAll(haFlow.getProtectedSubPathIds());

        List<SpeakerData> removeCommands = new ArrayList<>();
        for (HaFlowPath protectedPath : haFlow.getProtectedPaths()) {
            Set<PathId> pathIds = protectedPath.getSubPathIds();
            // Primary path IDs will be used in rule manager to indicate that shared rules are used by them
            pathIds.addAll(haFlow.getPrimarySubPathIds());
            DataAdapter dataAdapter = buildDataAdapter(protectedPath, pathIds, subPathIds);
            removeCommands.addAll(ruleManager.buildRulesHaFlowPath(
                    protectedPath, true, false, true, false, dataAdapter));
        }

        stateMachine.getRemoveCommands().clear();
        buildHaFlowDeleteRequests(removeCommands, stateMachine.getCommandContext())
                .forEach(request -> {
                    stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                    stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                    stateMachine.getCarrier().sendSpeakerRequest(request);
                });
    }

    private DataAdapter buildDataAdapter(
            HaFlowPath haPath, Collection<PathId> targetPathIds, Collection<PathId> overlappingPathIds) {
        Set<SwitchId> switchIds = haPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(targetPathIds);
        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
    }
}
