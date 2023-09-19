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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RecalculateFlowStatusAction extends
        FlowProcessingWithHistorySupportAction<HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> {

    private final FlowOperationsDashboardLogger dashboardLogger;

    public RecalculateFlowStatusAction(PersistenceManager persistenceManager,
                                       FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(State from, State to, Event event, HaFlowPathSwapContext context,
                           HaFlowPathSwapFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();

        boolean commandsFailed = !stateMachine.getFailedCommands().isEmpty();
        FlowStatus resultStatus = transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(haFlowId);
            for (HaFlowPath haFlowPath : haFlow.getUsedPaths()) {
                if (commandsFailed && !haFlowPath.isProtected()) {
                    haFlowPath.setStatus(FlowPathStatus.INACTIVE);
                    for (FlowPath subPath : haFlowPath.getSubPaths()) {
                        subPath.setStatus(FlowPathStatus.INACTIVE);
                    }
                } else {
                    haFlowPath.setStatus(stateMachine.getOldPathStatus(haFlowPath.getHaPathId()));
                    for (FlowPath subPath : haFlowPath.getSubPaths()) {
                        subPath.setStatus(FlowPathStatus.INACTIVE);
                    }
                }
            }

            FlowStatus status = haFlow.computeStatus();
            if (status != haFlow.getStatus()) {
                dashboardLogger.onHaFlowStatusUpdate(haFlowId, status);
                haFlow.setStatus(status);
                haFlow.recalculateHaSubFlowStatuses();
            }
            return status;
        });

        saveActionToHistory(stateMachine, resultStatus);
    }

    private void saveActionToHistory(HaFlowPathSwapFsm stateMachine, FlowStatus resultStatus) {
        FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .of(stateMachine.getCommandContext().getCorrelationId())
                .withAction(format("The HA-flow status has been set to %s", resultStatus))
                .withHaFlowId(stateMachine.getHaFlowId()));
    }
}
