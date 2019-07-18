/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.NbTrackableAction;
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
public class FlowValidateAction extends NbTrackableAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    private static final String ERROR_MESSAGE = "Could not create flow";

    private final FlowValidator flowValidator;
    private final FlowRepository flowRepository;
    private final FeatureTogglesRepository featureTogglesRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public FlowValidateAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        FlowRepository flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        IslRepository islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        this.flowValidator = new FlowValidator(flowRepository, switchRepository, islRepository);
        this.flowRepository = flowRepository;
        this.featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> perform(State from, State to, Event event, FlowCreateContext context,
                                        FlowCreateFsm stateMachine) throws FlowProcessingException {
        boolean isOperationAllowed = featureTogglesRepository.find()
                .map(FeatureToggles::getCreateFlowEnabled)
                .orElse(Boolean.FALSE);

        if (!isOperationAllowed) {
            log.warn("Flow create feature is disabled");
            throw new FlowProcessingException(ErrorType.NOT_ALLOWED,
                    getGenericErrorMessage(), "Flow create feature is disabled");
        }

        RequestedFlow request = context.getTargetFlow();
        dashboardLogger.onFlowCreate(request.getFlowId(),
                request.getSrcSwitch(), request.getSrcPort(), request.getSrcVlan(),
                request.getDestSwitch(), request.getDestPort(), request.getDestVlan(),
                request.getDiverseFlowId(), request.getBandwidth());

        if (flowRepository.exists(request.getFlowId())) {
            log.debug("Cannot create flow: flow id {} already in use", request.getFlowId());
            throw new FlowProcessingException(ErrorType.ALREADY_EXISTS, getGenericErrorMessage(),
                    format("Flow %s already exists", request.getFlowId()));
        }

        try {
            flowValidator.validate(request);
        } catch (InvalidFlowException e) {
            log.debug("Flow validation error: {}", e.getMessage());
            throw new FlowProcessingException(e.getType(), getGenericErrorMessage(), e.getMessage());
        } catch (UnavailableFlowEndpointException e) {
            log.debug("Flow validation error: {}", e.getMessage());
            throw new FlowProcessingException(ErrorType.DATA_INVALID, getGenericErrorMessage(), e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return ERROR_MESSAGE;
    }
}
