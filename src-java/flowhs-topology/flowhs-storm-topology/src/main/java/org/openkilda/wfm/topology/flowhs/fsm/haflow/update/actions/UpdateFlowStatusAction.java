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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathId;
import org.openkilda.model.StatusInfo;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class UpdateFlowStatusAction extends
        FlowProcessingWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public UpdateFlowStatusAction(PersistenceManager persistenceManager,
                                  FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {

        FlowStatus resultStatus = transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());

            Map<String, FlowStatus> subFlowMap = new HashMap<>();
            FlowStatus newHaFlowStatusStatus = FlowStatus.UP;
            FlowPathStatus primaryPathStatus = FlowPathStatus.ACTIVE;

            List<FlowPath> primarySubPaths = new ArrayList<>(haFlow.getForwardPath().getSubPaths());
            primarySubPaths.addAll(haFlow.getReversePath().getSubPaths());

            for (FlowPath subPath : primarySubPaths) {
                if (isBackUpStrategyUsed(subPath.getPathId(), stateMachine)) {
                    subPath.setStatus(FlowPathStatus.DEGRADED);
                    subFlowMap.put(subPath.getHaSubFlowId(), FlowStatus.DEGRADED);
                    newHaFlowStatusStatus = FlowStatus.DEGRADED;
                    primaryPathStatus = FlowPathStatus.DEGRADED;
                } else {
                    subPath.setStatus(FlowPathStatus.ACTIVE);
                }
            }

            haFlow.getForwardPath().setStatus(primaryPathStatus);
            haFlow.getReversePath().setStatus(primaryPathStatus);

            if (haFlow.getProtectedForwardPath() != null && haFlow.getProtectedReversePath() != null) {
                FlowPathStatus protectedPathStatus = FlowPathStatus.ACTIVE;

                List<FlowPath> protectedSubPaths = new ArrayList<>(haFlow.getProtectedForwardPath().getSubPaths());
                protectedSubPaths.addAll(haFlow.getProtectedReversePath().getSubPaths());

                for (FlowPath subPath : protectedSubPaths) {
                    if (isBackUpStrategyUsed(subPath.getPathId(), stateMachine)) {
                        subPath.setStatus(FlowPathStatus.DEGRADED);
                        subFlowMap.put(subPath.getHaSubFlowId(), FlowStatus.DEGRADED);
                        newHaFlowStatusStatus = FlowStatus.DEGRADED;
                        protectedPathStatus = FlowPathStatus.DEGRADED;
                    } else {
                        subPath.setStatus(FlowPathStatus.ACTIVE);
                    }
                }

                haFlow.getProtectedForwardPath().setStatus(protectedPathStatus);
                haFlow.getProtectedReversePath().setStatus(protectedPathStatus);
            }

            for (HaSubFlow subFlow : haFlow.getHaSubFlows()) {
                subFlow.setStatus(subFlowMap.getOrDefault(subFlow.getHaSubFlowId(), FlowStatus.UP));
            }

            if (newHaFlowStatusStatus != haFlow.getStatus()) {
                dashboardLogger.onHaFlowStatusUpdate(stateMachine.getHaFlowId(), newHaFlowStatusStatus);
                haFlow.setStatus(newHaFlowStatusStatus);
                haFlow.setStatusInfo(getFlowStatusInfo(newHaFlowStatusStatus, stateMachine));
            } else if (FlowStatus.DEGRADED.equals(newHaFlowStatusStatus)) {
                haFlow.setStatusInfo(getDegradedFlowStatusInfo(stateMachine));
            }

            stateMachine.setNewFlowStatus(newHaFlowStatusStatus);
            return newHaFlowStatusStatus;
        });

        stateMachine.saveActionToHistory(format("The ha-flow status was set to %s", resultStatus));
    }

    private boolean isBackUpStrategyUsed(PathId pathId, HaFlowUpdateFsm stateMachine) {
        Boolean result = stateMachine.getBackUpComputationWayUsedMap().get(pathId);
        if (result == null) {
            throw new IllegalArgumentException(format("There is no path id %s in the path map. Valid values are: %s",
                    pathId, stateMachine.getBackUpComputationWayUsedMap().keySet()));
        }
        return result;
    }

    private String getFlowStatusInfo(FlowStatus flowStatus, HaFlowUpdateFsm stateMachine) {
        String flowStatusInfo = null;
        if (!FlowStatus.UP.equals(flowStatus) && !flowStatus.equals(stateMachine.getOriginalHaFlow().getStatus())) {
            flowStatusInfo = stateMachine.getErrorReason();
        }
        if (FlowStatus.DEGRADED.equals(flowStatus)) {
            flowStatusInfo = getDegradedFlowStatusInfo(stateMachine);
        }
        return flowStatusInfo;
    }

    private String getDegradedFlowStatusInfo(HaFlowUpdateFsm stateMachine) {
        boolean isBackUpPathComputationWayUsed = stateMachine.getBackUpComputationWayUsedMap().values().stream()
                .anyMatch(Boolean::booleanValue);
        if (isBackUpPathComputationWayUsed) {
            return StatusInfo.BACK_UP_STRATEGY_USED;
        } else {
            return StatusInfo.OVERLAPPING_PROTECTED_PATH;
        }
    }
}
