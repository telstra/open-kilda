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
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flow.model.HaFlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.BaseHaResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Slf4j
public class AllocateProtectedResourcesAction extends
        BaseHaResourceAllocationAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {

    public static final String PATHS_TYPE = "protected";

    public AllocateProtectedResourcesAction(
            PersistenceManager persistenceManager, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
            int resourceAllocationRetriesLimit, PathComputer pathComputer, FlowResourcesManager resourcesManager,
            FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                pathComputer, resourcesManager, dashboardLogger);
    }

    @Override
    protected boolean isAllocationRequired(HaFlowRerouteFsm stateMachine) {
        return stateMachine.isRerouteProtected();
    }

    @TimedExecution("fsm.resource_allocation_protected")
    @Override
    protected void allocate(HaFlowRerouteFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        String haFlowId = stateMachine.getHaFlowId();

        HaFlow haFlow = getHaFlow(haFlowId);
        // Detach the entity to avoid propagation to the database.
        haFlowRepository.detach(haFlow);
        if (stateMachine.getNewEncapsulationType() != null) {
            // This is for PCE to use proper (updated) encapsulation type.
            haFlow.setEncapsulationType(stateMachine.getNewEncapsulationType());
        }

        HaFlowPathPair oldPaths = new HaFlowPathPair(haFlow.getProtectedForwardPath(),
                haFlow.getProtectedReversePath());
        HaFlowPath primaryForward = getHaFlowPath(
                haFlow, stateMachine.getNewPrimaryPathIds().getForward().getHaPathId());
        HaFlowPath primaryReverse = getHaFlowPath(haFlow,
                stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId());
        Predicate<GetHaPathsResult> testNonOverlappingPath = buildNonOverlappingPathPredicate(
                primaryForward, primaryReverse);

        HaPathIdsPair newPathIdsPair = resourcesManager.generateHaPathIds(haFlowId, haFlow.getHaSubFlows());

        List<PathId> pathIdsToReuse = new ArrayList<>(haFlow.getProtectedSubPathIds());
        pathIdsToReuse.addAll(stateMachine.getRejectedSubPathsIds());

        log.debug("Finding a new protected path for HA-flow {}", haFlowId);
        GetHaPathsResult allocatedPaths = allocatePathPair(haFlow, newPathIdsPair,
                stateMachine.isIgnoreBandwidth(), pathIdsToReuse, oldPaths, stateMachine.isRecreateIfSamePath(),
                testNonOverlappingPath, true);
        if (allocatedPaths != null) {
            stateMachine.setBackUpProtectedPathComputationWayUsed(allocatedPaths.isBackUpPathComputationWayUsed());

            if (!testNonOverlappingPath.test(allocatedPaths)) {
                stateMachine.saveActionToHistory("Couldn't find non overlapping protected path. Skipped creating it");
                stateMachine.fireNoPathFound("Couldn't find non overlapping protected path");
            } else {
                log.debug("New protected paths have been allocated: {}", allocatedPaths);
                stateMachine.addBackUpComputationStatuses(allocatedPaths, newPathIdsPair);
                stateMachine.setNewProtectedPathIds(newPathIdsPair);

                log.debug("Allocating resources for a new protected path of HA-flow {}", haFlowId);
                HaFlowResources haFlowResources = allocateFlowResources(
                        haFlow, allocatedPaths.getForward().getYPointSwitchId(), newPathIdsPair);
                stateMachine.setNewProtectedResources(haFlowResources);

                HaFlowPathPair createdPaths = createHaFlowPathPair(haFlowId, haFlowResources, allocatedPaths,
                        stateMachine.isIgnoreBandwidth());
                log.debug("New protected paths have been created: {}", createdPaths);

                saveAllocationActionToHistory(stateMachine, haFlow, PATHS_TYPE, createdPaths);
            }
        } else {
            stateMachine.saveActionToHistory("Found the same protected path. Skipped creating of it");
        }
    }

    @Override
    protected void onFailure(HaFlowRerouteFsm stateMachine) {
        stateMachine.setNewProtectedResources(null);
        stateMachine.setNewProtectedPathIds(null);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute HA-flow";
    }

    @Override
    protected void handleError(HaFlowRerouteFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
