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

package org.openkilda.wfm.topology.flowhs.fsm.sync.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FailedCompleteAction
        extends FlowProcessingWithHistorySupportAction<FlowSyncFsm, State, Event, FlowSyncContext> {
    private static final String OPERATION_LEVEL_ERROR_MESSAGE = "Could not sync flow";

    private final FlowSyncCarrier carrier;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public FailedCompleteAction(
            @NonNull FlowSyncCarrier carrier, @NonNull PersistenceManager persistenceManager,
            FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);

        this.carrier = carrier;
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowSyncContext context, FlowSyncFsm stateMachine) {
        if (context.getErrorType() == null) {
            reportGenericFailure(stateMachine);
        } else {
            reportSpecificFailure(context.getErrorType(), context.getErrorDetails(), stateMachine.getCommandContext());
        }
        stateMachine.fireNext(context);
    }

    private void reportGenericFailure(FlowSyncFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());

        final int failCount = stateMachine.getPathOperationFail().size();
        final int successCount = stateMachine.getPathOperationSuccess().size();

        FlowStatus status = flow.computeFlowStatus();
        if (stateMachine.isDangerousSync()) {
            flow.setStatus(FlowStatus.DOWN);
            flow.setStatusInfo(String.format(
                    "%d of %d path operations have failed during DANGEROUS sync attempt",
                    failCount, failCount + successCount));
        } else if (FlowStatus.UP != status) {
            flow.setStatus(status);
            flow.setStatusInfo(String.format(
                    "%d of %d path operations have failed during sync attempt", failCount, failCount + successCount));
        } else {
            flow.setStatus(FlowStatus.DEGRADED);
            flow.setStatusInfo("Failed to update flow info during sync, some flow fields can contain not actual info");
        }
        stateMachine.saveActionToHistory(format("The flow status was set to %s", flow.getStatus()));
        log.error("{} - setting flow \"{}\" status to {}", flow.getStatusInfo(), flow.getFlowId(), flow.getStatus());

        ErrorData error = new ErrorData(
                ErrorType.INTERNAL_ERROR, OPERATION_LEVEL_ERROR_MESSAGE,
                format("Failed to sync flow %s", flow.getFlowId()));
        sendResponse(error, stateMachine.getCommandContext());

        dashboardLogger.onFailedFlowSync(flow.getFlowId(), failCount, failCount + successCount);
    }

    private void reportSpecificFailure(ErrorType errorType, String errorDetails, CommandContext commandContext) {
        ErrorData error = new ErrorData(errorType, OPERATION_LEVEL_ERROR_MESSAGE, errorDetails);
        sendResponse(error, commandContext);
    }

    private void sendResponse(ErrorData payload, CommandContext commandContext) {
        carrier.sendNorthboundResponse(
                new ErrorMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId()));
    }
}
