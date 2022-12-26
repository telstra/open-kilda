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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.FlowRulesConverter;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Slf4j
public class EmitCommandRequestsAction extends
        FlowProcessingWithHistorySupportAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final RuleManager ruleManager;

    public EmitCommandRequestsAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
        this.ruleManager = ruleManager;
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowMirrorPointDeleteContext context, FlowMirrorPointDeleteFsm stateMachine) {
        PathId flowPathId = stateMachine.getFlowPathId();
        SwitchId mirrorSwitchId = stateMachine.getMirrorSwitchId();

        FlowMirrorPoints mirrorPoints = flowMirrorPointsRepository.findByPathIdAndSwitchId(flowPathId, mirrorSwitchId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow mirror points for flow path %s and mirror switch id %s not found",
                                flowPathId, mirrorSwitchId)));

        List<SpeakerData> installSpeakerCommands = new ArrayList<>();
        List<SpeakerData> modifySpeakerCommands = new ArrayList<>();
        List<SpeakerData> deleteSpeakerCommands = new ArrayList<>();

        if (mirrorPoints.getFlowMirrors().isEmpty()) {
            deleteSpeakerCommands.addAll(stateMachine.getMirrorPointSpeakerData());
        } else {
            for (SpeakerData command : buildSpeakerCommands(stateMachine, mirrorPoints)) {
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
            stateMachine.saveActionToHistory("No need to remove group");
            return;
        } else {
            for (BaseSpeakerCommandsRequest command : speakerCommands) {
                stateMachine.getCarrier().sendSpeakerRequest(command);
                stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
                stateMachine.addSpeakerCommand(command.getCommandId(), command);
            }
            stateMachine.saveActionToHistory("Commands for removing group have been sent");
        }
    }

    private List<SpeakerData> buildSpeakerCommands(
            FlowMirrorPointDeleteFsm stateMachine, FlowMirrorPoints mirrorPoints) {
        Flow flow = getFlow(stateMachine.getFlowId());
        PathId flowPathId = stateMachine.getFlowPathId();
        FlowPath path = flow.getPath(flowPathId).orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                format("Flow path %s not found", flowPathId)));
        Set<PathId> involvedPaths = newHashSet(stateMachine.getFlowPathId());
        getFlow(stateMachine.getFlowId()).getOppositePathId(path.getPathId()).ifPresent(involvedPaths::add);
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, involvedPaths,
                newHashSet(stateMachine.getMirrorSwitchId()), false);

        return ruleManager.buildMirrorPointRules(mirrorPoints, dataAdapter);
    }
}
