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

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections4.ListUtils.union;

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
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flow.model.UpdatedFlow;
import org.openkilda.wfm.topology.flow.model.UpdatedFlowPair;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        FlowPair resultFlowPair = transactionManager.doInTransaction(() -> {
            Instant timestamp = Instant.now();
            FlowPair flowPair = allocateFlowResources(buildFlowPair(flow, pathPair, timestamp));
            flowPair.setTimeCreate(timestamp);

            Flow newFlow = flowPair.getFlowEntity();
            flowPairRepository.createOrUpdate(flowPair);

            updateIslsForFlowPath(newFlow.getForwardPath());
            updateIslsForFlowPath(newFlow.getReversePath());

            if (newFlow.isAllocateProtectedPath()) {
                createProtectedPath(newFlow);
            }

            return flowPair;
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendInstallRulesCommand(resultFlowPair.getFlowEntity());

        return resultFlowPair;
    }

    private void createProtectedPath(Flow flow)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException, ResourceAllocationException {
        flow.setGroupId(
                getOrCreateFlowGroupId(flow.getFlowId()));

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair protectedPathPair = pathComputer.getPath(flow);

        log.info("Creating the protected path {} for flow {}", protectedPathPair, flow);

        allocateProtectedPathResources(createFlowProtectedPath(flow, protectedPathPair));

        FlowPath forwardPath = flow.getProtectedForwardPath();
        FlowPath reversePath = flow.getProtectedReversePath();
        List<PathSegment> segments = union(forwardPath.getSegments(), reversePath.getSegments());

        List<PathSegment> primaryFlowSegments = Stream.of(flow.getForwardPath(), flow.getReversePath())
                .map(FlowPath::getSegments)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        if (IntersectionComputer.isProtectedPathOverlaps(primaryFlowSegments, segments)) {
            throw new UnroutableFlowException("Couldn't find non overlapping protected path",
                    flow.getFlowId());
        }

        flowRepository.createOrUpdate(flow);

        updateIslsForFlowPath(flow.getProtectedForwardPath());
        updateIslsForFlowPath(flow.getProtectedReversePath());
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

        FlowPair resultFlowPair = transactionManager.doInTransaction(() -> {
            //TODO(siakovenko): flow needs to be validated (existence of switches, same end-points, etc.)

            flowPairRepository.createOrUpdate(flowPairWithResources);

            updateIslsForFlowPath(flowPairWithResources.getFlowEntity().getForwardPath());
            updateIslsForFlowPath(flowPairWithResources.getFlowEntity().getReversePath());

            return flowPairWithResources;
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendInstallRulesCommand(resultFlowPair.getFlowEntity());
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
        FlowPair resultFlowPair = transactionManager.doInTransaction(() -> {
            FlowPair flowPair = getFlowPair(flowId)
                    .orElseThrow(() -> new FlowNotFoundException(flowId));
            Flow flow = flowPair.getFlowEntity();

            log.info("Deleting the flow: {}", flowPair);

            flowPairRepository.delete(flowPair);

            updateIslsForFlowPath(flow.getForwardPath());
            updateIslsForFlowPath(flow.getReversePath());
            if (flow.isAllocateProtectedPath()) {
                updateIslsForFlowPath(flow.getProtectedForwardPath());
                updateIslsForFlowPath(flow.getProtectedReversePath());
            }

            deallocateFlowResources(flow);

            return flowPair;
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendRemoveRulesCommand(resultFlowPair.getFlowEntity());

        return resultFlowPair;
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

        updatingFlow.setStatus(FlowStatus.IN_PROGRESS);

        UpdatedFlowPair result = transactionManager.doInTransaction(() -> {
            FlowPair currentFlowPair = getFlowPair(updatingFlow.getFlowId())
                    .orElseThrow(() -> new FlowNotFoundException(updatingFlow.getFlowId()));

            if (diverseFlowId == null) {
                updatingFlow.setGroupId(null);
            } else {
                checkDiverseFlow(updatingFlow, diverseFlowId);
                updatingFlow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
            }

            PathComputer pathComputer = pathComputerFactory.getPathComputer();
            PathPair pathPair = pathComputer.getPath(updatingFlow.getFlowEntity(), true);

            FlowPair newFlowPair = allocateFlowResources(buildFlowPair(updatingFlow, pathPair));
            newFlowPair.setTimeCreate(currentFlowPair.getForward().getTimeCreate());

            log.info("Updating the flow with {} and path: {}", updatingFlow, pathPair);

            flowPairRepository.delete(currentFlowPair);

            Flow currentFlow = currentFlowPair.getFlowEntity();
            updateIslsForFlowPath(currentFlow.getForwardPath());
            updateIslsForFlowPath(currentFlow.getReversePath());

            flowPairRepository.createOrUpdate(newFlowPair);

            Flow newFlow = newFlowPair.getFlowEntity();
            if (newFlow.isAllocateProtectedPath()) {
                createProtectedPath(newFlow);
            }

            updateIslsForFlowPath(newFlow.getForwardPath());
            updateIslsForFlowPath(newFlow.getReversePath());

            deallocateFlowResources(currentFlow);

            return new UpdatedFlowPair(currentFlowPair, newFlowPair);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendUpdateRulesCommand(new UpdatedFlow(
                result.getOldFlow().getFlowEntity(), result.getNewFlow().getFlowEntity())
        );

        return result.getNewFlow();
    }

    /**
     * Swaps primary path for the flow with protected paths.
     *
     * @param flowId    the flow id to be updated.
     * @param requestedPathId the path id to set as main flow path.
     * @param sender    the command sender for flow rules installation and deletion.
     * @return the updated flow.
     */
    public FlowPair pathSwap(String flowId, String requestedPathId, FlowCommandSender sender)
            throws FlowNotFoundException, FlowValidationException {
        FlowPair result = transactionManager.doInTransaction(() -> {
            FlowPair updatingFlowPair = getFlowPair(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));
            Flow flow = updatingFlowPair.getFlowEntity();

            // TODO validate pathId
            if (!flow.isAllocateProtectedPath()) {
                throw new FlowValidationException(format("Flow %s doesn't have protected path", flowId),
                        ErrorType.PARAMETERS_INVALID);
            }
            if (FlowStatus.UP != flow.getStatus()) {
                throw new FlowValidationException(
                        format("Flow %s is not in UP state", flowId), ErrorType.INTERNAL_ERROR);
            }

            log.info("Swapping paths {} for flow {}", flow);

            updatingFlowPair.setStatus(FlowStatus.IN_PROGRESS);

            FlowPath oldPrimaryForward = flow.getForwardPath();
            FlowPath oldPrimaryReverse = flow.getReversePath();
            flow.setForwardPath(flow.getProtectedForwardPath());
            flow.setReversePath(flow.getProtectedReversePath());
            flow.setProtectedForwardPath(oldPrimaryForward);
            flow.setProtectedReversePath(oldPrimaryReverse);

            flowPairRepository.createOrUpdate(updatingFlowPair);

            return updatingFlowPair;
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendSwapIngressCommand(result.getFlowEntity());

        return result;
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
    public UpdatedFlowPair rerouteFlow(String flowId, boolean forceToReroute, FlowCommandSender sender)
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

            return new UpdatedFlowPair(currentFlow, null);
        }

        FlowPair newFlow = allocateFlowResources(buildFlowPair(currentFlow.getForward(), pathPair));

        UpdatedFlowPair result = transactionManager.doInTransaction(() -> {
            newFlow.setStatus(FlowStatus.IN_PROGRESS);
            newFlow.setTimeCreate(currentFlow.getForward().getTimeCreate());

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

            return new UpdatedFlowPair(currentFlow, newFlow);
        });

        FlowPath forwardPath = currentFlow.getForward().getFlowPath();
        FlowPath reversePath = currentFlow.getReverse().getFlowPath();
        FlowResources flowResources = FlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                .forward(PathResources.builder().pathId(forwardPath.getPathId()).build())
                .reverse(PathResources.builder().pathId(reversePath.getPathId()).build())
                .build();

        flowResourcesManager.deallocateFlowResources(currentFlow.getFlowEntity(), flowResources);

        log.warn("Rerouted flow with new path: {}", result.getNewFlow());

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendUpdateRulesCommand(new UpdatedFlow(
                result.getOldFlow().getFlowEntity(), result.getNewFlow().getFlowEntity())
        );

        return result;
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

    private Flow createFlowProtectedPath(Flow flow, PathPair pathPair) {
        FlowPath forwardPath = buildFlowPath(flow.getFlowId(), pathPair.getForward());
        forwardPath.setBandwidth(flow.getBandwidth());
        forwardPath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        FlowPath reversePath = buildFlowPath(flow.getFlowId(), pathPair.getReverse());
        reversePath.setBandwidth(flow.getBandwidth());
        reversePath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        flow.setProtectedForwardPath(forwardPath);
        flow.setProtectedReversePath(reversePath);

        return flow;
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

        flow.setForwardPath(initForwardPathWithResources(
                flowPair.getForward().getFlowPath(), flowResources
        ));
        flow.setReversePath(initReversePathWithResources(
                flowPair.getReverse().getFlowPath(), flowResources
        ));

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

    private void allocateProtectedPathResources(Flow flow) throws ResourceAllocationException {
        FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);

        flow.setProtectedForwardPath(initForwardPathWithResources(
                flow.getProtectedForwardPath(), flowResources
        ));
        flow.setProtectedReversePath(initReversePathWithResources(
                flow.getProtectedReversePath(), flowResources
        ));
    }

    private void deallocateFlowResources(Flow flow) {
        FlowPath forwardPath = flow.getForwardPath();
        FlowPath reversePath = flow.getReversePath();
        FlowResources flowResources = FlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                .forward(PathResources.builder().pathId(forwardPath.getPathId()).build())
                .reverse(PathResources.builder().pathId(reversePath.getPathId()).build())
                .build();
        flowResourcesManager.deallocateFlowResources(flow, flowResources);

        if (flow.isAllocateProtectedPath()) {
            forwardPath = flow.getProtectedForwardPath();
            reversePath = flow.getProtectedReversePath();
            flowResources = FlowResources.builder()
                    .unmaskedCookie(forwardPath.getCookie().getUnmaskedValue())
                    .forward(PathResources.builder().pathId(forwardPath.getPathId()).build())
                    .reverse(PathResources.builder().pathId(reversePath.getPathId()).build())
                    .build();
            flowResourcesManager.deallocateFlowResources(flow, flowResources);
        }
    }

    private FlowPath initForwardPathWithResources(FlowPath path, FlowResources resources) {
        path.setPathId(resources.getForward().getPathId());
        path.setCookie(Cookie.buildForwardCookie(resources.getUnmaskedCookie()));
        if (resources.getForward().getMeterId() != null) {
            path.setMeterId(resources.getForward().getMeterId());
        }
        return path;
    }

    private FlowPath initReversePathWithResources(FlowPath path, FlowResources resources) {
        path.setPathId(resources.getReverse().getPathId());
        path.setCookie(Cookie.buildReverseCookie(resources.getUnmaskedCookie()));
        if (resources.getReverse().getMeterId() != null) {
            path.setMeterId(resources.getReverse().getMeterId());
        }
        return path;
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
}
