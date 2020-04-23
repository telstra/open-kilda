/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsContext;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.validation.FlowValidator;
import org.openkilda.wfm.topology.flowhs.validation.InvalidFlowException;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ValidateFlowsAction
        extends FlowProcessingAction<FlowSwapEndpointsFsm, State, Event, FlowSwapEndpointsContext> {
    private final FeatureTogglesRepository featureTogglesRepository;
    private final FlowValidator flowValidator;

    public ValidateFlowsAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        SwitchRepository switchRepository = repositoryFactory.createSwitchRepository();
        IslRepository islRepository = repositoryFactory.createIslRepository();
        SwitchPropertiesRepository switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        flowValidator = new FlowValidator(flowRepository, switchRepository, islRepository, switchPropertiesRepository);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowSwapEndpointsContext context,
                           FlowSwapEndpointsFsm stateMachine) {
        RequestedFlow firstTargetFlow = stateMachine.getFirstTargetFlow();
        RequestedFlow secondTargetFlow = stateMachine.getSecondTargetFlow();

        boolean isOperationAllowed = featureTogglesRepository.find()
                .map(FeatureToggles::getUpdateFlowEnabled).orElse(Boolean.FALSE);
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Flow update feature is disabled");
        }

        try {
            flowValidator.validateForSwapEndpoints(firstTargetFlow, secondTargetFlow);
        } catch (InvalidFlowException e) {
            stateMachine.fireValidationError(
                    new ErrorData(e.getType(), FlowSwapEndpointsFsm.GENERIC_ERROR_MESSAGE, e.getMessage()));
            return;
        } catch (UnavailableFlowEndpointException e) {
            stateMachine.fireValidationError(
                    new ErrorData(ErrorType.DATA_INVALID, FlowSwapEndpointsFsm.GENERIC_ERROR_MESSAGE, e.getMessage()));
            return;
        }

        try {
            persistenceManager.getTransactionManager().doInTransaction(() -> {
                Flow foundFirstFlow = checkAndGetFlow(stateMachine.getFirstFlowId());
                Flow foundSecondFlow = checkAndGetFlow(stateMachine.getSecondFlowId());

                stateMachine.setFirstOriginalFlow(foundFirstFlow);
                stateMachine.setSecondOriginalFlow(foundSecondFlow);
            });
        } catch (FlowProcessingException e) {
            stateMachine.fireValidationError(
                    new ErrorData(e.getErrorType(), FlowSwapEndpointsFsm.GENERIC_ERROR_MESSAGE, e.getMessage()));
            return;
        }

        stateMachine.saveNewEventToHistory(stateMachine.getFirstFlowId(),
                format("Current flow and flow %s were validated successfully", stateMachine.getSecondFlowId()),
                FlowEventData.Event.SWAP_ENDPOINTS);
        stateMachine.saveNewEventToHistory(stateMachine.getSecondFlowId(),
                format("Current flow and flow %s were validated successfully", stateMachine.getFirstFlowId()),
                FlowEventData.Event.SWAP_ENDPOINTS);

        stateMachine.fireNext();
    }

    private Flow checkAndGetFlow(String flowId) {
        Flow flow = getFlow(flowId);
        if (flow.getStatus() == FlowStatus.IN_PROGRESS) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID, format("Flow %s is in progress now", flowId));
        }
        return flow;
    }
}
