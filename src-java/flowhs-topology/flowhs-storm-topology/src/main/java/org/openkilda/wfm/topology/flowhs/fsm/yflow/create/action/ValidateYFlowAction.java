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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.InvalidFlowException;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;
import org.openkilda.wfm.topology.flowhs.validation.YFlowValidator;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ValidateYFlowAction extends NbTrackableAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final YFlowRepository yFlowRepository;
    private final YFlowValidator yFlowValidator;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateYFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        yFlowValidator = new YFlowValidator(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowCreateContext context,
                                                    YFlowCreateFsm stateMachine) {
        YFlowRequest targetFlow = context.getTargetFlow();
        String yFlowId = targetFlow.getYFlowId();

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getModifyYFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Y-flow create feature is disabled");
        }

        if (yFlowRepository.exists(yFlowId)) {
            throw new FlowProcessingException(ErrorType.ALREADY_EXISTS,
                    format("Y-flow %s already exists", yFlowId));
        }

        try {
            yFlowValidator.validate(targetFlow);
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage(), e);
        } catch (UnavailableFlowEndpointException e) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID, e.getMessage(), e);
        }

        stateMachine.setTargetFlow(targetFlow);

        List<FlowEndpoint> subFlowEndpoints = targetFlow.getSubFlows().stream()
                .map(SubFlowDto::getEndpoint)
                .collect(Collectors.toList());
        dashboardLogger.onYFlowCreate(yFlowId, targetFlow.getSharedEndpoint(), subFlowEndpoints,
                targetFlow.getMaximumBandwidth());

        stateMachine.saveNewEventToHistory("Y-flow was validated successfully", FlowEventData.Event.CREATE);

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create y-flow";
    }
}
