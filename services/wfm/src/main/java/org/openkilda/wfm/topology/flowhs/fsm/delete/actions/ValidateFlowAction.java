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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.history.FlowEventRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowHistorySupportingCarrier;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
public class ValidateFlowAction extends
        NbTrackableAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {

    private final TransactionManager transactionManager;
    private final FlowRepository flowRepository;
    private final FlowEventRepository flowEventRepository;
    private final KildaConfigurationRepository kildaConfigurationRepository;
    private final FeatureTogglesRepository featureTogglesRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
        flowEventRepository = repositoryFactory.createFlowEventRepository();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> perform(State from, State to,
                                        Event event, FlowDeleteContext context,
                                        FlowDeleteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        String eventKey = stateMachine.getCommandContext().getCorrelationId();
        if (flowEventRepository.existsByTaskId(eventKey)) {
            String errorMessage = format("Attempt to reuse key %s, but there's a history record(s) for it.", eventKey);
            log.debug(errorMessage);

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.REQUEST_INVALID,
                    getGenericErrorMessage(), errorMessage));
        }

        dashboardLogger.onFlowDelete(flowId);

        try {
            Flow flow = transactionManager.doInTransaction(() -> {
                Flow foundFlow = flowRepository.findById(flowId, FetchStrategy.DIRECT_RELATIONS)
                        .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                                getGenericErrorMessage(), format("Flow %s was not found", flowId)));
                if (foundFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                    throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                            getGenericErrorMessage(), format("Flow %s is in progress now", flowId));
                }

                // Keep it, just in case we have to revert it.
                stateMachine.setOriginalFlowStatus(foundFlow.getStatus());

                flowRepository.updateStatus(foundFlow.getFlowId(), FlowStatus.IN_PROGRESS);
                return foundFlow;
            });

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, "Flow was validated successfully");

            InfoData flowData = new FlowResponse(FlowMapper.INSTANCE.map(flow));
            CommandContext commandContext = stateMachine.getCommandContext();
            return Optional.of(new InfoMessage(flowData, commandContext.getCreateTime(),
                    commandContext.getCorrelationId()));
        } catch (FlowProcessingException e) {
            // This is a validation error.
            String errorMessage = format("%s: %s", e.getErrorMessage(), e.getErrorDescription());
            dashboardLogger.onFailedFlowDelete(flowId, errorMessage);

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, e.getErrorDescription());

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, e.getErrorType(), e.getErrorMessage(),
                    e.getErrorDescription()));
        }
    }

    @Override
    protected void saveHistory(FlowDeleteFsm stateMachine, FlowHistorySupportingCarrier carrier,
                               String flowId, String action) {
        Instant timestamp = Instant.now();
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .time(timestamp)
                        .flowId(flowId)
                        .build())
                .flowEventData(FlowEventData.builder()
                        .flowId(flowId)
                        .event(FlowEventData.Event.DELETE)
                        .time(timestamp)
                        .build())
                .build();
        carrier.sendHistoryUpdate(historyHolder);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not delete flow";
    }
}
