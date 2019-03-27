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
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandGroup;
import org.openkilda.messaging.command.CommandGroup.FailureReaction;
import org.openkilda.messaging.command.flow.DeallocateFlowResourcesRequest;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.flow.UpdateFlowPathStatusRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
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
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanResources;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flow.model.FlowPathPairWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.ReroutedFlow;
import org.openkilda.wfm.topology.flow.model.UpdatedFlowPathPair;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class FlowService extends BaseFlowService {
    private final SwitchRepository switchRepository;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final IslRepository islRepository;
    private final TransitVlanRepository transitVlanRepository;
    private final PathComputerFactory pathComputerFactory;
    private final FlowResourcesManager flowResourcesManager;
    private final FlowValidator flowValidator;
    private final FlowCommandFactory flowCommandFactory;

    public FlowService(@NonNull PersistenceManager persistenceManager, @NonNull PathComputerFactory pathComputerFactory,
                       @NonNull FlowResourcesManager flowResourcesManager, @NonNull FlowValidator flowValidator,
                       @NonNull FlowCommandFactory flowCommandFactory) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        islRepository = repositoryFactory.createIslRepository();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();
        this.pathComputerFactory = pathComputerFactory;
        this.flowResourcesManager = flowResourcesManager;
        this.flowValidator = flowValidator;
        this.flowCommandFactory = flowCommandFactory;
    }

    /**
     * Creates a flow by allocating a path and resources. Stores the flow entities into DB, and
     * invokes flow rules installation via the command sender.
     * <p/>
     * The flow and paths are created with IN_PROGRESS status.
     *
     * @param flow          the flow to be created.
     * @param diverseFlowId the flow id to build diverse group.
     * @param sender        the command sender for flow rules installation.
     * @return the created flow with the path and resources set.
     */
    public FlowPair createFlow(Flow flow, String diverseFlowId, FlowCommandSender sender)
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
        PathPair pathPair = pathComputer.getPath(flow);

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        flow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);

        // Build and store the flow with a path pair, use allocated resources for paths.

        FlowPathPairWithEncapsulation result = transactionManager.doInTransaction(() -> {
            Instant timestamp = Instant.now();
            FlowPathPair flowPathPair =
                    buildFlowPathPair(flow, pathPair, flowResources, FlowPathStatus.IN_PROGRESS, timestamp);

            Flow flowWithPaths = buildFlowWithPaths(flow, flowPathPair, FlowStatus.IN_PROGRESS, timestamp);
            flowWithPaths.setTimeCreate(timestamp);

            log.info("Creating the flow: {}", flowWithPaths);

            //
            flowPathRepository.lockInvolvedSwitches(flowPathPair.getForward(), flowPathPair.getReverse());

            // Store the flow and both paths
            flowRepository.createOrUpdate(flowWithPaths);

            updateIslsForFlowPath(flowPathPair.getForward());
            updateIslsForFlowPath(flowPathPair.getReverse());

            return buildFlowPathsWithEncapsulation(flowWithPaths, flowPathPair, flowResources);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(flow.getFlowId(),
                createInstallRulesGroups(result),
                createFlowPathStatusRequests(result, FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result, FlowPathStatus.INACTIVE));

        return buildFlowPair(result);
    }

    /**
     * Stores a flow and related entities into DB, and invokes flow rules installation via the command sender.
     *
     * @param flowPair the flow to be saved.
     * @param sender   the command sender for flow rules installation.
     */
    public void saveFlow(FlowPair flowPair, FlowCommandSender sender) throws FlowAlreadyExistException,
            ResourceAllocationException {
        String flowId = flowPair.getFlowEntity().getFlowId();
        if (doesFlowExist(flowId)) {
            throw new FlowAlreadyExistException(flowId);
        }

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        flowPair.getFlowEntity().setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        FlowResources flowResources = flowResourcesManager.allocateFlowResources(flowPair.getFlowEntity());

        // Store the flow, use allocated resources for paths.

        FlowPathPairWithEncapsulation result = transactionManager.doInTransaction(() -> {
            Instant timestamp = Instant.now();
            FlowPathPair flowPathPair = buildFlowPathPair(flowPair, flowResources, timestamp);

            Flow flowWithPaths = buildFlowWithPaths(flowPair.getFlowEntity(), flowPathPair,
                    flowPair.getFlowEntity().getStatus(), timestamp);
            flowWithPaths.setTimeCreate(timestamp);

            log.info("Saving (pushing) the flow: {}", flowWithPaths);

            flowPathRepository.lockInvolvedSwitches(flowPathPair.getForward(), flowPathPair.getReverse());

            //TODO(siakovenko): flow needs to be validated (existence of switches, same end-points, etc.)

            // Store the flow and both paths
            flowRepository.createOrUpdate(flowWithPaths);

            updateIslsForFlowPath(flowPathPair.getForward());
            updateIslsForFlowPath(flowPathPair.getReverse());

            return buildFlowPathsWithEncapsulation(flowWithPaths, flowPathPair, flowResources);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(flowId,
                createInstallRulesGroups(result),
                createFlowPathStatusRequests(result, FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result, FlowPathStatus.INACTIVE));
    }

    /**
     * Deletes a flow and its paths from DB. Initiates resource deallocation.
     * Invokes flow rules deletion via the command sender.
     *
     * @param flowId the flow to be deleted.
     * @param sender the command sender for flow rules deletion.
     * @return the deleted flow.
     */
    public UnidirectionalFlow deleteFlow(String flowId, FlowCommandSender sender) throws FlowNotFoundException {
        List<FlowPathWithEncapsulation> result = transactionManager.doInTransaction(() -> {
            Flow flow = flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

            log.info("Deleting the flow: {}", flow);

            Collection<FlowPath> flowPaths = flowPathRepository.findByFlowId(flowId);

            flowPathRepository.lockInvolvedSwitches(flowPaths.toArray(new FlowPath[0]));

            List<FlowPathWithEncapsulation> flowPathWithEncapsulations = flowPaths.stream()
                    .map(flowPath -> getFlowPathWithEncapsulation(flow, flowPath))
                    .collect(Collectors.toList());

            // Remove flow and all associated paths
            flowRepository.delete(flow);

            flowPathWithEncapsulations.forEach(flowPath -> updateIslsForFlowPath(flowPath.getFlowPath()));

            return flowPathWithEncapsulations;
        });

        // Assemble a command batch with RemoveRule commands and resource deallocation requests.

        List<CommandGroup> commandGroups = new ArrayList<>();
        // We can assemble all paths into a single command batch as regardless of each execution result,
        // the TransactionBolt will try to perform all of them.
        // This is because FailureReaction.IGNORE used in createRemoveXXX methods.
        result.forEach(flowPathToRemove -> commandGroups.addAll(createRemoveRulesGroups(flowPathToRemove)));
        commandGroups.addAll(createDeallocateResourcesGroups(flowId, result));

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(flowId, commandGroups, emptyList(), emptyList());

        if (!result.isEmpty()) {
            return buildForwardUnidirectionalFlow(result.get(0));
        } else {
            return null;
        }
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
    public FlowPair updateFlow(Flow updatingFlow, String diverseFlowId, FlowCommandSender sender)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException, FlowValidationException,
            SwitchValidationException, ResourceAllocationException {
        flowValidator.validate(updatingFlow);

        String flowId = updatingFlow.getFlowId();
        FlowPathPairWithEncapsulation currentFlow =
                getFlowPathPairWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        if (diverseFlowId == null) {
            updatingFlow.setGroupId(null);
        } else {
            checkDiverseFlow(updatingFlow, diverseFlowId);
            updatingFlow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
        }

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(updatingFlow, true);

        log.info("Updating the flow with {} and path: {}", updatingFlow, pathPair);

        UpdatedFlowPathPair result = updateFlowAndPaths(currentFlow, updatingFlow, pathPair, sender);

        return buildFlowPair(result);
    }

    /**
     * Reroutes a flow via a new path. Allocates new path and resources.
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
        FlowPathPairWithEncapsulation currentFlow =
                getFlowPathPairWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        log.warn("Origin flow {} path: {}", flowId, currentFlow.getForward());

        // TODO: the strategy is defined either per flow or system-wide.
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(currentFlow.getFlow(), true);

        log.warn("Potential New Path for flow {} with LEFT path: {}, RIGHT path: {}",
                flowId, pathPair.getForward(), pathPair.getReverse());

        boolean isFoundNewPath = !isSamePath(pathPair.getForward(), currentFlow.getForwardPath())
                || !isSamePath(pathPair.getReverse(), currentFlow.getReversePath());
        //no need to emit changes if path wasn't changed and flow is active.
        //force means to update flow even if path is not changed.
        if (!isFoundNewPath && currentFlow.getFlow().isActive() && !forceToReroute) {
            log.warn("Reroute {} is unsuccessful: can't find new path.", flowId);

            return new ReroutedFlow(
                    buildForwardUnidirectionalFlow(currentFlow.getForward()), null);
        }

        UpdatedFlowPathPair result = updateFlowAndPaths(currentFlow, currentFlow.getFlow(), pathPair, sender);

        log.warn("Rerouted flow with new path: {}", result.getForward());

        return new ReroutedFlow(
                buildForwardUnidirectionalFlow(currentFlow.getForward()),
                buildForwardUnidirectionalFlow(result.getForward()));
    }

    private UpdatedFlowPathPair updateFlowAndPaths(FlowPathPairWithEncapsulation currentFlow, Flow updatingFlow,
                                                   PathPair newPathPair, FlowCommandSender sender)
            throws ResourceAllocationException {

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        updatingFlow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        FlowResources flowResources = flowResourcesManager.allocateFlowResources(updatingFlow);

        // Recreate the flow, use allocated resources for new paths.

        UpdatedFlowPathPair result = transactionManager.doInTransaction(() -> {
            Instant timestamp = Instant.now();
            FlowPathPair newFlowPathPair =
                    buildFlowPathPair(updatingFlow, newPathPair, flowResources, FlowPathStatus.IN_PROGRESS, timestamp);

            Flow newFlowWithPaths =
                    buildFlowWithPaths(updatingFlow, newFlowPathPair, FlowStatus.IN_PROGRESS, timestamp);

            FlowPath currentForwardPath = currentFlow.getForwardPath();
            FlowPath currentReversePath = currentFlow.getReversePath();
            FlowPath newForwardPath = newFlowWithPaths.getForwardPath();
            FlowPath newReversePath = newFlowWithPaths.getReversePath();

            flowPathRepository.lockInvolvedSwitches(currentForwardPath, currentReversePath,
                    newForwardPath, newReversePath);

            flowRepository.delete(currentFlow.getFlow());

            updateIslsForFlowPath(currentForwardPath);
            updateIslsForFlowPath(currentReversePath);

            flowRepository.createOrUpdate(newFlowWithPaths);

            updateIslsForFlowPath(newForwardPath);
            updateIslsForFlowPath(newReversePath);

            return new UpdatedFlowPathPair(currentFlow,
                    buildFlowPathsWithEncapsulation(newFlowWithPaths, newFlowPathPair, flowResources));
        });

        // Assemble a command batch with InstallXXXRule, RemoveRule commands and a resource deallocation request.

        List<CommandGroup> commandGroups = new ArrayList<>();
        commandGroups.addAll(createInstallRulesGroups(result));
        commandGroups.addAll(createRemoveRulesGroups(result.getOldForward()));
        commandGroups.addAll(createRemoveRulesGroups(result.getOldReverse()));
        commandGroups.addAll(createDeallocateResourcesGroups(currentFlow.getFlow().getFlowId(),
                asList(result.getOldForward(), result.getOldReverse())));

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(currentFlow.getFlow().getFlowId(),
                commandGroups,
                createFlowPathStatusRequests(result, FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result, FlowPathStatus.INACTIVE));

        return result;
    }

    /**
     * Deallocates resources of the flow path.
     *
     * @param pathId            the flow path to be used to identify resources.
     * @param unmaskedCookie    the flow cookie to be released.
     * @param encapsulationType determine the encapsulation type used for resource allocation.
     */
    public void deallocateResources(PathId pathId, long unmaskedCookie, FlowEncapsulationType encapsulationType) {
        flowResourcesManager.deallocatePathPairResources(pathId, unmaskedCookie, encapsulationType);
    }

    /**
     * Updates the flow path status. It also affects the flow status if the path is active.
     *
     * @param flowId         the flow to be updated.
     * @param pathId         the flow path to be updated.
     * @param flowPathStatus the status to be set.
     */

    public void updateFlowPathStatus(String flowId, PathId pathId, FlowPathStatus flowPathStatus) {
        transactionManager.doInTransaction(() -> {
            Flow flow = flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

            FlowPath flowPath = flowPathRepository.findById(pathId)
                    .orElseThrow(() -> new FlowNotFoundException(flowId, format("Flow path %s not found.", pathId)));

            if (flowPathStatus != flowPath.getStatus()) {
                flowPath.setStatus(flowPathStatus);
                flowPathRepository.createOrUpdate(flowPath);
            }

            FlowPath pairedFlowPath = null;
            if (pathId.equals(flow.getForwardPathId())) {
                pairedFlowPath = flow.getReversePath();
            } else if (pathId.equals(flow.getReversePathId())) {
                pairedFlowPath = flow.getForwardPath();
            }

            if (pairedFlowPath == null) {
                // The path is not active for the flow or it has no paired path. Skip it.
                return;
            }

            // Calculate the combined flow status.
            FlowStatus flowStatus = flow.getStatus();
            if (pairedFlowPath.getStatus() == FlowPathStatus.ACTIVE) {
                switch (flowPathStatus) {
                    case ACTIVE:
                        flowStatus = FlowStatus.UP;
                        break;
                    case INACTIVE:
                        flowStatus = FlowStatus.DOWN;
                        break;
                    case IN_PROGRESS:
                        flowStatus = FlowStatus.IN_PROGRESS;
                        break;
                    default:
                        throw new IllegalArgumentException(format("Unsupported flow path status %s", flowPathStatus));
                }
            } else if (flowPathStatus == FlowPathStatus.INACTIVE) {
                flowStatus = FlowStatus.DOWN;
            }

            if (flowStatus != flow.getStatus()) {
                flow.setStatus(flowStatus);
                flowRepository.createOrUpdate(flow);
            }
        });
    }

    private FlowPathPair buildFlowPathPair(FlowPair flowPair, FlowResources flowResources, Instant timeCreate) {
        FlowPathStatus pathStatus = flowPair.getForward().getStatus() == FlowStatus.IN_PROGRESS
                ? FlowPathStatus.IN_PROGRESS : FlowPathStatus.ACTIVE;

        FlowPath forwardPath = flowPair.getForward().getFlowPath().toBuilder()
                .srcSwitch(switchRepository.reload(flowPair.getForward().getSrcSwitch()))
                .destSwitch(switchRepository.reload(flowPair.getForward().getDestSwitch()))
                .status(pathStatus)
                .timeCreate(timeCreate)
                .build();
        FlowPath reversePath = flowPair.getReverse().getFlowPath().toBuilder()
                .srcSwitch(switchRepository.reload(flowPair.getReverse().getSrcSwitch()))
                .destSwitch(switchRepository.reload(flowPair.getReverse().getDestSwitch()))
                .status(pathStatus)
                .timeCreate(timeCreate)
                .build();

        FlowPathPair pathPair = FlowPathPair.builder()
                .forward(forwardPath)
                .reverse(reversePath)
                .build();
        setResourcesInPaths(pathPair, flowResources);
        return pathPair;
    }

    private FlowPathPair buildFlowPathPair(Flow flow, PathPair pathPair, FlowResources flowResources,
                                           FlowPathStatus pathStatus, Instant timeCreate) {
        FlowPath forwardPath = buildFlowPath(flow.getFlowId(), pathPair.getForward(), pathStatus, timeCreate);
        forwardPath.setBandwidth(flow.getBandwidth());
        forwardPath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        FlowPath reversePath = buildFlowPath(flow.getFlowId(), pathPair.getReverse(), pathStatus, timeCreate);
        reversePath.setBandwidth(flow.getBandwidth());
        reversePath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        FlowPathPair flowPathPair = FlowPathPair.builder()
                .forward(forwardPath)
                .reverse(reversePath)
                .build();
        setResourcesInPaths(flowPathPair, flowResources);
        return flowPathPair;
    }

    private void setResourcesInPaths(FlowPathPair pathPair, FlowResources flowResources) {
        FlowPath forwardPath = pathPair.getForward();
        forwardPath.setPathId(flowResources.getForward().getPathId());
        forwardPath.setCookie(Cookie.buildForwardCookie(flowResources.getUnmaskedCookie()));
        if (flowResources.getForward().getMeterId() != null) {
            forwardPath.setMeterId(flowResources.getForward().getMeterId());
        }

        FlowPath reversePath = pathPair.getReverse();
        reversePath.setPathId(flowResources.getReverse().getPathId());
        reversePath.setCookie(Cookie.buildReverseCookie(flowResources.getUnmaskedCookie()));
        if (flowResources.getReverse().getMeterId() != null) {
            reversePath.setMeterId(flowResources.getReverse().getMeterId());
        }
    }

    private FlowPath buildFlowPath(String flowId, Path path, FlowPathStatus pathStatus, Instant timeCreate) {
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
                .status(pathStatus)
                .timeCreate(timeCreate)
                .build();
    }

    private Flow buildFlowWithPaths(Flow flow, FlowPathPair flowPathPair, FlowStatus status, Instant timeModify) {
        return flow.toBuilder()
                .srcSwitch(switchRepository.reload(flow.getSrcSwitch()))
                .destSwitch(switchRepository.reload(flow.getDestSwitch()))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .timeModify(timeModify)
                .forwardPath(flowPathPair.getForward())
                .reversePath(flowPathPair.getReverse())
                .status(status)
                .build();
    }

    private boolean isSamePath(Path path, FlowPath flowPath) {
        if (!path.getSrcSwitchId().equals(flowPath.getSrcSwitch().getSwitchId())
                || !path.getDestSwitchId().equals(flowPath.getDestSwitch().getSwitchId())
                || path.getSegments().size() != flowPath.getSegments().size()) {
            return false;
        }

        Iterator<Path.Segment> pathIt = path.getSegments().iterator();
        Iterator<PathSegment> flowPathIt = flowPath.getSegments().iterator();
        while (pathIt.hasNext() && flowPathIt.hasNext()) {
            Path.Segment pathSegment = pathIt.next();
            PathSegment flowSegment = flowPathIt.next();
            if (!pathSegment.getSrcSwitchId().equals(flowSegment.getSrcSwitch().getSwitchId())
                    || !pathSegment.getDestSwitchId().equals(flowSegment.getDestSwitch().getSwitchId())
                    || pathSegment.getSrcPort() != flowSegment.getSrcPort()
                    || pathSegment.getDestPort() != flowSegment.getDestPort()) {
                return false;
            }
        }

        return true;
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

    private void checkDiverseFlow(Flow targetFlow, String flowId) throws FlowNotFoundException,
            FlowValidationException {
        if (targetFlow.isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't add one-switch flow into diverse group",
                    ErrorType.NOT_IMPLEMENTED);
        }

        Flow diverseFlow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        if (diverseFlow.isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't create diverse group with one-switch flow",
                    ErrorType.NOT_IMPLEMENTED);
        }
    }

    private FlowPair buildFlowPair(FlowPathPairWithEncapsulation flowPath) {
        return new FlowPair(flowPath.getFlow(), flowPath.getForwardTransitVlan(), flowPath.getReverseTransitVlan());
    }

    private UnidirectionalFlow buildForwardUnidirectionalFlow(FlowPathWithEncapsulation flowPath) {
        return new UnidirectionalFlow(flowPath.getFlow(), flowPath.getFlowPath(), flowPath.getTransitVlan(), true);
    }

    private FlowPathPairWithEncapsulation buildFlowPathsWithEncapsulation(Flow flow, FlowPathPair flowPathPair,
                                                                          FlowResources flowResources) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan forwardTransitVlan =
                Optional.ofNullable((TransitVlanResources) flowResources.getForward().getEncapsulationResources())
                        .map(TransitVlanResources::getTransitVlan)
                        .orElse(null);
        TransitVlan reverseTransitVlan =
                Optional.ofNullable((TransitVlanResources) flowResources.getReverse().getEncapsulationResources())
                        .map(TransitVlanResources::getTransitVlan)
                        .orElse(null);

        return FlowPathPairWithEncapsulation.builder()
                .flow(flow)
                .forwardPath(flowPathPair.getForward())
                .reversePath(flowPathPair.getReverse())
                .forwardTransitVlan(forwardTransitVlan)
                .reverseTransitVlan(reverseTransitVlan)
                .build();
    }

    private FlowPathWithEncapsulation getFlowPathWithEncapsulation(Flow flow, FlowPath flowPath) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan = transitVlanRepository.findByPathId(flowPath.getPathId()).orElse(null);

        return FlowPathWithEncapsulation.builder()
                .flow(flow)
                .flowPath(flowPath)
                .transitVlan(transitVlan)
                .build();
    }

    private List<CommandGroup> createInstallRulesGroups(FlowPathPairWithEncapsulation pathsToInstall) {
        List<CommandGroup> commandGroups = new ArrayList<>();

        createInstallTransitAndEgressRules(pathsToInstall.getFlow(), pathsToInstall.getForwardPath(),
                pathsToInstall.getForwardTransitVlan()).ifPresent(commandGroups::add);
        createInstallTransitAndEgressRules(pathsToInstall.getFlow(), pathsToInstall.getReversePath(),
                pathsToInstall.getReverseTransitVlan()).ifPresent(commandGroups::add);
        // The ingress rule must be installed after the egress and transit ones.
        commandGroups.add(createInstallIngressRules(pathsToInstall.getFlow(), pathsToInstall.getForwardPath(),
                pathsToInstall.getForwardTransitVlan()));
        commandGroups.add(createInstallIngressRules(pathsToInstall.getFlow(), pathsToInstall.getReversePath(),
                pathsToInstall.getReverseTransitVlan()));

        return commandGroups;
    }

    private Optional<CommandGroup> createInstallTransitAndEgressRules(Flow flow, FlowPath flowPath,
                                                                      TransitVlan transitVlan) {
        List<InstallTransitFlow> rules = flowCommandFactory.createInstallTransitAndEgressRulesForFlow(flow, flowPath,
                transitVlan);
        return !rules.isEmpty() ? Optional.of(new CommandGroup(rules, FailureReaction.ABORT_BATCH))
                : Optional.empty();
    }

    private CommandGroup createInstallIngressRules(Flow flow, FlowPath flowPath, TransitVlan transitVlan) {
        return new CommandGroup(singletonList(
                flowCommandFactory.createInstallIngressRulesForFlow(flow, flowPath, transitVlan)),
                FailureReaction.ABORT_BATCH);
    }

    private List<CommandGroup> createRemoveRulesGroups(FlowPathWithEncapsulation pathToRemove) {
        List<CommandGroup> commandGroups = new ArrayList<>();

        commandGroups.add(createRemoveIngressRules(pathToRemove.getFlow(), pathToRemove.getFlowPath()));
        createRemoveTransitAndEgressRules(pathToRemove.getFlow(), pathToRemove.getFlowPath(),
                pathToRemove.getTransitVlan()).ifPresent(commandGroups::add);

        return commandGroups;
    }

    private CommandGroup createRemoveIngressRules(Flow flow, FlowPath flowPath) {
        return new CommandGroup(singletonList(
                flowCommandFactory.createRemoveIngressRulesForFlow(flow, flowPath)), FailureReaction.IGNORE);
    }

    private Optional<CommandGroup> createRemoveTransitAndEgressRules(Flow flow, FlowPath flowPath,
                                                                     TransitVlan transitVlan) {
        List<RemoveFlow> rules =
                flowCommandFactory.createRemoveTransitAndEgressRulesForFlow(flow, flowPath, transitVlan);
        return !rules.isEmpty() ? Optional.of(new CommandGroup(rules, FailureReaction.IGNORE))
                : Optional.empty();
    }

    private Collection<CommandGroup> createDeallocateResourcesGroups(String flowId,
                                                                     List<FlowPathWithEncapsulation> flowPaths) {
        List<CommandData> deallocationCommands = flowPaths.stream()
                .map(flowPath ->
                        new DeallocateFlowResourcesRequest(flowId,
                                flowPath.getFlowPath().getCookie().getUnmaskedValue(),
                                flowPath.getFlowPath().getPathId(),
                                flowPath.getFlow().getEncapsulationType()))
                .collect(Collectors.toList());
        return singletonList(new CommandGroup(deallocationCommands, FailureReaction.IGNORE));
    }

    private List<? extends CommandData> createFlowPathStatusRequests(FlowPathPairWithEncapsulation pathPair,
                                                                     FlowPathStatus status) {
        return asList(
                new UpdateFlowPathStatusRequest(pathPair.getFlow().getFlowId(),
                        pathPair.getForwardPath().getPathId(), status),
                new UpdateFlowPathStatusRequest(pathPair.getFlow().getFlowId(),
                        pathPair.getReversePath().getPathId(), status)
        );
    }
}
