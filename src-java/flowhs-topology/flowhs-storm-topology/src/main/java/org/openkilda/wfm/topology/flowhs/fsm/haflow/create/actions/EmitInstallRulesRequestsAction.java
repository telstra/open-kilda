/* Copyright 2021 Telstra Open Source
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
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowRuleManagerProcessingAction;
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
import java.util.stream.Collectors;

@Slf4j
public class EmitInstallRulesRequestsAction extends
        HaFlowRuleManagerProcessingAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private final FlowPathRepository flowPathRepository;

    public EmitInstallRulesRequestsAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
        flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        List<SpeakerData> speakerCommands = buildSpeakerCommands(stateMachine);
        stateMachine.getSentCommands().addAll(speakerCommands);
        Collection<InstallSpeakerCommandsRequest> installRequests = buildHaFlowInstallRequests(
                speakerCommands, stateMachine.getCommandContext());

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

    private DataAdapter buildDataAdapter(HaFlow haFlow) {
        Set<PathId> pathIds = new HashSet<>();
        for (SwitchId switchId : haFlow.getEndpointSwitchIds()) {
            pathIds.addAll(flowPathRepository.findByEndpointSwitch(switchId, false).stream()
                    .map(FlowPath::getPathId)
                    .collect(Collectors.toSet()));
        }

        Set<SwitchId> switchIds = haFlow.getEndpointSwitchIds();
        for (HaFlowPath haFlowPath : haFlow.getPaths()) {
            for (FlowPath subPath : haFlowPath.getSubPaths()) {
                for (PathSegment segment : subPath.getSegments()) {
                    switchIds.add(segment.getSrcSwitchId());
                    switchIds.add(segment.getDestSwitchId());
                }
            }
        }

        return new PersistenceDataAdapter(persistenceManager, pathIds, switchIds, false);
    }

    private List<SpeakerData> buildSpeakerCommands(HaFlowCreateFsm stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getFlowId());

        DataAdapter dataAdapter = buildDataAdapter(haFlow);
        List<SpeakerData> result = new ArrayList<>();
        result.addAll(ruleManager.buildRulesHaFlowPath(haFlow.getForwardPath(), true, dataAdapter));
        result.addAll(ruleManager.buildRulesHaFlowPath(haFlow.getReversePath(), true, dataAdapter));

        if (haFlow.getProtectedForwardPath() != null && haFlow.getProtectedForwardPath() != null) {
            result.addAll(ruleManager.buildRulesHaFlowPath(haFlow.getProtectedForwardPath(), true, dataAdapter));
            result.addAll(ruleManager.buildRulesHaFlowPath(haFlow.getProtectedReversePath(), true, dataAdapter));
        }
        return result;
    }
}
