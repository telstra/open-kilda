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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions;

import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class OnFinishedWithErrorAction extends AnonymousAction<YFlowDeleteFsm, State, Event, YFlowDeleteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public OnFinishedWithErrorAction(FlowOperationsDashboardLogger dashboardLogger) {
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void execute(State from, State to, Event event, YFlowDeleteContext context, YFlowDeleteFsm stateMachine) {
        dashboardLogger.onFailedYFlowDelete(stateMachine.getYFlowId(), stateMachine.getErrorReason());
        stateMachine.saveActionToHistory("Failed to delete the y-flow", stateMachine.getErrorReason());
    }
}
