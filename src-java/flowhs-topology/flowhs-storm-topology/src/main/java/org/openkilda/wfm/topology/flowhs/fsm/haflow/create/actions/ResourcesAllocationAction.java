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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSegmentCookieBuilder;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSubType;
import org.openkilda.pce.GetHaPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.IslRepository.IslEndpoints;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowAlreadyExistException;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.flowhs.service.FlowPathBuilder;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class ResourcesAllocationAction extends
        NbTrackableWithHistorySupportAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private final PathComputer pathComputer;
    protected final int pathAllocationRetriesLimit;
    protected final int pathAllocationRetryDelay;
    private final FlowResourcesManager resourcesManager;
    private final IslRepository islRepository;
    private final HaFlowPathRepository haFlowPathRepository;

    private final FlowPathBuilder flowPathBuilder;

    public ResourcesAllocationAction(PathComputer pathComputer, PersistenceManager persistenceManager,
                                     int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                     FlowResourcesManager resourcesManager) {
        super(persistenceManager);

        this.pathComputer = pathComputer;
        this.pathAllocationRetriesLimit = pathAllocationRetriesLimit;
        this.pathAllocationRetryDelay = pathAllocationRetryDelay;
        this.resourcesManager = resourcesManager;
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        this.haFlowPathRepository = persistenceManager.getRepositoryFactory().createHaFlowPathRepository();
        KildaConfigurationRepository kildaConfigurationRepository = persistenceManager.getRepositoryFactory()
                .createKildaConfigurationRepository();

        this.flowPathBuilder = new FlowPathBuilder(persistenceManager.getRepositoryFactory()
                .createSwitchPropertiesRepository(), kildaConfigurationRepository);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, HaFlowCreateContext context,
                                                    HaFlowCreateFsm stateMachine) throws FlowProcessingException {
        try {
            String haFlowId = stateMachine.getHaFlowId();
            log.debug("Allocation resources has been started");
            stateMachine.setPathsBeenAllocated(false);

            if (context != null && context.getTargetFlow() != null) {
                createFlow(context.getTargetFlow());
            } else if (!flowRepository.exists(haFlowId)) {
                log.warn("HA-flow {} has been deleted while creation was in progress", haFlowId);
                return Optional.empty();
            }

            createPaths(stateMachine);

            log.debug("Resources allocated successfully for the flow {}", haFlowId);
            stateMachine.setPathsBeenAllocated(true);

            HaFlow resultHaFlow = getHaFlow(haFlowId);
            //TODO save history
            //saveHistory(stateMachine, resultHaFlow);
            stateMachine.fireNext(context);

            // Notify about successful allocation.
            stateMachine.notifyEventListeners(listener -> listener.onResourcesAllocated(haFlowId));

            return Optional.of(buildResponseMessage(resultHaFlow, stateMachine.getCommandContext()));
        } catch (UnroutableFlowException | RecoverableException e) {
            throw new FlowProcessingException(ErrorType.NOT_FOUND,
                    "Not enough bandwidth or no path found. " + e.getMessage(), e);
        } catch (ResourceAllocationException e) {
            throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                    "Failed to allocate flow resources. " + e.getMessage(), e);
        } catch (FlowNotFoundException e) {
            throw new FlowProcessingException(ErrorType.NOT_FOUND,
                    "Couldn't find the diverse flow. " + e.getMessage(), e);
        } catch (FlowAlreadyExistException e) {
            if (!stateMachine.retryIfAllowed()) {
                throw new FlowProcessingException(ErrorType.INTERNAL_ERROR, e.getMessage(), e);
            } else {
                // we have retried the operation, no need to respond.
                log.debug(e.getMessage(), e);
                return Optional.empty();
            }
        }
    }

    private void createFlow(HaFlowRequest targetFlow) throws FlowNotFoundException, FlowAlreadyExistException {
        try {
            transactionManager.doInTransaction(() -> {
                HaFlow haFlow = HaFlowMapper.INSTANCE.toHaFlow(targetFlow);
                haFlow.setStatus(FlowStatus.IN_PROGRESS);
                getOrCreateFlowDiverseGroup(targetFlow.getDiverseFlowId()).ifPresent(haFlow::setDiverseGroupId);
                haFlowRepository.add(haFlow);

                Set<HaSubFlow> subFlows = new HashSet<>();
                for (HaSubFlowDto subFlowDto : targetFlow.getSubFlows()) {
                    HaSubFlow subFlow = HaFlowMapper.INSTANCE.toSubFlow(subFlowDto);
                    subFlow.setStatus(FlowStatus.IN_PROGRESS);
                    haSubFlowRepository.add(subFlow);
                    subFlows.add(subFlow);
                }
                haFlow.setHaSubFlows(subFlows);
            });
        } catch (ConstraintViolationException e) {
            throw new FlowAlreadyExistException(
                    format("Failed to save ha-flow with id %s", targetFlow.getHaFlowId()), e);
        }
    }

    @SneakyThrows
    private void createPaths(HaFlowCreateFsm stateMachine) throws UnroutableFlowException,
            RecoverableException, ResourceAllocationException, FlowNotFoundException, FlowAlreadyExistException {
        RetryPolicy<Void> pathAllocationRetryPolicy = new RetryPolicy<Void>()
                .handle(RecoverableException.class)
                .handle(ResourceAllocationException.class)
                .handle(UnroutableFlowException.class)
                .handle(PersistenceException.class)
                .onRetry(e -> log.warn("Failure in resource allocation. Retrying #{}...", e.getAttemptCount(),
                        e.getLastFailure()))
                .onRetriesExceeded(e -> log.warn("Failure in resource allocation. No more retries", e.getFailure()))
                .withMaxRetries(pathAllocationRetriesLimit);
        if (pathAllocationRetryDelay > 0) {
            pathAllocationRetryPolicy.withDelay(Duration.ofMillis(pathAllocationRetryDelay));
        }
        try {
            Failsafe.with(pathAllocationRetryPolicy).run(() -> allocateMainPath(stateMachine));
            Failsafe.with(pathAllocationRetryPolicy).run(() -> allocateProtectedPath(stateMachine));
        } catch (FailsafeException ex) {
            throw ex.getCause();
        }
    }

    private void allocateMainPath(HaFlowCreateFsm stateMachine) throws UnroutableFlowException,
            RecoverableException, ResourceAllocationException {
        GetHaPathsResult paths = pathComputer.getHaPath(getHaFlow(stateMachine.getHaFlowId()), false);
        stateMachine.setBackUpPrimaryPathComputationWayUsed(paths.isBackUpPathComputationWayUsed());

        log.debug("Creating the primary path {} for ha-flow {}", paths, stateMachine.getHaFlowId());

        transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
            HaFlowResources haFlowResources = resourcesManager.allocateHaFlowResources(
                    haFlow, paths.getForward().getYPointSwitchId());
            final FlowSegmentCookieBuilder cookieBuilder = FlowSegmentCookie.builder()
                    .flowEffectiveId(haFlowResources.getUnmaskedCookie());

            HaFlowPath forward = flowPathBuilder.buildHaFlowPath(
                    haFlow, haFlowResources.getForward(), paths.getForward(),
                    cookieBuilder.direction(FlowPathDirection.FORWARD).subType(FlowSubType.SHARED).build());
            forward.setStatus(FlowPathStatus.IN_PROGRESS);
            forward.setSubPaths(createForwardSubPaths(paths, haFlow, haFlowResources, forward, stateMachine));
            haFlowPathRepository.add(forward);
            forward.setHaSubFlows(haFlow.getHaSubFlows());
            haFlow.setForwardPath(forward);
            stateMachine.getBackUpComputationWayUsedMap().put(forward.getHaPathId(), paths.getForward().isBackupPath());

            HaFlowPath reverse = flowPathBuilder.buildHaFlowPath(
                    haFlow, haFlowResources.getReverse(), paths.getReverse(),
                    cookieBuilder.direction(FlowPathDirection.REVERSE).subType(FlowSubType.SHARED).build());
            reverse.setStatus(FlowPathStatus.IN_PROGRESS);
            reverse.setSubPaths(createReverseSubPaths(paths, haFlow, haFlowResources, reverse, stateMachine));
            haFlowPathRepository.add(reverse);
            reverse.setHaSubFlows(haFlow.getHaSubFlows());
            haFlow.setReversePath(reverse);
            stateMachine.getBackUpComputationWayUsedMap().put(reverse.getHaPathId(), paths.getReverse().isBackupPath());

            updateIslsForHaFlowPath(forward);
            updateIslsForHaFlowPath(reverse);

            log.debug("Allocated resources for the ha-flow {}: {}", haFlow.getHaFlowId(), haFlowResources);
            stateMachine.setPrimaryResources(haFlowResources);
        });
    }

    private void allocateProtectedPath(HaFlowCreateFsm stateMachine) throws UnroutableFlowException,
            RecoverableException, ResourceAllocationException, FlowNotFoundException {
        String haFlowId = stateMachine.getHaFlowId();
        HaFlow tmpFlow = getHaFlow(haFlowId);
        if (!tmpFlow.isAllocateProtectedPath()) {
            return;
        }
        if (tmpFlow.getDiverseGroupId() == null) {
            tmpFlow.setDiverseGroupId(haFlowRepository.getOrCreateDiverseHaFlowGroupId(haFlowId)
                    .orElseThrow(() -> new FlowNotFoundException(haFlowId)));
        }
        GetHaPathsResult protectedPaths = pathComputer.getHaPath(tmpFlow, true);
        stateMachine.setBackUpProtectedPathComputationWayUsed(protectedPaths.isBackUpPathComputationWayUsed());

        //TODO check protected overlapping?
        log.debug("Creating the protected path {} for flow {}", protectedPaths, tmpFlow);

        transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(haFlowId);

            HaFlowResources haFlowResources = resourcesManager.allocateHaFlowResources(
                    haFlow, protectedPaths.getReverse().getYPointSwitchId());
            final FlowSegmentCookieBuilder cookieBuilder = FlowSegmentCookie.builder()
                    .flowEffectiveId(haFlowResources.getUnmaskedCookie());

            HaFlowPath forward = flowPathBuilder.buildHaFlowPath(
                    haFlow, haFlowResources.getForward(), protectedPaths.getForward(),
                    cookieBuilder.direction(FlowPathDirection.FORWARD).subType(FlowSubType.SHARED).build());
            forward.setStatus(FlowPathStatus.IN_PROGRESS);
            forward.setSubPaths(createForwardSubPaths(protectedPaths, haFlow, haFlowResources, forward, stateMachine));
            haFlowPathRepository.add(forward);
            forward.setHaSubFlows(haFlow.getHaSubFlows());
            haFlow.setProtectedForwardPath(forward);
            stateMachine.getBackUpComputationWayUsedMap().put(
                    forward.getHaPathId(), protectedPaths.getForward().isBackupPath());

            HaFlowPath reverse = flowPathBuilder.buildHaFlowPath(
                    haFlow, haFlowResources.getReverse(), protectedPaths.getReverse(),
                    cookieBuilder.direction(FlowPathDirection.REVERSE).subType(FlowSubType.SHARED).build());
            reverse.setStatus(FlowPathStatus.IN_PROGRESS);
            reverse.setSubPaths(createReverseSubPaths(protectedPaths, haFlow, haFlowResources, reverse, stateMachine));
            haFlowPathRepository.add(reverse);
            reverse.setHaSubFlows(haFlow.getHaSubFlows());
            haFlow.setProtectedReversePath(reverse);
            stateMachine.getBackUpComputationWayUsedMap().put(
                    reverse.getHaPathId(), protectedPaths.getReverse().isBackupPath());

            updateIslsForHaFlowPath(forward.getHaPathId());
            updateIslsForHaFlowPath(reverse.getHaPathId());

            log.debug("Allocated resources for the flow {}: {}", haFlow.getHaFlowId(), haFlowResources);
            stateMachine.setProtectedResources(haFlowResources);
        });
    }


    private List<FlowPath> createForwardSubPaths(
            GetHaPathsResult paths, HaFlow haFlow, HaFlowResources haFlowResources, HaFlowPath forward,
            HaFlowCreateFsm stateMachine) {
        List<HaSubFlow> subFlows = new ArrayList<>(haFlow.getHaSubFlows());
        List<FlowPath> forwardSubPaths = new ArrayList<>();
        for (int i = 0; i < subFlows.size(); i++) {
            HaSubFlow subFlow = subFlows.get(i);
            Path subPath = paths.getForward().getSubPaths().get(i);
            FlowPath forwardSubPath = flowPathBuilder.buildHaSubPath(
                    haFlow, haFlowResources.getForward().getSubPathResources(subFlow.getHaSubFlowId()), subPath,
                    haFlow.getSharedSwitch(), subFlow.getEndpointSwitch(),
                    forward.getCookie().toBuilder().subType(getSubType(i)).build());
            flowPathRepository.add(forwardSubPath);
            forwardSubPaths.add(forwardSubPath);
            stateMachine.getBackUpComputationWayUsedMap().put(forwardSubPath.getPathId(), subPath.isBackupPath());
        }
        return forwardSubPaths;
    }

    private List<FlowPath> createReverseSubPaths(
            GetHaPathsResult paths, HaFlow haFlow, HaFlowResources haFlowResources, HaFlowPath reverse,
            HaFlowCreateFsm stateMachine) {
        List<HaSubFlow> subFlows = new ArrayList<>(haFlow.getHaSubFlows());
        List<FlowPath> reverseSubPaths = new ArrayList<>();
        for (int i = 0; i < subFlows.size(); i++) {
            HaSubFlow subFlow = subFlows.get(i);
            Path subPath = paths.getReverse().getSubPaths().get(i);
            FlowPath reverseSubPath = flowPathBuilder.buildHaSubPath(
                    haFlow, haFlowResources.getReverse().getSubPathResources(subFlow.getHaSubFlowId()), subPath,
                    subFlow.getEndpointSwitch(), haFlow.getSharedSwitch(),
                    reverse.getCookie().toBuilder().subType(getSubType(i)).build());
            flowPathRepository.add(reverseSubPath);
            reverseSubPaths.add(reverseSubPath);
            stateMachine.getBackUpComputationWayUsedMap().put(reverseSubPath.getPathId(), subPath.isBackupPath());
        }
        return reverseSubPaths;
    }

    private void updateIslsForHaFlowPath(HaFlowPath haFlowPath) throws ResourceAllocationException {
        for (FlowPath subPath : haFlowPath.getSubPaths()) {
            updateIslsForHaFlowPath(subPath.getPathId());
        }
    }

    private void updateIslsForHaFlowPath(PathId pathId) throws ResourceAllocationException {
        //TODO check ISL bandwidth
        Map<IslEndpoints, Long> updatedIsls = islRepository.updateAvailableBandwidthOnIslsOccupiedByPath(pathId);
        for (Entry<IslEndpoints, Long> entry : updatedIsls.entrySet()) {
            IslEndpoints isl = entry.getKey();
            if (entry.getValue() < 0) {
                throw new ResourceAllocationException(format("ISL %s_%d-%s_%d was over-provisioned",
                        isl.getSrcSwitch(), isl.getSrcPort(), isl.getDestSwitch(), isl.getDestPort()));
            }
        }
    }

    private Message buildResponseMessage(HaFlow haFlow, CommandContext commandContext) {
        HaFlowResponse response = HaFlowResponse.builder()
                .haFlow(HaFlowMapper.INSTANCE.toHaFlowDto(haFlow, flowRepository, haFlowRepository))
                .build();
        return new InfoMessage(response, commandContext.getCreateTime(), commandContext.getCorrelationId());
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create ha-flow";
    }

    @Override
    protected void handleError(HaFlowCreateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListeners(listener ->
                listener.onFailed(stateMachine.getHaFlowId(), stateMachine.getErrorReason(), errorType));
    }

    protected HaFlow getHaFlow(String haFlowId) {
        return haFlowRepository.findById(haFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("HA-flow %s not found", haFlowId)));
    }

    private FlowSubType getSubType(int id) {
        //TODO find a better way
        switch (id) {
            case 0:
                return FlowSubType.HA_SUB_FLOW_1;
            case 1:
                return FlowSubType.HA_SUB_FLOW_2;
            default:
                throw new IllegalArgumentException(format("Unknown sub type %d", id));
        }
    }

}
