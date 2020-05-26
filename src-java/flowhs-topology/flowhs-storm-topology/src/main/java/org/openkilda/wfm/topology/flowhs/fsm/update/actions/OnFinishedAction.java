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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowStatus;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnFinishedAction extends HistoryRecordingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public OnFinishedAction(FlowOperationsDashboardLogger dashboardLogger) {
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        if (stateMachine.getNewFlowStatus() == FlowStatus.UP) {
            sendPeriodicPingNotification(stateMachine);
            dashboardLogger.onSuccessfulFlowUpdate(stateMachine.getFlowId());
            stateMachine.saveActionToHistory("Flow was updated successfully");
        } else if (stateMachine.getNewFlowStatus() == FlowStatus.DEGRADED) {
            sendPeriodicPingNotification(stateMachine);
            dashboardLogger.onFailedFlowUpdate(stateMachine.getFlowId(), "Protected path not found");
            stateMachine.saveActionToHistory("Main flow path updated successfully but no protected path found");
        } else {
            stateMachine.saveActionToHistory("Flow update completed",
                    format("Flow update completed with status %s and error %s", stateMachine.getNewFlowStatus(),
                            stateMachine.getErrorReason()));
        }
    }

    private void sendPeriodicPingNotification(FlowUpdateFsm stateMachine) {
        RequestedFlow requestedFlow = stateMachine.getTargetFlow();
        stateMachine.getCarrier().sendPeriodicPingNotification(requestedFlow.getFlowId(),
                requestedFlow.isPeriodicPings());
    }
}
