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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CompleteHaFlowCreateAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public CompleteHaFlowCreateAction(PersistenceManager persistenceManager,
                                      FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        FlowStatus flowStatus = transactionManager.doInTransaction(() -> {
            HaFlow haFlow = haFlowRepository.findById(stateMachine.getHaFlowId())
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        "Couldn't complete ha-flow creation. The ha-flow was deleted"));

            Map<String, FlowStatus> subFlowMap = new HashMap<>();
            FlowStatus haFlowStatus = FlowStatus.UP;
            FlowPathStatus primaryPathStatus = FlowPathStatus.ACTIVE;

            List<FlowPath> primarySubPaths = new ArrayList<>(haFlow.getForwardPath().getSubPaths());
            primarySubPaths.addAll(haFlow.getReversePath().getSubPaths());

            for (FlowPath subPath : primarySubPaths) {
                if (isBackUpStrategyUsed(subPath.getPathId(), stateMachine)) {
                    subPath.setStatus(FlowPathStatus.DEGRADED);
                    subFlowMap.put(subPath.getHaSubFlowId(), FlowStatus.DEGRADED);
                    haFlowStatus = FlowStatus.DEGRADED;
                    primaryPathStatus = FlowPathStatus.DEGRADED;
                } else {
                    subPath.setStatus(FlowPathStatus.ACTIVE);
                }
            }

            haFlow.getForwardPath().setStatus(primaryPathStatus);
            haFlow.getReversePath().setStatus(primaryPathStatus);

            if (stateMachine.getTargetFlow().isAllocateProtectedPath()
                    && haFlow.getProtectedForwardPath() != null && haFlow.getProtectedReversePath() != null) {
                FlowPathStatus protectedPathStatus = FlowPathStatus.ACTIVE;

                List<FlowPath> protectedSubPaths = new ArrayList<>(haFlow.getProtectedForwardPath().getSubPaths());
                protectedSubPaths.addAll(haFlow.getProtectedReversePath().getSubPaths());

                for (FlowPath subPath : protectedSubPaths) {
                    if (isBackUpStrategyUsed(subPath.getPathId(), stateMachine)) {
                        subPath.setStatus(FlowPathStatus.DEGRADED);
                        subFlowMap.put(subPath.getHaSubFlowId(), FlowStatus.DEGRADED);
                        haFlowStatus = FlowStatus.DEGRADED;
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
            haFlow.setStatus(haFlowStatus);
            //TODO add status info?
            return haFlowStatus;
        });

        dashboardLogger.onFlowStatusUpdate(stateMachine.getHaFlowId(), flowStatus);
        stateMachine.saveActionToHistory(format("The ha-flow status was set to %s", flowStatus));
    }

    private boolean isBackUpStrategyUsed(PathId pathId, HaFlowCreateFsm stateMachine) {
        Boolean result = stateMachine.getBackUpComputationWayUsedMap().get(pathId);
        if (result == null) {
            throw new IllegalArgumentException(format("There is no path id %s in the path map. Valid values are: %s",
                    pathId, stateMachine.getBackUpComputationWayUsedMap().keySet()));
        }
        return result;
    }
}
