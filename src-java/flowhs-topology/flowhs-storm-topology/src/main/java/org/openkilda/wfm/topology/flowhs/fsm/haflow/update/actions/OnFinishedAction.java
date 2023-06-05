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
            stateMachine.saveActionToHistory("Flow was updated successfully");
        } else if (stateMachine.getNewFlowStatus() == FlowStatus.DEGRADED) {
            sendPeriodicPingNotification(stateMachine);
            updateFlowMonitoring(stateMachine);
            dashboardLogger.onFailedHaFlowUpdate(stateMachine.getFlowId(), DEGRADED_FAIL_REASON);
            stateMachine.saveActionToHistory(DEGRADED_FAIL_REASON);
        } else {
            stateMachine.saveActionToHistory("Flow update completed",
                    format("Flow update completed with status %s and error %s", stateMachine.getNewFlowStatus(),
                            stateMachine.getErrorReason()));
        }
    }

    private void sendPeriodicPingNotification(HaFlowUpdateFsm stateMachine) {
        HaFlowRequest requestedFlow = stateMachine.getTargetHaFlow();
        stateMachine.getCarrier().sendPeriodicPingNotification(
                requestedFlow.getHaFlowId(), requestedFlow.isPeriodicPings());
    }

    private void updateFlowMonitoring(HaFlowUpdateFsm stateMachine) {
        HaFlow original = stateMachine.getOriginalHaFlow();
        HaFlow target = HaFlowMapper.INSTANCE.toHaFlow(stateMachine.getTargetHaFlow());

        for (HaSubFlow originalSubFlow : original.getHaSubFlows()) {
            HaSubFlow targetSubFlow = target.getHaSubFlowOrThrowException(originalSubFlow.getHaSubFlowId());
            boolean originalNotSingle = !originalSubFlow.isOneSwitch();
            boolean targetNotSingle = !targetSubFlow.isOneSwitch();
            boolean srcUpdated = isSrcUpdated(original, target);
            boolean dstUpdated = isDstUpdated(originalSubFlow, targetSubFlow);

            // clean up old if it is not single
            //TODO: Review logic during https://github.com/telstra/open-kilda/issues/5208
            if (originalNotSingle && (srcUpdated || dstUpdated)) {
                stateMachine.getCarrier().sendDeactivateFlowMonitoring(stateMachine.getFlowId(),
                        original.getSharedSwitchId(), originalSubFlow.getEndpointSwitchId());

            }
            // setup new if it is not single
            //TODO: Review logic during https://github.com/telstra/open-kilda/issues/5208
            if (targetNotSingle && (srcUpdated || dstUpdated)) {
                stateMachine.getCarrier().sendActivateFlowMonitoring(null);
            }
        }
    }

    private boolean isSrcUpdated(HaFlow original, HaFlow target) {
        return !(original.getSharedSwitchId().equals(target.getSharedSwitchId())
                && original.getSharedPort() == target.getSharedPort()
                && original.getSharedOuterVlan() == target.getSharedOuterVlan()
                && original.getSharedInnerVlan() == target.getSharedInnerVlan());
    }

    private boolean isDstUpdated(HaSubFlow originalSubFlow, HaSubFlow targetSubFlow) {
        return !(originalSubFlow.getEndpointSwitchId().equals(targetSubFlow.getEndpointSwitchId())
                && originalSubFlow.getEndpointPort() == targetSubFlow.getEndpointPort()
                && originalSubFlow.getEndpointVlan() == targetSubFlow.getEndpointVlan()
                && originalSubFlow.getEndpointInnerVlan() == targetSubFlow.getEndpointInnerVlan());
    }
}
