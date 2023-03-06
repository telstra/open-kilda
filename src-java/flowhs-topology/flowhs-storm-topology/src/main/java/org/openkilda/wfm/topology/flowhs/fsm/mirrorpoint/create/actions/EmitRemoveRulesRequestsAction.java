/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static com.google.common.collect.Sets.newHashSet;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.FlowRulesConverter;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class EmitRemoveRulesRequestsAction extends
        FlowProcessingWithHistorySupportAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final RuleManager ruleManager;

    public EmitRemoveRulesRequestsAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.ruleManager = ruleManager;
        flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowMirrorPointCreateContext context, FlowMirrorPointCreateFsm stateMachine) {
        stateMachine.clearSpeakerCommands();
        stateMachine.clearPendingCommands();
        stateMachine.clearFailedCommands();
        stateMachine.clearRetriedCommands();

        Optional<FlowMirrorPoints> mirrorPoint = flowMirrorPointsRepository.findByPathIdAndSwitchId(
                stateMachine.getFlowPathId(), stateMachine.getMirrorSwitchId());

        List<SpeakerData> installSpeakerCommands = new ArrayList<>();
        List<SpeakerData> modifySpeakerCommands = new ArrayList<>();
        List<SpeakerData> deleteSpeakerCommands = new ArrayList<>();

        if (!mirrorPoint.isPresent() || mirrorPoint.get().getMirrorPaths().isEmpty()) {
            deleteSpeakerCommands.addAll(stateMachine.getRevertCommands());
        } else {
            for (SpeakerData command : buildSpeakerCommands(stateMachine, mirrorPoint.get())) {
                if (command instanceof GroupSpeakerData
                        && command.getSwitchId().equals(stateMachine.getMirrorSwitchId())) {
                    modifySpeakerCommands.add(command);
                } else {
                    installSpeakerCommands.add(command);
                }
            }
        }

        // emitting
        Collection<BaseSpeakerCommandsRequest> speakerCommands = new ArrayList<>(FlowRulesConverter.INSTANCE
                .buildFlowInstallCommands(installSpeakerCommands, stateMachine.getCommandContext()));
        speakerCommands.addAll(FlowRulesConverter.INSTANCE.buildFlowModifyCommands(
                modifySpeakerCommands, stateMachine.getCommandContext()));
        speakerCommands.addAll(FlowRulesConverter.INSTANCE.buildFlowDeleteCommands(
                deleteSpeakerCommands, stateMachine.getCommandContext()));

        if (speakerCommands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to revert rules");
        } else {
            for (BaseSpeakerCommandsRequest command : speakerCommands) {
                stateMachine.getCarrier().sendSpeakerRequest(command);
                stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
                stateMachine.addSpeakerCommand(command.getCommandId(), command);
            }
            stateMachine.saveActionToHistory("Commands for reverting rules have been sent");
        }
    }

    private List<SpeakerData> buildSpeakerCommands(
            FlowMirrorPointCreateFsm stateMachine, FlowMirrorPoints mirrorPoints) {
        PathId flowPathId = stateMachine.getFlowPathId();
        Set<PathId> involvedPaths = newHashSet(stateMachine.getMirrorPathId());
        getFlow(stateMachine.getFlowId()).getOppositePathId(flowPathId).ifPresent(involvedPaths::add);
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, involvedPaths,
                newHashSet(stateMachine.getMirrorSwitchId()), false);

        return ruleManager.buildMirrorPointRules(mirrorPoints, dataAdapter);
    }
}
