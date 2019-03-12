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

package org.openkilda.wfm.topology.flowhs.fsm.action.create;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flow.validation.ValidationException;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.FlowValidator;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Optional;

@Slf4j
public class FlowValidateAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

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
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Optional<Message> errorResponse = Optional.empty();

        boolean isOperationAllowed = featureTogglesRepository.find()
                .map(FeatureToggles::getCreateFlowEnabled)
                .orElse(Boolean.FALSE);

        if (!isOperationAllowed) {
            log.warn("Flow create feature is disabled");
            errorResponse = Optional.of(new InfoMessage(null, 0L, null));
        }

        if (flowRepository.exists(stateMachine.getFlow().getFlowId())) {
            log.info("Cannot create flow: flow id {} already in use", stateMachine.getFlow().getFlowId());
        }
        try {
            flowValidator.validate(stateMachine.getFlow());
        } catch (ValidationException e) {
            log.warn("Flow validation error: {}", e.getMessage());
            errorResponse = Optional.of(new InfoMessage(null, 0L, null));
        }

        if (errorResponse.isPresent()) {
            stateMachine.getCarrier().sendNorthboundResponse();
            stateMachine.fire(Event.Error);
        } else {
            log.debug("Flow {} validated successfully", stateMachine.getFlow().getFlowId());
            stateMachine.fire(Event.Next);

        }
    }
}
