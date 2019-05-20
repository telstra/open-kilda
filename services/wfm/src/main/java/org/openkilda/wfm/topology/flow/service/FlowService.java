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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.collections4.ListUtils.union;

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
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flow.model.FlowPathWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation.FlowPathsWithEncapsulationBuilder;
import org.openkilda.wfm.topology.flow.model.ReroutedFlowPaths;
import org.openkilda.wfm.topology.flow.model.UpdatedFlowPathsWithEncapsulation;
import org.openkilda.wfm.topology.flow.validation.FlowValidationException;
import org.openkilda.wfm.topology.flow.validation.FlowValidator;
import org.openkilda.wfm.topology.flow.validation.SwitchValidationException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.SyncFailsafe;
import org.neo4j.driver.v1.exceptions.TransientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FlowService extends BaseFlowService {

    private static final int MAX_TRANSACTION_RETRY_COUNT = 3;
    private static final int RETRY_DELAY = 100;

    private final SwitchRepository switchRepository;
    private final FlowPathRepository flowPathRepository;
    private final IslRepository islRepository;
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

        FlowPathsWithEncapsulation result = null;
        try {
            result = (FlowPathsWithEncapsulation) getFailsafe().get(
                    () -> transactionManager.doInTransaction(() -> {
                        // TODO: the strategy is defined either per flow or system-wide.
                        PathComputer pathComputer = pathComputerFactory.getPathComputer();
                        PathPair pathPair = pathComputer.getPath(flow);

                        //TODO: hard-coded encapsulation will be removed in Flow H&S
                        flow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);

                        FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);

                        Instant timestamp = Instant.now();
                        // Build and store the flow with a path pair, use allocated resources for paths.
                        FlowPathPair flowPathPair =
                                buildFlowPathPair(flow, pathPair, flowResources, FlowPathStatus.IN_PROGRESS, timestamp);

                        Flow flowWithPaths = buildFlowWithPaths(flow, flowPathPair, FlowStatus.IN_PROGRESS, timestamp);
                        flowWithPaths.setTimeCreate(timestamp);

                        log.info("Creating the flow: {}", flowWithPaths);

                        flowPathRepository.lockInvolvedSwitches(flowPathPair.getForward(), flowPathPair.getReverse());

                        // Store the flow and both paths
                        flowRepository.createOrUpdate(flowWithPaths);

                        updateIslsForFlowPath(flowPathPair.getForward());
                        updateIslsForFlowPath(flowPathPair.getReverse());

                        FlowResources protectedFlowResources = null;
                        if (flowWithPaths.isAllocateProtectedPath()) {
                            protectedFlowResources = createProtectedPath(flowWithPaths, timestamp);
                        }

                        return buildFlowPathsWithEncapsulation(flowWithPaths, flowResources, protectedFlowResources);
                    }));
        } catch (FailsafeException e) {
            unwrapCrudFaisafeException(e);
        }

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(flow.getFlowId(),
                createInstallRulesGroups(result),
                createFlowPathStatusRequests(result, FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result, FlowPathStatus.INACTIVE));

        return buildFlowPair(result);
    }

    @VisibleForTesting
    FlowResources createProtectedPath(Flow flow, Instant timestamp)
            throws RecoverableException, UnroutableFlowException, FlowNotFoundException, ResourceAllocationException,
            FlowValidationException {
        if (flow.isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't setup protected path for one-switch flow",
                    ErrorType.PARAMETERS_INVALID);
        }

        flow.setGroupId(
                getOrCreateFlowGroupId(flow.getFlowId()));

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        PathPair protectedPathPair = pathComputer.getPath(flow);

        log.info("Creating the protected path {} for flow {}", protectedPathPair, flow);

        FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);
        FlowPathPair pathPair =
                buildFlowPathPair(flow, protectedPathPair, flowResources, FlowPathStatus.IN_PROGRESS, timestamp);

        checkProtectedPathsDontOverlapsWithPrimary(flow, pathPair);

        flow.setProtectedForwardPath(pathPair.getForward());
        flow.setProtectedReversePath(pathPair.getReverse());

        flowRepository.createOrUpdate(flow);

        updateIslsForFlowPath(flow.getProtectedForwardPath());
        updateIslsForFlowPath(flow.getProtectedReversePath());

        return flowResources;
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

        FlowPathsWithEncapsulation result = transactionManager.doInTransaction(() -> {
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

            return buildFlowPathsWithEncapsulation(flowWithPaths, flowResources, null);
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
                    .map(this::getFlowPathWithEncapsulation)
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
        result.forEach(flowPathToRemove -> commandGroups.addAll(
                createRemoveRulesGroups(flowPathToRemove, !flowPathToRemove.getFlowPath().isProtected())));
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
        FlowPathsWithEncapsulation currentFlow =
                getFlowPathPairWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        if (diverseFlowId == null) {
            updatingFlow.setGroupId(null);
        } else {
            checkDiverseFlow(updatingFlow, diverseFlowId);
            updatingFlow.setGroupId(getOrCreateFlowGroupId(diverseFlowId));
        }

        UpdatedFlowPathsWithEncapsulation result = updateFlowAndPaths(currentFlow, updatingFlow, sender);

        return buildFlowPair(result);
    }

    private UpdatedFlowPathsWithEncapsulation updateFlowAndPaths(FlowPathsWithEncapsulation currentFlow,
                                                                 Flow updatingFlow, FlowCommandSender sender)
            throws ResourceAllocationException, UnroutableFlowException, RecoverableException, FlowNotFoundException,
            FlowValidationException {

        UpdatedFlowPathsWithEncapsulation result = null;
        try {
            result = (UpdatedFlowPathsWithEncapsulation) getFailsafe().get(
                    () -> transactionManager.doInTransaction(() -> {
                        PathComputer pathComputer = pathComputerFactory.getPathComputer();
                        PathPair newPathPair = pathComputer.getPath(updatingFlow,
                                currentFlow.getFlow().getFlowPathIds());

                        log.info("Updating the flow with {} and path: {}", updatingFlow, newPathPair);

                        //TODO: hard-coded encapsulation will be removed in Flow H&S
                        updatingFlow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);

                        FlowResources flowResources = flowResourcesManager.allocateFlowResources(updatingFlow);

                        // Recreate the flow, use allocated resources for new paths.
                        Instant timestamp = Instant.now();
                        FlowPathPair newFlowPathPair =
                                buildFlowPathPair(updatingFlow, newPathPair, flowResources, FlowPathStatus.IN_PROGRESS,
                                        timestamp);

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
                        if (currentFlow.getProtectedForwardPath() != null) {
                            updateIslsForFlowPath(currentFlow.getProtectedForwardPath());
                        }
                        if (currentFlow.getProtectedReversePath() != null) {
                            updateIslsForFlowPath(currentFlow.getProtectedReversePath());
                        }

                        flowRepository.createOrUpdate(newFlowWithPaths);

                        updateIslsForFlowPath(newForwardPath);
                        updateIslsForFlowPath(newReversePath);

                        FlowResources protectedResources = null;
                        if (newFlowWithPaths.isAllocateProtectedPath()) {
                            protectedResources = createProtectedPath(newFlowWithPaths, timestamp);
                        }

                        return new UpdatedFlowPathsWithEncapsulation(currentFlow,
                                buildFlowPathsWithEncapsulation(newFlowWithPaths, flowResources, protectedResources));
                    }));
        } catch (FailsafeException e) {
            unwrapCrudFaisafeException(e);
        }

        // Assemble a command batch with InstallXXXRule, RemoveRule commands and a resource deallocation request.

        List<CommandGroup> commandGroups = new ArrayList<>();
        commandGroups.addAll(createInstallRulesGroups(result));
        commandGroups.addAll(createRemoveRulesGroups(result.getOldFlowPair()));
        commandGroups.addAll(createDeallocateResourcesGroups(result.getOldFlowPair()));

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(currentFlow.getFlow().getFlowId(),
                commandGroups,
                createFlowPathStatusRequests(result, FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result, FlowPathStatus.INACTIVE));

        return result;
    }

    /**
     * Reroutes a flow via a new path. Allocates new path and resources.
     * Stores the flow entities into DB, and invokes flow rules installation and deletion via the command sender.
     * <p/>
     * The rerouted flow has IN_PROGRESS status.
     *
     * @param flowId         the flow to be rerouted.
     * @param forceToReroute if true the flow will be recreated even there's no better path found.
     * @param pathIds        the set of path if to reroute.
     * @param sender         the command sender for flow rules installation and deletion.
     */
    public ReroutedFlowPaths rerouteFlow(String flowId, boolean forceToReroute, Set<PathId> pathIds,
                                         FlowCommandSender sender) throws RecoverableException, UnroutableFlowException,
            FlowNotFoundException, ResourceAllocationException {
        RerouteResult result = null;
        try {
            result = (RerouteResult) getFailsafe().get(() ->
                    transactionManager.doInTransaction(() -> doReroute(flowId, forceToReroute, pathIds)));
        } catch (FailsafeException e) {
            unwrapFaisafeException(e);
        }

        log.warn("Reroute finished. Paths to create: {}. Paths to remove: {}",
                result.getToCreateFlow(), result.getToRemoveFlow());

        // Assemble a command batch with InstallXXXRule, RemoveRule commands and a resource deallocation request.
        List<CommandGroup> commandGroups = new ArrayList<>();

        commandGroups.addAll(createInstallRulesGroups(result.getToCreateFlow()));
        commandGroups.addAll(createRemoveRulesGroups(result.getToRemoveFlow()));
        commandGroups.addAll(createDeallocateResourcesGroups(result.getToRemoveFlow()));

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(flowId,
                commandGroups,
                createFlowPathStatusRequests(result.getToCreateFlow(), FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result.getToCreateFlow(), FlowPathStatus.INACTIVE));

        return new ReroutedFlowPaths(result.getInitialFlow(), result.getUpdatedFlow());
    }

    private RerouteResult doReroute(String flowId, boolean forceToReroute, Set<PathId> pathIds)
            throws FlowNotFoundException, RecoverableException, UnroutableFlowException, ResourceAllocationException {
        FlowPathsWithEncapsulation currentFlow =
                getFlowPathPairWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        Flow flow = currentFlow.getFlow();
        Flow initialFlow = flow.toBuilder().build();
        FlowPathsWithEncapsulationBuilder toCreateBuilder = FlowPathsWithEncapsulation.builder();
        FlowPathsWithEncapsulationBuilder toRemoveBuilder = currentFlow.toBuilder().flow(initialFlow);
        Instant timestamp = Instant.now();

        boolean reroutePrimary = pathIds.isEmpty() || pathIds.contains(flow.getForwardPathId())
                || pathIds.contains(flow.getReversePathId());
        boolean rerouteProtected = flow.isAllocateProtectedPath() && (pathIds.isEmpty()
                || pathIds.contains(flow.getProtectedForwardPathId())
                || pathIds.contains(flow.getProtectedReversePathId()));

        // primary path
        if (reroutePrimary) {
            log.warn("Origin flow {} path: {}", flowId, flow.getForwardPath());

            PathComputer pathComputer = pathComputerFactory.getPathComputer();
            PathPair pathPair = pathComputer.getPath(flow, flow.getFlowPathIds());

            log.warn("Potential New Path for flow {} with LEFT path: {}, RIGHT path: {}",
                    flowId, pathPair.getForward(), pathPair.getReverse());

            if (flow.isAllocateProtectedPath()) {
                log.warn("Rerouting primary path for flow with protected path available, with flow id {}", flowId);
            }

            boolean isFoundNewPath = !isSamePath(pathPair.getForward(), currentFlow.getForwardPath())
                    || !isSamePath(pathPair.getReverse(), currentFlow.getReversePath());

            //no need to emit changes if path wasn't changed and flow is active.
            //force means to update flow even if path is not changed.
            if (!isFoundNewPath && flow.isActive() && !forceToReroute) {
                log.warn("Reroute {} is unsuccessful: can't find new path.", flowId);
                toRemoveBuilder.forwardPath(null).reversePath(null);
            } else {
                FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);

                // Recreate the flow, use allocated resources for new paths.
                FlowPathPair newFlowPathPair =
                        buildFlowPathPair(flow, pathPair, flowResources, FlowPathStatus.IN_PROGRESS, timestamp);

                final FlowPath currentForwardPath = flow.getForwardPath();
                final FlowPath currentReversePath = flow.getReversePath();

                FlowPath newForwardPath = newFlowPathPair.getForward();
                FlowPath newReversePath = newFlowPathPair.getReverse();

                flowPathRepository.lockInvolvedSwitches(currentForwardPath, currentReversePath,
                        newForwardPath, newReversePath);

                flowPathRepository.delete(currentForwardPath);
                flowPathRepository.delete(currentReversePath);
                updateIslsForFlowPath(currentForwardPath);
                updateIslsForFlowPath(currentReversePath);

                flowPathRepository.createOrUpdate(newForwardPath);
                flowPathRepository.createOrUpdate(newReversePath);
                updateIslsForFlowPath(newForwardPath);
                updateIslsForFlowPath(newReversePath);

                flow.setStatus(FlowStatus.IN_PROGRESS);
                flow.setTimeModify(timestamp);
                flow.setForwardPath(newFlowPathPair.getForward());
                flow.setReversePath(newFlowPathPair.getReverse());
                flowRepository.createOrUpdate(flow);

                toCreateBuilder.forwardPath(newForwardPath)
                        .reversePath(newReversePath)
                        .forwardEncapsulation(flowResources.getForward().getEncapsulationResources())
                        .reverseEncapsulation(flowResources.getReverse().getEncapsulationResources());
            }
        } else {
            toRemoveBuilder.forwardPath(null).reversePath(null);
        }

        // protected path
        if (rerouteProtected) {
            log.warn("Origin flow {} protected path: {}", flowId, flow.getProtectedForwardPath());

            final FlowPath currentForwardPath = flow.getProtectedForwardPath();
            final FlowPath currentReversePath = flow.getProtectedReversePath();

            PathComputer pathComputer = pathComputerFactory.getPathComputer();
            PathPair pathPair = pathComputer.getPath(flow,
                    Arrays.asList(flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()));

            log.warn("Potential New Path for flow {} with LEFT path: {}, RIGHT path: {}",
                    flowId, pathPair.getForward(), pathPair.getReverse());

            boolean isFoundNewPath = !isSamePath(pathPair.getForward(), currentFlow.getProtectedForwardPath())
                    || !isSamePath(pathPair.getReverse(), currentFlow.getProtectedReversePath());

            if (!isFoundNewPath && flow.isActive() && !forceToReroute) {
                log.warn("Reroute {} is unsuccessful: can't find new protected path.", flowId);

                toRemoveBuilder.protectedForwardPath(null).protectedReversePath(null);
            } else {
                // Create new flow paths, without resources
                FlowPathPair newFlowPathPair =
                        buildFlowPathPair(flow, pathPair, FlowPathStatus.IN_PROGRESS, timestamp);

                flow.setTimeModify(timestamp);

                if (isProtectedPathsDontOverlapsWithPrimary(flow, newFlowPathPair)) {

                    // allocate and setup resources
                    FlowResources flowResources = flowResourcesManager.allocateFlowResources(flow);
                    setResourcesInPaths(newFlowPathPair, flowResources);

                    FlowPath newForwardPath = newFlowPathPair.getForward();
                    FlowPath newReversePath = newFlowPathPair.getReverse();

                    flowPathRepository.lockInvolvedSwitches(currentForwardPath, currentReversePath,
                            newForwardPath, newReversePath);

                    flowPathRepository.delete(currentForwardPath);
                    flowPathRepository.delete(currentReversePath);
                    updateIslsForFlowPath(currentForwardPath);
                    updateIslsForFlowPath(currentReversePath);

                    flowPathRepository.createOrUpdate(newForwardPath);
                    flowPathRepository.createOrUpdate(newReversePath);
                    updateIslsForFlowPath(newForwardPath);
                    updateIslsForFlowPath(newReversePath);

                    flow.setStatus(FlowStatus.IN_PROGRESS);
                    flow.setProtectedForwardPath(newFlowPathPair.getForward());
                    flow.setProtectedReversePath(newFlowPathPair.getReverse());
                    flowRepository.createOrUpdate(flow);

                    toCreateBuilder.protectedForwardPath(newForwardPath)
                            .protectedReversePath(newReversePath)
                            .protectedForwardEncapsulation(flowResources.getForward().getEncapsulationResources())
                            .protectedReverseEncapsulation(flowResources.getReverse().getEncapsulationResources());
                } else {
                    log.warn("Reroute {} is unsuccessful: can't find non overlapping new protected path.", flowId);

                    flow.setStatus(FlowStatus.DOWN);
                    currentForwardPath.setStatus(FlowPathStatus.INACTIVE);
                    currentReversePath.setStatus(FlowPathStatus.INACTIVE);

                    flowPathRepository.createOrUpdate(currentForwardPath);
                    flowPathRepository.createOrUpdate(currentReversePath);
                    flowRepository.createOrUpdate(flow);

                    toRemoveBuilder.protectedForwardPath(null).protectedReversePath(null);
                }
            }
        } else {
            toRemoveBuilder.protectedForwardPath(null).protectedReversePath(null);
        }

        FlowPathsWithEncapsulation updatedFlow =
                getFlowPathPairWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

        return new RerouteResult(currentFlow.toBuilder().flow(initialFlow).build(), updatedFlow,
                toCreateBuilder.flow(flow).build(), toRemoveBuilder.build());
    }

    /**
     * Swaps primary path for the flow with protected paths.
     *
     * @param flowId    the flow id to be updated.
     * @param pathId the primary path id to move from.
     * @param sender    the command sender for flow rules installation and deletion.
     * @return the updated flow.
     */
    public UnidirectionalFlow pathSwap(String flowId, PathId pathId, FlowCommandSender sender)
            throws FlowNotFoundException, FlowValidationException {
        FlowPathsWithEncapsulation result = transactionManager.doInTransaction(() -> {
            FlowPathsWithEncapsulation currentFlow =
                    getFlowPathPairWithEncapsulation(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

            Flow flow = currentFlow.getFlow();

            if (pathId != null && !(pathId.equals(flow.getForwardPathId()) || pathId.equals(flow.getReversePathId()))) {
                throw new FlowValidationException(format("Requested pathId %s doesn't belongs to primary "
                        + "flow path for flow with id %s", pathId, flowId),
                        ErrorType.PARAMETERS_INVALID);
            }

            if (!flow.isAllocateProtectedPath()) {
                throw new FlowValidationException(format("Flow %s doesn't have protected path", flowId),
                        ErrorType.PARAMETERS_INVALID);
            }
            if (FlowPathStatus.ACTIVE != flow.getProtectedForwardPath().getStatus()
                    || FlowPathStatus.ACTIVE != flow.getProtectedReversePath().getStatus()) {
                throw new FlowValidationException(
                        format("Protected flow path %s is not in ACTIVE state", flowId), ErrorType.PARAMETERS_INVALID);
            }

            log.info("Swapping path with id {} in flow {}", pathId, flow);

            flow.setStatus(FlowStatus.IN_PROGRESS);
            flow.setTimeModify(Instant.now());

            FlowPath oldPrimaryForward = flow.getForwardPath();
            FlowPath oldPrimaryReverse = flow.getReversePath();
            flow.setForwardPath(flow.getProtectedForwardPath());
            flow.setReversePath(flow.getProtectedReversePath());
            flow.setProtectedForwardPath(oldPrimaryForward);
            flow.setProtectedReversePath(oldPrimaryReverse);

            flowRepository.createOrUpdate(flow);

            return currentFlow;
        });

        // Assemble a command batch with InstallXXXRule, RemoveRule commands and a resource deallocation request.

        List<CommandGroup> commandGroups = createSwapIngressCommand(result);

        // To avoid race condition in DB updates, we should send commands only after DB transaction commit.
        sender.sendFlowCommands(result.getFlow().getFlowId(),
                commandGroups,
                createFlowPathStatusRequests(result, FlowPathStatus.ACTIVE),
                createFlowPathStatusRequests(result, FlowPathStatus.INACTIVE));

        return buildForwardUnidirectionalFlow(result.getProtectedForward());
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
     *
     * @param flowId a flow ID used to locate the flow(s).
     * @param status the status to set.
     */
    public void updateFlowStatus(String flowId, FlowStatus status, Set<PathId> pathIdSet) {
        transactionManager.doInTransaction(() -> {
            flowRepository.findById(flowId)
                    .ifPresent(flow -> {
                        flow.setStatus(status);
                        flowRepository.createOrUpdate(flow);
                    });

            Stream<FlowPath> pathsStream = flowPathRepository.findByFlowId(flowId).stream();
            if (!pathIdSet.isEmpty()) {
                pathsStream = pathsStream.filter(path -> pathIdSet.contains(path.getPathId()));
            }
            pathsStream.forEach(path -> {
                path.setStatusLikeFlow(status);
                flowPathRepository.createOrUpdate(path);
            });
        });
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
            FlowPath flowPath = flowPathRepository.findById(pathId)
                    .orElseThrow(() -> new FlowNotFoundException(flowId, format("Flow path %s not found.", pathId)));

            if (flowPathStatus != flowPath.getStatus()) {
                flowPath.setStatus(flowPathStatus);
                flowPathRepository.createOrUpdate(flowPath);
            }
        });

        transactionManager.doInTransaction(() -> {
            Flow flow = flowRepository.findById(flowId).orElseThrow(() -> new FlowNotFoundException(flowId));

            FlowPathStatus prioritizedPathsStatus = Stream.of(flow.getForwardPath(),
                    flow.getReversePath(), flow.getProtectedForwardPath(), flow.getProtectedReversePath())
                    .filter(Objects::nonNull)
                    .map(FlowPath::getStatus)
                    .max(FlowPathStatus::compareTo)
                    .orElse(null);

            // Calculate the combined flow status.
            FlowStatus flowStatus;
            switch (prioritizedPathsStatus) {
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
                    throw new IllegalArgumentException(
                            format("Unsupported flow path status %s", prioritizedPathsStatus));
            }

            if (flowStatus != flow.getStatus()) {
                log.debug("Set flow {} status to {}", flowId, flowStatus);

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
        FlowPathPair flowPathPair = buildFlowPathPair(flow, pathPair, pathStatus, timeCreate);
        setResourcesInPaths(flowPathPair, flowResources);
        return flowPathPair;
    }

    private FlowPathPair buildFlowPathPair(Flow flow, PathPair pathPair, FlowPathStatus pathStatus,
                                           Instant timeCreate) {
        FlowPath forwardPath = buildFlowPath(flow, pathPair.getForward(), pathStatus, timeCreate);
        FlowPath reversePath = buildFlowPath(flow, pathPair.getReverse(), pathStatus, timeCreate);

        return FlowPathPair.builder()
                .forward(forwardPath)
                .reverse(reversePath)
                .build();
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
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
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
        log.info("Getting flow group for flow with id {}", flowId);
        return flowRepository.getOrCreateFlowGroupId(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));
    }

    private void checkDiverseFlow(Flow targetFlow, String flowId) throws FlowNotFoundException,
            FlowValidationException {
        if (targetFlow.isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't add one-switch flow into diverse group",
                    ErrorType.PARAMETERS_INVALID);
        }

        Flow diverseFlow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        if (diverseFlow.isOneSwitchFlow()) {
            throw new FlowValidationException("Couldn't create diverse group with one-switch flow",
                    ErrorType.PARAMETERS_INVALID);
        }
    }

    private void checkProtectedPathsDontOverlapsWithPrimary(Flow flow, FlowPathPair newProtectedPathPair)
            throws UnroutableFlowException {
        if (!isProtectedPathsDontOverlapsWithPrimary(flow, newProtectedPathPair)) {
            log.warn("Couldn't find non overlapping protected path. Result flow state: {}", flow);
            throw new UnroutableFlowException("Couldn't find non overlapping protected path",
                    flow.getFlowId());
        }
    }

    private boolean isProtectedPathsDontOverlapsWithPrimary(Flow flow, FlowPathPair newProtectedPathPair) {
        List<PathSegment> segments = union(newProtectedPathPair.getForward().getSegments(),
                newProtectedPathPair.getReverse().getSegments());

        List<PathSegment> primaryFlowSegments = Stream.of(flow.getForwardPath(), flow.getReversePath())
                .map(FlowPath::getSegments)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        return !IntersectionComputer.isProtectedPathOverlaps(primaryFlowSegments, segments);
    }

    private FlowPair buildFlowPair(FlowPathsWithEncapsulation flowPath) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan forwardTransitVlan = mapTransitVlan(flowPath.getForwardEncapsulation());
        TransitVlan reverseTransitVlan = mapTransitVlan(flowPath.getReverseEncapsulation());

        return new FlowPair(flowPath.getFlow(), forwardTransitVlan, reverseTransitVlan);
    }

    private UnidirectionalFlow buildForwardUnidirectionalFlow(FlowPathWithEncapsulation flowPath) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan = mapTransitVlan(flowPath.getEncapsulation());

        return new UnidirectionalFlow(flowPath.getFlowPath(), transitVlan, true);
    }

    private FlowPathsWithEncapsulation buildFlowPathsWithEncapsulation(Flow flow, FlowResources primaryResources,
                                                                       FlowResources protectedResources) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        FlowPathsWithEncapsulationBuilder builder =
                FlowPathsWithEncapsulation.builder()
                        .flow(flow)
                        .forwardPath(flow.getForwardPath())
                        .reversePath(flow.getReversePath())
                        .forwardEncapsulation(primaryResources.getForward().getEncapsulationResources())
                        .reverseEncapsulation(primaryResources.getReverse().getEncapsulationResources())
                        .protectedForwardPath(flow.getProtectedForwardPath())
                        .protectedReversePath(flow.getProtectedReversePath());

        if (protectedResources != null) {
            builder.protectedForwardEncapsulation(protectedResources.getForward().getEncapsulationResources())
                    .protectedReverseEncapsulation(protectedResources.getReverse().getEncapsulationResources());
        }
        return builder.build();
    }

    private TransitVlan mapTransitVlan(EncapsulationResources resources) {
        return Optional.ofNullable(resources)
                .filter(r -> r instanceof TransitVlanEncapsulation)
                .map(TransitVlanEncapsulation.class::cast)
                .map(TransitVlanEncapsulation::getTransitVlan)
                .orElse(null);
    }

    private FlowPathWithEncapsulation getFlowPathWithEncapsulation(FlowPath flowPath) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan = findTransitVlan(flowPath.getPathId());

        return FlowPathWithEncapsulation.builder()
                .flowPath(flowPath)
                .encapsulation(TransitVlanEncapsulation.builder().transitVlan(transitVlan).build())
                .build();
    }

    private List<CommandGroup> createSwapIngressCommand(FlowPathsWithEncapsulation pathsToSwap) {
        Preconditions.checkArgument(pathsToSwap.getFlow().isAllocateProtectedPath());

        List<CommandGroup> commandGroups = new ArrayList<>();

        // new primary path
        commandGroups.add(createInstallIngressRules(pathsToSwap.getProtectedForwardPath(),
                mapTransitVlan(pathsToSwap.getProtectedForwardEncapsulation())));
        commandGroups.add(createInstallIngressRules(pathsToSwap.getProtectedReversePath(),
                mapTransitVlan(pathsToSwap.getProtectedReverseEncapsulation())));

        if (pathsToSwap.getForwardPath().getMeterId() != null) {
            commandGroups.add(createRemoveMeter(pathsToSwap.getForwardPath()));
        }
        if (pathsToSwap.getReversePath().getMeterId() != null) {
            commandGroups.add(createRemoveMeter(pathsToSwap.getReversePath()));
        }

        return commandGroups;
    }

    private List<CommandGroup> createInstallRulesGroups(FlowPathsWithEncapsulation pathsToInstall) {
        List<CommandGroup> commandGroups = new ArrayList<>();

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan forwardTransitVlan = mapTransitVlan(pathsToInstall.getForwardEncapsulation());
        TransitVlan reverseTransitVlan = mapTransitVlan(pathsToInstall.getReverseEncapsulation());

        if (pathsToInstall.getForwardPath() != null) {
            createInstallTransitAndEgressRules(pathsToInstall.getForwardPath(),
                    forwardTransitVlan).ifPresent(commandGroups::add);
        }
        if (pathsToInstall.getReversePath() != null) {
            createInstallTransitAndEgressRules(pathsToInstall.getReversePath(),
                    reverseTransitVlan).ifPresent(commandGroups::add);
        }

        if (pathsToInstall.getProtectedForwardPath() != null) {
            createInstallTransitAndEgressRules(pathsToInstall.getProtectedForwardPath(),
                    mapTransitVlan(pathsToInstall.getProtectedForwardEncapsulation())).ifPresent(commandGroups::add);
        }
        if (pathsToInstall.getProtectedReversePath() != null) {
            createInstallTransitAndEgressRules(pathsToInstall.getProtectedReversePath(),
                    mapTransitVlan(pathsToInstall.getProtectedReverseEncapsulation())).ifPresent(commandGroups::add);
        }

        // The ingress rule must be installed after the egress and transit ones.
        if (pathsToInstall.getForwardPath() != null) {
            commandGroups.add(createInstallIngressRules(pathsToInstall.getForwardPath(), forwardTransitVlan));
        }
        if (pathsToInstall.getReversePath() != null) {
            commandGroups.add(createInstallIngressRules(pathsToInstall.getReversePath(), reverseTransitVlan));
        }

        return commandGroups;
    }

    private Optional<CommandGroup> createInstallTransitAndEgressRules(FlowPath flowPath, TransitVlan transitVlan) {
        //TODO: hard-coded encapsulation will be removed in Flow H&S
        List<InstallTransitFlow> rules = flowCommandFactory.createInstallTransitAndEgressRulesForFlow(flowPath,
                transitVlan);
        return !rules.isEmpty() ? Optional.of(new CommandGroup(rules, FailureReaction.ABORT_BATCH))
                : Optional.empty();
    }

    private CommandGroup createInstallIngressRules(FlowPath flowPath, TransitVlan transitVlan) {
        return new CommandGroup(singletonList(
                flowCommandFactory.createInstallIngressRulesForFlow(flowPath, transitVlan)),
                FailureReaction.ABORT_BATCH);
    }

    private CommandGroup createRemoveMeter(FlowPath flowPath) {
        return new CommandGroup(singletonList(flowCommandFactory.createDeleteMeter(flowPath)),
                FailureReaction.ABORT_BATCH);
    }

    private List<CommandGroup> createRemoveRulesGroups(FlowPathsWithEncapsulation pathsToRemove) {
        List<CommandGroup> commandGroups = new ArrayList<>();

        if (pathsToRemove.getForwardPath() != null) {
            commandGroups.addAll(createRemoveRulesGroups(pathsToRemove.getForward(), true));
        }
        if (pathsToRemove.getReversePath() != null) {
            commandGroups.addAll(createRemoveRulesGroups(pathsToRemove.getReverse(), true));
        }

        if (pathsToRemove.getProtectedForwardPath() != null) {
            commandGroups.addAll(createRemoveRulesGroups(pathsToRemove.getProtectedForward(), false));
        }
        if (pathsToRemove.getProtectedReversePath() != null) {
            commandGroups.addAll(createRemoveRulesGroups(pathsToRemove.getProtectedReverse(), false));
        }

        return commandGroups;
    }

    private List<CommandGroup> createRemoveRulesGroups(FlowPathWithEncapsulation pathToRemove, boolean isPrimary) {
        List<CommandGroup> commandGroups = new ArrayList<>();
        FlowPath flowPath = pathToRemove.getFlowPath();

        //TODO: hard-coded encapsulation will be removed in Flow H&S
        TransitVlan transitVlan = mapTransitVlan(pathToRemove.getEncapsulation());

        if (isPrimary) {
            commandGroups.add(createRemoveIngressRules(flowPath));
        }
        createRemoveTransitAndEgressRules(flowPath, transitVlan)
                .ifPresent(commandGroups::add);

        return commandGroups;
    }

    private CommandGroup createRemoveIngressRules(FlowPath flowPath) {
        return new CommandGroup(singletonList(
                flowCommandFactory.createRemoveIngressRulesForFlow(flowPath)), FailureReaction.IGNORE);
    }

    private Optional<CommandGroup> createRemoveTransitAndEgressRules(FlowPath flowPath, TransitVlan transitVlan) {
        List<RemoveFlow> rules =
                flowCommandFactory.createRemoveTransitAndEgressRulesForFlow(flowPath, transitVlan);
        return !rules.isEmpty() ? Optional.of(new CommandGroup(rules, FailureReaction.IGNORE))
                : Optional.empty();
    }

    private List<CommandGroup> createDeallocateResourcesGroups(FlowPathsWithEncapsulation pathsToDeallocate) {
        List<FlowPathWithEncapsulation> flowPaths = new ArrayList<>();

        if (pathsToDeallocate.getForwardPath() != null) {
            flowPaths.add(pathsToDeallocate.getForward());
        }
        if (pathsToDeallocate.getReversePath() != null) {
            flowPaths.add(pathsToDeallocate.getReverse());
        }

        if (pathsToDeallocate.getProtectedForwardPath() != null) {
            flowPaths.add(pathsToDeallocate.getProtectedForward());
        }
        if (pathsToDeallocate.getProtectedReversePath() != null) {
            flowPaths.add(pathsToDeallocate.getProtectedReverse());
        }

        return createDeallocateResourcesGroups(pathsToDeallocate.getFlow().getFlowId(), flowPaths);
    }

    private List<CommandGroup> createDeallocateResourcesGroups(String flowId,
                                                               List<FlowPathWithEncapsulation> flowPaths) {
        List<CommandData> deallocationCommands = flowPaths.stream()
                .map(flowPath ->
                        new DeallocateFlowResourcesRequest(flowId,
                                flowPath.getFlowPath().getCookie().getUnmaskedValue(),
                                flowPath.getFlowPath().getPathId(),
                                flowPath.getFlowPath().getFlow().getEncapsulationType()))
                .collect(Collectors.toList());
        return singletonList(new CommandGroup(deallocationCommands, FailureReaction.IGNORE));
    }

    private List<UpdateFlowPathStatusRequest> createFlowPathStatusRequests(FlowPathsWithEncapsulation flowPaths,
                                                                           FlowPathStatus status) {
        String flowId = flowPaths.getFlow().getFlowId();
        List<UpdateFlowPathStatusRequest> commands = new ArrayList<>();

        if (flowPaths.getForwardPath() != null) {
            commands.add(new UpdateFlowPathStatusRequest(flowId, flowPaths.getForwardPath().getPathId(), status));
        }
        if (flowPaths.getReversePath() != null) {
            commands.add(new UpdateFlowPathStatusRequest(flowId, flowPaths.getReversePath().getPathId(), status));
        }

        if (flowPaths.getProtectedForwardPath() != null) {
            commands.add(new UpdateFlowPathStatusRequest(
                    flowId, flowPaths.getProtectedForwardPath().getPathId(), status));
        }
        if (flowPaths.getProtectedReversePath() != null) {
            commands.add(new UpdateFlowPathStatusRequest(
                    flowId, flowPaths.getProtectedReversePath().getPathId(), status));
        }

        return commands;
    }

    private SyncFailsafe getFailsafe() {
        return Failsafe.with(new RetryPolicy()
                .retryOn(RecoverableException.class)
                .retryOn(ResourceAllocationException.class)
                .retryOn(TransientException.class)
                .withDelay(RETRY_DELAY, TimeUnit.MILLISECONDS)
                .withMaxRetries(MAX_TRANSACTION_RETRY_COUNT))
                .onRetry(e -> log.warn("Retrying transaction finished with exception", e))
                .onRetriesExceeded(e -> log.warn("TX retry attempts exceed with error", e));
    }

    private void unwrapFaisafeException(FailsafeException e) throws UnroutableFlowException,
            ResourceAllocationException, FlowNotFoundException {
        unwrapBaseFaisafeException(e);
        throw e;
    }

    private void unwrapCrudFaisafeException(FailsafeException e) throws UnroutableFlowException,
            ResourceAllocationException, FlowNotFoundException, FlowValidationException {
        unwrapBaseFaisafeException(e);

        Throwable cause = e.getCause();
        if (cause instanceof FlowValidationException) {
            throw (FlowValidationException) cause;
        }
        throw e;
    }

    private void unwrapBaseFaisafeException(FailsafeException e) throws UnroutableFlowException,
            ResourceAllocationException, FlowNotFoundException {
        Throwable cause = e.getCause();
        if (cause instanceof UnroutableFlowException) {
            throw (UnroutableFlowException) cause;
        }
        if (cause instanceof ResourceAllocationException) {
            throw (ResourceAllocationException) cause;
        }
        if (cause instanceof FlowNotFoundException) {
            throw (FlowNotFoundException) cause;
        }
    }

    @Value
    private class RerouteResult {
        FlowPathsWithEncapsulation initialFlow;
        FlowPathsWithEncapsulation updatedFlow;
        FlowPathsWithEncapsulation toCreateFlow;
        FlowPathsWithEncapsulation toRemoveFlow;
    }
}
