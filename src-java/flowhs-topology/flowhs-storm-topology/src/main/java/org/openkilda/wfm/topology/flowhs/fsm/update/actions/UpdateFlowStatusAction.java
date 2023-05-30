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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.StatusInfo;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class UpdateFlowStatusAction extends
        FlowProcessingWithHistorySupportAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public UpdateFlowStatusAction(PersistenceManager persistenceManager,
                                  FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        FlowStatus resultStatus = transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(flowId);
            FlowStatus flowStatus = Optional.ofNullable(stateMachine.getNewFlowStatus())
                    .orElse(flow.computeFlowStatus());
            if (flowStatus != flow.getStatus() && stateMachine.getBulkUpdateFlowIds().isEmpty()) {
                dashboardLogger.onFlowStatusUpdate(flowId, flowStatus);
                flow.setStatus(flowStatus);
                flow.setStatusInfo(getFlowStatusInfo(flow, flowStatus, stateMachine));
            } else if (FlowStatus.DEGRADED.equals(flowStatus)) {
                flow.setStatusInfo(getDegradedFlowStatusInfo(flow, stateMachine));
            }
            stateMachine.setNewFlowStatus(flowStatus);
            return flowStatus;
        });

        if (stateMachine.getBulkUpdateFlowIds().isEmpty()) {
            stateMachine.saveActionToHistory(format("The flow status was set to %s", resultStatus));
        }
    }

    private String getFlowStatusInfo(Flow flow, FlowStatus flowStatus, FlowUpdateFsm stateMachine) {
        String flowStatusInfo = null;
        if (!FlowStatus.UP.equals(flowStatus) && !flowStatus.equals(stateMachine.getOriginalFlowStatus())) {
            flowStatusInfo = stateMachine.getErrorReason();
        }
        if (FlowStatus.DEGRADED.equals(flowStatus)) {
            flowStatusInfo = getDegradedFlowStatusInfo(flow, stateMachine);
        }
        return flowStatusInfo;
    }

    private String getDegradedFlowStatusInfo(Flow flow, FlowUpdateFsm stateMachine) {
        boolean isBackUpPathComputationWayUsed = stateMachine.isBackUpPrimaryPathComputationWayUsed()
                || (flow.isAllocateProtectedPath() && stateMachine.isBackUpProtectedPathComputationWayUsed());
        if (isBackUpPathComputationWayUsed) {
            return StatusInfo.BACK_UP_STRATEGY_USED;
        } else {
            return StatusInfo.OVERLAPPING_PROTECTED_PATH;
        }
    }
}
