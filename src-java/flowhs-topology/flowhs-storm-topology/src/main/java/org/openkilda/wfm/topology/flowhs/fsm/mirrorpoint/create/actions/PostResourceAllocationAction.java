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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowMirrorPointResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirror;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostResourceAllocationAction extends
        NbTrackableWithHistorySupportAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {

    private final FlowMirrorRepository flowMirrorRepository;

    public PostResourceAllocationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        flowMirrorRepository = persistenceManager.getRepositoryFactory().createFlowMirrorRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event,
                                                    FlowMirrorPointCreateContext context,
                                                    FlowMirrorPointCreateFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());

        String flowMirrorId = stateMachine.getFlowMirrorId();
        FlowMirror flowMirror = flowMirrorRepository.findById(flowMirrorId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow mirror point %s not found", flowMirrorId)));

        String direction = flowMirror.getFlowMirrorPoints().getFlowPath().isForward() ? "forward" : "reverse";
        FlowMirrorPointResponse response = FlowMirrorPointResponse.builder()
                .flowId(flow.getFlowId())
                .mirrorPointId(flowMirror.getFlowMirrorId())
                .mirrorPointDirection(direction)
                .mirrorPointSwitchId(flowMirror.getMirrorSwitchId())
                .sinkEndpoint(FlowEndpoint.builder()
                        .switchId(flowMirror.getEgressSwitchId())
                        .portNumber(flowMirror.getEgressPort())
                        .outerVlanId(flowMirror.getEgressOuterVlan())
                        .innerVlanId(flowMirror.getEgressInnerVlan())
                        .build())
                .build();

        CommandContext commandContext = stateMachine.getCommandContext();
        return Optional.of(new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId()));
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create flow mirror point";
    }
}
