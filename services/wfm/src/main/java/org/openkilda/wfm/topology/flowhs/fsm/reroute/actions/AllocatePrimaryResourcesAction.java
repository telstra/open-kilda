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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AllocatePrimaryResourcesAction extends BaseResourceAllocationAction {
    public AllocatePrimaryResourcesAction(PersistenceManager persistenceManager, PathComputer pathComputer,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager, pathComputer, resourcesManager);
    }

    @Override
    protected boolean isAllocationRequired(FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        return stateMachine.isReroutePrimary();
    }

    @Override
    protected void allocate(FlowRerouteContext context, FlowRerouteFsm stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        String flowId = stateMachine.getFlowId();
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        "Could not create a new path", format("Flow %s not found", flowId)));

        if (stateMachine.getNewEncapsulationType() != null) {
            flow.setEncapsulationType(stateMachine.getNewEncapsulationType());
        }

        log.debug("Finding a new primary path for flow {}", flowId);
        PathPair potentialPath = pathComputer.getPath(flow, flow.getFlowPathIds());
        boolean newPathFound = isNotSamePath(potentialPath, flow.getForwardPath(), flow.getReversePath());
        if (newPathFound || stateMachine.isRecreateIfSamePath()) {
            if (!newPathFound) {
                log.debug("Found the same primary path for flow {}. Proceed with recreating it.", flowId);
            }

            log.debug("Allocating resources for a new primary path of flow {}", flowId);
            FlowResources flowResources = resourcesManager.allocateFlowResources(flow);
            log.debug("Resources have been allocated: {}", flowResources);
            stateMachine.addNewResources(flowResources);

            FlowPathPair paths = createFlowPathPair(flow, potentialPath, flowResources);
            log.debug("New primary path has been created: {}", paths);
            stateMachine.setNewPrimaryForwardPath(paths.getForward().getPathId());
            stateMachine.setNewPrimaryReversePath(paths.getReverse().getPathId());

            flowRepository.updateStatus(flow.getFlowId(), FlowStatus.IN_PROGRESS);

            FlowPathPair oldPaths = FlowPathPair.builder()
                    .forward(flow.getForwardPath())
                    .reverse(flow.getReversePath())
                    .build();
            saveHistory(stateMachine, flow, oldPaths, paths);
        } else {
            log.debug("Found the same primary path for flow {}. Skip creating of it.", flowId);
        }
    }
}
