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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.action;

import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class OnFinishedWithErrorAction extends AnonymousAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public OnFinishedWithErrorAction(FlowOperationsDashboardLogger dashboardLogger) {
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void execute(State from, State to, Event event, FlowPathSwapContext context, FlowPathSwapFsm stateMachine) {
        dashboardLogger.onFailedFlowUpdate(stateMachine.getFlowId(), stateMachine.getErrorReason());
        stateMachine.saveActionToHistory("Failed to swap paths for the flow", stateMachine.getErrorReason());

        log.warn("Flow {} path swap failed", stateMachine.getFlowId());
        stateMachine.getCarrier().sendPathSwapResultStatus(stateMachine.getFlowId(), false,
                stateMachine.getCommandContext().getCorrelationId());
    }
}
