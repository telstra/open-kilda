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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class InstallIngressRulesAction extends
        HaFlowRuleManagerProcessingAction<HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> {

    public InstallIngressRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, HaFlowPathSwapContext context,
                           HaFlowPathSwapFsm stateMachine) {
        String haFlowId = stateMachine.getFlowId();
        HaFlow haFlow = getHaFlow(haFlowId);
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(haFlow);

        List<SpeakerData> commands = new ArrayList<>();
        for (HaFlowPath haFlowPath : haFlow.getPrimaryPaths()) {
            commands.addAll(buildRules(haFlowPath, overlappingPathIds));
        }

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to install new rules");
            stateMachine.fire(Event.RULES_INSTALLED);
        } else {
            stateMachine.clearPendingAndRetriedAndFailedCommands();
            buildHaFlowInstallRequests(commands, stateMachine.getCommandContext(), false)
                    .forEach(request -> {
                        stateMachine.getIngressCommands().put(request.getCommandId(), request);
                        stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                        stateMachine.getCarrier().sendSpeakerRequest(request);
                    });
            saveActionToHistory(stateMachine);
        }
    }

    private List<SpeakerData> buildRules(HaFlowPath haPath, Set<PathId> overlappingPathIds) {
        Set<SwitchId> switchIds = haPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(haPath.getSubPathIds());
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, pathIds, switchIds);
        return ruleManager.buildRulesHaFlowPath(haPath, false, false, true, false, dataAdapter);
    }

    private void saveActionToHistory(HaFlowPathSwapFsm stateMachine) {
        FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .of(stateMachine.getCommandContext().getCorrelationId())
                .withAction("Commands for installing ingress rules have been sent")
                .withHaFlowId(stateMachine.getHaFlowId()));
    }
}
