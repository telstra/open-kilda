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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.InvalidFlowException;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;
import org.openkilda.wfm.topology.flowhs.validation.YFlowValidator;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ValidateYFlowAction extends
        NbTrackableWithHistorySupportAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
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
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowUpdateContext context,
                                                    YFlowUpdateFsm stateMachine) {
        stateMachine.saveNewEventToHistory("Y-flow update request validation has been started",
                FlowEventData.Event.UPDATE);
        YFlowRequest targetFlow = context.getTargetFlow();

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getModifyYFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Y-flow create feature is disabled");
        }

        try {
            yFlowValidator.validate(targetFlow);
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage(), e);
        } catch (UnavailableFlowEndpointException e) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID, e.getMessage(), e);
        }

        String yFlowId = targetFlow.getYFlowId();
        YFlow yFlow = transactionManager.doInTransaction(() -> {
            YFlow result = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            if (result.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Y-flow %s is in progress now", yFlowId));
            }

            // Keep it, just in case we have to revert it.
            stateMachine.setOriginalYFlowStatus(result.getStatus());

            result.setStatus(FlowStatus.IN_PROGRESS);
            return result;
        });

        Set<String> requestedSubFlowIds = targetFlow.getSubFlows().stream()
                .map(SubFlowDto::getFlowId)
                .collect(Collectors.toSet());

        Set<String> originalSubFlowIds = yFlow.getSubFlows().stream()
                .map(YSubFlow::getSubFlowId)
                .collect(Collectors.toSet());

        if (!requestedSubFlowIds.equals(originalSubFlowIds)) {
            throw new FlowProcessingException(ErrorType.PARAMETERS_INVALID,
                    format("Unable to map provided sub-flows set onto existing y-flow %s", yFlowId));
        }

        YSubFlow subFlow = yFlow.getSubFlows().stream().findAny()
                .orElseThrow(() -> new FlowProcessingException(ErrorType.DATA_INVALID,
                        format("No sub-flows of the y-flow %s were found", yFlowId)));
        stateMachine.setMainAffinityFlowId(subFlow.getFlow().getAffinityGroupId());

        List<FlowEndpoint> subFlowEndpoints = targetFlow.getSubFlows().stream()
                .map(SubFlowDto::getEndpoint)
                .collect(Collectors.toList());
        dashboardLogger.onYFlowUpdate(yFlowId, targetFlow.getSharedEndpoint(), subFlowEndpoints,
                targetFlow.getMaximumBandwidth());

        stateMachine.setTargetFlow(targetFlow);

        stateMachine.saveActionToHistory("Y-flow was validated successfully");

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not update y-flow";
    }
}
