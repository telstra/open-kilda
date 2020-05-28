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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class OnFinishedWithErrorAction extends AnonymousAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public OnFinishedWithErrorAction(FlowOperationsDashboardLogger dashboardLogger) {
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void execute(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        dashboardLogger.onFailedFlowUpdate(stateMachine.getFlowId(), stateMachine.getErrorReason());
        stateMachine.saveActionToHistory("Failed to update the flow", stateMachine.getErrorReason());

        Message message = stateMachine.getOperationResultMessage();
        if (stateMachine.getBulkUpdateFlowIds() != null && !stateMachine.getBulkUpdateFlowIds().isEmpty()) {
            if (message instanceof ErrorMessage) {
                stateMachine.sendHubSwapEndpointsResponse(message);
            } else {
                stateMachine.sendHubSwapEndpointsResponse(buildErrorMessage(ErrorType.UPDATE_FAILURE,
                        "Could not update flow",
                        "Update failed while installing rules on switches",
                        stateMachine.getCommandContext()));
            }
        }
    }

    private Message buildErrorMessage(ErrorType errorType, String errorMessage, String errorDescription,
                                      CommandContext commandContext) {
        ErrorData error = new ErrorData(errorType, errorMessage, errorDescription);
        return new ErrorMessage(error, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }
}
