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
import org.openkilda.messaging.info.reroute.error.NoPathFoundError;
import org.openkilda.model.HaFlow;
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
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AllocatePrimaryResourcesAction extends
        BaseHaResourceAllocationAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {

    public static final String PATHS_TYPE = "primary";

    public AllocatePrimaryResourcesAction(
            PersistenceManager persistenceManager, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
            int resourceAllocationRetriesLimit, PathComputer pathComputer, FlowResourcesManager resourcesManager,
            FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                pathComputer, resourcesManager, dashboardLogger);
    }

    @Override
    protected boolean isAllocationRequired(HaFlowRerouteFsm stateMachine) {
        return stateMachine.isReroutePrimary();
    }

    @TimedExecution("fsm.resource_allocation_primary")
    @Override
    protected void allocate(HaFlowRerouteFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        String haFlowId = stateMachine.getFlowId();

        HaFlow haFlow = getHaFlow(haFlowId);
        // Detach the entity to avoid propagation to the database.
        haFlowRepository.detach(haFlow);
        if (stateMachine.getNewEncapsulationType() != null) {
            // This is for PCE to use proper (updated) encapsulation type.
            haFlow.setEncapsulationType(stateMachine.getNewEncapsulationType());
        }

        List<PathId> pathIdsToReuse = new ArrayList<>(haFlow.getPrimarySubPathIds());
        // TODO add protected?
        pathIdsToReuse.addAll(stateMachine.getRejectedSubPathsIds());

        HaFlowPathPair oldPaths = new HaFlowPathPair(haFlow.getForwardPath(), haFlow.getReversePath());
        HaPathIdsPair newPathIdsPair = resourcesManager.generateHaPathIds(haFlowId, haFlow.getHaSubFlows());

        log.debug("Finding a new primary path for HA-flow {}", haFlowId);
        GetHaPathsResult allocatedPaths = allocatePathPair(haFlow, newPathIdsPair,
                stateMachine.isIgnoreBandwidth(), pathIdsToReuse, oldPaths, stateMachine.isRecreateIfSamePath(),
                path -> true, false);
        if (allocatedPaths != null) {
            log.debug("New primary paths have been allocated: {}", allocatedPaths);
            stateMachine.addBackUpComputationStatuses(allocatedPaths, newPathIdsPair);
            stateMachine.setNewPrimaryPathIds(newPathIdsPair);

            log.debug("Allocating resources for a new primary path of HA-flow {}", haFlowId);
            HaFlowResources haFlowResources = allocateFlowResources(
                    haFlow, allocatedPaths.getForward().getYPointSwitchId(), newPathIdsPair);
            stateMachine.setNewPrimaryResources(haFlowResources);

            HaFlowPathPair createdPaths = createHaFlowPathPair(haFlowId, haFlowResources, allocatedPaths,
                    stateMachine.isIgnoreBandwidth());
            log.debug("New primary paths have been created: {}", createdPaths);

            saveAllocationActionToHistory(stateMachine, haFlow, PATHS_TYPE, createdPaths);
        } else {
            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("Found the same primary path. Skipped creating of it")
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
    }

    @Override
    protected void onFailure(HaFlowRerouteFsm stateMachine) {
        stateMachine.setNewPrimaryResources(null);
        stateMachine.setNewPrimaryPathIds(null);
        if (!stateMachine.isIgnoreBandwidth()) {
            stateMachine.setRerouteError(new NoPathFoundError());
        }
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
