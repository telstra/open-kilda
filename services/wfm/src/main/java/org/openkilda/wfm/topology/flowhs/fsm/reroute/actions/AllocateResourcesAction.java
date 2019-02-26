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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.service.FlowPathBuilder;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import org.neo4j.driver.v1.exceptions.TransientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class AllocateResourcesAction extends
        NbTrackableAction<FlowRerouteFsm, FlowRerouteFsm.State, FlowRerouteFsm.Event, FlowRerouteContext> {
    private static final int MAX_TRANSACTION_RETRY_COUNT = 3;

    private final TransactionManager transactionManager;
    private final FlowRepository flowRepository;
    private final FlowPathRepository flowPathRepository;
    private final IslRepository islRepository;

    private final PathComputer pathComputer;
    private final FlowResourcesManager resourcesManager;
    private final FlowPathBuilder flowPathBuilder;

    public AllocateResourcesAction(PersistenceManager persistenceManager, PathComputer pathComputer,
                                   FlowResourcesManager resourcesManager) {
        this.transactionManager = persistenceManager.getTransactionManager();
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();

        this.pathComputer = pathComputer;
        this.resourcesManager = resourcesManager;

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.flowPathBuilder = new FlowPathBuilder(switchRepository);
    }

    @Override
    protected Optional<Message> perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                                        FlowRerouteFsm.Event event, FlowRerouteContext context,
                                        FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        try {
            ResourceAllocationResult result = createNewPathsInTransaction(flowId, stateMachine.isReroutePrimary(),
                    stateMachine.isRerouteProtected(), stateMachine.isRecreateIfSamePath());

            if (stateMachine.isRerouteProtected() && result.protectedPaths == null
                    && result.overlappingProtectedPathFound) {
                log.debug("Reroute of the protected path for {} flow is unsuccessful: "
                        + "can't find non-overlapping protected path(s).", flowId);

                // Update the status here as no reroute is going to be performed for the protected.
                FlowPath protectedForwardPath = result.flow.getProtectedForwardPath();
                protectedForwardPath.setStatus(FlowPathStatus.INACTIVE);
                flowPathRepository.createOrUpdate(protectedForwardPath);

                FlowPath protectedReversePath = result.flow.getProtectedReversePath();
                protectedReversePath.setStatus(FlowPathStatus.INACTIVE);
                flowPathRepository.createOrUpdate(protectedReversePath);
            }

            if (result.primaryPaths == null && result.protectedPaths == null) {
                log.debug("Reroute {} is unsuccessful: can't find new path(s).", flowId);

                if (stateMachine.isRerouteProtected() && result.overlappingProtectedPathFound) {
                    stateMachine.fire(Event.NO_PATH_FOUND);
                } else {
                    stateMachine.fire(Event.REROUTE_IS_SKIPPED);
                }

                return Optional.of(buildRerouteResponseMessage(result.flow, null,
                        stateMachine.getCommandContext()));
            }

            log.debug("New paths have been created: {}", result);

            stateMachine.setNewResources(result.allocatedResources);
            if (result.primaryPaths != null) {
                stateMachine.setNewPrimaryForwardPath(result.primaryPaths.getForward().getPathId());
                stateMachine.setNewPrimaryReversePath(result.primaryPaths.getReverse().getPathId());
                FlowPathPair oldPaths = FlowPathPair.builder()
                        .forward(result.flow.getForwardPath())
                        .reverse(result.flow.getReversePath())
                        .build();
                saveHistory(result.flow, oldPaths, result.primaryPaths, stateMachine);
            }

            if (result.protectedPaths != null) {
                stateMachine.setNewProtectedForwardPath(result.protectedPaths.getForward().getPathId());
                stateMachine.setNewProtectedReversePath(result.protectedPaths.getReverse().getPathId());
                FlowPathPair oldPaths = FlowPathPair.builder()
                        .forward(result.flow.getProtectedForwardPath())
                        .reverse(result.flow.getProtectedReversePath())
                        .build();

                saveHistory(result.flow, oldPaths, result.protectedPaths, stateMachine);
            }

            return Optional.of(buildRerouteResponseMessage(result.flow, result.primaryPaths,
                    stateMachine.getCommandContext()));

        } catch (UnroutableFlowException | RecoverableException e) {
            String errorDescription = format("Not enough bandwidth found or path not found for flow %s: %s",
                    flowId, e.getMessage());
            log.debug(getGenericErrorMessage() + ": " + errorDescription);

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, errorDescription);

            stateMachine.fire(Event.NO_PATH_FOUND);

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.NOT_FOUND,
                    getGenericErrorMessage(), errorDescription));
        } catch (ResourceAllocationException e) {
            String errorDescription = format("Failed to allocate resources for flow %s: %s",
                    flowId, e.getMessage());
            log.warn(getGenericErrorMessage() + ": " + errorDescription);

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, errorDescription);

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.INTERNAL_ERROR,
                    getGenericErrorMessage(), errorDescription));
        } catch (Exception e) {
            String errorDescription = format("Failed to create flow paths for flow %s: %s",
                    flowId, e.getMessage());
            log.error(getGenericErrorMessage() + ": " + errorDescription, e);

            saveHistory(stateMachine, stateMachine.getCarrier(), flowId, errorDescription);

            stateMachine.fireError();

            return Optional.of(buildErrorMessage(stateMachine, ErrorType.INTERNAL_ERROR,
                    getGenericErrorMessage(), errorDescription));
        }
    }

    private ResourceAllocationResult createNewPathsInTransaction(String flowId, boolean createPrimaryPath,
                                                                 boolean createProtectedPath, boolean createIfSamePath)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        try {
            return Failsafe.with(new RetryPolicy()
                    .retryOn(UnroutableFlowException.class)
                    .retryOn(RecoverableException.class)
                    .retryOn(ResourceAllocationException.class)
                    .retryOn(TransientException.class)
                    .withMaxRetries(MAX_TRANSACTION_RETRY_COUNT))
                    .onRetry(e -> log.warn("Retrying transaction finished with exception", e))
                    .onRetriesExceeded(e -> log.warn("TX retry attempts exceed with error", e))
                    .get(() -> transactionManager.doInTransaction(() ->
                            createNewPaths(flowId, createPrimaryPath, createProtectedPath, createIfSamePath)));
        } catch (FailsafeException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RecoverableException) {
                throw (RecoverableException) cause;
            } else if (cause instanceof UnroutableFlowException) {
                throw (UnroutableFlowException) cause;
            } else if (cause instanceof ResourceAllocationException) {
                throw (ResourceAllocationException) cause;
            }
            throw ex;
        }
    }

    private ResourceAllocationResult createNewPaths(String flowId, boolean createPrimaryPath,
                                                    boolean createProtectedPath, boolean createIfSamePath)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {

        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        "Could not create new paths", format("Flow %s not found", flowId)));

        ResourceAllocationResult result = new ResourceAllocationResult();
        result.flow = flow;

        if (createPrimaryPath) {
            log.debug("Finding a new primary path for flow {}", flowId);
            PathPair potentialPath = findPath(flow);
            boolean newPathFound = isNotSamePath(potentialPath, flow.getForwardPath(),
                    flow.getReversePath());
            if (newPathFound || createIfSamePath) {
                if (!newPathFound) {
                    log.debug("Found the same primary path for flow {}. Proceed with recreating it.", flowId);
                }
                log.debug("Allocating resources for a new primary path of flow {}", flowId);
                FlowResources flowResources = resourcesManager.allocateFlowResources(flow);
                log.debug("Resources have been allocated: {}", flowResources);
                result.allocatedResources.add(flowResources);
                result.primaryPaths = createFlowPathPair(flow, potentialPath, flowResources);
            } else {
                log.debug("Found the same primary path for flow {}. Skip creating of it.", flowId);
            }
        }

        if (createProtectedPath) {
            log.debug("Finding a new protected path for flow {}", flowId);
            PathPair potentialPath = findPath(flow);
            FlowPathPair pathToAvoid = Optional.ofNullable(result.primaryPaths)
                    .orElseGet(() -> FlowPathPair.builder()
                            .forward(flow.getForwardPath())
                            .reverse(flow.getReversePath())
                            .build());
            result.overlappingProtectedPathFound =
                    flowPathBuilder.arePathsOverlapped(potentialPath.getForward(), pathToAvoid.getForward())
                            || flowPathBuilder.arePathsOverlapped(potentialPath.getReverse(), pathToAvoid.getReverse());
            if (result.overlappingProtectedPathFound) {
                log.warn("Can't find non overlapping new protected path for flow {}. Skip creating it.",
                        flow.getFlowId());
            } else {
                boolean newPathFound = isNotSamePath(potentialPath, flow.getProtectedForwardPath(),
                        flow.getProtectedReversePath());
                if (newPathFound || createIfSamePath) {
                    if (!newPathFound) {
                        log.debug("Found the same protected path for flow {}. Proceed with recreating it.", flowId);
                    }
                    log.debug("Allocating resources for a new protected path of flow {}", flowId);
                    FlowResources flowResources = resourcesManager.allocateFlowResources(flow);
                    log.debug("Resources have been allocated: {}", flowResources);
                    result.allocatedResources.add(flowResources);
                    result.protectedPaths = createFlowPathPair(flow, potentialPath, flowResources);
                } else {
                    log.debug("Found the same protected path for flow {}. Skip creating of it.", flowId);
                }
            }
        }

        if (result.primaryPaths != null || result.protectedPaths != null) {
            flow.setStatus(FlowStatus.IN_PROGRESS);
            flowRepository.createOrUpdate(flow);
        }

        return result;
    }

    private PathPair findPath(Flow flow) throws RecoverableException, UnroutableFlowException {
        return pathComputer.getPath(flow,
                Stream.of(flow.getForwardPathId(), flow.getReversePathId(),
                        flow.getProtectedForwardPathId(), flow.getProtectedReversePathId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    private boolean isNotSamePath(PathPair pathPair, FlowPath forwardPath, FlowPath reversePath) {
        return !flowPathBuilder.isSamePath(pathPair.getForward(), forwardPath)
                || !flowPathBuilder.isSamePath(pathPair.getReverse(), reversePath);
    }

    private FlowPathPair createFlowPathPair(Flow flow, PathPair pathPair, FlowResources flowResources) {
        long cookie = flowResources.getUnmaskedCookie();
        FlowPath newForwardPath = flowPathBuilder.buildFlowPath(flow, flowResources.getForward(),
                pathPair.getForward(), Cookie.buildForwardCookie(cookie));
        FlowPath newReversePath = flowPathBuilder.buildFlowPath(flow, flowResources.getReverse(),
                pathPair.getReverse(), Cookie.buildReverseCookie(cookie));
        FlowPathPair newFlowPaths = FlowPathPair.builder().forward(newForwardPath).reverse(newReversePath).build();

        log.debug("Persisting the paths {}", newFlowPaths);

        flowPathRepository.lockInvolvedSwitches(newForwardPath, newReversePath);

        newForwardPath.setStatus(FlowPathStatus.IN_PROGRESS);
        flowPathRepository.createOrUpdate(newForwardPath);
        newReversePath.setStatus(FlowPathStatus.IN_PROGRESS);
        flowPathRepository.createOrUpdate(newReversePath);

        updateIslsForFlowPath(newForwardPath);
        updateIslsForFlowPath(newReversePath);

        return newFlowPaths;
    }

    private void updateIslsForFlowPath(FlowPath path) {
        path.getSegments().forEach(pathSegment -> {
            log.debug("Updating ISL for the path segment: {}", pathSegment);

            updateAvailableBandwidth(pathSegment.getSrcSwitch().getSwitchId(), pathSegment.getSrcPort(),
                    pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort());
        });
    }

    private void updateAvailableBandwidth(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort) {
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(srcSwitch, srcPort,
                dstSwitch, dstPort);

        Optional<Isl> matchedIsl = islRepository.findByEndpoints(srcSwitch, srcPort, dstSwitch, dstPort);
        matchedIsl.ifPresent(isl -> {
            isl.setAvailableBandwidth(isl.getMaxBandwidth() - usedBandwidth);
            islRepository.createOrUpdate(isl);
        });
    }

    private Message buildRerouteResponseMessage(Flow flow, FlowPathPair newFlowPaths,
                                                CommandContext commandContext) {
        PathInfoData currentPath = FlowPathMapper.INSTANCE.map(flow.getForwardPath());
        PathInfoData resultPath = Optional.ofNullable(newFlowPaths)
                .map(flowPaths -> FlowPathMapper.INSTANCE.map(flowPaths.getForward()))
                .orElse(currentPath);

        FlowRerouteResponse response = new FlowRerouteResponse(resultPath, !resultPath.equals(currentPath));
        return new InfoMessage(response, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
    }

    private void saveHistory(Flow flow, FlowPathPair oldFlowPaths, FlowPathPair newFlowPaths,
                             FlowRerouteFsm stateMachine) {
        Instant timestamp = Instant.now();
        FlowDumpData oldDumpData = HistoryMapper.INSTANCE.map(flow,
                oldFlowPaths.getForward(), oldFlowPaths.getReverse());
        oldDumpData.setDumpType(DumpType.STATE_BEFORE);

        FlowDumpData newDumpData = HistoryMapper.INSTANCE.map(flow,
                newFlowPaths.getForward(), newFlowPaths.getReverse());
        newDumpData.setDumpType(DumpType.STATE_AFTER);

        Stream.of(oldDumpData, newDumpData).forEach(dumpData -> {
            FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                    .taskId(stateMachine.getCommandContext().getCorrelationId())
                    .flowDumpData(dumpData)
                    .flowHistoryData(FlowHistoryData.builder()
                            .action("New paths were created (with allocated resources)")
                            .time(timestamp)
                            .flowId(flow.getFlowId())
                            .build())
                    .flowEventData(FlowEventData.builder()
                            .flowId(flow.getFlowId())
                            .event(FlowEventData.Event.REROUTE)
                            .time(timestamp)
                            .build())
                    .build();
            stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
        });
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute flow";
    }

    @ToString
    private static class ResourceAllocationResult {
        Flow flow;
        FlowPathPair primaryPaths;
        FlowPathPair protectedPaths;
        Collection<FlowResources> allocatedResources = new ArrayList<>();
        boolean overlappingProtectedPathFound;
    }
}
