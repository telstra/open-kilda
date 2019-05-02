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
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flow.model.FlowPathWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.ReroutedFlow;
import org.openkilda.wfm.topology.flow.model.UpdatedFlowWithEncapsulation;
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

        FlowWithEncapsulation result = transactionManager.doInTransaction(() -> {
            Instant timestamp = Instant.now();
            FlowPathPair flowPathPair =
                    buildFlowPathPair(flow, pathPair, flowResources, FlowPathStatus.IN_PROGRESS, timestamp);

            Flow flowWithPaths = buildFlowWithPaths(flow, flowPathPair, FlowStatus.IN_PROGRESS, timestamp);
            flowWithPaths.setTimeCreate(timestamp);

            log.info("Creating the flow: {}", flowWithPaths);

            flowPathRepository.lockInvolvedSwitches(flowWithPaths.getForwardPath(), flowWithPaths.getReversePath());

            // Store the flow and both paths
            flowRepository.createOrUpdate(flowWithPaths);

            updateIslsForFlowPath(flowWithPaths.getForwardPath());
            updateIslsForFlowPath(flowWithPaths.getReversePath());

            return buildFlowWithEncapsulation(flowWithPaths, flowResources);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(flow.getFlowId(),
                createInstallRulesGroups(result),
                createFlowPathStatusRequests(result.getFlow(), FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result.getFlow(), FlowPathStatus.INACTIVE));

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
        Flow flow = flowPair.getForward().getFlow();
        String flowId = flow.getFlowId();
        if (doesFlowExist(flowId)) {
            throw new FlowAlreadyExistException(flowId);
        }

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        flow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);

        // Store the flow, use allocated resources for paths.

        FlowWithEncapsulation result = transactionManager.doInTransaction(() -> {
            Instant timestamp = Instant.now();
            FlowPathPair flowPathPair = buildFlowPathPair(flowPair, flowResources, timestamp);

            Flow flowWithPaths = buildFlowWithPaths(flow, flowPathPair, flow.getStatus(), timestamp);
            flowWithPaths.setTimeCreate(timestamp);

            log.info("Saving (pushing) the flow: {}", flowWithPaths);

            flowPathRepository.lockInvolvedSwitches(flowWithPaths.getForwardPath(), flowWithPaths.getReversePath());

            //TODO(siakovenko): flow needs to be validated (existence of switches, same end-points, etc.)

            // Store the flow and both paths
            flowRepository.createOrUpdate(flowWithPaths);

            updateIslsForFlowPath(flowWithPaths.getForwardPath());
            updateIslsForFlowPath(flowWithPaths.getReversePath());

            return buildFlowWithEncapsulation(flowWithPaths, flowResources);
        });

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(flowId,
                createInstallRulesGroups(result),
                createFlowPathStatusRequests(result.getFlow(), FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result.getFlow(), FlowPathStatus.INACTIVE));
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

            Collection<FlowPath> flowPaths = flow.getPaths();

            flowPathRepository.lockInvolvedSwitches(flowPaths.toArray(new FlowPath[0]));

            List<FlowPathWithEncapsulation> flowPathWithEncapsulation = flowPaths.stream()
                    .map(flowPath -> getFlowPathWithEncapsulation(flowPath))
                    .collect(Collectors.toList());

            // Remove flow and all associated paths
            flowRepository.delete(flow);

            flowPathWithEncapsulation.forEach(flowPath -> updateIslsForFlowPath(flowPath.getFlowPath()));

            return flowPathWithEncapsulation;
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
        FlowWithEncapsulation currentFlow =
                getFlowWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        if (diverseFlowId == null) {
            updatingFlow.setGroupId(null);
        } else {
            checkDiverseFlow(updatingFlow, diverseFlowId);
            updatingFlow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
        }

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair pathPair = pathComputer.getPath(updatingFlow, true);

        log.info("Updating the flow with {} and path: {}", updatingFlow, pathPair);

        UpdatedFlowWithEncapsulation result = updateFlowAndPaths(currentFlow, updatingFlow, pathPair, sender);

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
        FlowWithEncapsulation currentFlow =
                getFlowWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        log.warn("Origin flow {} path: {}", flowId, currentFlow.getForwardPath());

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
                    buildForwardUnidirectionalFlow(currentFlow), null);
        }

        UpdatedFlowWithEncapsulation result = updateFlowAndPaths(currentFlow, currentFlow.getFlow(), pathPair, sender);

        log.warn("Rerouted flow with new path: {}", result.getForwardPath());

        return new ReroutedFlow(
                buildForwardUnidirectionalFlow(currentFlow),
                buildForwardUnidirectionalFlow(result));
    }

    private UpdatedFlowWithEncapsulation updateFlowAndPaths(FlowWithEncapsulation currentFlow, Flow updatingFlow,
                                                            PathPair newPathPair, FlowCommandSender sender)
            throws ResourceAllocationException {

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        updatingFlow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);
        FlowResources flowResources = flowResourcesManager.allocateFlowResources(updatingFlow);

        // Recreate the flow, use allocated resources for new paths.

        UpdatedFlowWithEncapsulation result = transactionManager.doInTransaction(() -> {
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

            return new UpdatedFlowWithEncapsulation(currentFlow,
                    buildFlowWithEncapsulation(newFlowWithPaths, flowResources));
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
                createFlowPathStatusRequests(result.getFlow(), FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result.getFlow(), FlowPathStatus.INACTIVE));

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
        flowResourcesManager.deallocatePathResources(pathId, unmaskedCookie, encapsulationType);
    }

    /**
     * Updates the status of a flow(s).
     * <p/>
     * If the flow obtains DOWN status, then forward and reverse paths become INACTIVE.
     *
     * @param flowId a flow ID used to locate the flow(s).
     * @param status the status to set.
     */
    public void updateFlowStatus(String flowId, FlowStatus status) {
        transactionManager.doInTransaction(() ->
                flowRepository.findById(flowId)
                        .ifPresent(flow -> {
                            flow.setStatus(status);

                            if (status == FlowStatus.DOWN) {
                                // Mark active paths as INACTIVE
                                flow.getForwardPath().setStatus(FlowPathStatus.INACTIVE);
                                flow.getReversePath().setStatus(FlowPathStatus.INACTIVE);
                            }

                            flowRepository.createOrUpdate(flow);
                        }));
    }

    /**
     * Updates the flow path status.
     * <p/>
     * It also affects the flow status if both (forward and reverse) paths are active.
     *
     * @param flowId         the flow to be updated.
     * @param pathId         the flow path to be updated.
     * @param flowPathStatus the status to be set.
     */

    public void updateFlowPathStatus(String flowId, PathId pathId, FlowPathStatus flowPathStatus) {
        transactionManager.doInTransaction(() -> {
            Flow flow = flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

            log.debug("Updating the status of flow path {} in {}", pathId, flow);

            FlowPath flowPath = flow.getPaths().stream()
                    .filter(path -> path.getPathId().equals(pathId))
                    .findAny()
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
                        throw new IllegalArgumentException(format("Unsupported flow path status %s",
                                flowPathStatus));
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
        FlowPath forwardPath = buildFlowPath(flow, pathPair.getForward(), pathStatus, timeCreate);
        forwardPath.setBandwidth(flow.getBandwidth());
        forwardPath.setIgnoreBandwidth(flow.isIgnoreBandwidth());

        FlowPath reversePath = buildFlowPath(flow, pathPair.getReverse(), pathStatus, timeCreate);
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

    private FlowPath buildFlowPath(Flow flow, Path path, FlowPathStatus pathStatus, Instant timeCreate) {
        PathId pathId = new PathId(UUID.randomUUID().toString());
        FlowPath flowPath = FlowPath.builder()
                .flow(flow)
                .pathId(pathId)
                .srcSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getSrcSwitchId()).build()))
                .destSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getDestSwitchId()).build()))
                .status(pathStatus)
                .timeCreate(timeCreate)
                .build();

        List<PathSegment> segments = path.getSegments().stream()
                .map(segment -> PathSegment.builder()
                        .path(flowPath)
                        .srcSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getSrcSwitchId()).build()))
                        .srcPort(segment.getSrcPort())
                        .destSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getDestSwitchId()).build()))
                        .destPort(segment.getDestPort())
                        .latency(segment.getLatency())
                        .build())
                .collect(Collectors.toList());
        flowPath.setSegments(segments);

        return flowPath;
    }

    private Flow buildFlowWithPaths(Flow flow, FlowPathPair flowPathPair, FlowStatus status, Instant timeModify) {
        Flow copied = flow.toBuilder()
                .srcSwitch(switchRepository.reload(flow.getSrcSwitch()))
                .destSwitch(switchRepository.reload(flow.getDestSwitch()))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .timeModify(timeModify)
                .status(status)
                .build();

        FlowPath forward = flowPathPair.getForward();
        forward.setFlow(copied);
        copied.setForwardPath(forward);

        FlowPath reverse = flowPathPair.getReverse();
        reverse.setFlow(copied);
        copied.setReversePath(reverse);
        return copied;
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

    private FlowPair buildFlowPair(FlowWithEncapsulation flow) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan forwardTransitVlan =
                Optional.ofNullable((TransitVlanEncapsulation) flow.getForwardEncapsulation())
                        .map(TransitVlanEncapsulation::getTransitVlan)
                        .orElse(null);
        TransitVlan reverseTransitVlan =
                Optional.ofNullable((TransitVlanEncapsulation) flow.getReverseEncapsulation())
                        .map(TransitVlanEncapsulation::getTransitVlan)
                        .orElse(null);

        return new FlowPair(flow.getFlow(), forwardTransitVlan, reverseTransitVlan);
    }

    private UnidirectionalFlow buildForwardUnidirectionalFlow(FlowWithEncapsulation flow) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan =
                Optional.ofNullable((TransitVlanEncapsulation) flow.getForwardEncapsulation())
                        .map(TransitVlanEncapsulation::getTransitVlan)
                        .orElse(null);

        return new UnidirectionalFlow(flow.getForwardPath(), transitVlan, true);
    }

    private UnidirectionalFlow buildForwardUnidirectionalFlow(FlowPathWithEncapsulation flow) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan =
                Optional.ofNullable((TransitVlanEncapsulation) flow.getEncapsulation())
                        .map(TransitVlanEncapsulation::getTransitVlan)
                        .orElse(null);

        return new UnidirectionalFlow(flow.getFlowPath(), transitVlan, true);
    }

    private FlowWithEncapsulation buildFlowWithEncapsulation(Flow flow, FlowResources flowResources) {
        return FlowWithEncapsulation.builder()
                .flow(flow)
                .forwardEncapsulation(flowResources.getForward().getEncapsulationResources())
                .reverseEncapsulation(flowResources.getReverse().getEncapsulationResources())
                .build();
    }

    private FlowPathWithEncapsulation getFlowPathWithEncapsulation(FlowPath flowPath) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan = transitVlanRepository.findByPathId(flowPath.getPathId()).stream()
                .findAny().orElse(null);

        return FlowPathWithEncapsulation.builder()
                .flowPath(flowPath)
                .encapsulation(TransitVlanEncapsulation.builder().transitVlan(transitVlan).build())
                .build();
    }

    private List<CommandGroup> createInstallRulesGroups(FlowWithEncapsulation pathsToInstall) {
        List<CommandGroup> commandGroups = new ArrayList<>();

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan forwardTransitVlan =
                Optional.ofNullable((TransitVlanEncapsulation) pathsToInstall.getForwardEncapsulation())
                        .map(TransitVlanEncapsulation::getTransitVlan)
                        .orElse(null);
        TransitVlan reverseTransitVlan =
                Optional.ofNullable((TransitVlanEncapsulation) pathsToInstall.getReverseEncapsulation())
                        .map(TransitVlanEncapsulation::getTransitVlan)
                        .orElse(null);

        createInstallTransitAndEgressRules(pathsToInstall.getForwardPath(),
                forwardTransitVlan).ifPresent(commandGroups::add);
        createInstallTransitAndEgressRules(pathsToInstall.getReversePath(),
                reverseTransitVlan).ifPresent(commandGroups::add);
        // The ingress rule must be installed after the egress and transit ones.
        commandGroups.add(createInstallIngressRules(pathsToInstall.getForwardPath(),
                forwardTransitVlan));
        commandGroups.add(createInstallIngressRules(pathsToInstall.getReversePath(),
                reverseTransitVlan));

        return commandGroups;
    }

    private Optional<CommandGroup> createInstallTransitAndEgressRules(FlowPath flowPath,
                                                                      TransitVlan transitVlan) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        List<InstallTransitFlow> rules =
                flowCommandFactory.createInstallTransitAndEgressRulesForFlow(flowPath.getFlow(), flowPath, transitVlan);
        return !rules.isEmpty() ? Optional.of(new CommandGroup(rules, FailureReaction.ABORT_BATCH))
                : Optional.empty();
    }

    private CommandGroup createInstallIngressRules(FlowPath flowPath, TransitVlan transitVlan) {
        return new CommandGroup(singletonList(
                flowCommandFactory.createInstallIngressRulesForFlow(flowPath.getFlow(), flowPath, transitVlan)),
                FailureReaction.ABORT_BATCH);
    }

    private List<CommandGroup> createRemoveRulesGroups(FlowPathWithEncapsulation pathToRemove) {
        List<CommandGroup> commandGroups = new ArrayList<>();

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan = Optional.ofNullable((TransitVlanEncapsulation) pathToRemove.getEncapsulation())
                .map(TransitVlanEncapsulation::getTransitVlan)
                .orElse(null);

        commandGroups.add(createRemoveIngressRules(pathToRemove.getFlowPath()));
        createRemoveTransitAndEgressRules(pathToRemove.getFlowPath(), transitVlan).ifPresent(commandGroups::add);

        return commandGroups;
    }

    private CommandGroup createRemoveIngressRules(FlowPath flowPath) {
        return new CommandGroup(
                singletonList(flowCommandFactory.createRemoveIngressRulesForFlow(flowPath.getFlow(), flowPath)),
                FailureReaction.IGNORE);
    }

    private Optional<CommandGroup> createRemoveTransitAndEgressRules(FlowPath flowPath, TransitVlan transitVlan) {
        List<RemoveFlow> rules =
                flowCommandFactory.createRemoveTransitAndEgressRulesForFlow(flowPath.getFlow(), flowPath, transitVlan);
        return !rules.isEmpty() ? Optional.of(new CommandGroup(rules, FailureReaction.IGNORE))
                : Optional.empty();
    }

    private Collection<CommandGroup> createDeallocateResourcesGroups(String flowId,
                                                                     List<FlowPathWithEncapsulation> flowPaths) {
        List<CommandData> deallocationCommands = flowPaths.stream()
                .map(FlowPathWithEncapsulation::getFlowPath)
                .map(flowPath ->
                        new DeallocateFlowResourcesRequest(flowId,
                                flowPath.getCookie().getUnmaskedValue(),
                                flowPath.getPathId(),
                                flowPath.getFlow().getEncapsulationType()))
                .collect(Collectors.toList());
        return singletonList(new CommandGroup(deallocationCommands, FailureReaction.IGNORE));
    }

    private List<? extends CommandData> createFlowPathStatusRequests(Flow flow, FlowPathStatus status) {
        return asList(
                new UpdateFlowPathStatusRequest(flow.getFlowId(), flow.getForwardPath().getPathId(), status),
                new UpdateFlowPathStatusRequest(flow.getFlowId(), flow.getReversePath().getPathId(), status));
    }
}
