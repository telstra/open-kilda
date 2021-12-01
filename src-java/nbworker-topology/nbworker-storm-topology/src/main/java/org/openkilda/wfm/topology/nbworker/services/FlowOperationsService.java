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

package org.openkilda.wfm.topology.nbworker.services;

import static java.lang.String.format;
import static org.apache.commons.collections4.ListUtils.union;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;

import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.messaging.model.FlowPatch;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowPathDtoBuilder;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.model.PatchEndpoint;
import org.openkilda.messaging.nbtopology.request.FlowsDumpRequest;
import org.openkilda.messaging.nbtopology.response.FlowMirrorPointsDumpResponse.FlowMirrorPoint;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowFilter;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStats;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowStatsRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.mappers.FlowMapper;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.mappers.RequestedFlowMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.nbworker.bolts.FlowOperationsCarrier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FlowOperationsService {
    private final FlowOperationsDashboardLogger flowDashboardLogger = new FlowOperationsDashboardLogger(log);

    private static final int MAX_TRANSACTION_RETRY_COUNT = 3;
    private static final int RETRY_DELAY = 100;
    private static final Set<PathComputationStrategy> LATENCY_BASED_STRATEGIES = Sets.newHashSet(MAX_LATENCY, LATENCY);

    private TransactionManager transactionManager;
    private IslRepository islRepository;
    private SwitchRepository switchRepository;
    private FlowRepository flowRepository;
    private FlowStatsRepository flowStatsRepository;
    private FlowPathRepository flowPathRepository;
    private SwitchConnectedDeviceRepository switchConnectedDeviceRepository;
    private YFlowRepository yFlowRepository;

    public FlowOperationsService(RepositoryFactory repositoryFactory, TransactionManager transactionManager) {
        this.islRepository = repositoryFactory.createIslRepository();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowStatsRepository = repositoryFactory.createFlowStatsRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
        this.switchConnectedDeviceRepository = repositoryFactory.createSwitchConnectedDeviceRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        this.transactionManager = transactionManager;
    }

    private <T> RetryPolicy<T> getReadOperationRetryPolicy() {
        return new RetryPolicy<T>()
                .handle(PersistenceException.class)
                .withDelay(Duration.ofMillis(RETRY_DELAY))
                .withMaxRetries(MAX_TRANSACTION_RETRY_COUNT)
                .onRetry(e -> log.debug("Failure in transaction. Retrying #{}...", e.getAttemptCount(),
                        e.getLastFailure()))
                .onRetriesExceeded(e -> log.error("Failure in transaction. No more retries", e.getFailure()));
    }

    /**
     * Return flow by flow id.
     */
    public Flow getFlow(String flowId) throws FlowNotFoundException {
        Optional<Flow> found = transactionManager.doInTransaction(getReadOperationRetryPolicy(),
                () -> flowRepository.findById(flowId));
        return found.orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    /**
     * Return flow stats by flow id.
     */
    public FlowStats getFlowStats(String flowId) {
        Optional<FlowStats> found = transactionManager.doInTransaction(getReadOperationRetryPolicy(),
                () -> flowStatsRepository.findByFlowId(flowId));
        return found.orElse(FlowStats.EMPTY);
    }

    /**
     * Return all flow properties.
     */
    public Collection<FlowStats> getFlowStats() {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(),
                () -> flowStatsRepository.findAll());
    }

    /**
     * Return flow ids in the same flow diverse group.
     */
    public Set<String> getDiverseFlowsId(Flow flow) {
        return flow.getDiverseGroupId() == null ? Collections.emptySet() :
                flowRepository.findFlowsIdByDiverseGroupId(flow.getDiverseGroupId()).stream()
                        .filter(flowId -> !flowId.equals(flow.getFlowId()))
                        .collect(Collectors.toSet());
    }

    /**
     * Return flow mirror paths by flow.
     */
    public List<FlowMirrorPath> getFlowMirrorPaths(Flow flow) {
        return flow.getPaths().stream()
                .map(FlowPath::getFlowMirrorPointsSet)
                .flatMap(Collection::stream)
                .map(FlowMirrorPoints::getMirrorPaths)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Get flows.
     */
    public Collection<Flow> getAllFlows(FlowsDumpRequest request) {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(),
                () -> flowRepository.findByFlowFilter(FlowFilter.builder()
                        .flowStatus(request.getStatus())
                        .build()));
    }

    /**
     * Return all paths for a particular link.
     *
     * @param srcSwitchId source switch id.
     * @param srcPort source port.
     * @param dstSwitchId destination switch id.
     * @param dstPort destination port.
     * @return all paths for a particular link.
     * @throws IslNotFoundException if there is no link with these parameters.
     */
    public Collection<FlowPath> getFlowPathsForLink(SwitchId srcSwitchId, Integer srcPort,
                                                    SwitchId dstSwitchId, Integer dstPort)
            throws IslNotFoundException {

        flowDashboardLogger.onFlowPathsDumpByLink(srcSwitchId, srcPort, dstSwitchId, dstPort);

        if (!islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort).isPresent()) {
            throw new IslNotFoundException(srcSwitchId, srcPort, dstSwitchId, dstPort);
        }

        Collection<FlowPath> paths = flowPathRepository.findWithPathSegment(srcSwitchId, srcPort, dstSwitchId, dstPort);
        paths.forEach(path -> flowPathRepository.detach(path));
        return paths;
    }

    /**
     * Return all flows for a particular endpoint or for a particular switch if port is null.
     *
     * @param switchId switch id.
     * @param port port.
     * @return all flows for a particular endpoint.
     * @throws SwitchNotFoundException if there is no switch with this switch id.
     */
    public Collection<Flow> getFlowsForEndpoint(SwitchId switchId, Integer port)
            throws SwitchNotFoundException {

        flowDashboardLogger.onFlowPathsDumpByEndpoint(switchId, port);

        if (!switchRepository.findById(switchId).isPresent()) {
            throw new SwitchNotFoundException(switchId);
        }

        if (port != null) {
            return getFlowsForEndpoint(flowPathRepository.findBySegmentEndpoint(switchId, port),
                    flowRepository.findByEndpoint(switchId, port));
        } else {
            return getFlowsForEndpoint(flowPathRepository.findBySegmentSwitch(switchId),
                    flowRepository.findByEndpointSwitch(switchId));
        }
    }

    private Collection<Flow> getFlowsForEndpoint(Collection<FlowPath> flowPaths,
                                                 Collection<Flow> flows) {
        Stream<Flow> flowBySegment = flowPaths.stream()
                // NOTE(tdurakov): filter out paths here that are orphaned for the flow
                .filter(flowPath -> flowPath.getFlow().isActualPathId(flowPath.getPathId()))
                .map(FlowPath::getFlow);
        // need to return Flows unique by id
        // Due to possible race condition we can have one flow with different flow paths
        // In this case we should get the last one
        return Stream.concat(flowBySegment, flows.stream())
                .collect(Collectors.toMap(Flow::getFlowId, Function.identity(), (flow1, flow2) -> flow2))
                .values();
    }

    /**
     * Return flow paths for a switch.
     *
     * @param switchId switch id.
     * @return all flow paths for a switch.
     */
    public Collection<FlowPath> getFlowPathsForSwitch(SwitchId switchId) {
        flowDashboardLogger.onFlowPathsDumpBySwitch(switchId);

        Collection<FlowPath> paths = flowPathRepository.findBySegmentSwitch(switchId);
        paths.forEach(path -> flowPathRepository.detach(path));
        return paths;
    }

    /**
     * Returns flow path. If flow has group, returns also path for each flow in group.
     *
     * @param flowId the flow to get a path.
     */
    public List<FlowPathDto> getFlowPath(String flowId) throws FlowNotFoundException {
        flowDashboardLogger.onFlowPathsRead(flowId);

        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        String groupId = flow.getDiverseGroupId();
        if (groupId == null) {
            return Collections.singletonList(
                    toFlowPathDtoBuilder(flow).build());
        } else {
            Collection<Flow> flowsInGroup = flowRepository.findByDiverseGroupId(groupId);
            Collection<FlowPath> flowPathsInGroup = flowPathRepository.findByFlowGroupId(groupId);

            IntersectionComputer primaryIntersectionComputer = new IntersectionComputer(
                    flow.getFlowId(), flow.getForwardPathId(), flow.getReversePathId(), flowPathsInGroup);

            // target flow primary path
            FlowPathDtoBuilder targetFlowDtoBuilder = this.toFlowPathDtoBuilder(flow)
                    .segmentsStats(primaryIntersectionComputer.getOverlappingStats());

            // other flows in the the group
            List<FlowPathDto> payloads = flowsInGroup.stream()
                    .filter(e -> !e.getFlowId().equals(flowId))
                    .map(e -> this.mapGroupPathFlowDto(e, true, primaryIntersectionComputer))
                    .collect(Collectors.toList());

            if (flow.isAllocateProtectedPath()) {
                IntersectionComputer protectedIntersectionComputer = new IntersectionComputer(
                        flow.getFlowId(), flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(),
                        flowPathsInGroup);

                // target flow protected path
                targetFlowDtoBuilder.protectedPath(FlowProtectedPathDto.builder()
                        .forwardPath(buildPathFromFlow(flow, flow.getProtectedForwardPath()))
                        .reversePath(buildPathFromFlow(flow, flow.getProtectedReversePath()))
                        .segmentsStats(
                                protectedIntersectionComputer.getOverlappingStats())
                        .build());

                // other flows in the the group
                List<FlowPathDto> protectedPathPayloads = flowsInGroup.stream()
                        .filter(e -> !e.getFlowId().equals(flowId))
                        .map(e -> this.mapGroupPathFlowDto(e, false, protectedIntersectionComputer))
                        .collect(Collectors.toList());
                payloads = union(payloads, protectedPathPayloads);
            }

            payloads.add(targetFlowDtoBuilder.build());

            return payloads;
        }
    }

    private FlowPathDto mapGroupPathFlowDto(Flow flow, boolean primaryPathCorrespondStat,
                                            IntersectionComputer intersectionComputer) {
        FlowPathDtoBuilder builder = this.toFlowPathDtoBuilder(flow)
                .primaryPathCorrespondStat(primaryPathCorrespondStat)
                .segmentsStats(
                        intersectionComputer.getOverlappingStats(flow.getForwardPathId(), flow.getReversePathId()));
        if (flow.isAllocateProtectedPath()) {
            builder.protectedPath(FlowProtectedPathDto.builder()
                    .forwardPath(buildPathFromFlow(flow, flow.getProtectedForwardPath()))
                    .reversePath(buildPathFromFlow(flow, flow.getProtectedReversePath()))
                    .segmentsStats(
                            intersectionComputer.getOverlappingStats(
                                    flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()))
                    .build());
        }
        return builder.build();
    }

    private FlowPathDtoBuilder toFlowPathDtoBuilder(Flow flow) {
        return FlowPathDto.builder()
                .id(flow.getFlowId())
                .forwardPath(buildPathFromFlow(flow, flow.getForwardPath()))
                .reversePath(buildPathFromFlow(flow, flow.getReversePath()));
    }

    private List<PathNodePayload> buildPathFromFlow(Flow flow, FlowPath flowPath) {
        return flowPath != null ? FlowPathMapper.INSTANCE.mapToPathNodes(flow, flowPath) : Collections.emptyList();
    }

    /**
     * Partial update flow.
     */
    public Flow updateFlow(FlowOperationsCarrier carrier, FlowPatch flowPatch) throws FlowNotFoundException {
        String flowId = flowPatch.getFlowId();
        if (yFlowRepository.isSubFlow(flowId)) {
            throw new MessageException(ErrorType.REQUEST_INVALID, "Could not modify flow",
                    format("%s is a sub-flow of a y-flow. Operations on sub-flows are forbidden.", flowId));
        }

        UpdateFlowResult updateFlowResult = transactionManager.doInTransaction(() -> {
            Optional<Flow> foundFlow = flowRepository.findById(flowId);
            if (!foundFlow.isPresent()) {
                return Optional.<UpdateFlowResult>empty();
            }
            Flow currentFlow = foundFlow.get();

            validateFlow(flowPatch, currentFlow);

            final UpdateFlowResult.UpdateFlowResultBuilder result = prepareFlowUpdateResult(flowPatch, currentFlow);

            Optional.ofNullable(flowPatch.getMaxLatency()).ifPresent(currentFlow::setMaxLatency);
            Optional.ofNullable(flowPatch.getMaxLatencyTier2()).ifPresent(currentFlow::setMaxLatencyTier2);
            Optional.ofNullable(flowPatch.getPriority()).ifPresent(currentFlow::setPriority);
            Optional.ofNullable(flowPatch.getPinned()).ifPresent(currentFlow::setPinned);
            Optional.ofNullable(flowPatch.getDescription()).ifPresent(currentFlow::setDescription);
            Optional.ofNullable(flowPatch.getTargetPathComputationStrategy())
                    .ifPresent(currentFlow::setTargetPathComputationStrategy);
            Optional.ofNullable(flowPatch.getStrictBandwidth()).ifPresent(currentFlow::setStrictBandwidth);

            Optional.ofNullable(flowPatch.getPeriodicPings()).ifPresent(periodicPings -> {
                boolean oldPeriodicPings = currentFlow.isPeriodicPings();
                currentFlow.setPeriodicPings(periodicPings);
                if (oldPeriodicPings != currentFlow.isPeriodicPings()) {
                    carrier.emitPeriodicPingUpdate(flowId, flowPatch.getPeriodicPings());
                }
            });

            return Optional.of(result.updatedFlow(currentFlow).build());

        }).orElseThrow(() -> new FlowNotFoundException(flowId));

        Flow updatedFlow = updateFlowResult.getUpdatedFlow();
        if (updateFlowResult.isNeedUpdateFlow()) {
            FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(updatedFlow);
            addChangedFields(flowRequest, flowPatch);
            flowDashboardLogger.onFlowPatchUpdate(RequestedFlowMapper.INSTANCE.toFlow(flowRequest));
            carrier.sendUpdateRequest(addChangedFields(flowRequest, flowPatch));
        } else {
            flowDashboardLogger.onFlowPatchUpdate(updatedFlow);
            carrier.sendNorthboundResponse(new FlowResponse(FlowMapper.INSTANCE.map(updatedFlow,
                    getDiverseFlowsId(updatedFlow), getFlowMirrorPaths(updatedFlow))));
        }

        return updateFlowResult.getUpdatedFlow();
    }

    @VisibleForTesting
    UpdateFlowResult.UpdateFlowResultBuilder prepareFlowUpdateResult(FlowPatch flowPatch, Flow flow) {
        boolean updateRequired = updateRequiredByPathComputationStrategy(flowPatch, flow);

        updateRequired |= updateRequiredBySource(flowPatch, flow);
        updateRequired |= updateRequiredByDestination(flowPatch, flow);

        updateRequired |= flowPatch.getBandwidth() != null && flow.getBandwidth() != flowPatch.getBandwidth();
        updateRequired |= flowPatch.getAllocateProtectedPath() != null
                && !flowPatch.getAllocateProtectedPath().equals(flow.isAllocateProtectedPath());

        updateRequired |= updateRequiredByDiverseFlowIdField(flowPatch, flow);

        updateRequired |= flowPatch.getIgnoreBandwidth() != null
                && flow.isIgnoreBandwidth() != flowPatch.getIgnoreBandwidth();

        updateRequired |= flowPatch.getEncapsulationType() != null
                && !flow.getEncapsulationType().equals(flowPatch.getEncapsulationType());

        return UpdateFlowResult.builder()
                .needUpdateFlow(updateRequired);
    }

    private boolean updateRequiredByPathComputationStrategy(FlowPatch flowPatch, Flow flow) {
        boolean changedStrategy = flowPatch.getPathComputationStrategy() != null
                && !flowPatch.getPathComputationStrategy().equals(flow.getPathComputationStrategy());
        boolean changedMaxLatency = flowPatch.getMaxLatency() != null
                && !flowPatch.getMaxLatency().equals(flow.getMaxLatency());
        boolean changedMaxLatencyTier2 = flowPatch.getMaxLatencyTier2() != null
                && !flowPatch.getMaxLatencyTier2().equals(flow.getMaxLatencyTier2());
        boolean strategyIsLatencyBased =
                LATENCY_BASED_STRATEGIES.contains(flowPatch.getPathComputationStrategy())
                        || flowPatch.getPathComputationStrategy() == null
                        && LATENCY_BASED_STRATEGIES.contains(flow.getPathComputationStrategy());
        return changedStrategy || (strategyIsLatencyBased && (changedMaxLatency || changedMaxLatencyTier2));
    }

    private boolean updateRequiredBySource(FlowPatch flowPatch, Flow flow) {
        if (flowPatch.getSource() == null) {
            return false;
        }

        boolean updateRequired = flowPatch.getSource().getSwitchId() != null
                && !flow.getSrcSwitchId().equals(flowPatch.getSource().getSwitchId());
        updateRequired |= flowPatch.getSource().getPortNumber() != null
                && flow.getSrcPort() != flowPatch.getSource().getPortNumber();
        updateRequired |= flowPatch.getSource().getVlanId() != null
                && flow.getSrcVlan() != flowPatch.getSource().getVlanId();
        updateRequired |= flowPatch.getSource().getInnerVlanId() != null
                && flow.getSrcInnerVlan() != flowPatch.getSource().getInnerVlanId();
        updateRequired |= flowPatch.getSource().getTrackLldpConnectedDevices() != null
                && !flowPatch.getSource().getTrackLldpConnectedDevices()
                .equals(flow.getDetectConnectedDevices().isSrcLldp());
        updateRequired |= flowPatch.getSource().getTrackArpConnectedDevices() != null
                && !flowPatch.getSource().getTrackArpConnectedDevices()
                .equals(flow.getDetectConnectedDevices().isSrcArp());
        return updateRequired;
    }

    private boolean updateRequiredByDestination(FlowPatch flowPatch, Flow flow) {
        if (flowPatch.getDestination() == null) {
            return false;
        }

        boolean updateRequired = flowPatch.getDestination().getSwitchId() != null
                && !flow.getDestSwitchId().equals(flowPatch.getDestination().getSwitchId());
        updateRequired |= flowPatch.getDestination().getPortNumber() != null
                && flow.getDestPort() != flowPatch.getDestination().getPortNumber();
        updateRequired |= flowPatch.getDestination().getVlanId() != null
                && flow.getDestVlan() != flowPatch.getDestination().getVlanId();
        updateRequired |= flowPatch.getDestination().getInnerVlanId() != null
                && flow.getDestInnerVlan() != flowPatch.getDestination().getInnerVlanId();
        updateRequired |= flowPatch.getDestination().getTrackLldpConnectedDevices() != null
                && !flowPatch.getDestination().getTrackLldpConnectedDevices()
                .equals(flow.getDetectConnectedDevices().isDstLldp());
        updateRequired |= flowPatch.getDestination().getTrackArpConnectedDevices() != null
                && !flowPatch.getDestination().getTrackArpConnectedDevices()
                .equals(flow.getDetectConnectedDevices().isDstArp());
        return updateRequired;
    }

    private boolean updateRequiredByDiverseFlowIdField(FlowPatch flowPatch, Flow flow) {
        return flowPatch.getDiverseFlowId() != null
                && flowRepository.getOrCreateDiverseFlowGroupId(flowPatch.getDiverseFlowId())
                .map(groupId -> !flowRepository.findFlowsIdByDiverseGroupId(groupId).contains(flow.getFlowId()))
                .orElse(true);
    }

    private FlowRequest addChangedFields(FlowRequest flowRequest, FlowPatch flowPatch) {
        boolean trackSrcLldp = flowRequest.getSource().isTrackLldpConnectedDevices();
        boolean trackSrcArp = flowRequest.getSource().isTrackArpConnectedDevices();
        PatchEndpoint source = flowPatch.getSource();
        if (source != null) {
            SwitchId switchId = Optional.ofNullable(source.getSwitchId())
                    .orElse(flowRequest.getSource().getSwitchId());
            int port = Optional.ofNullable(source.getPortNumber())
                    .orElse(flowRequest.getSource().getPortNumber());
            int vlan = Optional.ofNullable(source.getVlanId())
                    .orElse(flowRequest.getSource().getOuterVlanId());
            int innerVlan = Optional.ofNullable(source.getInnerVlanId())
                    .orElse(flowRequest.getSource().getInnerVlanId());
            trackSrcLldp = Optional.ofNullable(source.getTrackLldpConnectedDevices())
                    .orElse(flowRequest.getSource().isTrackLldpConnectedDevices());
            trackSrcArp = Optional.ofNullable(source.getTrackArpConnectedDevices())
                    .orElse(flowRequest.getSource().isTrackArpConnectedDevices());
            flowRequest.setSource(new FlowEndpoint(switchId, port, vlan, innerVlan));
        }

        boolean trackDstLldp = flowRequest.getDestination().isTrackLldpConnectedDevices();
        boolean trackDstArp = flowRequest.getDestination().isTrackArpConnectedDevices();
        PatchEndpoint destination = flowPatch.getDestination();
        if (destination != null) {
            SwitchId switchId = Optional.ofNullable(destination.getSwitchId())
                    .orElse(flowRequest.getDestination().getSwitchId());
            int port = Optional.ofNullable(destination.getPortNumber())
                    .orElse(flowRequest.getDestination().getPortNumber());
            int vlan = Optional.ofNullable(destination.getVlanId())
                    .orElse(flowRequest.getDestination().getOuterVlanId());
            int innerVlan = Optional.ofNullable(destination.getInnerVlanId())
                    .orElse(flowRequest.getDestination().getInnerVlanId());
            trackDstLldp = Optional.ofNullable(destination.getTrackLldpConnectedDevices())
                    .orElse(flowRequest.getDestination().isTrackLldpConnectedDevices());
            trackDstArp = Optional.ofNullable(destination.getTrackArpConnectedDevices())
                    .orElse(flowRequest.getDestination().isTrackArpConnectedDevices());
            flowRequest.setDestination(new FlowEndpoint(switchId, port, vlan, innerVlan));
        }

        flowRequest.setDetectConnectedDevices(
                new DetectConnectedDevicesDto(trackSrcLldp, trackSrcArp, trackDstLldp, trackDstArp));

        Optional.ofNullable(flowPatch.getBandwidth()).ifPresent(flowRequest::setBandwidth);
        Optional.ofNullable(flowPatch.getIgnoreBandwidth()).ifPresent(flowRequest::setIgnoreBandwidth);
        Optional.ofNullable(flowPatch.getAllocateProtectedPath()).ifPresent(flowRequest::setAllocateProtectedPath);
        Optional.ofNullable(flowPatch.getEncapsulationType()).map(FlowMapper.INSTANCE::map)
                .ifPresent(flowRequest::setEncapsulationType);
        Optional.ofNullable(flowPatch.getPathComputationStrategy()).map(PathComputationStrategy::toString)
                .ifPresent(flowRequest::setPathComputationStrategy);
        Optional.ofNullable(flowPatch.getDiverseFlowId()).ifPresent(flowRequest::setDiverseFlowId);

        return flowRequest;
    }

    private void validateFlow(FlowPatch flowPatch, Flow flow) {
        boolean strictBandwidthPatch = Optional.ofNullable(flowPatch.getStrictBandwidth()).orElse(false);
        boolean ignoreBandwidthPatch = Optional.ofNullable(flowPatch.getIgnoreBandwidth()).orElse(false);

        if (strictBandwidthPatch && (ignoreBandwidthPatch || flow.isIgnoreBandwidth())) {
            throw new IllegalArgumentException("Can not turn on ignore bandwidth flag and strict bandwidth flag "
                    + "at the same time");
        }
    }

    /**
     * Get connected devices for Flow.
     *
     * @param flowId flow ID
     * @return connected devices for flow
     */
    public Collection<SwitchConnectedDevice> getFlowConnectedDevice(String flowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(() -> {
            if (!flowRepository.exists(flowId)) {
                throw new FlowNotFoundException(flowId);
            }

            return switchConnectedDeviceRepository.findByFlowId(flowId);
        });
    }

    /**
     * Produce reroute request for all affected paths/flows.
     */
    public List<FlowRerouteRequest> makeRerouteRequests(
            Collection<FlowPath> targetPaths, Set<IslEndpoint> affectedIslEndpoints, String reason) {
        List<FlowRerouteRequest> results = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        for (FlowPath entry : targetPaths) {
            Flow flow = entry.getFlow();
            if (processed.add(flow.getFlowId())) {
                FlowRerouteRequest request = new FlowRerouteRequest(
                        flow.getFlowId(), false, false, false, affectedIslEndpoints, reason, false);
                results.add(request);
            }
        }

        return results;
    }

    /**
     * Get flow loops.
     */
    public Collection<Flow> getLoopedFlows(String flowId, SwitchId switchId) {
        if (flowId == null) {
            return flowRepository.findLoopedByLoopSwitchId(switchId);
        }
        return flowRepository.findLoopedByFlowIdAndLoopSwitchId(flowId, switchId);
    }

    /**
     * Dump flow mirror points by flow id.
     */
    public List<FlowMirrorPoint> getFlowMirrorPoints(String flowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Optional<Flow> foundFlow = flowRepository.findById(flowId);
            if (!foundFlow.isPresent()) {
                return Optional.<List<FlowMirrorPoint>>empty();
            }
            Flow flow = foundFlow.get();

            List<FlowMirrorPoint> points = new ArrayList<>();

            for (FlowPath flowPath : Lists.newArrayList(flow.getForwardPath(), flow.getReversePath())) {
                String direction = flowPath.isForward() ? "forward" : "reverse";
                for (FlowMirrorPoints mirrorPoints : flowPath.getFlowMirrorPointsSet()) {
                    for (FlowMirrorPath mirrorPath : mirrorPoints.getMirrorPaths()) {
                        points.add(FlowMirrorPoint.builder()
                                .mirrorPointId(mirrorPath.getPathId().toString())
                                .mirrorPointSwitchId(mirrorPoints.getMirrorSwitchId())
                                .mirrorPointDirection(direction)
                                .sinkEndpoint(FlowEndpoint.builder()
                                        .switchId(mirrorPath.getEgressSwitchId())
                                        .portNumber(mirrorPath.getEgressPort())
                                        .outerVlanId(mirrorPath.getEgressOuterVlan())
                                        .innerVlanId(mirrorPath.getEgressInnerVlan())
                                        .build())
                                .build());
                    }
                }
            }

            return Optional.of(points);

        }).orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    @Data
    @Builder
    static class UpdateFlowResult {
        private Flow updatedFlow;
        private boolean needUpdateFlow;
    }
}
