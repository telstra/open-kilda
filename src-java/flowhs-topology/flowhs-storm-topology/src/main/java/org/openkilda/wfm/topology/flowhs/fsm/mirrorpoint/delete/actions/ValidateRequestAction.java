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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowMirrorPointResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ValidateRequestAction extends
        NbTrackableWithHistorySupportAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;
    private final FlowMirrorRepository flowMirrorRepository;

    public ValidateRequestAction(PersistenceManager persistenceManager,
                                 FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowMirrorRepository = repositoryFactory.createFlowMirrorRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event,
                                                    FlowMirrorPointDeleteContext context,
                                                    FlowMirrorPointDeleteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        String flowMirrorId = context.getFlowMirrorPointId();
        stateMachine.setFlowMirrorId(flowMirrorId);

        dashboardLogger.onFlowMirrorPointDelete(flowId, context.getFlowMirrorPointId());

        FlowMirrorPointResponse response = transactionManager.doInTransaction(() -> {
            Flow foundFlow = getFlow(flowId);
            if (foundFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Flow %s is in progress now", flowId));
            }
            stateMachine.setFlowStatus(foundFlow.getStatus());
            flowRepository.updateStatus(flowId, FlowStatus.IN_PROGRESS);

            FlowMirror flowMirror = flowMirrorRepository.findById(flowMirrorId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Flow mirror point %s not found", flowMirrorId)));
            if (flowMirror.getStatus() == FlowPathStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Flow mirror point %s is in progress now", flowMirrorId));
            }

            stateMachine.setOriginalFlowMirrorStatus(flowMirror.getStatus());
            flowMirrorRepository.updateStatus(flowMirrorId, FlowPathStatus.IN_PROGRESS);

            String direction = flowMirror.getFlowMirrorPoints().getFlowPath().isForward() ? "forward" : "reverse";
            return FlowMirrorPointResponse.builder()
                    .flowId(foundFlow.getFlowId())
                    .mirrorPointId(flowMirror.getFlowMirrorId())
                    .mirrorPointDirection(direction)
                    .mirrorPointSwitchId(flowMirror.getMirrorSwitchId())
                    .sinkEndpoint(FlowEndpoint.builder()
                            .switchId(flowMirror.getEgressSwitchId())
                            .portNumber(flowMirror.getEgressPort())
                            .innerVlanId(flowMirror.getEgressInnerVlan())
                            .outerVlanId(flowMirror.getEgressOuterVlan())
                            .build())
                    .build();
        });

        stateMachine.saveNewEventToHistory("Flow was validated successfully",
                FlowEventData.Event.FLOW_MIRROR_POINT_DELETE);

        CommandContext commandContext = stateMachine.getCommandContext();
        return Optional.of(new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId()));
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not delete flow mirror point";
    }
}
