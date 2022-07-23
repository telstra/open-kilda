/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class CompleteYFlowSwappingAction extends
        NbTrackableWithHistorySupportAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;
    private final YFlowRepository yFlowRepository;

    public CompleteYFlowSwappingAction(PersistenceManager persistenceManager,
                                       FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);

        this.dashboardLogger = dashboardLogger;
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowPathSwapContext context,
                                                    YFlowPathSwapFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlow result = transactionManager.doInTransaction(() -> {
            YFlow yFlow = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                            format("Y-flow %s not found", yFlowId)));
            yFlow.recalculateStatus();
            return yFlow;
        });

        dashboardLogger.onYFlowStatusUpdate(yFlowId, result.getStatus());
        stateMachine.saveActionToHistory(format("The y-flow status was set to %s", result.getStatus()));

        return Optional.of(buildResponseMessage(result, stateMachine.getCommandContext()));
    }

    private Message buildResponseMessage(YFlow yFlow, CommandContext commandContext) {
        YFlowResponse response = YFlowResponse.builder()
                .yFlow(YFlowMapper.INSTANCE.toYFlowDto(yFlow, flowRepository))
                .build();
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap y-flow paths";
    }
}
