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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteFlowCreateAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public CompleteFlowCreateAction(PersistenceManager persistenceManager,
                                    FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        if (!flowRepository.exists(flowId)) {
            throw new FlowProcessingException(ErrorType.NOT_FOUND,
                    "Couldn't complete flow creation. The flow was deleted");
        }

        FlowStatus flowStatus = transactionManager.doInTransaction(() -> {
            FlowStatus status = FlowStatus.UP;
            FlowPathStatus primaryPathStatus;
            if (stateMachine.isBackUpPrimaryPathComputationWayUsed()) {
                primaryPathStatus = FlowPathStatus.DEGRADED;
                status = FlowStatus.DEGRADED;
            } else {
                primaryPathStatus = FlowPathStatus.ACTIVE;
            }

            flowPathRepository.updateStatus(stateMachine.getForwardPathId(), primaryPathStatus);
            flowPathRepository.updateStatus(stateMachine.getReversePathId(), primaryPathStatus);

            if (stateMachine.getProtectedForwardPathId() != null
                    && stateMachine.getProtectedReversePathId() != null) {
                FlowPathStatus protectedPathStatus;
                if (stateMachine.isBackUpProtectedPathComputationWayUsed()) {
                    protectedPathStatus = FlowPathStatus.DEGRADED;
                    status = FlowStatus.DEGRADED;
                } else {
                    protectedPathStatus = FlowPathStatus.ACTIVE;
                }

                flowPathRepository.updateStatus(stateMachine.getProtectedForwardPathId(), protectedPathStatus);
                flowPathRepository.updateStatus(stateMachine.getProtectedReversePathId(), protectedPathStatus);
            }

            flowRepository.updateStatus(flowId, status);
            if (FlowStatus.DEGRADED.equals(status)) {
                flowRepository.updateStatusInfo(flowId, "An alternative way "
                        + "(back up strategy or max_latency_tier2 value) of building the path was used");
            }

            return status;
        });

        dashboardLogger.onFlowStatusUpdate(flowId, flowStatus);
        stateMachine.saveActionToHistory(format("The flow status was set to %s", flowStatus));
    }
}
