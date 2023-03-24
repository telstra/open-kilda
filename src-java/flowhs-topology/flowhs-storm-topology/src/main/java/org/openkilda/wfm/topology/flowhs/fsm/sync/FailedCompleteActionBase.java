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

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class FailedCompleteActionBase<F extends SyncFsmBase<F, S, E>, S, E>
        extends FlowProcessingWithHistorySupportAction<F, S, E, FlowSyncContext> {

    protected final FlowOperationsDashboardLogger dashboardLogger;

    public FailedCompleteActionBase(
            PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(S from, S to, E event, FlowSyncContext context, F stateMachine) {
        if (context.getErrorType() != ErrorType.NOT_FOUND) {
            updateStatus(stateMachine);
        }

        ErrorData error;
        if (context.getErrorType() == null) {
            error = reportGenericFailure(stateMachine);
        } else {
            error = reportSpecificFailure(
                    context.getErrorType(), context.getErrorDetails(), stateMachine.getCommandContext());
        }

        sendResponse(error, stateMachine.getCarrier(), stateMachine.getCommandContext());
        stateMachine.fireNext(context);
    }

    protected abstract void updateStatus(F stateMachine);

    protected abstract ErrorData reportGenericFailure(F stateMachine);

    protected abstract ErrorData reportSpecificFailure(
            ErrorType errorType, String errorDetails, CommandContext commandContext);

    protected boolean reportIncompletePathOperations(Flow flow) {
        List<String> affectedPaths = new ArrayList<>();
        for (FlowPath path : flow.getPaths()) {
            if (path.getStatus() != FlowPathStatus.IN_PROGRESS) {
                continue;
            }
            path.setStatus(FlowPathStatus.INACTIVE);
            affectedPaths.add(path.getPathId().toString());
        }

        if (affectedPaths.isEmpty()) {
            return false;
        }

        log.error(
                "Flow path(s) \"{}\" stays in {} state after end of sync operation, it should never happens, "
                        + "forcing flow status to {}",
                String.join("\", \"", affectedPaths), FlowStatus.IN_PROGRESS, FlowStatus.DOWN);
        return true;
    }

    protected void forceFlowDown(Flow flow) {
        flow.setStatus(FlowStatus.DOWN);
        flow.setStatusInfo("Internal error during SYNC operation");
    }

    private void sendResponse(ErrorData payload, FlowSyncCarrier carrier, CommandContext commandContext) {
        carrier.sendNorthboundResponse(
                new ErrorMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId()));
    }
}
