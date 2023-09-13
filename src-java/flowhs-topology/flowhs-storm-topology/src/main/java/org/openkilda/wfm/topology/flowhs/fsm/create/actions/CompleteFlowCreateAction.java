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

package org.openkilda.wfm.topology.flowhs.fsm.create.actions;

import static java.lang.String.format;
import static org.openkilda.wfm.share.history.model.DumpType.STATE_AFTER;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class CompleteFlowCreateAction extends
        FlowProcessingWithHistorySupportAction<FlowCreateFsm, State, Event, FlowCreateContext> {
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
        saveHistoryWithDump(stateMachine, flowStatus);
    }

    private void saveHistoryWithDump(FlowCreateFsm stateMachine, FlowStatus flowStatus) {
        Optional<Flow> flow = flowRepository.findById(stateMachine.getFlowId());
        Optional<FlowPath> forwardPath = flowPathRepository.findById(stateMachine.getForwardPathId());
        Optional<FlowPath> reversePath = flowPathRepository.findById(stateMachine.getReversePathId());
        Optional<FlowPath> protectedForwardPath = flowPathRepository.findById(stateMachine.getProtectedForwardPathId());
        Optional<FlowPath> protectedReversePath = flowPathRepository.findById(stateMachine.getProtectedReversePathId());

        if (flow.isPresent() && forwardPath.isPresent() && reversePath.isPresent()) {
            stateMachine.saveActionWithDumpToHistory(format("The flow status was set to %s", flowStatus),
                    "Flow creation complete",
                    HistoryMapper.INSTANCE.map(flow.get(), forwardPath.get(), reversePath.get(), STATE_AFTER));
        } else {
            log.error("Could not save flow history; there is not enough information. isPresent: Flow: {}, Forward path:"
                    + " {}, Reverse path: {}", flow.isPresent(), forwardPath.isPresent(), reversePath.isPresent());
            stateMachine.saveActionToHistory(format("The flow status was set to %s", flowStatus),
                    "Flow creation complete");
        }

        if (flow.isPresent() && protectedForwardPath.isPresent() && protectedReversePath.isPresent()) {
            stateMachine.saveActionWithDumpToHistory("Flow creation complete",
                    format("Protected paths have been allocated. Forward %s. Reverse %s.",
                            protectedForwardPath.get(), protectedReversePath.get()),
                    HistoryMapper.INSTANCE.map(flow.get(), protectedForwardPath.get(), protectedReversePath.get(),
                            STATE_AFTER));
        }
    }
}
