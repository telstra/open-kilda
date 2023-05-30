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

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class RemoveOldRulesAction extends HaFlowRuleManagerProcessingAction<
        HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> {

    public RemoveOldRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, HaFlowPathSwapContext context,
                           HaFlowPathSwapFsm stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(haFlow);

        List<SpeakerData> commands = new ArrayList<>();
        for (HaFlowPath protectedPath : haFlow.getProtectedPaths()) {
            commands.addAll(buildRules(haFlow, protectedPath, overlappingPathIds));
        }

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove old rules");
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            stateMachine.clearPendingAndRetriedAndFailedCommands();
            buildHaFlowDeleteRequests(commands, stateMachine.getCommandContext())
                    .forEach(request -> {
                        stateMachine.getRemoveCommands().put(request.getCommandId(), request);
                        stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
                        stateMachine.getCarrier().sendSpeakerRequest(request);
                    });
            stateMachine.saveActionToHistory("Remove commands for old rules have been sent");
        }
    }

    private List<SpeakerData> buildRules(HaFlow haFlow, HaFlowPath haPath, Set<PathId> overlappingPathIds) {
        Set<SwitchId> switchIds = haPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(haPath.getSubPathIds());
        pathIds.addAll(haFlow.getPrimarySubPathIds());
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
        return ruleManager.buildRulesHaFlowPath(
                haPath, true, false, true, false, dataAdapter);
    }
}
