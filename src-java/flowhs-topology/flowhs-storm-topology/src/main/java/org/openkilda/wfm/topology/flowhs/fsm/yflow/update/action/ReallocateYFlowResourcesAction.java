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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ReallocateYFlowResourcesAction extends
        YFlowProcessingAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    private final PathComputer pathComputer;

    public ReallocateYFlowResourcesAction(PersistenceManager persistenceManager, PathComputer pathComputer) {
        super(persistenceManager);
        this.pathComputer = pathComputer;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        try {
            String yFlowId = stateMachine.getYFlowId();
            YFlowResources reallocatedResources;
            // This could be a retry.
            if (stateMachine.getReallocatedResources() != null) {
                reallocatedResources = stateMachine.getReallocatedResources();
            } else {
                reallocatedResources = new YFlowResources();
                stateMachine.setReallocatedResources(reallocatedResources);
            }

            YFlow yFlow = getYFlow(yFlowId);
            SwitchId sharedSwitchId = yFlow.getSharedEndpoint().getSwitchId();
            YFlowResources oldResources = stateMachine.getOldResources();
            if (reallocatedResources.getMainPathYPointResources() == null) {
                FlowPath[] subFlowsForwardPaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getFlow)
                        .map(Flow::getForwardPath)
                        .toArray(FlowPath[]::new);
                FlowPath[] subFlowsReversePaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getFlow)
                        .map(Flow::getReversePath)
                        .toArray(FlowPath[]::new);

                MeterId meterId = Optional.ofNullable(oldResources.getMainPathYPointResources())
                        .map(EndpointResources::getMeterId).orElse(null);
                EndpointResources endpointResources = allocateYPointResources(yFlowId, sharedSwitchId,
                        meterId, subFlowsForwardPaths, subFlowsReversePaths);
                reallocatedResources.setMainPathYPointResources(endpointResources);

                stateMachine.saveActionToHistory("A new y-point was found for the y-flow",
                        format("A new y-point %s was found", endpointResources.getEndpoint()));
            }

            if (yFlow.isAllocateProtectedPath() && reallocatedResources.getProtectedPathYPointResources() == null) {
                FlowPath[] subFlowsForwardPaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getFlow)
                        .map(Flow::getProtectedForwardPath)
                        .toArray(FlowPath[]::new);
                FlowPath[] subFlowsReversePaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getFlow)
                        .map(Flow::getProtectedReversePath)
                        .toArray(FlowPath[]::new);

                MeterId meterId = Optional.ofNullable(oldResources.getProtectedPathYPointResources())
                        .map(EndpointResources::getMeterId).orElse(null);
                EndpointResources endpointResources = allocateYPointResources(yFlowId, sharedSwitchId,
                        meterId, subFlowsForwardPaths, subFlowsReversePaths);
                reallocatedResources.setProtectedPathYPointResources(endpointResources);

                stateMachine.saveActionToHistory("A new y-point was found for the y-flow",
                        format("A new y-point %s was found", endpointResources.getEndpoint()));
            }

            transactionManager.doInTransaction(() -> {
                YFlow flow = getYFlow(yFlowId);
                flow.setYPoint(reallocatedResources.getMainPathYPointResources().getEndpoint());
                flow.setMeterId(reallocatedResources.getMainPathYPointResources().getMeterId());
                if (reallocatedResources.getProtectedPathYPointResources() != null) {
                    flow.setProtectedPathYPoint(reallocatedResources.getProtectedPathYPointResources().getEndpoint());
                    flow.setProtectedPathMeterId(reallocatedResources.getProtectedPathYPointResources().getMeterId());
                }
            });
        } catch (ResourceAllocationException ex) {
            String errorMessage = format("Failed to allocate y-flow resources. %s", ex.getMessage());
            stateMachine.saveErrorToHistory(errorMessage, ex);
            stateMachine.fireError(errorMessage);
        }
    }

    private EndpointResources allocateYPointResources(String yFlowId, SwitchId sharedEndpoint, MeterId meterId,
                                                    FlowPath[] subFlowsForwardPaths, FlowPath[] subFlowsReversePaths)
            throws ResourceAllocationException {
        SwitchId forwardYPoint = pathComputer.getIntersectionPoint(sharedEndpoint, subFlowsForwardPaths);
        SwitchId reverseYPoint = pathComputer.getIntersectionPoint(sharedEndpoint, subFlowsReversePaths);

        if (!forwardYPoint.equals(reverseYPoint)) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Y-flow %s has different y-points for forward and reverse paths: %s / %s", yFlowId,
                            forwardYPoint, reverseYPoint));
        }

        return EndpointResources.builder().endpoint(forwardYPoint).meterId(meterId).build();
    }
}
