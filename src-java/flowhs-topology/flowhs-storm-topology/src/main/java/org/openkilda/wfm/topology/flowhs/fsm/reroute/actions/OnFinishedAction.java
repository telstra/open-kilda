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

import static java.lang.String.format;

import org.openkilda.model.FlowStatus;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnFinishedAction extends HistoryRecordingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    private final FlowOperationsDashboardLogger dashboardLogger;
    private final FlowRerouteHubCarrier carrier;

    public OnFinishedAction(FlowOperationsDashboardLogger dashboardLogger, FlowRerouteHubCarrier carrier) {
        this.dashboardLogger = dashboardLogger;
        this.carrier = carrier;
    }

    @Override
    public void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        if (stateMachine.getNewFlowStatus() == FlowStatus.UP) {
            dashboardLogger.onSuccessfulFlowReroute(stateMachine.getFlowId());
            stateMachine.saveActionToHistory("Flow was rerouted successfully");
        } else {
            stateMachine.saveActionToHistory("Flow reroute completed",
                    format("Flow reroute completed with status %s  and error %s", stateMachine.getNewFlowStatus(),
                            stateMachine.getErrorReason()));
        }
        log.info("Flow {} reroute success", stateMachine.getFlowId());
        carrier.sendRerouteResultStatus(stateMachine.getFlowId(), true, stateMachine.getRerouteError(),
                stateMachine.getCommandContext().getCorrelationId());
    }
}
