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

package org.openkilda.wfm.topology.flow.service;

import static java.util.Objects.requireNonNull;

import org.openkilda.messaging.error.ErrorType;
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
import org.openkilda.model.TransitVlan;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class FlowService extends BaseFlowService {
    private SwitchRepository switchRepository;
    private FlowRepository flowRepository;
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
        flowRepository = repositoryFactory.createFlowRepository();
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
     * @param flow          the flow to be created.
     * @param diverseFlowId the flow id to build diverse group.
     * @param sender        the command sender for flow rules installation.
     * @return the created flow with the path and resources set.
     */
    public FlowPair createFlow(UnidirectionalFlow flow, String diverseFlowId, FlowCommandSender sender)
            throws RecoverableException, UnroutableFlowException, FlowAlreadyExistException, FlowValidationException,
            SwitchValidationException, FlowNotFoundException, ResourceAllocationException {
        flowValidator.validate(flow);

        if (doesFlowExist(flow.getFlowId())) {
            throw new FlowAlreadyExistException(flow.getFlowId());
        }

        if (diverseFlowId != null) {
            checkDiverseFlow(flow, diverseFlowId);
            flow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
        }

        // TODO: the strategy is defined either per flow or system-wide.
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(flow.getFlowEntity());

        flow.setStatus(FlowStatus.IN_PROGRESS);

        log.info("Creating the flow {} with path: {}", flow, pathPair);

        Instant timestamp = Instant.now();
        FlowPair flowPair = allocateFlowResources(buildFlowPair(flow, pathPair, timestamp));
        flowPair.setTimeCreate(timestamp);

        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            FlowPath newForwardPath = flowPair.getFlowEntity().getForwardPath();
            FlowPath newReversePath = flowPair.getFlowEntity().getReversePath();
            flowPathRepository.lockInvolvedSwitches(newForwardPath, newReversePath);

            flowPairRepository.createOrUpdate(flowPair);

            updateIslsForFlowPath(flowPair.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(flowPair.getFlowEntity().getReversePath());

            return new FlowPairWithSegments(flowPair, newForwardPath.getSegments(), newReversePath.getSegments());
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendInstallRulesCommand(result);

        return result.getFlowPair();
    }

    /**
     * Stores a flow and related entities into DB, and invokes flow rules installation via the command sender.
     *
     * @param flowPair the flow to be saved.
     * @param sender   the command sender for flow rules installation.
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
        Instant timestamp = Instant.now();
        flowPairWithResources.setTimeCreate(timestamp);
        flowPairWithResources.setTimeModify(timestamp);

        FlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            FlowPath newForwardPath = flowPairWithResources.getFlowEntity().getForwardPath();
            FlowPath newReversePath = flowPairWithResources.getFlowEntity().getReversePath();
            flowPathRepository.lockInvolvedSwitches(newForwardPath, newReversePath);

            //TODO(siakovenko): flow needs to be validated (existence of switches, same end-points, etc.)

            flowPairRepository.createOrUpdate(flowPairWithResources);

            updateIslsForFlowPath(flowPairWithResources.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(flowPairWithResources.getFlowEntity().getReversePath());

            return new FlowPairWithSegments(flowPairWithResources,
                    newForwardPath.getSegments(), newReversePath.getSegments());
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

            FlowPath currentForwardPath = flowPair.getFlowEntity().getForwardPath();
            FlowPath currentReversePath = flowPair.getFlowEntity().getReversePath();
            flowPathRepository.lockInvolvedSwitches(currentForwardPath, currentReversePath);

            log.info("Deleting the flow: {}", flowPair);

            flowPairRepository.delete(flowPair);

            updateIslsForFlowPath(flowPair.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(flowPair.getFlowEntity().getReversePath());

            return Optional.of(new FlowPairWithSegments(flowPair,
                    currentForwardPath.getSegments(),
                    currentReversePath.getSegments()));
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
     * @param updatingFlow  the flow to be updated.
     * @param diverseFlowId the flow id to build diverse group.
     * @param sender        the command sender for flow rules installation and deletion.
     * @return the updated flow with the path and resources set.
     */
    public FlowPair updateFlow(UnidirectionalFlow updatingFlow, String diverseFlowId, FlowCommandSender sender)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {
        flowValidator.validate(updatingFlow);

        FlowPair currentFlow = getFlowPair(updatingFlow.getFlowId())
                .orElseThrow(() -> new FlowNotFoundException(updatingFlow.getFlowId()));

        if (diverseFlowId == null) {
            updatingFlow.setGroupId(null);
        } else {
            checkDiverseFlow(updatingFlow, diverseFlowId);
            updatingFlow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
        }

        updatingFlow.setStatus(FlowStatus.IN_PROGRESS);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(updatingFlow.getFlowEntity(), true);

        FlowPair newFlowWithResources = allocateFlowResources(buildFlowPair(updatingFlow, pathPair));

        UpdatedFlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            log.info("Updating the flow with {} and path: {}", updatingFlow, pathPair);

            FlowPath currentForwardPath = currentFlow.getFlowEntity().getForwardPath();
            FlowPath currentReversePath = currentFlow.getFlowEntity().getReversePath();
            FlowPath newForwardPath = newFlowWithResources.getFlowEntity().getForwardPath();
            FlowPath newReversePath = newFlowWithResources.getFlowEntity().getReversePath();
            flowPathRepository.lockInvolvedSwitches(currentForwardPath, currentReversePath,
                    newForwardPath, newReversePath);

            flowPairRepository.delete(currentFlow);

            updateIslsForFlowPath(currentFlow.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(currentFlow.getFlowEntity().getReversePath());

            flowPairRepository.createOrUpdate(newFlowWithResources);

            updateIslsForFlowPath(newFlowWithResources.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(newFlowWithResources.getFlowEntity().getReversePath());

            FlowPath forwardPath = currentFlow.getForward().getFlowPath();
            FlowPath reversePath = currentFlow.getReverse().getFlowPath();
            FlowResources flowResources = FlowResources.builder()
                    .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                    .forward(PathResources.builder().pathId(forwardPath.getPathId()).build())
                    .reverse(PathResources.builder().pathId(reversePath.getPathId()).build())
                    .build();

            flowResourcesManager.deallocateFlowResources(currentFlow.getFlowEntity(), flowResources);

            return UpdatedFlowPairWithSegments.builder()
                    .oldFlowPair(currentFlow)
                    .oldForwardSegments(currentForwardPath.getSegments())
                    .oldReverseSegments(currentReversePath.getSegments())
                    .flowPair(newFlowWithResources)
                    .forwardSegments(newForwardPath.getSegments())
                    .reverseSegments(newReversePath.getSegments()).build();
        });

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
     * @param flowId         the flow to be rerouted.
     * @param forceToReroute if true the flow will be recreated even there's no better path found.
     * @param sender         the command sender for flow rules installation and deletion.
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
        newFlow.setStatus(FlowStatus.IN_PROGRESS);

        UpdatedFlowPairWithSegments result = transactionManager.doInTransaction(() -> {
            FlowPath currentForwardPath = currentFlow.getFlowEntity().getForwardPath();
            FlowPath currentReversePath = currentFlow.getFlowEntity().getReversePath();
            FlowPath newForwardPath = newFlow.getFlowEntity().getForwardPath();
            FlowPath newReversePath = newFlow.getFlowEntity().getReversePath();
            flowPathRepository.lockInvolvedSwitches(currentForwardPath, currentReversePath,
                    newForwardPath, newReversePath);

            // No need to re-read currentFlow as it's going to be removed.
            flowPairRepository.delete(currentFlow);

            updateIslsForFlowPath(currentForwardPath);
            updateIslsForFlowPath(currentReversePath);

            flowPairRepository.createOrUpdate(newFlow);

            updateIslsForFlowPath(newForwardPath);
            updateIslsForFlowPath(newReversePath);

            return UpdatedFlowPairWithSegments.builder()
                    .oldFlowPair(currentFlow)
                    .oldForwardSegments(currentForwardPath.getSegments())
                    .oldReverseSegments(currentReversePath.getSegments())
                    .flowPair(newFlow)
                    .forwardSegments(newForwardPath.getSegments())
                    .reverseSegments(newReversePath.getSegments()).build();
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
        return buildFlowPair(flow, pathPair, Instant.now());
    }

    private FlowPair buildFlowPair(UnidirectionalFlow flow, PathPair pathPair, Instant timeModify) {
        FlowPath forwardPath = buildFlowPath(flow.getFlowId(), pathPair.getForward());
        forwardPath.setBandwidth(flow.getBandwidth());
        forwardPath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        FlowPath reversePath = buildFlowPath(flow.getFlowId(), pathPair.getReverse());
        reversePath.setBandwidth(flow.getBandwidth());
        reversePath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        Flow flowEntity = flow.getFlowEntity().toBuilder()
                .srcSwitch(switchRepository.reload(flow.getSrcSwitch()))
                .destSwitch(switchRepository.reload(flow.getDestSwitch()))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .timeModify(timeModify)
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
        TransitVlan forwardTransitVlan =
                Optional.ofNullable((TransitVlanResources) flowResources.getForward().getEncapsulationResources())
                        .map(TransitVlanResources::getTransitVlan)
                        .orElse(null);
        TransitVlan reverseTransitVlan =
                Optional.ofNullable((TransitVlanResources) flowResources.getReverse().getEncapsulationResources())
                        .map(TransitVlanResources::getTransitVlan)
                        .orElse(null);

        return new FlowPair(flow, forwardTransitVlan, reverseTransitVlan);
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

    private String getOrCreateFlowGroupId(String flowId) throws FlowNotFoundException {
        log.info("Getting flow group for flow with id ", flowId);
        return flowRepository.getOrCreateFlowGroupId(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    private void checkDiverseFlow(UnidirectionalFlow targetFlow, String flowId) throws FlowNotFoundException,
            FlowValidationException {
        if (targetFlow.isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't add one-switch flow into diverse group",
                    ErrorType.NOT_IMPLEMENTED);
        }

        FlowPair diverseFlow = flowPairRepository.findById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        if (diverseFlow.getForward().isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't create diverse group with one-switch flow",
                    ErrorType.NOT_IMPLEMENTED);
        }
    }

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ReroutedFlow {
        FlowPair oldFlow;
        FlowPair newFlow;
    }
}
