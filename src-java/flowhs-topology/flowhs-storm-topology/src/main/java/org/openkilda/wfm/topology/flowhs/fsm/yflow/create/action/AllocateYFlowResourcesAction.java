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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action;

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
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.YPointResources;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
public class AllocateYFlowResourcesAction extends
        YFlowProcessingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final int resourceAllocationRetriesLimit;
    private final PathComputer pathComputer;
    private final FlowResourcesManager resourcesManager;

    public AllocateYFlowResourcesAction(PersistenceManager persistenceManager,
                                        int resourceAllocationRetriesLimit,
                                        PathComputer pathComputer, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourceAllocationRetriesLimit = resourceAllocationRetriesLimit;
        this.pathComputer = pathComputer;
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        try {
            String yFlowId = stateMachine.getYFlowId();
            YFlowResources newResources;
            // This could be a retry.
            if (stateMachine.getNewResources() != null) {
                newResources = stateMachine.getNewResources();
            } else {
                newResources = new YFlowResources();
                stateMachine.setNewResources(newResources);
            }

            YFlow yFlow = getYFlow(yFlowId);
            SwitchId sharedEndpoint = yFlow.getSharedEndpoint().getSwitchId();
            if (newResources.getMainPathResources() == null) {
                FlowPath[] subFlowsForwardPaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getSubFlow)
                        .map(Flow::getForwardPath)
                        .toArray(FlowPath[]::new);
                FlowPath[] subFlowsReversePaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getSubFlow)
                        .map(Flow::getReversePath)
                        .toArray(FlowPath[]::new);

                YPointResources yPointResources = allocateYPointResources(yFlowId, sharedEndpoint,
                        yFlow.getMaximumBandwidth(), subFlowsForwardPaths, subFlowsReversePaths);
                newResources.setMainPathResources(yPointResources);

                stateMachine.saveActionToHistory("A new meter was allocated for the y-flow",
                        format("A new meter %s / %s was allocated", yPointResources.getMeterId(),
                                yPointResources.getYPoint()));
            }

            if (yFlow.isAllocateProtectedPath() && newResources.getProtectedPathResources() == null) {
                FlowPath[] subFlowsForwardPaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getSubFlow)
                        .map(Flow::getProtectedForwardPath)
                        .toArray(FlowPath[]::new);
                FlowPath[] subFlowsReversePaths = yFlow.getSubFlows().stream()
                        .map(YSubFlow::getSubFlow)
                        .map(Flow::getProtectedReversePath)
                        .toArray(FlowPath[]::new);

                YPointResources yPointResources = allocateYPointResources(yFlowId, sharedEndpoint,
                        yFlow.getMaximumBandwidth(), subFlowsForwardPaths, subFlowsReversePaths);
                newResources.setProtectedPathResources(yPointResources);

                stateMachine.saveActionToHistory("A new meter was allocated for the y-flow",
                        format("A new meter %s / %s was allocated", yPointResources.getMeterId(),
                                yPointResources.getYPoint()));
            }
        } catch (ResourceAllocationException ex) {
            String errorMessage = format("Failed to allocate y-flow resources. %s", ex.getMessage());
            stateMachine.saveErrorToHistory(errorMessage, ex);
            stateMachine.fireError(errorMessage);
        }
    }

    private YPointResources allocateYPointResources(String yFlowId, SwitchId sharedEndpoint, long bandwidth,
                                                    FlowPath[] subFlowsForwardPaths, FlowPath[] subFlowsReversePaths)
            throws ResourceAllocationException {
        SwitchId forwardYPoint = pathComputer.getIntersectionPoint(sharedEndpoint, subFlowsForwardPaths);
        SwitchId reverseYPoint = pathComputer.getIntersectionPoint(sharedEndpoint, subFlowsReversePaths);

        if (!forwardYPoint.equals(reverseYPoint)) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    format("Y-flow %s has different y-points for forward and reverse paths: %s / %s", yFlowId,
                            forwardYPoint, reverseYPoint));
        }

        MeterId meterId = null;
        if (bandwidth > 0L) {
            log.debug("Allocating resources for y-flow {}", yFlowId);
            meterId = allocateMeter(yFlowId, forwardYPoint);
        } else {
            log.debug("Meter is not required for y-flow {}", yFlowId);
        }

        return YPointResources.builder().yPoint(forwardYPoint).meterId(meterId).build();
    }

    @SneakyThrows
    protected MeterId allocateMeter(String flowId, SwitchId switchId) throws ResourceAllocationException {
        RetryPolicy<MeterId> resourceAllocationRetryPolicy =
                transactionManager.<MeterId>getDefaultRetryPolicy()
                        .handle(ResourceAllocationException.class)
                        .handle(ConstraintViolationException.class)
                        .onRetry(e -> log.warn("Failure in resource allocation. Retrying #{}...", e.getAttemptCount(),
                                e.getLastFailure()))
                        .onRetriesExceeded(e -> log.warn("Failure in resource allocation. No more retries",
                                e.getFailure()))
                        .withMaxRetries(resourceAllocationRetriesLimit);
        MeterId meterId = transactionManager.doInTransaction(resourceAllocationRetryPolicy,
                () -> resourcesManager.allocateMeter(flowId, switchId));
        log.debug("Meter {} has been allocated for y-flow {}", meterId, flowId);
        return meterId;
    }
}
