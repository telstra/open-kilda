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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowStatus;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnFinishedAction extends HistoryRecordingAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {

    private final FlowOperationsDashboardLogger dashboardLogger;
    private final FlowRerouteHubCarrier carrier;

    public OnFinishedAction(FlowOperationsDashboardLogger dashboardLogger, FlowRerouteHubCarrier carrier) {
        this.dashboardLogger = dashboardLogger;
        this.carrier = carrier;
    }

    @Override
    public void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        if (stateMachine.getNewHaFlowStatus() == FlowStatus.UP) {
            dashboardLogger.onSuccessfulHaFlowReroute(stateMachine.getHaFlowId());
            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow has been rerouted successfully")
                    .withHaFlowId(stateMachine.getHaFlowId()));

            sendPeriodicPingNotification(stateMachine);
        } else {
            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow reroute completed")
                    .withDescription(format("HA-flow reroute completed with status %s and error %s",
                            stateMachine.getNewHaFlowStatus(), stateMachine.getErrorReason()))
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
        log.info("HA-flow {} reroute success", stateMachine.getFlowId());
        carrier.sendRerouteResultStatus(stateMachine.getHaFlowId(), stateMachine.getRerouteError(),
                stateMachine.getCommandContext().getCorrelationId());
    }

    private void sendPeriodicPingNotification(HaFlowRerouteFsm stateMachine) {
        stateMachine.getCarrier().sendPeriodicPingNotification(stateMachine.getFlowId(),
                stateMachine.isPeriodicPingsEnabled());
    }
}
