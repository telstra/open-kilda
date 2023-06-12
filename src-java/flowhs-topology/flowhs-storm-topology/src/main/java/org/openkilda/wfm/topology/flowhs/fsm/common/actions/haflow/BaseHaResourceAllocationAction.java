/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSegmentCookieBuilder;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSubType;
import org.openkilda.pce.GetHaPathsResult;
import org.openkilda.pce.HaPath;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository.IslEndpoints;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.flow.resources.HaFlowResources.HaPathResources;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaFlowPathIds;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flow.model.HaFlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import com.google.common.base.Suppliers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A base for action classes that allocate resources for flow paths.
 */
@Slf4j
public abstract class BaseHaResourceAllocationAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E,
        C extends SpeakerResponseContext> extends BaseResourceAllocationAction<T, S, E, C> {

    protected final HaFlowPathRepository haFlowPathRepository;

    protected BaseHaResourceAllocationAction(
            PersistenceManager persistenceManager, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
            int resourceAllocationRetriesLimit, PathComputer pathComputer, FlowResourcesManager resourcesManager,
            FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, pathAllocationRetriesLimit, pathAllocationRetryDelay, resourceAllocationRetriesLimit,
                pathComputer, resourcesManager, dashboardLogger);
        this.haFlowPathRepository = persistenceManager.getRepositoryFactory().createHaFlowPathRepository();
    }

    protected boolean isNotSamePath(GetHaPathsResult pathPair, HaFlowPathPair haFlowPathPair) {
        if (isPathEmpty(haFlowPathPair.getForward()) || isPathEmpty(haFlowPathPair.getReverse())) {
            return true;
        }
        return isNotSameSubPaths(pathPair.getForward().getSubPaths(), haFlowPathPair.getForward().getSubPaths())
                || isNotSameSubPaths(pathPair.getReverse().getSubPaths(), haFlowPathPair.getReverse().getSubPaths());
    }

    private boolean isNotSameSubPaths(Map<String, Path> foundSubPaths, List<FlowPath> haFlowSubPaths) {
        if (foundSubPaths.size() != haFlowSubPaths.size()) {
            return true;
        }

        for (FlowPath haSubPath : haFlowSubPaths) {
            if (!foundSubPaths.containsKey(haSubPath.getHaSubFlowId())) {
                return true;
            }
            if (!flowPathBuilder.isSamePath(foundSubPaths.get(haSubPath.getHaSubFlowId()), haSubPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPathEmpty(HaFlowPath haFlowPath) {
        return haFlowPath == null || haFlowPath.getSubPaths() == null;
    }

    @SneakyThrows
    protected GetHaPathsResult allocatePathPair(
            HaFlow haFlow, HaPathIdsPair newPathIds, boolean forceToIgnoreBandwidth,
            List<PathId> subPathsToReuseBandwidth, HaFlowPathPair oldPaths, boolean allowOldPaths,
            Predicate<GetHaPathsResult> whetherCreatePathSegments, boolean isProtected)
            throws RecoverableException, UnroutableFlowException, ResourceAllocationException {
        // Lazy initialisable map with reused bandwidth...
        Supplier<Map<IslEndpoints, Long>> reuseBandwidthPerIsl = getBandwidthMapSupplier(
                haFlow, subPathsToReuseBandwidth);
        RetryPolicy<GetHaPathsResult> pathAllocationRetryPolicy = getPathAllocationRetryPolicy();
        try {
            return Failsafe.with(pathAllocationRetryPolicy).get(() -> {
                GetHaPathsResult potentialPath;
                if (forceToIgnoreBandwidth) {
                    boolean originalIgnoreBandwidth = haFlow.isIgnoreBandwidth();
                    haFlow.setIgnoreBandwidth(true);
                    potentialPath = pathComputer.getHaPath(haFlow, Collections.emptyList(), isProtected);
                    haFlow.setIgnoreBandwidth(originalIgnoreBandwidth);
                } else {
                    potentialPath = pathComputer.getHaPath(haFlow, subPathsToReuseBandwidth, isProtected);
                }

                boolean newPathFound = isNotSamePath(potentialPath, oldPaths);
                if (allowOldPaths || newPathFound) {
                    boolean createFoundPath = whetherCreatePathSegments.test(potentialPath);
                    log.debug("Found {} path for ha-flow {}. {} (re-)creating it", newPathFound ? "a new" : "the same",
                            haFlow.getHaFlowId(), createFoundPath ? "Proceed with" : "Skip");

                    if (createFoundPath) {
                        boolean ignoreBandwidth = forceToIgnoreBandwidth || haFlow.isIgnoreBandwidth();
                        List<PathSegment> segments = buildPathSegments(
                                haFlow, newPathIds.getForward(), potentialPath.getForward(), haFlow.getHaFlowId(),
                                ignoreBandwidth);

                        segments.addAll(buildPathSegments(
                                haFlow, newPathIds.getReverse(), potentialPath.getReverse(), haFlow.getHaFlowId(),
                                ignoreBandwidth));

                        transactionManager.doInTransaction(() -> {
                            createPathSegments(segments, reuseBandwidthPerIsl);
                        });
                    }

                    return potentialPath;
                } else {
                    log.debug("Found the same path for ha-flow {}, but not allowed", haFlow.getHaFlowId());
                    return null;
                }
            });
        } catch (FailsafeException ex) {
            throw ex.getCause();
        }
    }

    private List<PathSegment> buildPathSegments(
            HaFlow haFlow, HaFlowPathIds newPathIds, HaPath potentialPath, String sharedBandwidthGroupId,
            boolean ignoreBandwidth) {
        List<PathSegment> segments = new ArrayList<>();
        for (HaSubFlow haSubFlow : haFlow.getHaSubFlows()) {
            PathId subPathId = newPathIds.getSubPathId(haSubFlow.getHaSubFlowId());
            segments.addAll(flowPathBuilder.buildPathSegments(subPathId,
                    potentialPath.getSubPaths().get(haSubFlow.getHaSubFlowId()).getSegments(),
                    haFlow.getMaximumBandwidth(), ignoreBandwidth, sharedBandwidthGroupId));
        }

        return segments;
    }

    private Supplier<Map<IslEndpoints, Long>> getBandwidthMapSupplier(
            HaFlow haFlow, List<PathId> pathsToReuseBandwidth) {
        return Suppliers.memoize(() -> {
            Map<IslEndpoints, Long> result = new HashMap<>();
            if (pathsToReuseBandwidth != null && !pathsToReuseBandwidth.isEmpty()) {
                pathsToReuseBandwidth.stream()
                        .map(pathId -> haFlow.getSubPath(pathId)
                                .orElse(flowPathRepository.findById(pathId).orElse(null)))
                        .filter(Objects::nonNull)
                        .flatMap(path -> path.getSegments().stream())
                        .forEach(segment -> {
                            IslEndpoints isl = new IslEndpoints(
                                    segment.getSrcSwitchId().toString(), segment.getSrcPort(),
                                    segment.getDestSwitchId().toString(), segment.getDestPort());
                            result.put(isl, result.getOrDefault(isl, 0L) + segment.getBandwidth());
                        });
            }
            return result;
        });
    }

    @SneakyThrows
    protected HaFlowResources allocateFlowResources(HaFlow haFlow, SwitchId yPointSwitchId, HaPathIdsPair haPathIdsPair)
            throws ResourceAllocationException {
        HaFlowResources flowResources = transactionManager.doInTransaction(getResourcesAllocationRetryPolicy(),
                () -> resourcesManager.allocateHaFlowResources(haFlow, yPointSwitchId, haPathIdsPair));
        log.debug("Resources have been allocated: {}", flowResources);
        return flowResources;
    }

    protected HaFlowPathPair createHaFlowPathPair(
            String haFlowId, HaFlowResources haFlowResources, GetHaPathsResult haPaths,
            boolean forceToIgnoreBandwidth) {
        final FlowSegmentCookieBuilder cookieBuilder = FlowSegmentCookie.builder()
                .flowEffectiveId(haFlowResources.getUnmaskedCookie());

        return transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(haFlowId);

            HaFlowPath newForwardPath = flowPathBuilder.buildHaFlowPath(
                    haFlow, haFlowResources.getForward(), haPaths.getForward(),
                    cookieBuilder.direction(FlowPathDirection.FORWARD).subType(FlowSubType.SHARED).build(),
                    forceToIgnoreBandwidth);
            newForwardPath.setSubPaths(createSubPaths(haPaths.getForward(), haFlow, haFlowResources.getForward(),
                    newForwardPath, forceToIgnoreBandwidth));
            log.debug("Persisting new forward path {}", newForwardPath);
            haFlowPathRepository.add(newForwardPath);
            newForwardPath.setHaSubFlows(haFlow.getHaSubFlows());

            HaFlowPath newReversePath = flowPathBuilder.buildHaFlowPath(
                    haFlow, haFlowResources.getReverse(), haPaths.getReverse(),
                    cookieBuilder.direction(FlowPathDirection.REVERSE).subType(FlowSubType.SHARED).build(),
                    forceToIgnoreBandwidth);
            newReversePath.setSubPaths(createSubPaths(haPaths.getReverse(), haFlow, haFlowResources.getReverse(),
                    newReversePath, forceToIgnoreBandwidth));
            log.debug("Persisting new reverse path {}", newForwardPath);
            haFlowPathRepository.add(newReversePath);
            newReversePath.setHaSubFlows(haFlow.getHaSubFlows());

            haFlow.addPaths(newForwardPath, newReversePath);
            return HaFlowPathPair.builder().forward(newForwardPath).reverse(newReversePath).build();
        });
    }

    private List<FlowPath> createSubPaths(
            HaPath haPath, HaFlow haFlow, HaPathResources haPathResources, HaFlowPath haFlowPath,
            boolean forceIgnoreBandwidth) {

        Map<String, FlowSubType> subTypeMap = flowPathBuilder.buildSubTypeMap(haFlow.getHaSubFlows());
        List<FlowPath> subPaths = new ArrayList<>();
        for (HaSubFlow subFlow : haFlow.getHaSubFlows()) {
            Path path = haPath.getSubPaths().get(subFlow.getHaSubFlowId());
            List<PathSegment> segments = pathSegmentRepository.findByPathId(
                    haPathResources.getSubPathIds().get(subFlow.getHaSubFlowId()));
            Switch srcSwitch = haFlowPath.isForward() ? haFlowPath.getSharedSwitch() : subFlow.getEndpointSwitch();
            Switch dstSwitch = haFlowPath.isForward() ? subFlow.getEndpointSwitch() : haFlowPath.getSharedSwitch();
            FlowPath subPath = flowPathBuilder.buildHaSubPath(
                    haFlow, haPathResources.getSubPathResources(subFlow.getHaSubFlowId()), path,
                    srcSwitch, dstSwitch,
                    haFlowPath.getCookie().toBuilder().subType(subTypeMap.get(subFlow.getHaSubFlowId())).build(),
                    segments, forceIgnoreBandwidth);
            flowPathRepository.add(subPath);
            subPath.setHaSubFlow(subFlow);
            subPaths.add(subPath);
        }
        return subPaths;
    }

    protected void saveAllocationActionWithDumpsToHistory(T stateMachine, HaFlow haFlow, String pathsType,
                                                          HaFlowPathPair newHaFlowPaths) {
        HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction(format("%s paths have been allocated", StringUtils.capitalize(pathsType)))
                .withDescription(
                        format("The following paths have been allocated for HA-flow %s: forward: %s, reverse: %s",
                                haFlow.getHaFlowId(),
                                newHaFlowPaths.getForward().getHaPathId(),
                                newHaFlowPaths.getReverse().getHaPathId()))
                .withHaFlowDumpAfter(haFlow));
    }

    @Override
    protected void notifyEventListeners(T stateMachine, String errorMessage, ErrorType errorType) {
        stateMachine.notifyEventListenersOnError(errorType, errorMessage);
    }

    @Override
    protected void firePathNotFound(T stateMachine, String errorMessage) {
        stateMachine.fireNoPathFound(errorMessage);
    }

    @Override
    protected void checkAllocatedPaths(T stateMachine) throws ResourceAllocationException {
        List<PathId> pathIds = getAllocatedSubPathIds(stateMachine);
        if (!pathIds.isEmpty()) {
            Collection<Isl> pathIsls = islRepository.findByPathIds(pathIds);
            for (Isl isl : pathIsls) {
                if (!IslStatus.ACTIVE.equals(isl.getStatus())) {
                    throw new ResourceAllocationException(
                            format("ISL %s_%d-%s_%d is not active on the allocated path",
                                    isl.getSrcSwitch().getSwitchId(), isl.getSrcPort(),
                                    isl.getDestSwitch().getSwitchId(), isl.getDestPort()));
                }
            }
        }
    }

    protected Predicate<GetHaPathsResult> buildNonOverlappingPathPredicate(
            HaFlowPath primaryForward, HaFlowPath primaryReverse) {
        return haPath -> (primaryForward == null
                || !flowPathBuilder.arePathsOverlapped(haPath.getForward(), primaryForward))
                && (primaryReverse == null
                || !flowPathBuilder.arePathsOverlapped(haPath.getReverse(), primaryReverse));
    }

    @Override
    protected void saveRejectedResources(T stateMachine) {
        stateMachine.getRejectedSubPathsIds().addAll(getAllocatedSubPathIds(stateMachine));
        stateMachine.getRejectedHaPathsIds().addAll(getAllocatedHaPathIds(stateMachine));

        Optional.ofNullable(stateMachine.getNewPrimaryResources())
                .ifPresent(stateMachine.getRejectedResources()::add);
        Optional.ofNullable(stateMachine.getNewProtectedResources())
                .ifPresent(stateMachine.getRejectedResources()::add);

        stateMachine.setNewPrimaryResources(null);
        stateMachine.setNewPrimaryPathIds(null);
        stateMachine.setNewProtectedPathIds(null);
    }

    private List<PathId> getAllocatedSubPathIds(T stateMachine) {
        List<PathId> pathIds = new ArrayList<>();
        Optional.ofNullable(stateMachine.getNewPrimaryPathIds())
                .map(HaPathIdsPair::getAllSubPathIds).ifPresent(pathIds::addAll);
        Optional.ofNullable(stateMachine.getNewProtectedPathIds())
                .map(HaPathIdsPair::getAllSubPathIds).ifPresent(pathIds::addAll);
        return pathIds;
    }

    private List<PathId> getAllocatedHaPathIds(T stateMachine) {
        List<PathId> pathIds = new ArrayList<>();
        if (stateMachine.getNewPrimaryPathIds() != null) {
            pathIds.addAll(stateMachine.getNewPrimaryPathIds().getAllHaFlowPathIds());
        }
        if (stateMachine.getNewProtectedPathIds() != null) {
            pathIds.addAll(stateMachine.getNewProtectedPathIds().getAllHaFlowPathIds());
        }
        return pathIds;
    }
}
