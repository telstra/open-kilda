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

package org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsContext;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.swapendpoints.FlowSwapEndpointsFsm.State;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
abstract class OnFinishedNbTrackableAction
        extends NbTrackableWithHistorySupportAction<FlowSwapEndpointsFsm, State, Event, FlowSwapEndpointsContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public OnFinishedNbTrackableAction(PersistenceManager persistenceManager,
                                       FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    protected void updateFlowsStatuses(FlowSwapEndpointsFsm stateMachine, boolean writeToHistory) {
        try {
            UpdateFlowsStatusesResult updateFlowsStatusesResult = transactionManager.doInTransaction(() ->
                            UpdateFlowsStatusesResult.builder()
                                    .firstFlowStatus(updateFlowStatus(stateMachine.getFirstFlowId()))
                                    .secondFlowStatus(updateFlowStatus(stateMachine.getSecondFlowId()))
                                    .build());
            if (writeToHistory) {
                stateMachine.saveFlowActionToHistory(stateMachine.getFirstFlowId(), format(
                        "The flow status was set to %s", updateFlowsStatusesResult.getFirstFlowStatus()));
                stateMachine.saveFlowActionToHistory(stateMachine.getSecondFlowId(), format(
                        "The flow status was set to %s", updateFlowsStatusesResult.getSecondFlowStatus()));
            }

        } catch (FlowProcessingException e) {
            log.error("Failed update flow status: {}", e.getMessage());
        }
    }

    private FlowStatus updateFlowStatus(String flowId) {
        Flow flow = getFlow(flowId);
        FlowStatus flowStatus = flow.computeFlowStatus();
        if (flowStatus != flow.getStatus()) {
            dashboardLogger.onFlowStatusUpdate(flowId, flowStatus);
            flow.setStatus(flowStatus);
        }
        return flowStatus;
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap endpoints";
    }

    @Builder
    @Getter
    private static class UpdateFlowsStatusesResult {
        FlowStatus firstFlowStatus;
        FlowStatus secondFlowStatus;
    }
}
