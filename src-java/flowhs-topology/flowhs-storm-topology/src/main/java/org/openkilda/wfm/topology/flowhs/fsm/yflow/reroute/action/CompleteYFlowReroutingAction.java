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

import static java.lang.String.format;

import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteYFlowReroutingAction
        extends YFlowProcessingAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public CompleteYFlowReroutingAction(PersistenceManager persistenceManager,
                                        FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(State from, State to, Event event,
                           YFlowRerouteContext context, YFlowRerouteFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();

        FlowStatus flowStatus = transactionManager.doInTransaction(() -> {
            YFlow flow = getYFlow(yFlowId);
            FlowStatus status = FlowStatus.UP;
            boolean allSubFlowsIsInactive = true;
            for (YSubFlow subFlow : flow.getSubFlows()) {
                if (!subFlow.getFlow().isActive()) {
                    status = FlowStatus.DEGRADED;
                }
                allSubFlowsIsInactive &= !subFlow.getFlow().isActive();
            }

            if (allSubFlowsIsInactive) {
                status = FlowStatus.DOWN;
            }

            flow.setStatus(status);
            return status;
        });

        dashboardLogger.onYFlowStatusUpdate(yFlowId, flowStatus);
        stateMachine.saveActionToHistory(format("The y-flow status was set to %s", flowStatus));

        if (stateMachine.getErrorReason() == null) {
            stateMachine.fire(Event.YFLOW_REROUTE_FINISHED);
        } else {
            stateMachine.fire(Event.ERROR);
        }
    }
}
