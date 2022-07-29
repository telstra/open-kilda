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

package org.openkilda.wfm.topology.flowhs.fsm.create.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSegmentCookieBuilder;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.IslRepository.IslEndpoints;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowAlreadyExistException;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.common.FlowPathBuilder;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilder;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.speaker.SpeakerRequestBuildContext;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.map.LazyMap;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

@Slf4j
public class ResourcesAllocationAction extends
        NbTrackableWithHistorySupportAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    private final PathComputer pathComputer;
    protected final int pathAllocationRetriesLimit;
    protected final int pathAllocationRetryDelay;
    private final FlowResourcesManager resourcesManager;
    private final IslRepository islRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final YFlowRepository yFlowRepository;

    private final FlowPathBuilder flowPathBuilder;
    private final FlowCommandBuilderFactory commandBuilderFactory;

    public ResourcesAllocationAction(PathComputer pathComputer, PersistenceManager persistenceManager,
                                     int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                                     FlowResourcesManager resourcesManager) {
        super(persistenceManager);

        this.pathComputer = pathComputer;
        this.pathAllocationRetriesLimit = pathAllocationRetriesLimit;
        this.pathAllocationRetryDelay = pathAllocationRetryDelay;
        this.resourcesManager = resourcesManager;
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        this.yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        KildaConfigurationRepository kildaConfigurationRepository = persistenceManager.getRepositoryFactory()
                .createKildaConfigurationRepository();

        this.flowPathBuilder = new FlowPathBuilder(switchPropertiesRepository,
                kildaConfigurationRepository);
        this.commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowCreateContext context,
                                                    FlowCreateFsm stateMachine) throws FlowProcessingException {
        try {
            String flowId = stateMachine.getFlowId();
            log.debug("Allocation resources has been started");
            stateMachine.setPathsBeenAllocated(false);

            if (context != null && context.getTargetFlow() != null) {
                createFlow(context.getTargetFlow());
            } else if (!flowRepository.exists(flowId)) {
                log.warn("Flow {} has been deleted while creation was in progress", flowId);
                return Optional.empty();
            }

            createPaths(stateMachine);

            log.debug("Resources allocated successfully for the flow {}", flowId);
            stateMachine.setPathsBeenAllocated(true);

            Flow resultFlow = getFlow(flowId);
            createSpeakerRequestFactories(stateMachine, resultFlow);
            saveHistory(stateMachine, resultFlow);
            if (resultFlow.isOneSwitchFlow()) {
                stateMachine.fire(Event.SKIP_NON_INGRESS_RULES_INSTALL);
            } else {
                stateMachine.fireNext(context);
            }

            // Notify about successful allocation.
            stateMachine.notifyEventListeners(listener -> listener.onResourcesAllocated(flowId));

            return Optional.of(buildResponseMessage(resultFlow, stateMachine.getCommandContext()));
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

    private void createFlow(RequestedFlow targetFlow) throws FlowNotFoundException, FlowAlreadyExistException {
        try {
            transactionManager.doInTransaction(() -> {
                Flow flow = RequestedFlowMapper.INSTANCE.toFlow(targetFlow);
                flow.setStatus(FlowStatus.IN_PROGRESS);
                getOrCreateFlowDiverseGroup(targetFlow.getDiverseFlowId())
                        .ifPresent(flow::setDiverseGroupId);
                getFlowAffinityGroupFromContext(targetFlow.getAffinityFlowId())
                        .ifPresent(flow::setAffinityGroupId);
                flowRepository.add(flow);

                String yFlowId = targetFlow.getYFlowId();
                if (yFlowId != null) {
                    YFlow yFlow = yFlowRepository.findById(yFlowId)
                            .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                                    format("Y-flow %s not found", yFlowId)));
                    if (!flow.getSrcSwitchId().equals(yFlow.getSharedEndpoint().getSwitchId())) {
                        throw new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                                format("Y-flow %s has different sharedpoint than the source of flow %s", yFlowId,
                                        flow.getFlowId()));
                    }
                    YSubFlow subFlow = YSubFlow.builder()
                            .yFlow(yFlow)
                            .flow(flow)
                            .sharedEndpointVlan(flow.getSrcVlan())
                            .sharedEndpointInnerVlan(flow.getSrcInnerVlan())
                            .endpointSwitchId(flow.getDestSwitchId())
                            .endpointPort(flow.getDestPort())
                            .endpointVlan(flow.getDestVlan())
                            .endpointInnerVlan(flow.getDestInnerVlan())
                            .build();
                    yFlow.addSubFlow(subFlow);
                    log.debug("Created a sub-flow reference from {} to y-flow {}", flow.getFlowId(), yFlowId);
                }
            });
        } catch (ConstraintViolationException e) {
            throw new FlowAlreadyExistException(format("Failed to save flow with id %s", targetFlow.getFlowId()), e);
        }
    }

    private Optional<String> getOrCreateFlowDiverseGroup(String diverseFlowId) throws FlowNotFoundException {
        if (StringUtils.isBlank(diverseFlowId)) {
            return Optional.empty();
        }
        Optional<String> groupId;
        if (yFlowRepository.exists(diverseFlowId)) {
            groupId = yFlowRepository.getOrCreateDiverseYFlowGroupId(diverseFlowId);
        } else if (yFlowRepository.isSubFlow(diverseFlowId)) {
            groupId = flowRepository.findById(diverseFlowId)
                    .map(Flow::getYFlowId)
                    .flatMap(yFlowRepository::getOrCreateDiverseYFlowGroupId);
        } else {
            groupId = flowRepository.getOrCreateDiverseFlowGroupId(diverseFlowId);
        }
        return Optional.of(groupId.orElseThrow(() -> new FlowNotFoundException(diverseFlowId)));
    }

    private Optional<String> getFlowAffinityGroupFromContext(String affinityFlowId) throws FlowNotFoundException {
        if (StringUtils.isNotBlank(affinityFlowId)) {
            return flowRepository.getOrCreateAffinityFlowGroupId(affinityFlowId)
                    .map(Optional::of)
                    .orElseThrow(() -> new FlowNotFoundException(affinityFlowId));
        }
        return Optional.empty();
    }

    @SneakyThrows
    private void createPaths(FlowCreateFsm stateMachine) throws UnroutableFlowException,
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

    private void createSpeakerRequestFactories(FlowCreateFsm stateMachine, Flow flow) {
        final FlowCommandBuilder commandBuilder = commandBuilderFactory.getBuilder(flow.getEncapsulationType());
        final CommandContext commandContext = stateMachine.getCommandContext();

        List<FlowSegmentRequestFactory> requestFactories;

        // ingress
        requestFactories = stateMachine.getIngressCommands();
        SpeakerRequestBuildContext buildContext = buildBaseSpeakerContextForInstall(
                flow.getSrcSwitchId(), flow.getDestSwitchId());
        requestFactories.addAll(commandBuilder.buildIngressOnly(stateMachine.getCommandContext(), flow, buildContext));

        // non ingress
        requestFactories = stateMachine.getNonIngressCommands();
        requestFactories.addAll(commandBuilder.buildAllExceptIngress(commandContext, flow));
        if (flow.isAllocateProtectedPath()) {
            requestFactories.addAll(commandBuilder.buildAllExceptIngress(
                    commandContext, flow,
                    flow.getProtectedForwardPath(), flow.getProtectedReversePath()));
        }
    }

    private void allocateMainPath(FlowCreateFsm stateMachine) throws UnroutableFlowException,
            RecoverableException, ResourceAllocationException {
        GetPathsResult paths = pathComputer.getPath(getFlow(stateMachine.getFlowId()));
        stateMachine.setBackUpPrimaryPathComputationWayUsed(paths.isBackUpPathComputationWayUsed());

        log.debug("Creating the primary path {} for flow {}", paths, stateMachine.getFlowId());

        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId());
            FlowResources flowResources = resourcesManager.allocateFlowResources(flow);
            final FlowSegmentCookieBuilder cookieBuilder = FlowSegmentCookie.builder()
                    .flowEffectiveId(flowResources.getUnmaskedCookie());

            updateSwitchRelatedFlowProperties(flow);

            FlowPath forward = flowPathBuilder.buildFlowPath(
                    flow, flowResources.getForward(), paths.getForward(),
                    cookieBuilder.direction(FlowPathDirection.FORWARD).build(), false,
                    stateMachine.getSharedBandwidthGroupId());
            forward.setStatus(FlowPathStatus.IN_PROGRESS);
            flowPathRepository.add(forward);
            flow.setForwardPath(forward);

            FlowPath reverse = flowPathBuilder.buildFlowPath(
                    flow, flowResources.getReverse(), paths.getReverse(),
                    cookieBuilder.direction(FlowPathDirection.REVERSE).build(), false,
                    stateMachine.getSharedBandwidthGroupId());
            reverse.setStatus(FlowPathStatus.IN_PROGRESS);
            flowPathRepository.add(reverse);
            flow.setReversePath(reverse);

            updateIslsForFlowPath(forward.getPathId());
            updateIslsForFlowPath(reverse.getPathId());

            stateMachine.setForwardPathId(forward.getPathId());
            stateMachine.setReversePathId(reverse.getPathId());
            log.debug("Allocated resources for the flow {}: {}", flow.getFlowId(), flowResources);
            stateMachine.getFlowResources().add(flowResources);
        });
    }

    private void allocateProtectedPath(FlowCreateFsm stateMachine) throws UnroutableFlowException,
            RecoverableException, ResourceAllocationException, FlowNotFoundException {
        String flowId = stateMachine.getFlowId();
        Flow tmpFlow = getFlow(flowId);
        if (!tmpFlow.isAllocateProtectedPath()) {
            return;
        }
        if (tmpFlow.getDiverseGroupId() == null) {
            tmpFlow.setDiverseGroupId(flowRepository.getOrCreateDiverseFlowGroupId(flowId)
                    .orElseThrow(() -> new FlowNotFoundException(flowId)));
        }
        GetPathsResult protectedPath = pathComputer.getPath(tmpFlow);
        stateMachine.setBackUpProtectedPathComputationWayUsed(protectedPath.isBackUpPathComputationWayUsed());

        boolean overlappingProtectedPathFound =
                flowPathBuilder.arePathsOverlapped(protectedPath.getForward(), tmpFlow.getForwardPath())
                        || flowPathBuilder.arePathsOverlapped(protectedPath.getReverse(), tmpFlow.getReversePath());
        if (overlappingProtectedPathFound) {
            log.info("Couldn't find non overlapping protected path. Result flow state: {}", tmpFlow);
            throw new UnroutableFlowException("Couldn't find non overlapping protected path", tmpFlow.getFlowId());
        }

        log.debug("Creating the protected path {} for flow {}", protectedPath, tmpFlow);

        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(flowId);

            FlowResources flowResources = resourcesManager.allocateFlowResources(flow);
            final FlowSegmentCookieBuilder cookieBuilder = FlowSegmentCookie.builder()
                    .flowEffectiveId(flowResources.getUnmaskedCookie());

            FlowPath forward = flowPathBuilder.buildFlowPath(
                    flow, flowResources.getForward(), protectedPath.getForward(),
                    cookieBuilder.direction(FlowPathDirection.FORWARD).build(), false,
                    stateMachine.getSharedBandwidthGroupId());
            forward.setStatus(FlowPathStatus.IN_PROGRESS);
            flowPathRepository.add(forward);
            flow.setProtectedForwardPath(forward);

            FlowPath reverse = flowPathBuilder.buildFlowPath(
                    flow, flowResources.getReverse(), protectedPath.getReverse(),
                    cookieBuilder.direction(FlowPathDirection.REVERSE).build(), false,
                    stateMachine.getSharedBandwidthGroupId());
            reverse.setStatus(FlowPathStatus.IN_PROGRESS);
            flowPathRepository.add(reverse);
            flow.setProtectedReversePath(reverse);

            updateIslsForFlowPath(forward.getPathId());
            updateIslsForFlowPath(reverse.getPathId());

            stateMachine.setProtectedForwardPathId(forward.getPathId());
            stateMachine.setProtectedReversePathId(reverse.getPathId());
            log.debug("Allocated resources for the flow {}: {}", flow.getFlowId(), flowResources);
            stateMachine.getFlowResources().add(flowResources);
        });
    }

    private void updateIslsForFlowPath(PathId pathId) throws ResourceAllocationException {
        Map<IslEndpoints, Long> updatedIsls = islRepository.updateAvailableBandwidthOnIslsOccupiedByPath(pathId);
        for (Entry<IslEndpoints, Long> entry : updatedIsls.entrySet()) {
            IslEndpoints isl = entry.getKey();
            if (entry.getValue() < 0) {
                throw new ResourceAllocationException(format("ISL %s_%d-%s_%d was over-provisioned",
                        isl.getSrcSwitch(), isl.getSrcPort(), isl.getDestSwitch(), isl.getDestPort()));
            }
        }
    }

    private void saveHistory(FlowCreateFsm stateMachine, Flow flow) {
        FlowDumpData primaryPathsDumpData =
                HistoryMapper.INSTANCE.map(flow, flow.getForwardPath(), flow.getReversePath(), DumpType.STATE_AFTER);
        stateMachine.saveActionWithDumpToHistory("New primary paths were created",
                format("The flow paths were created (with allocated resources): %s / %s",
                        flow.getForwardPathId(), flow.getReversePathId()),
                primaryPathsDumpData);

        if (flow.isAllocateProtectedPath()) {
            FlowDumpData protectedPathsDumpData = HistoryMapper.INSTANCE.map(flow, flow.getProtectedForwardPath(),
                    flow.getProtectedReversePath(), DumpType.STATE_AFTER);
            stateMachine.saveActionWithDumpToHistory("New protected paths were created",
                    format("The flow paths were created (with allocated resources): %s / %s",
                            flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()),
                    protectedPathsDumpData);
        }
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create flow";
    }

    //TODO: refactor FlowCreate and unify ResourcesAllocationAction with BaseResourceAllocationAction to avoid
    // code duplication.
    private void updateSwitchRelatedFlowProperties(Flow flow) {
        Map<SwitchId, SwitchProperties> switchProperties = LazyMap.lazyMap(new HashMap<>(), switchId ->
                switchPropertiesRepository.findBySwitchId(switchId).orElse(null));

        DetectConnectedDevices.DetectConnectedDevicesBuilder detectConnectedDevices =
                flow.getDetectConnectedDevices().toBuilder();
        SwitchProperties srcSwitchProps = switchProperties.get(flow.getSrcSwitchId());
        if (srcSwitchProps != null) {
            detectConnectedDevices.srcSwitchLldp(srcSwitchProps.isSwitchLldp());
            detectConnectedDevices.srcSwitchArp(srcSwitchProps.isSwitchArp());
        }
        SwitchProperties destSwitchProps = switchProperties.get(flow.getDestSwitchId());
        if (destSwitchProps != null) {
            switchProperties.put(flow.getDestSwitchId(), destSwitchProps);
            detectConnectedDevices.dstSwitchLldp(destSwitchProps.isSwitchLldp());
            detectConnectedDevices.dstSwitchArp(destSwitchProps.isSwitchArp());
        }
        flow.setDetectConnectedDevices(detectConnectedDevices.build());
    }

    @Override
    protected void handleError(FlowCreateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListeners(listener ->
                listener.onFailed(stateMachine.getFlowId(), stateMachine.getErrorReason(), errorType));
    }
}
