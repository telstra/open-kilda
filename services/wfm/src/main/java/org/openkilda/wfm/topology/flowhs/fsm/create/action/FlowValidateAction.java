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
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableAction;
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

    private static final String ERROR = "Could not create flow";

    private final FlowValidator flowValidator;
    private final FlowRepository flowRepository;
    private final FeatureTogglesRepository featureTogglesRepository;

    public FlowValidateAction(PersistenceManager persistenceManager) {
        FlowRepository flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        this.flowValidator = new FlowValidator(flowRepository, switchRepository);
        this.flowRepository = flowRepository;
        this.featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
    }

    @Override
    protected Optional<Message> perform(State from, State to, Event event, FlowCreateContext context,
                                        FlowCreateFsm stateMachine) throws FlowProcessingException {
        boolean isOperationAllowed = featureTogglesRepository.find()
                .map(FeatureToggles::getCreateFlowEnabled)
                .orElse(Boolean.FALSE);

        if (!isOperationAllowed) {
            log.warn("Flow create feature is disabled");
            throw new FlowProcessingException(ErrorType.NOT_ALLOWED, ERROR, "Flow create feature is disabled");
        }

        RequestedFlow request = context.getFlowDetails();
        if (flowRepository.exists(request.getFlowId())) {
            log.debug("Cannot create flow: flow id {} already in use", request.getFlowId());
            throw new FlowProcessingException(ErrorType.ALREADY_EXISTS, ERROR,
                    format("Flow %s already exists", request.getFlowId()));
        }

        try {
            flowValidator.validate(request);
        } catch (InvalidFlowException e) {
            log.debug("Flow validation error: {}", e.getMessage());
            throw new FlowProcessingException(e.getType(), ERROR, e.getMessage());
        } catch (UnavailableFlowEndpointException e) {
            log.debug("Flow validation error: {}", e.getMessage());
            throw new FlowProcessingException(ErrorType.DATA_INVALID, ERROR, e.getMessage());
        }

        return Optional.empty();
    }

}
