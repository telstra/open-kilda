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
public class OnFinishedWithErrorAction extends
        HistoryRecordingAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;
    private final FlowRerouteHubCarrier carrier;

    public OnFinishedWithErrorAction(FlowOperationsDashboardLogger dashboardLogger, FlowRerouteHubCarrier carrier) {
        this.dashboardLogger = dashboardLogger;
        this.carrier = carrier;
    }

    @Override
    public void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        dashboardLogger.onFailedHaFlowReroute(stateMachine.getHaFlowId(), stateMachine.getErrorReason());
        HaFlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction("Failed to reroute the HA-flow")
                .withDescription(stateMachine.getErrorReason())
                .withHaFlowId(stateMachine.getHaFlowId()));

        log.info("HA-flow {} reroute result failed with error {}", stateMachine.getHaFlowId(),
                stateMachine.getRerouteError());
        carrier.sendRerouteResultStatus(stateMachine.getHaFlowId(), stateMachine.getRerouteError(),
                stateMachine.getCommandContext().getCorrelationId());
    }
}
