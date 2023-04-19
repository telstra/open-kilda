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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.HaFlowValidator;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class HaFlowValidateAction extends
        NbTrackableWithHistorySupportAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final HaFlowValidator haFlowValidator;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public HaFlowValidateAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.haFlowValidator = new HaFlowValidator(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine)
            throws FlowProcessingException {
        if (!featureTogglesRepository.getOrDefault().getCreateHaFlowEnabled()) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "HA-flow create feature is disabled");
        }
        HaFlowRequest request = context.getTargetFlow();

        try {
            haFlowValidator.validateFlowIdUniqueness(request.getHaFlowId());
            for (HaSubFlowDto subFlow : request.getSubFlows()) {
                haFlowValidator.validateFlowIdUniqueness(subFlow.getFlowId());
            }
            haFlowValidator.validate(request);
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage(), e);
        } catch (UnavailableFlowEndpointException e) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID, e.getMessage(), e);
        }

        stateMachine.setTargetFlow(request);

        List<FlowEndpoint> subFlowEndpoints = request.getSubFlows().stream()
                .map(HaSubFlowDto::getEndpoint)
                .collect(Collectors.toList());
        dashboardLogger.onHaFlowCreate(request.getHaFlowId(), request.getSharedEndpoint(), subFlowEndpoints,
                request.getMaximumBandwidth(), request.getPathComputationStrategy(), request.getMaxLatency(),
                request.getMaxLatencyTier2());

        stateMachine.saveNewEventToHistory("HA-flow was validated successfully", FlowEventData.Event.CREATE);
        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create ha-flow";
    }

    @Override
    protected void handleError(HaFlowCreateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed validation.
        stateMachine.notifyEventListeners(listener ->
                listener.onFailed(stateMachine.getHaFlowId(), stateMachine.getErrorReason(), errorType));
    }
}
