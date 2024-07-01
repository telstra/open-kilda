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

package org.openkilda.wfm.topology.reroute.service;

import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;

import org.openkilda.messaging.command.flow.FlowRerouteFlushRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.reroute.FlowType;
import org.openkilda.messaging.info.reroute.SwitchStateChanged;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.EntityNotFoundException;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.reroute.bolts.MessageSender;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData.FlowThrottlingDataBuilder;

import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RerouteService {
    private final FlowOperationsDashboardLogger flowDashboardLogger = new FlowOperationsDashboardLogger(log);
    private final FlowRepository flowRepository;
    private final YFlowRepository yFlowRepository;
    private final HaFlowRepository haFlowRepository;
    private final FlowPathRepository flowPathRepository;
    private final PathSegmentRepository pathSegmentRepository;
    private final TransactionManager transactionManager;

    public RerouteService(PersistenceManager persistenceManager) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        this.haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.pathSegmentRepository = persistenceManager.getRepositoryFactory().createPathSegmentRepository();
        this.transactionManager = persistenceManager.getTransactionManager();
    }

    /**
     * Handles reroute on ISL down events.
     *
     * @param sender transport sender
     * @param correlationId correlation id to pass through
     * @param command origin command
     */
    @TimedExecution("reroute_affected_flows")
    public void rerouteAffectedFlows(MessageSender sender, String correlationId, RerouteAffectedFlows command) {
        // TODO(surabujin): need better/more detailed representation of failed ISL
        PathNode pathNode = command.getPathNode();
        int port = pathNode.getPortNo();
        SwitchId switchId = pathNode.getSwitchId();
        final IslEndpoint affectedIsl = new IslEndpoint(switchId, port);

        RerouteResult rerouteResult = transactionManager.doInTransaction(() -> {
            RerouteResult result = new RerouteResult();
            Collection<FlowPath> affectedFlowPaths = getAffectedFlowPaths(pathNode.getSwitchId(), pathNode.getPortNo());

            // swapping affected primary paths with available protected
            List<FlowPath> pathsForSwapping = getPathsForSwapping(affectedFlowPaths);
            for (FlowPath path : pathsForSwapping) {
                if (path.getFlow() != null) {
                    String yFlowId = path.getFlow().getYFlowId();
                    if (yFlowId != null) {
                        result.yFlowIdsForSwapPaths.add(yFlowId);
                    } else {
                        result.flowIdsForSwapPaths.add(path.getFlowId());
                    }
                } else if (path.getHaFlowId() != null) {
                    result.haFlowIdsForSwapPaths.add(path.getHaFlowId());
                }
            }

            for (FlowWithAffectedPaths entry : groupPathsForRerouting(affectedFlowPaths)) {
                Flow flow = entry.getFlow();
                boolean rerouteRequired = updateFlowPathsStateForFlow(switchId, port, entry.getAffectedPaths());
                FlowStatus flowStatus = flow.computeFlowStatus();
                String flowStatusInfo = null;
                if (!FlowStatus.UP.equals(flowStatus)) {
                    flowStatusInfo = command.getReason();
                }
                flowRepository.updateStatusSafe(flow, flowStatus, flowStatusInfo);

                if (rerouteRequired) {
                    if (flow.getYFlow() != null) {
                        result.yFlowsForReroute.add(flow.getYFlow());
                    } else {
                        result.flowsForReroute.add(flow);
                    }
                }
            }

            handleAffectedHaFlows(command, result, affectedFlowPaths);
            handleAffectedPinnedFlows(command, affectedFlowPaths);
            handleAffectedPinnedHaFlows(command, affectedFlowPaths);
            return result;
        });

        sendFlowRequests(sender, correlationId, command.getReason(), affectedIsl, rerouteResult);
        sendYFlowRequests(sender, correlationId, command.getReason(), affectedIsl, rerouteResult);
        sendHaFlowRequests(sender, correlationId, command.getReason(), affectedIsl, rerouteResult);
    }

    private void handleAffectedPinnedFlows(RerouteAffectedFlows command, Collection<FlowPath> affectedFlowPaths) {
        Set<Flow> affectedPinnedFlows = groupAffectedPinnedFlows(affectedFlowPaths);
        for (Flow flow : affectedPinnedFlows) {
            List<FlowPath> flowPaths = new ArrayList<>(flow.getPaths());
            updateFlowPathsStateForFlow(
                    command.getPathNode().getSwitchId(), command.getPathNode().getPortNo(), flowPaths);
            if (flow.getStatus() != FlowStatus.DOWN) {
                flowDashboardLogger.onFlowStatusUpdate(flow.getFlowId(), FlowStatus.DOWN);
                flowRepository.updateStatusSafe(flow, FlowStatus.DOWN, command.getReason());
            }
        }
    }

    private void sendFlowRequests(
            MessageSender sender, String correlationId, String reason, IslEndpoint affectedIsl,
            RerouteResult rerouteResult) {
        for (String flowId : rerouteResult.flowIdsForSwapPaths) {
            sender.emitPathSwapCommand(correlationId, flowId, reason);
        }
        for (Flow flow : rerouteResult.flowsForReroute) {
            FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(flow)
                    .correlationId(correlationId)
                    .affectedIsl(Collections.singleton(affectedIsl))
                    .effectivelyDown(true)
                    .reason(reason)
                    .build();
            sender.emitRerouteCommand(flow.getFlowId(), flowThrottlingData);
        }
    }

    private void sendYFlowRequests(
            MessageSender sender, String correlationId, String reason, IslEndpoint affectedIsl,
            RerouteResult rerouteResult) {
        for (String yFlowId : rerouteResult.yFlowIdsForSwapPaths) {
            sender.emitYFlowPathSwapCommand(correlationId, yFlowId, reason);
        }
        for (YFlow yFlow : rerouteResult.yFlowsForReroute) {
            FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(yFlow)
                    .correlationId(correlationId)
                    .affectedIsl(Collections.singleton(affectedIsl))
                    .effectivelyDown(true)
                    .reason(reason)
                    .build();
            sender.emitRerouteCommand(yFlow.getYFlowId(), flowThrottlingData);
        }
    }

    private void sendHaFlowRequests(
            MessageSender sender, String correlationId, String reason, IslEndpoint affectedIsl,
            RerouteResult rerouteResult) {
        for (String haFlowId : rerouteResult.haFlowIdsForSwapPaths) {
            sender.emitHaFlowPathSwapCommand(correlationId, haFlowId, reason);
        }
        for (HaFlow haFlow : rerouteResult.haFlowsForReroute) {
            FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(haFlow)
                    .correlationId(correlationId)
                    .affectedIsl(Collections.singleton(affectedIsl))
                    .effectivelyDown(true)
                    .reason(reason)
                    .build();
            sender.emitRerouteCommand(haFlow.getHaFlowId(), flowThrottlingData);
        }
    }

    private void handleAffectedHaFlows(
            RerouteAffectedFlows command, RerouteResult result, Collection<FlowPath> affectedFlowPaths) {
        for (HaFlowWithAffectedPaths entry : groupHaSubPathsForRerouting(affectedFlowPaths)) {
            HaFlow haFlow = entry.getHaFlow();
            final boolean rerouteRequired = updateHaFlowPathsState(
                    command.getPathNode().getSwitchId(), command.getPathNode().getPortNo(), entry.getAffectedPaths());
            FlowStatus newStatus = haFlow.computeStatus();
            String statusInfo = null;
            if (!FlowStatus.UP.equals(newStatus)) {
                statusInfo = command.getReason();
            }
            haFlow.recalculateHaSubFlowStatusesSafe();
            haFlowRepository.updateStatusSafe(haFlow, newStatus, statusInfo);

            if (rerouteRequired) {
                result.haFlowsForReroute.add(haFlow);
            }
        }
    }

    private void handleAffectedPinnedHaFlows(RerouteAffectedFlows command, Collection<FlowPath> affectedFlowPaths) {
        Set<HaFlow> affectedPinnedHaFlows = groupAffectedPinnedHaFlows(affectedFlowPaths);


        for (HaFlow haFlow : affectedPinnedHaFlows) {
            List<FlowPath> flowPaths = new ArrayList<>(haFlow.getSubPaths());
            updateHaFlowPathsState(command.getPathNode().getSwitchId(), command.getPathNode().getPortNo(), flowPaths);
            if (haFlow.getStatus() != FlowStatus.DOWN) {
                flowDashboardLogger.onHaFlowStatusUpdate(haFlow.getHaFlowId(), FlowStatus.DOWN);
                haFlow.recalculateHaSubFlowStatusesSafe();
                haFlowRepository.updateStatusSafe(haFlow, FlowStatus.DOWN, command.getReason());
            }
        }
    }

    private boolean updateFlowPathsStateForFlow(SwitchId switchId, int port, List<FlowPath> paths) {
        boolean rerouteRequired = false;
        for (FlowPath path : paths) {
            boolean failedFlowPath = updatePathSegmentStatuses(switchId, port, path, path.getFlowId());
            if (failedFlowPath) {
                rerouteRequired = true;
                updateFlowPathStatus(path, FlowPathStatus.INACTIVE);
            }
        }
        return rerouteRequired;
    }

    private boolean updateHaFlowPathsState(SwitchId switchId, int port, List<FlowPath> paths) {
        boolean rerouteRequired = false;
        for (FlowPath path : paths) {
            boolean failedPath = updatePathSegmentStatuses(switchId, port, path, path.getHaFlowId());
            if (failedPath) {
                rerouteRequired = true;
                updateFlowPathStatus(path, FlowPathStatus.INACTIVE);

                if (path.getHaFlowPath() != null) {
                    updateHaFlowPathStatus(path.getHaFlowPath(), FlowPathStatus.INACTIVE);
                }
                if (path.getHaSubFlow() != null) {
                    path.getHaSubFlow().setStatus(FlowStatus.DOWN);
                }
            }
        }
        return rerouteRequired;
    }

    private boolean updatePathSegmentStatuses(SwitchId switchId, int port, FlowPath path, String flowId) {
        boolean failedFlowPath = false;
        for (PathSegment pathSegment : path.getSegments()) {
            if (pathSegment.getSrcPort() == port
                    && switchId.equals(pathSegment.getSrcSwitchId())
                    || (pathSegment.getDestPort() == port
                    && switchId.equals(pathSegment.getDestSwitchId()))) {
                pathSegment.setFailed(true);
                try {
                    pathSegmentRepository.updateFailedStatus(path, pathSegment, true);
                    failedFlowPath = true;
                } catch (EntityNotFoundException e) {
                    log.warn("Path segment not found for flow {} and path {}. Skipping path segment status update.",
                            flowId, path.getPathId(), e);
                }
                break;
            }
        }
        return failedFlowPath;
    }

    /**
     * Handles reroute on switch up events.
     *
     * @param sender transport sender
     * @param correlationId correlation id to pass through
     * @param switchId switch id
     */
    @TimedExecution("reroute_inactive_affected_flows")
    public void rerouteInactiveAffectedFlows(MessageSender sender, String correlationId,
                                             SwitchId switchId) {
        Collection<FlowPath> affectedInactivePaths = flowPathRepository.findInactiveBySegmentSwitch(switchId);
        rerouteAffectedInactiveFlowsAndYFlows(sender, correlationId, switchId, affectedInactivePaths);
        rerouteAffectedInactiveHaFlows(sender, correlationId, switchId, affectedInactivePaths);
    }

    private void rerouteAffectedInactiveFlowsAndYFlows(
            MessageSender sender, String correlationId, SwitchId switchId, Collection<FlowPath> affectedInactivePaths) {
        Set<Flow> flowsForRerouting = getAffectedInactiveFlowsForRerouting(affectedInactivePaths, switchId);

        for (Flow flow : flowsForRerouting) {
            if (flow.isPinned()) {
                log.info("Skipping reroute command for pinned flow {}", flow.getFlowId());
            } else if (flow.getYFlow() != null) {
                YFlow yFlow = flow.getYFlow();
                log.info("Produce reroute (attempt to restore inactive flow) request for {} (switch online {})",
                        yFlow.getYFlowId(), switchId);
                // Emit reroute command with empty affectedIsls to force flow to reroute despite it's current paths
                FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(yFlow)
                        .correlationId(correlationId)
                        .affectedIsl(Collections.emptySet())
                        .effectivelyDown(true)
                        .reason(format("Switch '%s' online", switchId))
                        .build();
                sender.emitRerouteCommand(yFlow.getYFlowId(), flowThrottlingData);
            } else {
                log.info("Produce reroute (attempt to restore inactive flow) request for {} (switch online {})",
                        flow.getFlowId(), switchId);
                // Emit reroute command with empty affectedIsls to force flow to reroute despite it's current paths
                FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(flow)
                        .correlationId(correlationId)
                        .affectedIsl(Collections.emptySet())
                        .effectivelyDown(true)
                        .reason(format("Switch '%s' online", switchId))
                        .build();
                sender.emitRerouteCommand(flow.getFlowId(), flowThrottlingData);
            }
        }
    }

    private void rerouteAffectedInactiveHaFlows(
            MessageSender sender, String correlationId, SwitchId switchId, Collection<FlowPath> affectedInactivePaths) {
        Set<HaFlow> haFlowsForRerouting = getAffectedInactiveHaFlows(affectedInactivePaths, switchId);

        for (HaFlow haFlow : haFlowsForRerouting) {
            if (haFlow.isPinned()) {
                log.info("Skipping reroute command for pinned HA-flow {}", haFlow.getHaFlowId());
            } else {
                log.info("Produce reroute (attempt to restore inactive HA-flow) request for {} (switch online {})",
                        haFlow.getHaFlowId(), switchId);
                // Emit reroute command with empty affectedIsls to force HA-flow to reroute despite it's current paths
                FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(haFlow)
                        .correlationId(correlationId)
                        .affectedIsl(Collections.emptySet())
                        .effectivelyDown(true)
                        .reason(format("Switch '%s' online", switchId))
                        .build();
                sender.emitRerouteCommand(haFlow.getHaFlowId(), flowThrottlingData);
            }
        }
    }

    /**
     * Handles reroute on ISL up events.
     *
     * @param sender transport sender
     * @param correlationId correlation id to pass through
     * @param command origin command
     */
    @TimedExecution("reroute_inactive_flows")
    public void rerouteInactiveFlows(MessageSender sender, String correlationId, RerouteInactiveFlows command) {
        rerouteInactiveFlowsAndYFlows(sender, correlationId, command);
        rerouteInactiveHaFlows(sender, correlationId, command);
    }

    private void rerouteInactiveFlowsAndYFlows(
            MessageSender sender, String correlationId, RerouteInactiveFlows command) {
        PathNode pathNode = command.getPathNode();
        int port = pathNode.getPortNo();
        SwitchId switchId = pathNode.getSwitchId();
        Map<String, FlowThrottlingData> flowsForReroute = transactionManager.doInTransaction(() -> {
            Map<String, FlowThrottlingData> forReroute = new HashMap<>();

            for (Flow flow : getInactiveFlowsForRerouting()) {
                Set<IslEndpoint> allAffectedIslEndpoints = new HashSet<>();
                for (FlowPath flowPath : flow.getPaths()) {
                    allAffectedIslEndpoints.addAll(handleAffectedIslsAndPaths(port, switchId, flowPath));
                }
                FlowStatus flowStatus = flow.computeFlowStatus();
                String flowStatusInfo = null;
                if (!FlowStatus.UP.equals(flowStatus)) {
                    flowStatusInfo = command.getReason();
                }
                flowRepository.updateStatusSafe(flow, flowStatus, flowStatusInfo);

                if (flow.isPinned()) {
                    log.info("Skipping reroute command for pinned flow {}", flow.getFlowId());
                } else if (flow.getYFlow() != null) {
                    YFlow yFlow = flow.getYFlow();
                    log.info("Create reroute command (attempt to restore inactive flows) request for {} "
                                    + "(affected ISL endpoints: {})",
                            yFlow.getYFlowId(), allAffectedIslEndpoints);
                    FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(yFlow)
                            .correlationId(correlationId)
                            .affectedIsl(allAffectedIslEndpoints)
                            .effectivelyDown(true)
                            .reason(command.getReason())
                            .build();
                    sender.emitRerouteCommand(yFlow.getYFlowId(), flowThrottlingData);
                } else {
                    log.info("Create reroute command (attempt to restore inactive flows) request for {} (affected ISL "
                                    + "endpoints: {})",
                            flow.getFlowId(), allAffectedIslEndpoints);
                    FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(flow)
                            .correlationId(correlationId)
                            .affectedIsl(allAffectedIslEndpoints)
                            .effectivelyDown(true)
                            .reason(command.getReason())
                            .build();
                    forReroute.put(flow.getFlowId(), flowThrottlingData);
                }
            }
            return forReroute;
        });

        for (Entry<String, FlowThrottlingData> entry : flowsForReroute.entrySet()) {
            log.info("Produce reroute (attempt to restore inactive flows) request for {} (affected ISL endpoints: {})",
                    entry.getKey(), entry.getValue().getAffectedIsl());
            sender.emitRerouteCommand(entry.getKey(), entry.getValue());
        }
    }

    private void rerouteInactiveHaFlows(
            MessageSender sender, String correlationId, RerouteInactiveFlows command) {
        int port = command.getPathNode().getPortNo();
        SwitchId switchId = command.getPathNode().getSwitchId();

        Map<String, FlowThrottlingData> haFlowsForReroute = transactionManager.doInTransaction(() -> {
            Map<String, FlowThrottlingData> forReroute = new HashMap<>();

            for (HaFlow haFlow : haFlowRepository.findInactive()) {
                Set<IslEndpoint> allAffectedIslEndpoints = new HashSet<>();
                for (FlowPath subPath : haFlow.getSubPaths()) {
                    allAffectedIslEndpoints.addAll(handleAffectedIslsAndPaths(port, switchId, subPath));
                }
                for (HaFlowPath haFlowPath : haFlow.getPaths()) {
                    if (haFlowPath.getSubPaths().stream().map(FlowPath::getStatus)
                            .allMatch(FlowPathStatus.ACTIVE::equals)) {
                        updateHaFlowPathStatus(haFlowPath, FlowPathStatus.ACTIVE);
                    }
                }
                FlowStatus newStatus = haFlow.computeStatus();
                String flowStatusInfo = null;
                if (!FlowStatus.UP.equals(newStatus)) {
                    flowStatusInfo = command.getReason();
                }
                haFlow.recalculateHaSubFlowStatusesSafe();
                haFlowRepository.updateStatusSafe(haFlow, newStatus, flowStatusInfo);


                if (haFlow.isPinned()) {
                    log.info("Skipping reroute command for pinned HA-flow {}", haFlow.getHaFlowId());
                } else {
                    log.info("Create reroute command (attempt to restore inactive flows) request for {} (affected ISL "
                                    + "endpoints: {})",
                            haFlow.getHaFlowId(), allAffectedIslEndpoints);
                    FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(haFlow)
                            .correlationId(correlationId)
                            .affectedIsl(allAffectedIslEndpoints)
                            .effectivelyDown(true)
                            .reason(command.getReason())
                            .build();
                    forReroute.put(haFlow.getHaFlowId(), flowThrottlingData);
                }
            }
            return forReroute;
        });

        for (Entry<String, FlowThrottlingData> entry : haFlowsForReroute.entrySet()) {
            log.info("Produce reroute (attempt to restore inactive HA-flows) request for {} (affected ISL endpoints: "
                    + "{})", entry.getKey(), entry.getValue().getAffectedIsl());
            sender.emitRerouteCommand(entry.getKey(), entry.getValue());
        }
    }

    private Set<IslEndpoint> handleAffectedIslsAndPaths(int port, SwitchId switchId, FlowPath path) {
        Set<IslEndpoint> affectedIslEndpoints = new HashSet<>();
        PathSegment firstSegment = null;
        int failedSegmentsCount = 0;
        for (PathSegment pathSegment : path.getSegments()) {
            if (firstSegment == null) {
                firstSegment = pathSegment;
            }

            if (pathSegment.isFailed()) {
                affectedIslEndpoints.add(new IslEndpoint(pathSegment.getSrcSwitchId(), pathSegment.getSrcPort()));
                affectedIslEndpoints.add(new IslEndpoint(pathSegment.getDestSwitchId(), pathSegment.getDestPort()));

                if (pathSegment.containsNode(switchId, port)) {
                    pathSegment.setFailed(false);
                    pathSegmentRepository.updateFailedStatus(path, pathSegment, false);
                } else {
                    failedSegmentsCount++;
                }
            }
        }

        if (path.getStatus().equals(FlowPathStatus.INACTIVE) && failedSegmentsCount == 0) {
            updateFlowPathStatus(path, FlowPathStatus.ACTIVE);

            // force reroute of failed path only (required due to inaccurate path/segment state management)
            if (affectedIslEndpoints.isEmpty() && firstSegment != null) {
                affectedIslEndpoints.add(new IslEndpoint(firstSegment.getSrcSwitchId(), firstSegment.getSrcPort()));
            }
        }
        return affectedIslEndpoints;
    }

    /**
     * Get list of active affected flow paths with flows.
     *
     * @param switchId switch id.
     * @param port port.
     * @return list affected flows and flow paths.
     */
    public Collection<FlowPath> getAffectedFlowPaths(SwitchId switchId, int port) {
        log.info("Get affected flow paths by node {}_{}", switchId, port);
        return flowPathRepository.findBySegmentEndpoint(switchId, port);
    }


    /**
     * Get flow paths list to swap.
     *
     * @return list of flows paths.
     */
    public List<FlowPath> getPathsForSwapping(Collection<FlowPath> paths) {
        return paths.stream()
                .filter(path -> path.getStatus() == null || FlowPathStatus.ACTIVE.equals(path.getStatus()))
                .filter(this::filterPathForSwapping)
                .collect(Collectors.toList());
    }

    private boolean filterForwardPrimaryPath(FlowPath path) {
        return path.getPathId().equals(path.getFlow().getForwardPathId());
    }

    private boolean filterPathForSwapping(FlowPath path) {
        if (path.getFlow() != null) {
            return path.getFlow().isAllocateProtectedPath() && filterForwardPrimaryPath(path);
        }
        if (path.getHaFlow() != null) {
            HaFlow haFlow = path.getHaFlow();
            return haFlow.isAllocateProtectedPath() && path.getHaFlowPathId().equals(haFlow.getForwardPathId());
        }
        return false;
    }

    /**
     * Returns map with flow for reroute and set of reroute pathId.
     *
     * @return list with flow for reroute and set of reroute pathId.
     */
    private List<FlowWithAffectedPaths> groupPathsForRerouting(Collection<FlowPath> paths) {
        Map<String, FlowWithAffectedPaths> results = new HashMap<>();
        for (FlowPath entry : paths) {
            Flow flow = entry.getFlow();
            if (flow == null) {
                continue; // It is orphaned path of HA-flow sub path
            }
            if (flow.isPinned()) {
                continue;
            }
            results.computeIfAbsent(flow.getFlowId(), key -> new FlowWithAffectedPaths(flow))
                    .getAffectedPaths().add(entry);
        }
        return new ArrayList<>(results.values());
    }

    private List<HaFlowWithAffectedPaths> groupHaSubPathsForRerouting(Collection<FlowPath> paths) {
        Map<String, HaFlowWithAffectedPaths> result = new HashMap<>();
        for (FlowPath entry : paths) {
            HaFlow haFlow = entry.getHaFlow();
            if (haFlow == null) {
                continue; // It is orphaned path of flow path
            }
            if (haFlow.isPinned()) {
                continue;
            }
            result.computeIfAbsent(haFlow.getHaFlowId(), key -> new HaFlowWithAffectedPaths(haFlow))
                    .getAffectedPaths().add(entry);
        }
        return new ArrayList<>(result.values());
    }

    /**
     * Filters out unique pinned flow from paths.
     *
     * @param paths affected paths
     */
    public Set<Flow> groupAffectedPinnedFlows(Collection<FlowPath> paths) {
        return paths.stream()
                .map(FlowPath::getFlow)
                .filter(Objects::nonNull)
                .filter(Flow::isPinned)
                .collect(Collectors.toSet());
    }

    private Set<HaFlow> groupAffectedPinnedHaFlows(Collection<FlowPath> paths) {
        return paths.stream()
                .map(FlowPath::getHaFlow)
                .filter(Objects::nonNull)
                .filter(HaFlow::isPinned)
                .collect(Collectors.toSet());
    }

    /**
     * Returns a set with inactive flows for rerouting.
     */
    private Set<Flow> getInactiveFlowsForRerouting() {
        log.info("Get inactive flows");
        return flowRepository.findInactiveFlows().stream()
                .filter(flow -> !flow.isOneSwitchFlow())
                .collect(Collectors.toSet());
    }

    /**
     * Returns affected inactive flow set for rerouting.
     */
    public Set<Flow> getAffectedInactiveFlowsForRerouting(Collection<FlowPath> affectedPaths, SwitchId switchId) {
        log.info("Get affected inactive flows for switch {}", switchId);
        return affectedPaths.stream()
                .map(FlowPath::getFlow)
                .filter(Objects::nonNull)
                .filter(flow -> !flow.isOneSwitchFlow())
                .collect(toSet());
    }

    private Set<HaFlow> getAffectedInactiveHaFlows(Collection<FlowPath> affectedPaths, SwitchId switchId) {
        log.info("Get affected inactive HA-flows for switch {}", switchId);
        return affectedPaths.stream()
                .map(FlowPath::getHaFlow)
                .filter(Objects::nonNull)
                .collect(toSet());
    }

    private void updateFlowPathStatus(FlowPath path, FlowPathStatus status) {
        try {
            path.setStatus(status);
        } catch (PersistenceException e) {
            log.error("Unable to set path {} status to {}: {}", path.getPathId(), status, e.getMessage());
        }
    }

    private void updateHaFlowPathStatus(HaFlowPath haFlowPath, FlowPathStatus status) {
        try {
            haFlowPath.setStatus(status);
        } catch (PersistenceException e) {
            log.error("Unable to set Ha-flow path {} status to {}: {}",
                    haFlowPath.getHaPathId(), status, e.getMessage());
        }
    }

    /**
     * Process manual reroute request.
     */
    public void processRerouteRequest(MessageSender sender, String correlationId, FlowRerouteRequest request) {
        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
        FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(flow.orElse(null))
                .correlationId(correlationId)
                .affectedIsl(request.getAffectedIsls())
                .effectivelyDown(request.isEffectivelyDown())
                .reason(request.getReason())
                .build();
        if (request.isManual()) {
            sender.emitManualRerouteCommand(request.getFlowId(), flowThrottlingData);
        } else {
            sender.emitRerouteCommand(request.getFlowId(), flowThrottlingData);
        }
    }

    /**
     * Process manual reroute request.
     */
    public void processRerouteFlushRequest(MessageSender sender, String correlationId, FlowRerouteFlushRequest request) {
        Optional<Flow> flow = flowRepository.findById(request.getFlowId());
            FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(flow.orElse(null))
                    .correlationId(correlationId)
                    .affectedIsl(request.getAffectedIsls())
                    .effectivelyDown(request.isEffectivelyDown())
                    .reason(request.getReason())
                    .build();
        sender.emitManualRerouteFlushCommand(request.getFlowId(), flowThrottlingData);
    }

    /**
     * Process manual y-flow reroute request.
     */
    public void processRerouteRequest(MessageSender sender, String correlationId, YFlowRerouteRequest request) {
        Optional<YFlow> flow = yFlowRepository.findById(request.getYFlowId());
        FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(flow.orElse(null))
                .correlationId(correlationId)
                .affectedIsl(request.getAffectedIsls())
                .reason(request.getReason())
                .build();
        sender.emitManualRerouteCommand(request.getYFlowId(), flowThrottlingData);
    }

    /**
     * Process manual HA-flow reroute request.
     */
    public void processRerouteRequest(MessageSender sender, String correlationId, HaFlowRerouteRequest request) {
        Optional<HaFlow> haFlow = haFlowRepository.findById(request.getHaFlowId());
        FlowThrottlingData flowThrottlingData = getFlowThrottlingDataBuilder(haFlow.orElse(null))
                .correlationId(correlationId)
                .affectedIsl(request.getAffectedIsls())
                .effectivelyDown(request.isEffectivelyDown())
                .reason(request.getReason())
                .build();
        if (request.isManual()) {
            sender.emitManualRerouteCommand(request.getHaFlowId(), flowThrottlingData);
        } else {
            sender.emitRerouteCommand(request.getHaFlowId(), flowThrottlingData);
        }
    }

    /**
     * Handles request to update single switch flow status.
     */
    public void processSingleSwitchFlowStatusUpdate(SwitchStateChanged request) {
        transactionManager.doInTransaction(() -> {
            Collection<Flow> affectedFlows = flowRepository.findOneSwitchFlows(request.getSwitchId());
            FlowStatus newFlowStatus = request.getStatus() == SwitchStatus.ACTIVE ? FlowStatus.UP : FlowStatus.DOWN;
            String newFlowStatusInfo = request.getStatus() == SwitchStatus.ACTIVE
                    ? null : format("Switch %s is inactive", request.getSwitchId());
            FlowPathStatus newFlowPathStatus = request.getStatus() == SwitchStatus.ACTIVE
                    ? FlowPathStatus.ACTIVE : FlowPathStatus.INACTIVE;
            for (Flow flow : affectedFlows) {
                log.info("Updating flow and path statuses for flow {} to {}, {}", flow.getFlowId(), newFlowStatus,
                        newFlowPathStatus);
                flowDashboardLogger.onFlowStatusUpdate(flow.getFlowId(), newFlowStatus);
                flow.setStatus(newFlowStatus);
                flow.setStatusInfo(newFlowStatusInfo);
                flow.getForwardPath().setStatus(newFlowPathStatus);
                flow.getReversePath().setStatus(newFlowPathStatus);
            }
        });
    }

    private FlowThrottlingDataBuilder getFlowThrottlingDataBuilder(Flow flow) {
        return flow == null ? FlowThrottlingData.builder().flowType(FlowType.FLOW) :
                FlowThrottlingData.builder()
                        .priority(flow.getPriority())
                        .timeCreate(flow.getTimeCreate())
                        .pathComputationStrategy(flow.getPathComputationStrategy())
                        .bandwidth(flow.getBandwidth())
                        .flowType(FlowType.FLOW)
                        .strictBandwidth(flow.isStrictBandwidth());
    }

    private FlowThrottlingDataBuilder getFlowThrottlingDataBuilder(YFlow flow) {
        return flow == null ? FlowThrottlingData.builder().flowType(FlowType.Y_FLOW) :
                FlowThrottlingData.builder()
                        .priority(flow.getPriority())
                        .timeCreate(flow.getTimeCreate())
                        .pathComputationStrategy(flow.getPathComputationStrategy())
                        .bandwidth(flow.getMaximumBandwidth())
                        .strictBandwidth(flow.isStrictBandwidth())
                        .flowType(FlowType.Y_FLOW);
    }

    private FlowThrottlingDataBuilder getFlowThrottlingDataBuilder(HaFlow haFlow) {
        return haFlow == null ? FlowThrottlingData.builder().flowType(FlowType.HA_FLOW) :
                FlowThrottlingData.builder()
                        .priority(haFlow.getPriority())
                        .timeCreate(haFlow.getTimeCreate())
                        .pathComputationStrategy(haFlow.getPathComputationStrategy())
                        .bandwidth(haFlow.getMaximumBandwidth())
                        .strictBandwidth(haFlow.isStrictBandwidth())
                        .flowType(FlowType.HA_FLOW);
    }

    @Value
    private static class FlowWithAffectedPaths {
        Flow flow;
        List<FlowPath> affectedPaths = new ArrayList<>();
    }

    @Value
    private static class HaFlowWithAffectedPaths {
        HaFlow haFlow;
        List<FlowPath> affectedPaths = new ArrayList<>();
    }

    @Data
    private static class RerouteResult {
        Set<String> flowIdsForSwapPaths = new HashSet<>();
        Set<Flow> flowsForReroute = new HashSet<>();
        Set<String> yFlowIdsForSwapPaths = new HashSet<>();
        Set<YFlow> yFlowsForReroute = new HashSet<>();
        Set<String> haFlowIdsForSwapPaths = new HashSet<>();
        Set<HaFlow> haFlowsForReroute = new HashSet<>();
    }
}
