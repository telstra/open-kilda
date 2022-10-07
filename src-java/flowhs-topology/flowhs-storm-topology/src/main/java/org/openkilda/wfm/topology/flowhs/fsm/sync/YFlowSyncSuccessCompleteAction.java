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

package org.openkilda.wfm.topology.flowhs.fsm.sync;

import static java.lang.String.format;

import org.openkilda.messaging.command.yflow.YFlowRerouteResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.YFlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowPaths;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;
import org.openkilda.wfm.topology.flowhs.utils.YFlowUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YFlowSyncSuccessCompleteAction extends SuccessCompleteActionBase<YFlowSyncFsm, State, Event> {
    private final YFlowRepository yFlowRepository;
    private final YFlowUtils utils;

    public YFlowSyncSuccessCompleteAction(
            @NonNull PersistenceManager persistenceManager, @NonNull FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, dashboardLogger);
        yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        utils = new YFlowUtils(persistenceManager);
    }

    @Override
    protected void updateStatus(YFlowSyncFsm stateMachine) {
        boolean isError = false;
        for (String flowId : stateMachine.getTargets()) {
            Flow flow = getFlow(flowId);
            isError = ! updateFlowStatus(flow) || isError;
        }

        if (isError) {
            stateMachine.fireError();
            return;
        }

        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Y-flow %s not found", yFlowId)));
        yFlow.recalculateStatus();
        if (yFlow.getStatus() == FlowStatus.UP) {
            log.info("Y-Flow \"{}\" have been successfully synchronized", yFlowId);
        } else if (yFlow.getStatus() == FlowStatus.DEGRADED) {
            log.warn("Y-Flow \"{}\" synchronization is incomplete, final status is {}", yFlowId, FlowStatus.DEGRADED);
        } else {
            stateMachine.fireError();
        }

        sendResponse(yFlow, stateMachine.getCarrier(), stateMachine.getCommandContext());
    }

    private void sendResponse(YFlow yFlow, FlowSyncCarrier carrier, CommandContext commandContext) {
        YFlowPaths paths = utils.definePaths(yFlow);
        YFlowRerouteResponse response = new YFlowRerouteResponse(
                paths.getSharedPath(), paths.getSubFlowPaths(), false);
        carrier.sendNorthboundResponse(new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId()));
    }
}
