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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.reroute.RerouteResultInfoData;
import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnFinishedWithErrorAction extends
        HistoryRecordingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final FlowOperationsDashboardLogger dashboardLogger;
    private final FlowRerouteHubCarrier carrier;

    public OnFinishedWithErrorAction(FlowOperationsDashboardLogger dashboardLogger, FlowRerouteHubCarrier carrier) {
        this.dashboardLogger = dashboardLogger;
        this.carrier = carrier;
    }

    @Override
    public void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        dashboardLogger.onFailedFlowReroute(stateMachine.getFlowId(), stateMachine.getErrorReason());
        stateMachine.saveActionToHistory("Failed to reroute the flow", stateMachine.getErrorReason());
        RerouteError rerouteError = stateMachine.getRerouteError();
        RerouteResultInfoData rerouteResult = RerouteResultInfoData.builder()
                .flowId(stateMachine.getFlowId())
                .success(false)
                .rerouteError(rerouteError == null ? new RerouteError("Other error") : rerouteError)
                .build();
        Message message = new InfoMessage(rerouteResult, System.currentTimeMillis(),
                stateMachine.getCommandContext().getCorrelationId());
        log.info("Reroute result fail {}", rerouteResult);
        carrier.sendRerouteResultStatus(message);
    }
}
