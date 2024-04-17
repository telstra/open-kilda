/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static java.lang.String.format;

import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnFinishedAction extends HistoryRecordingAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    public static final String DEGRADED_FAIL_REASON = "Not all paths meet the SLA";
    private final FlowOperationsDashboardLogger dashboardLogger;

    public OnFinishedAction(FlowOperationsDashboardLogger dashboardLogger) {
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void perform(State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        if (stateMachine.getNewFlowStatus() == FlowStatus.UP) {
            sendPeriodicPingNotification(stateMachine);
            updateFlowMonitoring(stateMachine);
            dashboardLogger.onSuccessfulHaFlowUpdate(stateMachine.getHaFlowId());
            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow has been updated successfully")
                    .withHaFlowId(stateMachine.getHaFlowId()));
        } else if (stateMachine.getNewFlowStatus() == FlowStatus.DEGRADED) {
            sendPeriodicPingNotification(stateMachine);
            updateFlowMonitoring(stateMachine);
            dashboardLogger.onFailedHaFlowUpdate(stateMachine.getFlowId(), DEGRADED_FAIL_REASON);
            stateMachine.saveActionToHistory(DEGRADED_FAIL_REASON);
        } else {
            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow update has been completed")
                    .withDescription(format("HA-flow update has been completed with status %s and error %s",
                            stateMachine.getNewFlowStatus(), stateMachine.getErrorReason()))
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
    }

    private void sendPeriodicPingNotification(HaFlowUpdateFsm stateMachine) {
        HaFlowRequest requestedFlow = stateMachine.getTargetHaFlow();
        stateMachine.getCarrier().sendPeriodicPingNotification(
                requestedFlow.getHaFlowId(), requestedFlow.isPeriodicPings());
    }

    private void updateFlowMonitoring(HaFlowUpdateFsm stateMachine) {
        HaFlow original = stateMachine.getOriginalHaFlow();
        HaFlowRequest target = stateMachine.getTargetHaFlow();

        for (RequestedFlow targetSubFlow : HaFlowMapper.INSTANCE.toRequestedFlows(target)) {
            HaSubFlow originalHaSubFlow = original.getHaSubFlow(targetSubFlow.getFlowId()).orElseThrow(
                    () -> new IllegalArgumentException(format("target ha-subflow %s not found "
                                    + "in the original ha-flow %s",
                            targetSubFlow.getFlowId(), original.getHaFlowId())));
            if (isEndpointUpdated(originalHaSubFlow.getEndpoint(), targetSubFlow.getDstEndpoint())) {
                if (!originalHaSubFlow.isOneSwitch()) {
                    stateMachine.getCarrier().sendDeactivateFlowMonitoring(originalHaSubFlow.getHaSubFlowId(),
                            original.getSharedSwitchId(), originalHaSubFlow.getEndpointSwitchId());
                }

                if (!targetSubFlow.isOneSwitchFlow()) {
                    stateMachine.getCarrier().sendActivateFlowMonitoring(targetSubFlow);
                }
            }
        }
    }

    private boolean isEndpointUpdated(FlowEndpoint originalEndpoint, FlowEndpoint targetEndpoint) {
        return !originalEndpoint.equals(targetEndpoint);
    }
}
