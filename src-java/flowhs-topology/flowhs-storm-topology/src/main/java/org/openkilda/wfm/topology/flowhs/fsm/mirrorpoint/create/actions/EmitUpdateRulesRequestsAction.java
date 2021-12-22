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

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.model.MirrorContext;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlowMirrorPoint;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerInstallSegmentEmitter;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerRemoveSegmentEmitter;
import org.openkilda.wfm.topology.flowhs.utils.SpeakerRequestEmitter;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

@Slf4j
public class EmitUpdateRulesRequestsAction extends
        FlowProcessingWithHistorySupportAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;

    public EmitUpdateRulesRequestsAction(PersistenceManager persistenceManager,
                                         FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
        flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowMirrorPointCreateContext context, FlowMirrorPointCreateFsm stateMachine) {
        stateMachine.getCommands().clear();
        stateMachine.getPendingCommands().clear();

        String flowId = stateMachine.getFlowId();
        Flow flow = getFlow(flowId);

        Collection<FlowSegmentRequestFactory> commands = buildCommands(stateMachine, flow);

        // emitting
        PathId flowPathId = stateMachine.getFlowPathId();
        SwitchId mirrorSwitchId = stateMachine.getMirrorSwitchId();
        FlowMirrorPoints mirrorPoints = flowMirrorPointsRepository.findByPathIdAndSwitchId(flowPathId, mirrorSwitchId)
                .orElse(null);

        SpeakerRequestEmitter requestEmitter;
        if (mirrorPoints != null && mirrorPoints.getMirrorPaths().isEmpty()) {
            requestEmitter = SpeakerRemoveSegmentEmitter.INSTANCE;
        } else {
            requestEmitter = SpeakerInstallSegmentEmitter.INSTANCE;
        }

        requestEmitter.emitBatch(stateMachine.getCarrier(), commands, stateMachine.getCommands());
        stateMachine.getCommands().forEach(
                (key, value) -> stateMachine.getPendingCommands().put(key, value.getSwitchId()));

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to update rules");
        } else {
            stateMachine.saveActionToHistory("Commands for updating rules have been sent");
            stateMachine.setRulesInstalled(true);
        }
    }

    private Collection<FlowSegmentRequestFactory> buildCommands(FlowMirrorPointCreateFsm stateMachine, Flow flow) {

        final FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());

        PathId flowPathId = stateMachine.getFlowPathId();
        FlowPath path = flow.getPath(flowPathId).orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                format("Flow path %s not found", flowPathId)));
        PathId oppositePathId = flow.getOppositePathId(flowPathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Opposite flow path id for path %s not found", flowPathId)));
        FlowPath oppositePath = flow.getPath(oppositePathId).orElse(null);
        RequestedFlowMirrorPoint mirrorPoint = stateMachine.getRequestedFlowMirrorPoint();

        CommandContext context = stateMachine.getCommandContext();
        SpeakerRequestBuildContext speakerContext =
                buildBaseSpeakerContextForInstall(path.getSrcSwitchId(), path.getDestSwitchId());
        if (mirrorPoint.getMirrorPointSwitchId().equals(path.getSrcSwitchId())) {
            return new ArrayList<>(commandBuilder
                    .buildIngressOnlyOneDirection(context, flow, path, oppositePath,
                            speakerContext.getForward(),
                            MirrorContext.builder()
                                    .buildMirrorFactoryOnly(true)
                                    .addNewGroup(stateMachine.isAddNewGroup())
                                    .build()));

        } else if (mirrorPoint.getMirrorPointSwitchId().equals(path.getDestSwitchId())) {
            return new ArrayList<>(commandBuilder
                    .buildEgressOnlyOneDirection(context, flow, path, oppositePath,
                            MirrorContext.builder()
                                    .buildMirrorFactoryOnly(true)
                                    .addNewGroup(stateMachine.isAddNewGroup())
                                    .build()));
        }

        return Collections.emptyList();
    }
}
