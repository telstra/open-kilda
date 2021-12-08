/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action;

import org.openkilda.messaging.info.reroute.error.RerouteError;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.YFlowRerouteHubCarrier;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class OnFinishedWithErrorAction extends AnonymousAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;
    private final YFlowRerouteHubCarrier carrier;

    public OnFinishedWithErrorAction(FlowOperationsDashboardLogger dashboardLogger, YFlowRerouteHubCarrier carrier) {
        this.dashboardLogger = dashboardLogger;
        this.carrier = carrier;
    }

    @Override
    public void execute(State from, State to, Event event, YFlowRerouteContext context, YFlowRerouteFsm stateMachine) {
        dashboardLogger.onFailedYFlowReroute(stateMachine.getYFlowId(), stateMachine.getErrorReason());
        stateMachine.saveActionToHistory("Failed to reroute the y-flow", stateMachine.getErrorReason());

        carrier.sendYFlowRerouteResultStatus(stateMachine.getFlowId(), new RerouteError(stateMachine.getErrorReason()),
                stateMachine.getCommandContext().getCorrelationId());
    }
}
