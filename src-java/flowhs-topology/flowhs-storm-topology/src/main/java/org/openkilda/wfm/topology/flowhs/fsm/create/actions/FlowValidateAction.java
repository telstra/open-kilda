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

package org.openkilda.wfm.topology.flowhs.fsm.create.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.validation.FlowValidator;
import org.openkilda.wfm.topology.flowhs.validation.InvalidFlowException;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FlowValidateAction extends
        NbTrackableWithHistorySupportAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final YFlowRepository yFlowRepository;
    private final FlowValidator flowValidator;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public FlowValidateAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
        this.flowValidator = new FlowValidator(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowCreateContext context,
                                                    FlowCreateFsm stateMachine) throws FlowProcessingException {
        RequestedFlow request = context.getTargetFlow();
        dashboardLogger.onFlowCreate(request.getFlowId(),
                request.getSrcSwitch(), request.getSrcPort(), request.getSrcVlan(),
                request.getDestSwitch(), request.getDestPort(), request.getDestVlan(),
                request.getDiverseFlowId(), request.getBandwidth());

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getCreateFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Flow create feature is disabled");
        }

        if (flowRepository.exists(request.getFlowId())) {
            throw new FlowProcessingException(ErrorType.ALREADY_EXISTS,
                    format("Flow %s already exists", request.getFlowId()));
        }

        if (yFlowRepository.exists(request.getFlowId())) {
            throw new FlowProcessingException(ErrorType.ALREADY_EXISTS,
                    format("Y-flow %s already exists", request.getFlowId()));
        }

        try {
            flowValidator.validate(request);
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage(), e);
        } catch (UnavailableFlowEndpointException e) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID, e.getMessage(), e);
        }

        stateMachine.setTargetFlow(request);

        if (event != Event.RETRY) {
            stateMachine.saveNewEventToHistory("Flow was validated successfully", FlowEventData.Event.CREATE);
        } else {
            // no need to save a new event into DB, it should already exist there.
            stateMachine.saveActionToHistory("Flow was validated successfully");
        }

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create flow";
    }

    @Override
    protected void handleError(FlowCreateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed validation.
        stateMachine.notifyEventListeners(listener ->
                listener.onFailed(stateMachine.getFlowId(), stateMachine.getErrorReason(), errorType));
    }
}
