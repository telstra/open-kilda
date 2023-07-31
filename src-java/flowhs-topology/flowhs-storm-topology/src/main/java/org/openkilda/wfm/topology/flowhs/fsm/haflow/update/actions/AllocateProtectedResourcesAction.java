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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.pce.GetHaPathsResult;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flow.model.HaFlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.BaseHaResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Slf4j
public class AllocateProtectedResourcesAction extends
        BaseHaResourceAllocationAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public static final String PATHS_TYPE = "protected";

    public AllocateProtectedResourcesAction(
            PersistenceManager persistenceManager, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
            int resourceAllocationRetriesLimit, PathComputer pathComputer, FlowResourcesManager resourcesManager,
            FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                pathComputer, resourcesManager, dashboardLogger);
    }

    @Override
    protected boolean isAllocationRequired(HaFlowUpdateFsm stateMachine) {
        return stateMachine.getTargetHaFlow().isAllocateProtectedPath();
    }

    @Override
    protected void allocate(HaFlowUpdateFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        String haFlowId = stateMachine.getFlowId();

        HaFlow haFlow = getHaFlow(haFlowId);
        List<PathId> pathIdsToReuse = new ArrayList<>(haFlow.getPrimarySubPathIds());
        pathIdsToReuse.addAll(haFlow.getProtectedSubPathIds());
        pathIdsToReuse.addAll(stateMachine.getRejectedSubPathsIds());

        HaFlowPathPair oldPaths = new HaFlowPathPair(haFlow.getProtectedForwardPath(),
                haFlow.getProtectedReversePath());
        HaFlowPath primaryForward = getHaFlowPath(
                haFlow, stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());
        HaFlowPath primaryReverse = getHaFlowPath(haFlow,
                stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId());
        Predicate<GetHaPathsResult> testNonOverlappingPath = buildNonOverlappingPathPredicate(
                primaryForward, primaryReverse);
        HaPathIdsPair newPathIdsPair = resourcesManager.generateHaPathIds(haFlowId, haFlow.getHaSubFlows());

        log.debug("Finding a new protected paths for ha-flow {}", haFlowId);
        GetHaPathsResult allocatedPaths = allocatePathPair(haFlow, newPathIdsPair,
                false, pathIdsToReuse, oldPaths, true,
                testNonOverlappingPath, true);
        if (allocatedPaths == null) {
            throw new ResourceAllocationException("Unable to allocate protected ha-path");
        }

        if (!testNonOverlappingPath.test(allocatedPaths)) {
            throw new ResourceAllocationException("Couldn't find non overlapping protected path");
        }

        log.debug("New protected ha-paths have been allocated: {}", allocatedPaths);
        stateMachine.addBackUpComputationStatuses(allocatedPaths, newPathIdsPair);
        stateMachine.setNewProtectedPathIds(newPathIdsPair);

        log.debug("Allocating resources for a new protected ha-path of ha-flow {}", haFlowId);
        HaFlowResources haFlowResources = allocateFlowResources(
                haFlow, allocatedPaths.getForward().getYPointSwitchId(), newPathIdsPair);
        stateMachine.setNewProtectedResources(haFlowResources);

        final boolean forceIgnoreBandwidth = false;
        HaFlowPathPair createdPaths = createHaFlowPathPair(haFlowId, haFlowResources, allocatedPaths,
                forceIgnoreBandwidth);
        log.debug("New protected ha-path has been created: {}", createdPaths);

        saveAllocationActionToHistory(stateMachine, haFlow, PATHS_TYPE, createdPaths);
    }

    @Override
    protected void onFailure(HaFlowUpdateFsm stateMachine) {
        stateMachine.setNewProtectedResources(null);
        stateMachine.setNewProtectedPathIds(null);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Couldn't update HA-flow";
    }

    @Override
    protected void handleError(HaFlowUpdateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
