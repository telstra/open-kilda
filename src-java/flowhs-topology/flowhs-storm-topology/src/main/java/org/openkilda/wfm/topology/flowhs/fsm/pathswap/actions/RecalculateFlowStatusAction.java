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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RecalculateFlowStatusAction extends
        FlowProcessingWithHistorySupportAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {

    private final FlowOperationsDashboardLogger dashboardLogger;

    public RecalculateFlowStatusAction(PersistenceManager persistenceManager,
                                       FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowPathSwapContext context,
                           FlowPathSwapFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        FlowStatus resultStatus = transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(flowId);
            flow.getPaths().forEach(flowPath -> {
                FlowPathStatus pathStatus = stateMachine.getOldPathStatus(flowPath.getPathId());
                if (!flowPath.isProtected() && !stateMachine.getFailedCommands().isEmpty()) {
                    pathStatus = FlowPathStatus.INACTIVE;
                }
                flowPath.setStatus(pathStatus);
            });
            FlowStatus status = flow.computeFlowStatus();
            if (status != flow.getStatus()) {
                dashboardLogger.onFlowStatusUpdate(flowId, status);
                flow.setStatus(status);
            }
            return status;
        });

        stateMachine.saveActionToHistory(format("The flow status was set to %s", resultStatus));
    }
}
