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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import org.openkilda.model.Flow;
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
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
public class AllocatePrimaryResourcesAction extends
        BaseResourceAllocationAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    public AllocatePrimaryResourcesAction(PersistenceManager persistenceManager,
                                          int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                          int resourceAllocationRetriesLimit,
                                          PathComputer pathComputer, FlowResourcesManager resourcesManager,
                                          FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                pathComputer, resourcesManager, dashboardLogger);
    }

    @Override
    protected boolean isAllocationRequired(FlowUpdateFsm stateMachine) {
        // The primary path is always required to be updated.
        return true;
    }

    @Override
    protected void allocate(FlowUpdateFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        String flowId = stateMachine.getFlowId();

        Set<String> flowIds = Sets.newHashSet(flowId);
        if (stateMachine.getBulkUpdateFlowIds() != null) {
            flowIds.addAll(stateMachine.getBulkUpdateFlowIds());
        }
        log.debug("Finding paths for flows {}", flowIds);
        List<PathId> pathIdsToReuse = new ArrayList<>(flowPathRepository.findActualPathIdsByFlowIds(flowIds));
        pathIdsToReuse.addAll(stateMachine.getRejectedPaths());

        Flow tmpFlow = getFlow(flowId);
        FlowPathPair oldPaths = new FlowPathPair(tmpFlow.getForwardPath(), tmpFlow.getReversePath());
        PathId newForwardPathId = resourcesManager.generatePathId(flowId);
        PathId newReversePathId = resourcesManager.generatePathId(flowId);

        log.debug("Finding a new primary path for flow {}", flowId);
        GetPathsResult allocatedPaths = allocatePathPair(tmpFlow, newForwardPathId, newReversePathId,
                false, pathIdsToReuse, oldPaths, true, path -> true);
        if (allocatedPaths == null) {
            throw new ResourceAllocationException("Unable to allocate a path");
        }
        log.debug("New primary paths have been allocated: {}", allocatedPaths);
        stateMachine.setBackUpPrimaryPathComputationWayUsed(allocatedPaths.isBackUpPathComputationWayUsed());
        stateMachine.setNewPrimaryForwardPath(newForwardPathId);
        stateMachine.setNewPrimaryReversePath(newReversePathId);

        log.debug("Allocating resources for a new primary path of flow {}", flowId);
        FlowResources flowResources = allocateFlowResources(tmpFlow, newForwardPathId, newReversePathId);
        stateMachine.setNewPrimaryResources(flowResources);

        FlowPathPair createdPaths = createFlowPathPair(flowId, flowResources, allocatedPaths, false);
        log.debug("New primary path has been created: {}", createdPaths);

        setMirrorPointsToNewPath(oldPaths.getForwardPathId(), newForwardPathId);
        setMirrorPointsToNewPath(oldPaths.getReversePathId(), newReversePathId);

        saveAllocationActionWithDumpsToHistory(stateMachine, tmpFlow, "primary", createdPaths);
    }

    @Override
    protected void onFailure(FlowUpdateFsm stateMachine) {
        stateMachine.setNewPrimaryResources(null);
        stateMachine.setNewPrimaryForwardPath(null);
        stateMachine.setNewPrimaryReversePath(null);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not update flow";
    }
}
