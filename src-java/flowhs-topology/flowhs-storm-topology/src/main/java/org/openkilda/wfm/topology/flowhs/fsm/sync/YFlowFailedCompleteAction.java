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

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YFlowFailedCompleteAction extends FailedCompleteActionBase<YFlowSyncFsm, State, Event> {
    private static final String OPERATION_LEVEL_ERROR_MESSAGE = "Could not sync y-flow";

    private final YFlowRepository yFlowRepository;

    public YFlowFailedCompleteAction(
            PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, dashboardLogger);
        yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
    }

    @Override
    protected void updateStatus(YFlowSyncFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        for (String flowId : stateMachine.getTargets()) {
            Flow flow = getFlow(flowId);
            if (reportIncompletePathOperations(flow)) {
                forceFlowDown(flow);
                continue;
            }

            FlowStatus status = flow.computeFlowStatus();
            flow.setStatus(status);
            if (status == FlowStatus.UP) {
                flow.setStatusInfo(null);
            } else {
                flow.setStatusInfo(String.format(
                        "Set status to %s as a result of Y-flow \"%s\" sync operation", status, yFlowId));
            }
        }

        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Y-flow %s not found", yFlowId)));
        if (stateMachine.isDangerousSync()) {
            log.error(
                    "Force Y-flow \"{}\" status to {} due to errors during destructive sync operations",
                    yFlow.getYFlowId(), FlowStatus.DOWN);
            yFlow.setStatus(FlowStatus.DOWN);
        } else {
            yFlow.recalculateStatus();
            if (yFlow.getStatus() == FlowStatus.UP) {
                log.error(
                        "Calculated Y-flow \"{}\" status is {}, but due to errors during sync operation forcing "
                                + "it to {} (actual status of flow\'s rules is unknown)",
                        yFlowId, FlowStatus.UP, FlowStatus.DEGRADED);
                yFlow.setStatus(FlowStatus.DEGRADED);
            } else {
                log.error(
                        "Set Y-flow \"{}\" status to {} as result of failed sync operation",
                        yFlowId, yFlow.getStatus());

            }
        }
    }

    @Override
    protected ErrorData reportGenericFailure(YFlowSyncFsm stateMachine) {
        return new ErrorData(
                ErrorType.INTERNAL_ERROR, OPERATION_LEVEL_ERROR_MESSAGE,
                format("Failed to sync Y-flow %s", stateMachine.getYFlowId()));
    }

    @Override
    protected ErrorData reportSpecificFailure(ErrorType errorType, String errorDetails, CommandContext commandContext) {
        return new ErrorData(errorType, OPERATION_LEVEL_ERROR_MESSAGE, errorDetails);
    }
}
