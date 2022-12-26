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
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PhysicalPort;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlowMirrorPoint;
import org.openkilda.wfm.topology.flowhs.validation.FlowValidator;
import org.openkilda.wfm.topology.flowhs.validation.InvalidFlowException;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ValidateRequestAction extends
        NbTrackableWithHistorySupportAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private final FlowValidator flowValidator;
    private final FlowOperationsDashboardLogger dashboardLogger;
    private final FlowMirrorRepository flowMirrorRepository;
    private final PhysicalPortRepository physicalPortRepository;

    public ValidateRequestAction(PersistenceManager persistenceManager,
                                 FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.flowValidator = new FlowValidator(persistenceManager);
        this.dashboardLogger = dashboardLogger;
        this.flowMirrorRepository = persistenceManager.getRepositoryFactory().createFlowMirrorRepository();
        this.physicalPortRepository = persistenceManager.getRepositoryFactory().createPhysicalPortRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event,
                                                    FlowMirrorPointCreateContext context,
                                                    FlowMirrorPointCreateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        RequestedFlowMirrorPoint mirrorPoint = context.getMirrorPoint();
        String flowMirrorId = mirrorPoint.getMirrorPointId();
        stateMachine.setFlowMirrorId(flowMirrorId);
        stateMachine.setMirrorSwitchId(mirrorPoint.getMirrorPointSwitchId());

        dashboardLogger.onFlowMirrorPointCreate(flowId, mirrorPoint.getMirrorPointSwitchId(),
                mirrorPoint.getMirrorPointDirection().toString(), mirrorPoint.getSinkEndpoint().getSwitchId(),
                mirrorPoint.getSinkEndpoint().getPortNumber(), mirrorPoint.getSinkEndpoint().getOuterVlanId());

        stateMachine.setRequestedFlowMirrorPoint(mirrorPoint);
        Flow flow = transactionManager.doInTransaction(() -> {
            Flow foundFlow = getFlow(flowId);
            if (foundFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Flow %s is in progress now", flowId));
            }
            stateMachine.setFlowStatus(foundFlow.getStatus());
            flowRepository.updateStatus(flowId, FlowStatus.IN_PROGRESS);
            return foundFlow;
        });

        if (!mirrorPoint.getMirrorPointSwitchId().equals(mirrorPoint.getSinkEndpoint().getSwitchId())) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                    format("Invalid sink endpoint switch id: %s. In the current implementation, "
                            + "the sink switch id cannot differ from the mirror point switch id.",
                            mirrorPoint.getSinkEndpoint().getSwitchId()));
        }

        if (!mirrorPoint.getMirrorPointSwitchId().equals(flow.getSrcSwitchId())
                && !mirrorPoint.getMirrorPointSwitchId().equals(flow.getDestSwitchId())) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                    format("Invalid mirror point switch id: %s", mirrorPoint.getMirrorPointSwitchId()));
        }

        if (flowMirrorRepository.exists(flowMirrorId)) {
            throw new FlowProcessingException(ErrorType.ALREADY_EXISTS,
                    format("Flow mirror point %s already exists", flowMirrorId));
        }

        Optional<PhysicalPort> physicalPort = physicalPortRepository.findBySwitchIdAndPortNumber(
                mirrorPoint.getSinkEndpoint().getSwitchId(), mirrorPoint.getSinkEndpoint().getPortNumber());
        if (physicalPort.isPresent()) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                    format("Invalid sink port %d on switch %s. This port is part of LAG %d. Please delete LAG port "
                                    + "or choose another sink port.",
                            mirrorPoint.getSinkEndpoint().getPortNumber(), mirrorPoint.getSinkEndpoint().getSwitchId(),
                            physicalPort.get().getLagLogicalPort().getLogicalPortNumber()));
        }

        try {
            flowValidator.flowMirrorPointValidate(mirrorPoint);
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage(), e);
        } catch (UnavailableFlowEndpointException e) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID, e.getMessage(), e);
        }

        stateMachine.saveNewEventToHistory("Flow was validated successfully",
                FlowEventData.Event.FLOW_MIRROR_POINT_CREATE);

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create flow mirror point";
    }
}
