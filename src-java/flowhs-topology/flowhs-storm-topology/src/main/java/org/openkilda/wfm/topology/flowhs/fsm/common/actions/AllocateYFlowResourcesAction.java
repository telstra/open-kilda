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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources;
import org.openkilda.wfm.topology.flowhs.model.yflow.YFlowResources.EndpointResources;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AllocateYFlowResourcesAction<T extends YFlowProcessingFsm<T, S, E, C, ?, ?>, S, E, C>
        extends YFlowProcessingWithHistorySupportAction<T, S, E, C> {
    private final PathComputer pathComputer;
    private final FlowResourcesManager resourcesManager;
    private final RetryPolicy<MeterId> resourceAllocationRetryPolicy;

    public AllocateYFlowResourcesAction(PersistenceManager persistenceManager,
                                        int resourceAllocationRetriesLimit,
                                        PathComputer pathComputer, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.pathComputer = pathComputer;
        this.resourcesManager = resourcesManager;

        resourceAllocationRetryPolicy =
                transactionManager.<MeterId>getDefaultRetryPolicy()
                        .handle(ResourceAllocationException.class)
                        .handle(ConstraintViolationException.class)
                        .onRetry(e -> log.warn("Failure in resource allocation. Retrying #{}...", e.getAttemptCount(),
                                e.getLastFailure()))
                        .onRetriesExceeded(e -> log.warn("Failure in resource allocation. No more retries",
                                e.getFailure()))
                        .withMaxRetries(resourceAllocationRetriesLimit);
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
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
            if (newResources.getSharedEndpointResources() == null) {
                EndpointResources sharedEndpointResources =
                        allocateMeterAsEndpointResources(yFlowId, sharedEndpoint, yFlow.getMaximumBandwidth());
                newResources.setSharedEndpointResources(sharedEndpointResources);

                stateMachine.saveActionToHistory("A new meter was allocated for the y-flow shared endpoint",
                        format("A new meter %s / %s was allocated", sharedEndpointResources.getMeterId(),
                                sharedEndpointResources.getEndpoint()));
            }

            if (newResources.getMainPathYPointResources() == null) {
                List<FlowPath> subFlowsReversePaths = new ArrayList<>();
                yFlow.getSubFlows().forEach(subFlow -> {
                    Flow flow = subFlow.getFlow();
                    FlowPath path = flow.getReversePath();
                    if (path == null) {
                        throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                                format("Missing a reverse path for %s sub-flow", flow.getFlowId()));
                    } else if (!flow.isOneSwitchFlow()) {
                        subFlowsReversePaths.add(path);
                    }
                });

                EndpointResources yPointResources = allocateYPointResources(yFlowId, sharedEndpoint,
                        yFlow.getMaximumBandwidth(), subFlowsReversePaths.toArray(new FlowPath[0]));
                newResources.setMainPathYPointResources(yPointResources);

                stateMachine.saveActionToHistory("A new meter was allocated for the y-flow y-point",
                        format("A new meter %s / %s was allocated", yPointResources.getMeterId(),
                                yPointResources.getEndpoint()));
            }

            if (yFlow.isAllocateProtectedPath() && newResources.getProtectedPathYPointResources() == null) {
                List<FlowPath> subFlowsReversePaths = new ArrayList<>();
                yFlow.getSubFlows().forEach(subFlow -> {
                    Flow flow = subFlow.getFlow();
                    FlowPath path = flow.getProtectedReversePath();
                    if (path == null) {
                        if (flow.getStatus() == FlowStatus.UP) {
                            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                                    format("Missing a protected path for %s sub-flow", flow.getFlowId()));
                        } else {
                            log.warn("Sub-flow {} has no expected protected path and status {}",
                                    flow.getFlowId(), flow.getStatus());
                        }
                    } else if (!flow.isOneSwitchFlow()) {
                        subFlowsReversePaths.add(path);
                    }
                });

                if (subFlowsReversePaths.size() > 1) {
                    EndpointResources yPointResources = allocateYPointResources(yFlowId, sharedEndpoint,
                            yFlow.getMaximumBandwidth(), subFlowsReversePaths.toArray(new FlowPath[0]));
                    newResources.setProtectedPathYPointResources(yPointResources);

                    stateMachine.saveActionToHistory("A new meter was allocated for the y-flow protected path y-point",
                            format("A new meter %s / %s was allocated", yPointResources.getMeterId(),
                                    yPointResources.getEndpoint()));
                } else {
                    stateMachine.saveActionToHistory("Skip meter allocation for the y-flow protected path y-point",
                            "Y-flow protected path y-point can't be found - sub-flow(s) lacks a protected path");
                }
            }

            transactionManager.doInTransaction(() -> {
                YFlow yFlowToUpdate = getYFlow(yFlowId);
                yFlowToUpdate.setYPoint(newResources.getMainPathYPointResources().getEndpoint());
                yFlowToUpdate.setMeterId(newResources.getMainPathYPointResources().getMeterId());
                if (newResources.getProtectedPathYPointResources() != null) {
                    yFlowToUpdate.setProtectedPathYPoint(newResources.getProtectedPathYPointResources().getEndpoint());
                    yFlowToUpdate.setProtectedPathMeterId(newResources.getProtectedPathYPointResources().getMeterId());
                } else {
                    yFlowToUpdate.setProtectedPathYPoint(null);
                    yFlowToUpdate.setProtectedPathMeterId(null);
                }
                yFlowToUpdate.setSharedEndpointMeterId(newResources.getSharedEndpointResources().getMeterId());
            });

            notifyStats(stateMachine, newResources);
        } catch (ResourceAllocationException ex) {
            String errorMessage = format("Failed to allocate y-flow resources. %s", ex.getMessage());
            stateMachine.saveErrorToHistory(errorMessage, ex);
            stateMachine.fireError(errorMessage);
        }
    }

    private EndpointResources allocateYPointResources(String yFlowId, SwitchId sharedEndpoint, long bandwidth,
                                                      FlowPath[] subFlowsReversePaths)
            throws ResourceAllocationException {
        SwitchId reverseYPoint;
        if (subFlowsReversePaths.length != 0) {
            reverseYPoint = pathComputer.getIntersectionPoint(sharedEndpoint, subFlowsReversePaths);
        } else {
            reverseYPoint = sharedEndpoint;
        }
        return allocateMeterAsEndpointResources(yFlowId, reverseYPoint, bandwidth);
    }

    private EndpointResources allocateMeterAsEndpointResources(String yFlowId, SwitchId switchId, long bandwidth)
            throws ResourceAllocationException {
        MeterId meterId = null;
        if (bandwidth > 0L) {
            log.debug("Allocating resources for y-flow {}", yFlowId);
            meterId = allocateMeter(yFlowId, switchId);
        } else {
            log.debug("Meter is not required for y-flow {}", yFlowId);
        }

        return EndpointResources.builder().endpoint(switchId).meterId(meterId).build();
    }

    @SneakyThrows
    protected MeterId allocateMeter(String yFlowId, SwitchId switchId) throws ResourceAllocationException {
        MeterId meterId = transactionManager.doInTransaction(resourceAllocationRetryPolicy,
                () -> resourcesManager.allocateMeter(yFlowId, switchId));
        log.debug("Meter {} has been allocated for y-flow {}", meterId, yFlowId);
        return meterId;
    }

    private void notifyStats(T fsm, YFlowResources resources) {
        fsm.sendAddOrUpdateStatsNotification(resources);
    }
}
