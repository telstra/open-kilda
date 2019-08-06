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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;
import static java.util.Arrays.asList;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AllocateProtectedResourcesAction extends BaseResourceAllocationAction {
    public AllocateProtectedResourcesAction(PersistenceManager persistenceManager, PathComputer pathComputer,
                                            FlowResourcesManager resourcesManager,
                                            FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, pathComputer, resourcesManager, dashboardLogger);
    }

    @Override
    protected boolean isAllocationRequired(FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        return stateMachine.isRerouteProtected();
    }

    @Override
    protected void allocate(FlowRerouteContext context, FlowRerouteFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        String flowId = stateMachine.getFlowId();
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        "Could not create a new path", format("Flow %s not found", flowId)));

        FlowPath primaryForwardPath = flow.getPath(stateMachine.getNewPrimaryForwardPath())
                .orElse(flow.getForwardPath());
        FlowPath primaryReversePath = flow.getPath(stateMachine.getNewPrimaryReversePath())
                .orElse(flow.getReversePath());

        if (stateMachine.getNewEncapsulationType() != null) {
            // This is for PCE to use proper (updated) encapsulation type.
            flow.setEncapsulationType(stateMachine.getNewEncapsulationType());
        }

        log.debug("Finding a new protected path for flow {}", flowId);
        PathPair potentialPath = pathComputer.getPath(flow,
                asList(flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()));

        boolean overlappingProtectedPathFound =
                flowPathBuilder.arePathsOverlapped(potentialPath.getForward(), primaryForwardPath)
                        || flowPathBuilder.arePathsOverlapped(potentialPath.getReverse(), primaryReversePath);
        if (overlappingProtectedPathFound) {
            log.warn("Can't find non overlapping new protected path for flow {}. Skip creating it.",
                    flow.getFlowId());

            // Update the status here as no reroute is going to be performed for the protected.
            FlowPath protectedForwardPath = flow.getProtectedForwardPath();
            flowPathRepository.updateStatus(protectedForwardPath.getPathId(), FlowPathStatus.INACTIVE);

            FlowPath protectedReversePath = flow.getProtectedReversePath();
            flowPathRepository.updateStatus(protectedReversePath.getPathId(), FlowPathStatus.INACTIVE);

            FlowStatus flowStatus = flow.computeFlowStatus();
            if (flowStatus != flow.getStatus()) {
                dashboardLogger.onFlowStatusUpdate(flowId, flowStatus);
                flowRepository.updateStatus(flowId, flowStatus);
            }
            stateMachine.setOriginalFlowStatus(null);
        } else {
            boolean newPathFound = isNotSamePath(potentialPath, flow.getProtectedForwardPath(),
                    flow.getProtectedReversePath());
            if (newPathFound || stateMachine.isRecreateIfSamePath()) {
                if (!newPathFound) {
                    log.debug("Found the same protected path for flow {}. Proceed with recreating it.", flowId);
                }

                log.debug("Allocating resources for a new protected path of flow {}", flowId);
                FlowResources flowResources = resourcesManager.allocateFlowResources(flow);
                log.debug("Resources have been allocated: {}", flowResources);
                stateMachine.addNewResources(flowResources);

                FlowPathPair paths = createFlowPathPair(flow, potentialPath, flowResources);
                log.debug("New protected path has been created: {}", paths);
                stateMachine.setNewProtectedForwardPath(paths.getForward().getPathId());
                stateMachine.setNewProtectedReversePath(paths.getReverse().getPathId());

                FlowPathPair oldPaths = FlowPathPair.builder()
                        .forward(flow.getForwardPath())
                        .reverse(flow.getReversePath())
                        .build();
                saveHistory(stateMachine, flow, oldPaths, paths);
            } else {
                log.debug("Found the same protected path for flow {}. Skip creating of it.", flowId);
            }
        }
    }
}
