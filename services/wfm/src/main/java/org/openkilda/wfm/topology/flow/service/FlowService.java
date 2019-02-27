/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.flow.service;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections4.ListUtils.union;

import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanResources;
import org.openkilda.wfm.topology.flow.model.FlowPairWithSegments;
import org.openkilda.wfm.topology.flow.model.UpdatedFlowPairWithSegments;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class FlowService extends BaseFlowService {
    private SwitchRepository switchRepository;
    private FlowPathRepository flowPathRepository;
    private IslRepository islRepository;
    private PathComputerFactory pathComputerFactory;
    private FlowResourcesManager flowResourcesManager;
    private FlowValidator flowValidator;

    public FlowService(PersistenceManager persistenceManager, PathComputerFactory pathComputerFactory,
                       FlowResourcesManager flowResourcesManager, FlowValidator flowValidator) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        islRepository = repositoryFactory.createIslRepository();
        this.pathComputerFactory = requireNonNull(pathComputerFactory);
        this.flowResourcesManager = requireNonNull(flowResourcesManager);
        this.flowValidator = requireNonNull(flowValidator);
    }

    /**
     * Creates a flow by allocating a path and resources. Stores the flow entities into DB, and
     * invokes flow rules installation via the command sender.
     * <p/>
     * The flow is created with IN_PROGRESS status.
     *
     * @param flow the flow to be created.
     * @param sender the command sender for flow rules installation.
     * @return the created flow with the path and resources set.
     */
    public FlowPair createFlow(UnidirectionalFlow flow, FlowCommandSender sender) throws RecoverableException,
            UnroutableFlowException, FlowAlreadyExistException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {
        flowValidator.validate(flow);

        if (doesFlowExist(flow.getFlowId())) {
            throw new FlowAlreadyExistException(flow.getFlowId());
        }

        // TODO: the strategy is defined either per flow or system-wide.
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(flow.getFlowEntity());

        flow.setStatus(FlowStatus.IN_PROGRESS);

        log.info("Creating the flow {} with path: {}", flow, pathPair);

        FlowPair flowPair = allocateFlowResources(buildFlowPair(flow, pathPair));

        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            List<PathSegment> forwardSegments = flowPair.getFlowEntity().getForwardPath().getSegments();
            List<PathSegment> reverseSegments = flowPair.getFlowEntity().getReversePath().getSegments();
            lockSwitches(union(forwardSegments, reverseSegments));

            flowPairRepository.createOrUpdate(flowPair);

            updateIslsForFlowPath(flowPair.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(flowPair.getFlowEntity().getReversePath());

            return new FlowPairWithSegments(flowPair, forwardSegments, reverseSegments);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendInstallRulesCommand(result);

        return result.getFlowPair();
    }

    /**
     * Stores a flow and related entities into DB, and invokes flow rules installation via the command sender.
     *
     * @param flowPair the flow to be saved.
     * @param sender the command sender for flow rules installation.
     */
    public void saveFlow(FlowPair flowPair, FlowCommandSender sender) throws FlowAlreadyExistException,
            ResourceAllocationException {
        if (doesFlowExist(flowPair.getFlowEntity().getFlowId())) {
            throw new FlowAlreadyExistException(flowPair.getFlowEntity().getFlowId());
        }

        log.info("Saving (pushing) the flow: {}", flowPair);

        flowPair.getForward().setSrcSwitch(switchRepository.reload(flowPair.getForward().getSrcSwitch()));
        flowPair.getForward().setDestSwitch(switchRepository.reload(flowPair.getForward().getDestSwitch()));
        flowPair.getReverse().setSrcSwitch(switchRepository.reload(flowPair.getReverse().getSrcSwitch()));
        flowPair.getReverse().setDestSwitch(switchRepository.reload(flowPair.getReverse().getDestSwitch()));
        FlowPair flowPairWithResources = allocateFlowResources(flowPair);

        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            List<PathSegment> forwardSegments = flowPairWithResources.getFlowEntity().getForwardPath().getSegments();
            List<PathSegment> reverseSegments = flowPairWithResources.getFlowEntity().getReversePath().getSegments();
            lockSwitches(union(forwardSegments, reverseSegments));

            //TODO(siakovenko): flow needs to be validated (existence of switches, same end-points, etc.)

            flowPairRepository.createOrUpdate(flowPairWithResources);

            updateIslsForFlowPath(flowPairWithResources.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(flowPairWithResources.getFlowEntity().getReversePath());

            return new FlowPairWithSegments(flowPairWithResources, forwardSegments, reverseSegments);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendInstallRulesCommand(result);
    }

    /**
     * Deletes a flow and its segments from DB. Deallocates a path and resources.
     * Invokes flow rules deletion via the command sender.
     *
     * @param flowId the flow to be deleted.
     * @param sender the command sender for flow rules deletion.
     * @return the deleted flow.
     */
    public FlowPair deleteFlow(String flowId, FlowCommandSender sender) throws FlowNotFoundException {
        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            Optional<FlowPair> foundFlowPair = getFlowPair(flowId);
            if (!foundFlowPair.isPresent()) {
                return Optional.<FlowPairWithSegments>empty();
            }

            FlowPair flowPair = foundFlowPair.get();

            List<PathSegment> forwardSegments = flowPair.getFlowEntity().getForwardPath().getSegments();
            List<PathSegment> reverseSegments = flowPair.getFlowEntity().getReversePath().getSegments();
            lockSwitches(union(forwardSegments, reverseSegments));

            log.info("Deleting the flow: {}", flowPair);

            flowPairRepository.delete(flowPair);

            updateIslsForFlowPath(flowPair.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(flowPair.getFlowEntity().getReversePath());

            return Optional.of(new FlowPairWithSegments(flowPair, forwardSegments, reverseSegments));
        }).orElseThrow(() -> new FlowNotFoundException(flowId));

        FlowPath forwardPath = result.getFlowPair().getForward().getFlowPath();
        FlowPath reversePath = result.getFlowPair().getReverse().getFlowPath();
        FlowResources flowResources = FlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                .forward(PathResources.builder().pathId(forwardPath.getPathId()).build())
                .reverse(PathResources.builder().pathId(reversePath.getPathId()).build())
                .build();

        flowResourcesManager.deallocateFlowResources(result.getFlowPair().getFlowEntity(), flowResources);

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendRemoveRulesCommand(result);

        return result.getFlowPair();
    }

    /**
     * Replaces a flow with the new one. Allocates a path and resources.
     * Stores the flow entities into DB, and invokes flow rules installation and deletion via the command sender.
     * <p/>
     * The updated flow has IN_PROGRESS status.
     *
     * @param flowId the flow to be replaced.
     * @param newFlow the flow to be applied.
     * @param sender the command sender for flow rules installation and deletion.
     * @return the updated flow with the path and resources set.
     */
    public FlowPair updateFlow(String flowId, UnidirectionalFlow newFlow, FlowCommandSender sender)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException,
            FlowValidationException, SwitchValidationException, ResourceAllocationException {
        flowValidator.validate(newFlow);

        // TODO: the strategy is defined either per flow or system-wide.
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(newFlow.getFlowEntity(), true);

        newFlow.setStatus(FlowStatus.IN_PROGRESS);

        Optional<FlowPair> foundFlowPair = getFlowPair(flowId);
        if (!foundFlowPair.isPresent()) {
            throw new FlowNotFoundException(flowId);
        }
        FlowPair currentFlow = foundFlowPair.get();
        FlowPair newFlowWithResources = allocateFlowResources(buildFlowPair(newFlow, pathPair));

        UpdatedFlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            List<PathSegment> forwardSegments = currentFlow.getFlowEntity().getForwardPath().getSegments();
            List<PathSegment> reverseSegments = currentFlow.getFlowEntity().getReversePath().getSegments();
            List<PathSegment> flowSegments = union(forwardSegments, reverseSegments);

            log.info("Updating the flow with {} and path: {}", newFlow, pathPair);

            List<PathSegment> newForwardSegments = newFlowWithResources.getFlowEntity().getForwardPath().getSegments();
            List<PathSegment> newReverseSegments = newFlowWithResources.getFlowEntity().getReversePath().getSegments();
            List<PathSegment> newFlowSegments = union(newForwardSegments, newReverseSegments);

            lockSwitches(union(flowSegments, newFlowSegments));

            flowPairRepository.delete(currentFlow);

            updateIslsForFlowPath(currentFlow.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(currentFlow.getFlowEntity().getReversePath());

            flowPairRepository.createOrUpdate(newFlowWithResources);

            updateIslsForFlowPath(newFlowWithResources.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(newFlowWithResources.getFlowEntity().getReversePath());

            return UpdatedFlowPairWithSegments.builder()
                    .oldFlowPair(currentFlow).oldForwardSegments(forwardSegments).oldReverseSegments(reverseSegments)
                    .flowPair(newFlowWithResources).forwardSegments(newForwardSegments)
                    .reverseSegments(newReverseSegments).build();
        });

        FlowPath forwardPath = currentFlow.getForward().getFlowPath();
        FlowPath reversePath = currentFlow.getReverse().getFlowPath();
        FlowResources flowResources = FlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                .forward(PathResources.builder().pathId(forwardPath.getPathId()).build())
                .reverse(PathResources.builder().pathId(reversePath.getPathId()).build())
                .build();

        flowResourcesManager.deallocateFlowResources(currentFlow.getFlowEntity(), flowResources);

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendUpdateRulesCommand(result);

        return result.getFlowPair();
    }

    /**
     * Reroutes a flow via a new path. Deallocates old and allocates new path and resources.
     * Stores the flow entities into DB, and invokes flow rules installation and deletion via the command sender.
     * <p/>
     * The rerouted flow has IN_PROGRESS status.
     *
     * @param flowId the flow to be rerouted.
     * @param forceToReroute if true the flow will be recreated even there's no better path found.
     * @param sender the command sender for flow rules installation and deletion.
     */
    public ReroutedFlow rerouteFlow(String flowId, boolean forceToReroute, FlowCommandSender sender)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException, ResourceAllocationException {
        FlowPair currentFlow = getFlowPair(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        log.warn("Origin flow {} path: {}", flowId, currentFlow.getForward().getFlowPath());

        // TODO: the strategy is defined either per flow or system-wide.
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(currentFlow.getFlowEntity(), true);

        log.warn("Potential New Path for flow {} with LEFT path: {}, RIGHT path: {}",
                flowId, pathPair.getForward(), pathPair.getReverse());

        boolean isFoundNewPath = !pathPair.getForward().equals(currentFlow.getForward().getFlowPath())
                || !pathPair.getReverse().equals(currentFlow.getReverse().getFlowPath());
        //no need to emit changes if path wasn't changed and flow is active.
        //force means to update flow even if path is not changed.
        if (!isFoundNewPath && currentFlow.isActive() && !forceToReroute) {
            log.warn("Reroute {} is unsuccessful: can't find new path.", flowId);

            return new ReroutedFlow(currentFlow, null);
        }

        FlowPair newFlow = allocateFlowResources(buildFlowPair(currentFlow.getForward(), pathPair));

        UpdatedFlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            newFlow.setStatus(FlowStatus.IN_PROGRESS);

            List<PathSegment> forwardSegments = currentFlow.getFlowEntity().getForwardPath().getSegments();
            List<PathSegment> reverseSegments = currentFlow.getFlowEntity().getReversePath().getSegments();
            List<PathSegment> flowSegments = union(forwardSegments, reverseSegments);

            List<PathSegment> newForwardSegments = newFlow.getFlowEntity().getForwardPath().getSegments();
            List<PathSegment> newReverseSegments = newFlow.getFlowEntity().getReversePath().getSegments();
            List<PathSegment> newFlowSegments = union(newForwardSegments, newReverseSegments);

            lockSwitches(union(flowSegments, newFlowSegments));

            // No need to re-read currentFlow as it's going to be removed.
            flowPairRepository.delete(currentFlow);

            updateIslsForFlowPath(currentFlow.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(currentFlow.getFlowEntity().getReversePath());

            flowPairRepository.createOrUpdate(newFlow);

            updateIslsForFlowPath(newFlow.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(newFlow.getFlowEntity().getReversePath());

            return UpdatedFlowPairWithSegments.builder()
                    .oldFlowPair(currentFlow).oldForwardSegments(forwardSegments).oldReverseSegments(reverseSegments)
                    .flowPair(newFlow).forwardSegments(newForwardSegments)
                    .reverseSegments(newReverseSegments).build();
        });

        FlowPath forwardPath = currentFlow.getForward().getFlowPath();
        FlowPath reversePath = currentFlow.getReverse().getFlowPath();
        FlowResources flowResources = FlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                .forward(PathResources.builder().pathId(forwardPath.getPathId()).build())
                .reverse(PathResources.builder().pathId(reversePath.getPathId()).build())
                .build();

        flowResourcesManager.deallocateFlowResources(currentFlow.getFlowEntity(), flowResources);

        log.warn("Rerouted flow with new path: {}", result.getFlowPair());

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendUpdateRulesCommand(result);

        return new ReroutedFlow(currentFlow, result.getFlowPair());
    }

    private FlowPair buildFlowPair(UnidirectionalFlow flow, PathPair pathPair) {
        FlowPath forwardPath = buildFlowPath(flow.getFlowId(), pathPair.getForward());
        forwardPath.setBandwidth(flow.getBandwidth());
        forwardPath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        FlowPath reversePath = buildFlowPath(flow.getFlowId(), pathPair.getReverse());
        reversePath.setBandwidth(flow.getBandwidth());
        reversePath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        Flow flowEntity = Flow.builder()
                .flowId(flow.getFlowId())
                .srcSwitch(switchRepository.reload(flow.getSrcSwitch()))
                .srcPort(flow.getSrcPort())
                .srcVlan(flow.getSrcVlan())
                .destSwitch(switchRepository.reload(flow.getDestSwitch()))
                .destPort(flow.getDestPort())
                .destVlan(flow.getDestVlan())
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .description(flow.getDescription())
                .periodicPings(flow.isPeriodicPings())
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(flow.getStatus())
                .timeModify(Instant.now())
                .forwardPath(forwardPath)
                .reversePath(reversePath)
                .build();

        return new FlowPair(flowEntity, null, null);
    }

    private FlowPath buildFlowPath(String flowId, Path path) {
        PathId pathId = new PathId(UUID.randomUUID().toString());
        List<PathSegment> segments = path.getSegments().stream()
                .map(segment -> PathSegment.builder()
                        .pathId(pathId)
                        .srcSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getSrcSwitchId()).build()))
                        .srcPort(segment.getSrcPort())
                        .destSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getDestSwitchId()).build()))
                        .destPort(segment.getDestPort())
                        .latency(segment.getLatency())
                        .build())
                .collect(Collectors.toList());
        return FlowPath.builder()
                .flowId(flowId)
                .pathId(pathId)
                .srcSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getSrcSwitchId()).build()))
                .destSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getDestSwitchId()).build()))
                .segments(segments)
                .build();
    }

    private FlowPair allocateFlowResources(FlowPair flowPair) throws ResourceAllocationException {
        Flow flow = flowPair.getFlowEntity();
        FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);

        FlowPath forwardPath = flowPair.getForward().getFlowPath();
        forwardPath.setPathId(flowResources.getForward().getPathId());
        forwardPath.setCookie(Cookie.buildForwardCookie(flowResources.getUnmaskedCookie()));
        if (flowResources.getForward().getMeterId() != null) {
            forwardPath.setMeterId(flowResources.getForward().getMeterId());
        }
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = flowPair.getReverse().getFlowPath();
        reversePath.setPathId(flowResources.getReverse().getPathId());
        reversePath.setCookie(Cookie.buildReverseCookie(flowResources.getUnmaskedCookie()));
        if (flowResources.getReverse().getMeterId() != null) {
            reversePath.setMeterId(flowResources.getReverse().getMeterId());
        }
        flow.setReversePath(reversePath);

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlanResources forwardTransitVlanResources =
                (TransitVlanResources) flowResources.getForward().getEncapsulationResources();
        TransitVlanResources reverseTransitVlanResources =
                (TransitVlanResources) flowResources.getReverse().getEncapsulationResources();

        return new FlowPair(flow, forwardTransitVlanResources.getTransitVlan(),
                reverseTransitVlanResources.getTransitVlan());
    }

    private void updateIslsForFlowPath(FlowPath path) {
        path.getSegments().forEach(pathSegment -> {
            log.debug("Updating ISL for the path segment: {}", pathSegment);

            updateIslAvailableBandwidth(pathSegment.getSrcSwitch().getSwitchId(), pathSegment.getSrcPort(),
                    pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort());
        });
    }

    private void updateIslAvailableBandwidth(SwitchId srcSwitchId, int srcPort, SwitchId dstSwitchId, int dstPort) {
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(
                srcSwitchId, srcPort, dstSwitchId, dstPort);

        islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort)
                .ifPresent(isl -> {
                    isl.setAvailableBandwidth(isl.getMaxBandwidth() - usedBandwidth);

                    islRepository.createOrUpdate(isl);
                });
    }

    private void lockSwitches(List<PathSegment> flowSegments) {
        Set<Switch> switches = new HashSet<>();
        flowSegments.forEach(flowSegment -> switches.add(flowSegment.getSrcSwitch()));
        switchRepository.lockSwitches(switches.toArray(new Switch[0]));
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReroutedFlow {
        FlowPair oldFlow;
        FlowPair newFlow;
    }
}
