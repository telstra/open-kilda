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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
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
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class EmitInstallRulesRequestsAction extends
        HaFlowRuleManagerProcessingAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {

    public EmitInstallRulesRequestsAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        List<SpeakerData> speakerCommands = buildSpeakerCommands(stateMachine);
        stateMachine.getSentCommands().addAll(speakerCommands);
        Collection<InstallSpeakerCommandsRequest> installRequests = buildHaFlowInstallRequests(
                speakerCommands, stateMachine.getCommandContext(), true);

        if (installRequests.isEmpty()) {
            stateMachine.saveActionToHistory("No requests to install ha-flow rules");
            stateMachine.fire(Event.SKIP_RULES_INSTALL);
        } else {
            // emitting
            installRequests.forEach(request -> {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.addSpeakerCommand(request.getCommandId(), request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            });

            stateMachine.saveActionToHistory("Commands for installing ha-flow rules have been sent");
        }
    }

    private List<SpeakerData> buildRules(HaFlowPath haPath, Set<PathId> overlappingPathIds) {
        Set<SwitchId> switchIds = haPath.getAllInvolvedSwitches();
        Set<PathId> pathIds = new HashSet<>(overlappingPathIds);
        pathIds.addAll(haPath.getSubPathIds());
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
        return ruleManager.buildRulesHaFlowPath(haPath, true, dataAdapter);
    }

    private List<SpeakerData> buildSpeakerCommands(HaFlowCreateFsm stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getFlowId());
        Set<PathId> overlappingPathIds = getPathIdsWhichCanUseSharedRules(haFlow);

        List<SpeakerData> result = new ArrayList<>();
        result.addAll(buildRules(haFlow.getForwardPath(), overlappingPathIds));
        result.addAll(buildRules(haFlow.getReversePath(), overlappingPathIds));

        if (haFlow.getProtectedForwardPath() != null && haFlow.getProtectedForwardPath() != null) {
            result.addAll(buildRules(haFlow.getProtectedForwardPath(), overlappingPathIds));
            result.addAll(buildRules(haFlow.getProtectedReversePath(), overlappingPathIds));
        }
        return result;
    }
}
