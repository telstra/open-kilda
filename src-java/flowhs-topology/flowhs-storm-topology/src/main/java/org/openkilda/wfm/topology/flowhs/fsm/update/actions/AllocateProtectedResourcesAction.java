/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.FlowPathPair;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Slf4j
public class AllocateProtectedResourcesAction extends
        BaseResourceAllocationAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    public AllocateProtectedResourcesAction(PersistenceManager persistenceManager,
                                            int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                            int resourceAllocationRetriesLimit,
                                            PathComputer pathComputer, FlowResourcesManager resourcesManager,
                                            FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                pathComputer, resourcesManager, dashboardLogger);
    }

    @Override
    protected boolean isAllocationRequired(FlowUpdateFsm stateMachine) {
        return stateMachine.getTargetFlow().isAllocateProtectedPath();
    }

    @Override
    protected void allocate(FlowUpdateFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        String flowId = stateMachine.getFlowId();

        Set<String> flowIds = Sets.newHashSet(flowId);
        if (stateMachine.getBulkUpdateFlowIds() != null) {
            flowIds.addAll(stateMachine.getBulkUpdateFlowIds());
        }
        log.debug("Finding protected path ids for flows {}", flowIds);
        List<PathId> pathIdsToReuse = new ArrayList<>(flowPathRepository.findActualPathIdsByFlowIds(flowIds));
        pathIdsToReuse.addAll(stateMachine.getRejectedPaths());

        Flow tmpFlow = getFlow(flowId);
        FlowPathPair oldPaths = new FlowPathPair(tmpFlow.getProtectedForwardPath(),
                tmpFlow.getProtectedReversePath());
        FlowPath primaryForward = getFlowPath(tmpFlow, stateMachine.getNewPrimaryForwardPath());
        FlowPath primaryReverse = getFlowPath(tmpFlow, stateMachine.getNewPrimaryReversePath());
        Predicate<GetPathsResult> testNonOverlappingPath = path -> (primaryForward == null
                || !flowPathBuilder.arePathsOverlapped(path.getForward(), primaryForward))
                && (primaryReverse == null
                || !flowPathBuilder.arePathsOverlapped(path.getReverse(), primaryReverse));
        PathId newForwardPathId = resourcesManager.generatePathId(flowId);
        PathId newReversePathId = resourcesManager.generatePathId(flowId);

        log.debug("Finding a new protected path for flow {}", flowId);
        GetPathsResult allocatedPaths = allocatePathPair(tmpFlow, newForwardPathId, newReversePathId,
                false, pathIdsToReuse, oldPaths, true,
                stateMachine.getSharedBandwidthGroupId(), testNonOverlappingPath);
        if (allocatedPaths == null) {
            throw new ResourceAllocationException("Unable to allocate a path");
        }

        if (!testNonOverlappingPath.test(allocatedPaths)) {
            stateMachine.saveActionToHistory("Couldn't find non overlapping protected path");
        } else {
            log.debug("New protected paths have been allocated: {}", allocatedPaths);
            stateMachine.setNewProtectedForwardPath(newForwardPathId);
            stateMachine.setNewProtectedReversePath(newReversePathId);
            stateMachine.setBackUpProtectedPathComputationWayUsed(allocatedPaths.isBackUpPathComputationWayUsed());

            log.debug("Allocating resources for a new protected path of flow {}", flowId);
            FlowResources flowResources = allocateFlowResources(tmpFlow, newForwardPathId, newReversePathId);
            stateMachine.setNewProtectedResources(flowResources);

            FlowPathPair createdPaths = createFlowPathPair(flowId, flowResources, allocatedPaths, false,
                    stateMachine.getSharedBandwidthGroupId());
            log.debug("New protected path has been created: {}", createdPaths);

            saveAllocationActionWithDumpsToHistory(stateMachine, tmpFlow, "protected", createdPaths);
        }
    }

    @Override
    protected void onFailure(FlowUpdateFsm stateMachine) {
        stateMachine.setNewProtectedResources(null);
        stateMachine.setNewProtectedForwardPath(null);
        stateMachine.setNewProtectedReversePath(null);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not update flow";
    }

    @Override
    protected void handleError(FlowUpdateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
