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
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateFlowStatusAction extends
        FlowProcessingWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public UpdateFlowStatusAction(PersistenceManager persistenceManager,
                                  FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @TimedExecution("fsm.update_ha_flow_status")
    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();

        FlowStatus resultStatus = transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(haFlowId);
            FlowStatus flowStatus = haFlow.computeStatus();

            if (flowStatus != haFlow.getStatus()) {
                dashboardLogger.onHaFlowStatusUpdate(haFlowId, flowStatus);
                haFlow.setStatus(flowStatus);
                haFlow.setStatusInfo(getFlowStatusInfo(flowStatus, stateMachine));
            } else if (FlowStatus.DEGRADED.equals(flowStatus)) {
                haFlow.setStatusInfo(getDegradedFlowStatusInfo(stateMachine));
            }
            stateMachine.setNewHaFlowStatus(flowStatus);
            return flowStatus;
        });

        HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction(format("The flow status has been set to %s", resultStatus))
                .withHaFlowId(stateMachine.getHaFlowId()));
    }

    private String getFlowStatusInfo(FlowStatus flowStatus, HaFlowRerouteFsm stateMachine) {
        String flowStatusInfo = null;
        if (!FlowStatus.UP.equals(flowStatus) && !flowStatus.equals(stateMachine.getOriginalFlowStatus())) {
            flowStatusInfo = stateMachine.getErrorReason();
        }
        if (FlowStatus.DEGRADED.equals(flowStatus)) {
            flowStatusInfo = getDegradedFlowStatusInfo(stateMachine);
        }
        return flowStatusInfo;
    }

    private String getDegradedFlowStatusInfo(HaFlowRerouteFsm stateMachine) {
        boolean ignoreBandwidth = stateMachine.isIgnoreBandwidth();
        boolean isBackUpPathComputationWayUsed = stateMachine.getBackUpComputationWayUsedMap().values().stream()
                .anyMatch(Boolean::booleanValue);
        if (ignoreBandwidth && isBackUpPathComputationWayUsed) {
            return "Couldn't find path with required bandwidth and backup way was used to build the path";
        } else if (ignoreBandwidth) {
            return "Couldn't find path with required bandwidth";
        } else if (isBackUpPathComputationWayUsed) {
            return "An alternative way (back up strategy or max_latency_tier2 value) of building the path was used";
        } else {
            return "Couldn't find non overlapping protected path";
        }
    }
}
