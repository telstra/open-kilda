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
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;
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
public class EmitCommandRequestsAction
        extends FlowProcessingAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private final FlowCommandBuilderFactory commandBuilderFactory;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;

    public EmitCommandRequestsAction(PersistenceManager persistenceManager,
                                     FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
        flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowMirrorPointDeleteContext context, FlowMirrorPointDeleteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Flow flow = getFlow(flowId);

        PathId flowPathId = stateMachine.getFlowPathId();
        SwitchId mirrorSwitchId = stateMachine.getMirrorSwitchId();

        FlowMirrorPoints mirrorPoints = flowMirrorPointsRepository.findByPathIdAndSwitchId(flowPathId, mirrorSwitchId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow mirror points for flow path %s and mirror switch id %s not found",
                                flowPathId, mirrorSwitchId)));

        FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());
        Collection<FlowSegmentRequestFactory> commands =
                buildCommands(commandBuilder, stateMachine, flow, mirrorPoints);

        // emitting
        SpeakerRequestEmitter requestEmitter;
        if (mirrorPoints.getMirrorPaths().isEmpty()) {
            requestEmitter = SpeakerRemoveSegmentEmitter.INSTANCE;
        } else {
            requestEmitter = SpeakerInstallSegmentEmitter.INSTANCE;
        }

        requestEmitter.emitBatch(stateMachine.getCarrier(), commands, stateMachine.getCommands());
        stateMachine.getCommands().forEach(
                (key, value) -> stateMachine.getPendingCommands().put(key, value.getSwitchId()));

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to remove group");
        } else {
            stateMachine.saveActionToHistory("Commands for removing group have been sent");
        }
    }

    private Collection<FlowSegmentRequestFactory> buildCommands(FlowCommandBuilder commandBuilder,
                                                                FlowMirrorPointDeleteFsm stateMachine, Flow flow,
                                                                FlowMirrorPoints mirrorPoints) {
        FlowPath path = mirrorPoints.getFlowPath();
        FlowPath oppositePath = path.isForward() ? flow.getReversePath() : flow.getForwardPath();

        CommandContext context = stateMachine.getCommandContext();
        SpeakerRequestBuildContext speakerContext =
                buildBaseSpeakerContextForInstall(path.getSrcSwitchId(), path.getDestSwitchId());
        if (mirrorPoints.getMirrorSwitchId().equals(path.getSrcSwitchId())) {
            return new ArrayList<>(commandBuilder
                    .buildIngressOnlyOneDirection(context, flow, path, oppositePath,
                            speakerContext.getForward(),
                            MirrorContext.builder()
                                    .buildMirrorFactoryOnly(true)
                                    .build()));

        } else if (mirrorPoints.getMirrorSwitchId().equals(path.getDestSwitchId())) {
            return new ArrayList<>(commandBuilder
                    .buildEgressOnlyOneDirection(context, flow, path, oppositePath,
                            MirrorContext.builder()
                                    .buildMirrorFactoryOnly(true)
                                    .build()));
        }

        return Collections.emptyList();
    }
}
