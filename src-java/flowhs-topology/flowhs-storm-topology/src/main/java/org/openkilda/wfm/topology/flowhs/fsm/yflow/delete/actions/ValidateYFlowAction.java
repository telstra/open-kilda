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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ValidateYFlowAction extends NbTrackableAction<YFlowDeleteFsm, State, Event, YFlowDeleteContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final YFlowRepository yFlowRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateYFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowDeleteContext context,
                                                    YFlowDeleteFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        dashboardLogger.onYFlowDelete(yFlowId);

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getModifyYFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Y-flow delete feature is disabled");
        }

        YFlow result = transactionManager.doInTransaction(() -> {
            YFlow yFlow = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Y-flow %s not found", yFlowId)));
            if (yFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Y-flow %s is in progress now", yFlowId));
            }

            // Keep it, just in case we have to revert it.
            stateMachine.setOriginalYFlowStatus(yFlow.getStatus());

            yFlow.setStatus(FlowStatus.IN_PROGRESS);
            return yFlow;
        });

        stateMachine.saveNewEventToHistory("Y-flow was validated successfully", FlowEventData.Event.DELETE);

        return Optional.of(buildResponseMessage(result, stateMachine.getCommandContext()));
    }

    private Message buildResponseMessage(YFlow yFlow, CommandContext commandContext) {
        YFlowResponse response = YFlowResponse.builder().yFlow(YFlowMapper.INSTANCE.toYFlowDto(yFlow)).build();
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not delete y-flow";
    }
}
