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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.PathSegment;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.IslRepository.IslEndpoints;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.service.FlowPathBuilder;

import com.google.common.annotations.VisibleForTesting;
import dev.failsafe.RetryPolicy;
import dev.failsafe.RetryPolicyBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A base for action classes that allocate resources for flow paths.
 */
@Slf4j
public abstract class BaseResourceAllocationAction<T
        extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E, C> extends
        NbTrackableWithHistorySupportAction<T, S, E, C> {
    protected final int pathAllocationRetriesLimit;
    protected final int pathAllocationRetryDelay;
    protected final int resourceAllocationRetriesLimit;
    protected final SwitchRepository switchRepository;
    protected final IslRepository islRepository;
    protected final PathSegmentRepository pathSegmentRepository;
    protected final PathComputer pathComputer;
    protected final FlowResourcesManager resourcesManager;
    protected final FlowPathBuilder flowPathBuilder;
    protected final FlowOperationsDashboardLogger dashboardLogger;

    protected BaseResourceAllocationAction(PersistenceManager persistenceManager,
                                           int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                           int resourceAllocationRetriesLimit,
                                           PathComputer pathComputer, FlowResourcesManager resourcesManager,
                                           FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.pathAllocationRetriesLimit = pathAllocationRetriesLimit;
        this.pathAllocationRetryDelay = pathAllocationRetryDelay;
        this.resourceAllocationRetriesLimit = resourceAllocationRetriesLimit;

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        islRepository = repositoryFactory.createIslRepository();
        pathSegmentRepository = repositoryFactory.createPathSegmentRepository();
        flowPathBuilder = new FlowPathBuilder();

        this.pathComputer = pathComputer;
        this.resourcesManager = resourcesManager;
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected final Optional<Message> performWithResponse(S from, S to, E event, C context, T stateMachine) {
        if (!isAllocationRequired(stateMachine)) {
            return Optional.empty();
        }

        try {
            allocateAndCheck(stateMachine);

            return Optional.empty();
        } catch (UnroutableFlowException ex) {
            String errorMessage;
            if (ex.isIgnoreBandwidth()) {
                errorMessage = format("No path found. %s", ex.getMessage());
            } else {
                errorMessage = format("Not enough bandwidth or no path found. %s", ex.getMessage());
            }
            stateMachine.saveActionToHistory(errorMessage);
            firePathNotFound(stateMachine, errorMessage);

            ErrorType errorType = ErrorType.NOT_FOUND;
            Message message = stateMachine.buildErrorMessage(errorType, getGenericErrorMessage(), errorMessage);
            stateMachine.setOperationResultMessage(message);

            notifyEventListeners(stateMachine, errorMessage, errorType);
            return Optional.of(message);
        } catch (RecoverableException ex) {
            String errorMessage = format("Failed to find a path. %s", ex.getMessage());
            stateMachine.saveActionToHistory(errorMessage);
            stateMachine.fireError(errorMessage);

            ErrorType errorType = ErrorType.INTERNAL_ERROR;
            Message message = stateMachine.buildErrorMessage(errorType, getGenericErrorMessage(), errorMessage);
            stateMachine.setOperationResultMessage(message);

            notifyEventListeners(stateMachine, errorMessage, errorType);
            return Optional.of(message);
        } catch (ResourceAllocationException ex) {
            String errorMessage = format("Failed to allocate flow resources. %s", ex.getMessage());
            stateMachine.saveErrorToHistory(errorMessage, ex);
            stateMachine.fireError(errorMessage);

            ErrorType errorType = ErrorType.INTERNAL_ERROR;
            Message message = stateMachine.buildErrorMessage(errorType, getGenericErrorMessage(), errorMessage);
            stateMachine.setOperationResultMessage(message);

            notifyEventListeners(stateMachine, errorMessage, errorType);
            return Optional.of(message);
        }
    }

    protected abstract void notifyEventListeners(T stateMachine, String errorMessage, ErrorType errorType);

    protected abstract void firePathNotFound(T stateMachine, String errorMessage);

    /**
     * Check whether allocation is required, otherwise it's being skipped.
     */
    protected abstract boolean isAllocationRequired(T stateMachine);

    /**
     * Perform resource allocation, returns the allocated resources.
     */
    protected abstract void allocate(T stateMachine)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException;

    /**
     * Called in a case of allocation failure.
     */
    protected abstract void onFailure(T stateMachine);

    /**
     * Perform resource allocation in a transaction.
     */
    private void allocateAndCheck(T stateMachine) throws RecoverableException, UnroutableFlowException,
            ResourceAllocationException {
        try {
            allocate(stateMachine);
        } catch (Exception ex) {
            onFailure(stateMachine);
            throw ex;
        }
        log.debug("Resources allocated successfully for the flow {}", stateMachine.getFlowId());

        try {
            checkAllocatedPaths(stateMachine);
        } catch (ResourceAllocationException ex) {
            saveRejectedResources(stateMachine);
            throw ex;
        }
    }

    protected <P> RetryPolicy<P> getPathAllocationRetryPolicy() {
        RetryPolicyBuilder<P> pathAllocationRetryPolicy = RetryPolicy.<P>builder()
                .handle(RecoverableException.class)
                .handle(ResourceAllocationException.class)
                .handle(UnroutableFlowException.class)
                .handle(PersistenceException.class)
                .onRetry(e -> log.warn("Failure in path allocation. Retrying #{}...", e.getAttemptCount(),
                        e.getLastException()))
                .onRetriesExceeded(e -> log.warn("Failure in path allocation. No more retries", e.getException()))
                .withMaxRetries(pathAllocationRetriesLimit);
        if (pathAllocationRetryDelay > 0) {
            pathAllocationRetryPolicy.withDelay(Duration.ofMillis(pathAllocationRetryDelay));
        }
        return pathAllocationRetryPolicy.build();
    }

    @VisibleForTesting
    protected void createPathSegments(List<PathSegment> segments, Supplier<Map<IslEndpoints, Long>> reuseBandwidth)
            throws ResourceAllocationException {
        for (PathSegment segment : segments) {
            log.debug("Persisting the segment {}", segment);
            long updatedAvailableBandwidth =
                    pathSegmentRepository.addSegmentAndUpdateIslAvailableBandwidth(segment).orElse(0L);
            if (!segment.isIgnoreBandwidth() && updatedAvailableBandwidth < 0) {
                IslEndpoints isl = new IslEndpoints(segment.getSrcSwitchId().toString(), segment.getSrcPort(),
                        segment.getDestSwitchId().toString(), segment.getDestPort());
                log.debug("ISL {} is being over-provisioned, check if it's allowed", isl);

                long allowedOverprovisionedBandwidth = reuseBandwidth.get().getOrDefault(isl, 0L);
                if ((updatedAvailableBandwidth + allowedOverprovisionedBandwidth) < 0) {
                    throw new ResourceAllocationException(format("ISL %s_%d-%s_%d was overprovisioned",
                            isl.getSrcSwitch(), isl.getSrcPort(), isl.getDestSwitch(), isl.getDestPort()));
                }
            }
        }
    }

    protected <R> RetryPolicy<R> getResourcesAllocationRetryPolicy() {
        return transactionManager.<R>getDefaultRetryPolicy()
                .handle(ResourceAllocationException.class)
                .handle(ConstraintViolationException.class)
                .onRetry(e -> log.warn("Failure in resource allocation. Retrying #{}...", e.getAttemptCount(),
                        e.getLastException()))
                .onRetriesExceeded(e -> log.warn("Failure in resource allocation. No more retries",
                        e.getException()))
                .withMaxRetries(resourceAllocationRetriesLimit)
                .build();
    }

    protected abstract void checkAllocatedPaths(T stateMachine) throws ResourceAllocationException;

    protected abstract void saveRejectedResources(T stateMachine);
}
