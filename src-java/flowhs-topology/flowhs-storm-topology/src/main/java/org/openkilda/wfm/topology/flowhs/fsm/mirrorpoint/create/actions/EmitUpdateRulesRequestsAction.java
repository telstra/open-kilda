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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
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
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Slf4j
public class EmitUpdateRulesRequestsAction extends
        FlowProcessingWithHistorySupportAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final RuleManager ruleManager;

    public EmitUpdateRulesRequestsAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.ruleManager = ruleManager;
        flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowMirrorPointCreateContext context, FlowMirrorPointCreateFsm stateMachine) {

        List<SpeakerData> installSpeakerCommands = new ArrayList<>();
        List<SpeakerData> modifySpeakerCommands = new ArrayList<>();

        if (stateMachine.isAddNewGroup()) {
            installSpeakerCommands.addAll(buildSpeakerCommands(stateMachine));
        } else {
            for (SpeakerData command : buildSpeakerCommands(stateMachine)) {
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

        if (speakerCommands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to update rules");
        } else {
            for (BaseSpeakerCommandsRequest command : speakerCommands) {
                stateMachine.getCarrier().sendSpeakerRequest(command);
                stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
                stateMachine.addSpeakerCommand(command.getCommandId(), command);
            }
            stateMachine.saveActionToHistory("Commands for updating rules have been sent");
            stateMachine.setRulesInstalled(true);
        }
    }

    private List<SpeakerData> buildSpeakerCommands(FlowMirrorPointCreateFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        PathId flowPathId = stateMachine.getFlowPathId();
        FlowPath path = flow.getPath(flowPathId).orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                format("Flow path %s not found", flowPathId)));
        PathId oppositePathId = flow.getOppositePathId(flowPathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Opposite flow path id for path %s not found", flowPathId)));
        FlowMirrorPoints mirrorPoint = flowMirrorPointsRepository.findByPathIdAndSwitchId(
                flowPathId, stateMachine.getMirrorSwitchId()).orElseThrow(
                        () -> new FlowProcessingException(ErrorType.NOT_FOUND, format(
                                "Flow mirror point for flow path %s and mirror switchId %s not found",
                                flowPathId, stateMachine.getMirrorSwitchId())));

        Set<PathId> involvedPaths = newHashSet(path.getPathId(), oppositePathId);
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, involvedPaths, newHashSet(),
                newHashSet(stateMachine.getMirrorSwitchId()), false);

        return ruleManager.buildMirrorPointRules(mirrorPoint, dataAdapter);
    }
}
