/* Copyright 2022 Telstra Open Source
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

package org.openkilda.pce.impl;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.AFFINITY_ISL_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.DIVERSITY_ISL_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.DIVERSITY_POP_ISL_COST;
import static org.openkilda.pce.model.PathWeight.Penalty.DIVERSITY_SWITCH_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.PROTECTED_DIVERSITY_ISL_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.PROTECTED_DIVERSITY_SWITCH_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.UNDER_MAINTENANCE;
import static org.openkilda.pce.model.PathWeight.Penalty.UNSTABLE;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.GetHaPathsResult;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.HaPath;
import org.openkilda.pce.HaPath.HaPathBuilder;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.FailReason;
import org.openkilda.pce.finder.FailReasonType;
import org.openkilda.pce.finder.FinderUtils;
import org.openkilda.pce.finder.PathFinder;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindOneDirectionPathResult;
import org.openkilda.pce.model.FindPathResult;
import org.openkilda.pce.model.PathWeight;
import org.openkilda.pce.model.PathWeight.Penalty;
import org.openkilda.pce.model.WeightFunction;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of {@link PathComputer} that operates over in-memory {@link AvailableNetwork}.
 * <p/>
 * The path finding algorithm is defined by provided {@link PathFinder}.
 */
@Slf4j
public class InMemoryPathComputer implements PathComputer {
    private final AvailableNetworkFactory availableNetworkFactory;
    private final PathFinder pathFinder;
    private final PathComputerConfig config;

    public InMemoryPathComputer(AvailableNetworkFactory availableNetworkFactory, PathFinder pathFinder,
                                PathComputerConfig config) {
        this.availableNetworkFactory = availableNetworkFactory;
        this.pathFinder = pathFinder;
        this.config = config;
    }

    @Override
    public GetPathsResult getPath(Flow flow, Collection<PathId> reusePathsResources, boolean isProtected)
            throws UnroutableFlowException, RecoverableException {
        AvailableNetwork network = availableNetworkFactory.getAvailableNetwork(flow, reusePathsResources);

        return getPath(network, new RequestedPath(flow), isProtected);
    }

    private GetPathsResult getPath(AvailableNetwork network, RequestedPath requestedPath, boolean isProtected)
            throws UnroutableFlowException {
        if (requestedPath.isOneSwitch()) {
            log.info("No path computation for one-switch path");
            SwitchId singleSwitchId = requestedPath.srcSwitchId;
            FindOneDirectionPathResult pathResult = FindOneDirectionPathResult.builder()
                    .foundPath(emptyList()).backUpPathComputationWayUsed(false).build();
            return GetPathsResult.builder()
                    .forward(convertToPath(singleSwitchId, singleSwitchId, pathResult))
                    .reverse(convertToPath(singleSwitchId, singleSwitchId, pathResult))
                    .backUpPathComputationWayUsed(false)
                    .build();
        }

        WeightFunction weightFunction = getWeightFunctionByStrategy(requestedPath.strategy, isProtected);
        FindPathResult findPathResult;
        try {
            findPathResult = findPathInNetwork(requestedPath, network, weightFunction, requestedPath.strategy);
        } catch (UnroutableFlowException e) {
            String bandwidthMessage = "";
            if (requestedPath.getBandwidth() > 0) {
                bandwidthMessage = format(", %s=%s", FailReasonType.MAX_BANDWIDTH,
                        requestedPath.isIgnoreBandwidth() ? " ignored" : requestedPath.getBandwidth());
            }
            throw new UnroutableFlowException(e.getMessage().concat(bandwidthMessage), e, requestedPath.getName(),
                    requestedPath.isIgnoreBandwidth());
        }

        return convertToGetPathsResult(requestedPath.getSrcSwitchId(), requestedPath.getDstSwitchId(), findPathResult);
    }

    @Override
    public GetHaPathsResult getHaPath(HaFlow haFlow, boolean isProtected)
            throws UnroutableFlowException, RecoverableException {
        AvailableNetwork network = availableNetworkFactory.getAvailableNetwork(haFlow, new ArrayList<>());

        if (haFlow.getHaSubFlows() == null || haFlow.getHaSubFlows().size() != 2) {
            throw new IllegalArgumentException(format("HA-flow %s must have 2 sub flows to find a path, but it "
                    + "has following sub flows: %s", haFlow.getHaFlowId(), haFlow.getHaSubFlows()));
        }

        List<GetPathsResult> paths = new ArrayList<>();
        for (HaSubFlow subFlow : haFlow.getHaSubFlows()) {
            paths.add(getPath(network, new RequestedPath(haFlow, subFlow), isProtected));
        }

        return GetHaPathsResult.builder()
                .forward(unitePathsToHaPath(paths.get(0).getForward(), paths.get(1).getForward(), true))
                .reverse(unitePathsToHaPath(paths.get(0).getReverse(), paths.get(1).getReverse(), false))
                .backUpPathComputationWayUsed(
                        paths.get(0).isBackUpPathComputationWayUsed() || paths.get(1).isBackUpPathComputationWayUsed())
                .build();
    }

    private HaPath unitePathsToHaPath(Path firstPath, Path secondPath, boolean forward) {
        HaPathBuilder haPath = HaPath.builder();
        if (forward) {
            if (!firstPath.getSrcSwitchId().equals(secondPath.getSrcSwitchId())) {
                throw new IllegalArgumentException(format("Forward HA sub paths must have equal source switch."
                        + "The first sub path: %s, the  second sub path: %s", firstPath, secondPath));
            }
            haPath.sharedSwitchId(firstPath.getSrcSwitchId());
            haPath.yPointSwitchId(findYPointForForwardPaths(firstPath, secondPath));
        } else {
            if (!firstPath.getDestSwitchId().equals(secondPath.getDestSwitchId())) {
                throw new IllegalArgumentException(format("Reverse HA sub paths must have equal destination switch."
                        + "The first sub path: %s, the  second sub path: %s", firstPath, secondPath));
            }
            haPath.sharedSwitchId(firstPath.getDestSwitchId());
            haPath.yPointSwitchId(findYPointForReversePaths(firstPath, secondPath));
        }

        haPath.subPaths(Lists.newArrayList(firstPath, secondPath));
        haPath.latency(Math.max(firstPath.getLatency(), secondPath.getLatency()));
        haPath.isBackupPath(firstPath.isBackupPath() || secondPath.isBackupPath());
        haPath.minAvailableBandwidth(Math.min(
                firstPath.getMinAvailableBandwidth(), secondPath.getMinAvailableBandwidth()));
        return haPath.build();
    }

    private static SwitchId findYPointForForwardPaths(Path firstPath, Path secondPath) {
        int id = findLastForwardSharedSegmentId(firstPath.getSegments(), secondPath.getSegments());
        if (id == -1) {
            return firstPath.getSrcSwitchId();
        } else {
            return firstPath.getSegments().get(id).getDestSwitchId();
        }
    }

    private static SwitchId findYPointForReversePaths(Path firstPath, Path secondPath) {
        int id = findFirstReverseSharedSegmentId(firstPath.getSegments(), secondPath.getSegments());
        if (id == firstPath.getSegments().size()) {
            return firstPath.getDestSwitchId();
        } else {
            return firstPath.getSegments().get(id).getSrcSwitchId();
        }
    }

    private static int findLastForwardSharedSegmentId(List<Segment> firstSegments, List<Segment> secondSegments) {
        int i = 0;
        while (i < firstSegments.size() && i < secondSegments.size()
                && firstSegments.get(i).areEndpointsEqual(secondSegments.get(i))) {
            i++;
        }
        return i - 1;
    }

    private static int findFirstReverseSharedSegmentId(List<Segment> firstSegments, List<Segment> secondSegments) {
        int i = firstSegments.size() - 1;
        int j = secondSegments.size() - 1;

        while (i >= 0 && j >= 0 && firstSegments.get(i).areEndpointsEqual(secondSegments.get(j))) {
            i--;
            j--;
        }
        return i + 1;
    }

    private FindPathResult findPathInNetwork(RequestedPath requestedPath, AvailableNetwork network,
                                             WeightFunction weightFunction,
                                             PathComputationStrategy strategy)
            throws UnroutableFlowException {

        if (MAX_LATENCY.equals(strategy)
                && (requestedPath.getMaxLatency() == null || requestedPath.getMaxLatency() == 0)) {
            strategy = LATENCY;
        }

        switch (strategy) {
            case COST:
            case COST_AND_AVAILABLE_BANDWIDTH:
                return pathFinder.findPathWithMinWeight(network, requestedPath.getSrcSwitchId(),
                        requestedPath.getDstSwitchId(), weightFunction);
            case LATENCY:
                long maxLatency = requestedPath.getMaxLatency() == null || requestedPath.getMaxLatency() == 0
                        ? Long.MAX_VALUE : requestedPath.getMaxLatency();
                long maxLatencyTier2 = requestedPath.getMaxLatencyTier2() == null
                        || requestedPath.getMaxLatencyTier2() == 0
                        ? Long.MAX_VALUE : requestedPath.getMaxLatencyTier2();
                if (maxLatencyTier2 < maxLatency) {
                    log.warn("Bad flow params found: maxLatencyTier2 ({}) should be greater than maxLatency ({}). "
                                    + "Put maxLatencyTier2 = maxLatency during path calculation.",
                            requestedPath.getMaxLatencyTier2(), requestedPath.getMaxLatency());
                    maxLatencyTier2 = maxLatency;
                }
                return pathFinder.findPathWithMinWeightAndLatencyLimits(network, requestedPath.getSrcSwitchId(),
                        requestedPath.getDstSwitchId(), weightFunction, maxLatency, maxLatencyTier2);
            case MAX_LATENCY:
                try {
                    return pathFinder.findPathWithWeightCloseToMaxWeight(network, requestedPath.getSrcSwitchId(),
                            requestedPath.getDstSwitchId(), weightFunction, requestedPath.getMaxLatency(),
                            Optional.ofNullable(requestedPath.getMaxLatencyTier2()).orElse(0L));
                } catch (UnroutableFlowException e) {
                    if (e.getFailReason() == null
                            || !e.getFailReason().containsKey(FailReasonType.MAX_WEIGHT_EXCEEDED)) {
                        throw e;
                    }
                    Long actualLatency = e.getFailReason().get(FailReasonType.MAX_WEIGHT_EXCEEDED).getWeight();
                    Map<FailReasonType, FailReason> reasons = e.getFailReason();
                    reasons.remove(FailReasonType.MAX_WEIGHT_EXCEEDED);
                    String failLatencyReason;
                    if (actualLatency == null) {
                        failLatencyReason = format("Requested path must have latency %sms or lower",
                                TimeUnit.NANOSECONDS.toMillis(requestedPath.getMaxLatency()));
                    } else {
                        failLatencyReason = format("Requested path must have latency %sms or lower, "
                                        + "but best path has latency %sms",
                                TimeUnit.NANOSECONDS.toMillis(requestedPath.getMaxLatency()),
                                TimeUnit.NANOSECONDS.toMillis(actualLatency));
                    }
                    reasons.put(FailReasonType.LATENCY_LIMIT, new FailReason(FailReasonType.LATENCY_LIMIT,
                            failLatencyReason));
                    String[] split = e.getMessage().split(FinderUtils.REASONS_KEYWORD);
                    throw new UnroutableFlowException(split[0] + FinderUtils.reasonsToString(reasons));

                }
            default:
                throw new UnsupportedOperationException(format("Unsupported strategy type %s", strategy));
        }
    }

    @Override
    public List<Path> getNPaths(SwitchId srcSwitchId, SwitchId dstSwitchId, int count,
                                FlowEncapsulationType flowEncapsulationType,
                                PathComputationStrategy pathComputationStrategy,
                                Duration maxLatency, Duration maxLatencyTier2)
            throws RecoverableException, UnroutableFlowException {
        final long maxLatencyNs = maxLatency != null ? maxLatency.toNanos() : 0;
        final long maxLatencyTier2Ns = maxLatencyTier2 != null ? maxLatencyTier2.toNanos() : 0;

        Flow flow = Flow.builder()
                .flowId("") // just any id, as not used.
                .srcSwitch(Switch.builder().switchId(srcSwitchId).build())
                .destSwitch(Switch.builder().switchId(dstSwitchId).build())
                .ignoreBandwidth(false)
                .encapsulationType(flowEncapsulationType)
                .bandwidth(1) // to get ISLs with non zero available bandwidth
                .maxLatency(maxLatencyNs)
                .maxLatencyTier2(maxLatencyTier2Ns)
                .build();

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        if (MAX_LATENCY.equals(pathComputationStrategy)
                && (flow.getMaxLatency() == null || flow.getMaxLatency() == 0)) {
            pathComputationStrategy = LATENCY;
        }

        List<FindOneDirectionPathResult> paths;
        switch (pathComputationStrategy) {
            case COST:
            case LATENCY:
            case COST_AND_AVAILABLE_BANDWIDTH:
                paths = pathFinder.findNPathsBetweenSwitches(availableNetwork, srcSwitchId, dstSwitchId, count,
                        getWeightFunctionByStrategy(pathComputationStrategy, false));
                break;
            case MAX_LATENCY:
                paths = pathFinder.findNPathsBetweenSwitches(availableNetwork, srcSwitchId, dstSwitchId, count,
                        getWeightFunctionByStrategy(pathComputationStrategy, false), maxLatencyNs, maxLatencyTier2Ns);
                break;
            default:
                throw new UnsupportedOperationException(format(
                        "Unsupported strategy type %s", pathComputationStrategy));
        }
        Comparator<Path> comparator;
        if (pathComputationStrategy == LATENCY || pathComputationStrategy == MAX_LATENCY) {
            comparator = Comparator.comparing(Path::getLatency)
                    .thenComparing(Comparator.comparing(Path::getMinAvailableBandwidth).reversed());
        } else {
            comparator = Comparator.comparing(Path::getMinAvailableBandwidth).reversed()
                    .thenComparing(Path::getLatency);
        }

        return paths.stream()
                .map(foundPathResult -> convertToPath(srcSwitchId, dstSwitchId, foundPathResult))
                .sorted(comparator)
                .limit(count)
                .collect(Collectors.toList());
    }

    @VisibleForTesting
    WeightFunction getWeightFunctionByStrategy(PathComputationStrategy strategy, boolean isProtected) {
        switch (strategy) {
            case COST:
                return this::weightByCost;
            case LATENCY:
            case MAX_LATENCY:
                return edge -> weightByLatency(edge, isProtected);
            case COST_AND_AVAILABLE_BANDWIDTH:
                return this::weightByCostAndAvailableBandwidth;
            default:
                throw new UnsupportedOperationException(format("Unsupported strategy type %s", strategy));
        }
    }

    private PathWeight weightByCost(Edge edge) {
        Map<Penalty, Long> penalties = new EnumMap<>(Penalty.class);

        if (edge.isUnderMaintenance()) {
            penalties.put(UNDER_MAINTENANCE, (long) config.getUnderMaintenanceCostRaise());
        }

        if (edge.isUnstable()) {
            penalties.put(UNSTABLE, (long) config.getUnstableCostRaise());
        }

        if (edge.getDiversityGroupUseCounter() > 0) {
            int value = edge.getDiversityGroupUseCounter() * config.getDiversityIslCost();
            penalties.put(DIVERSITY_ISL_LATENCY, (long) value);
        }

        if (edge.getDiversityGroupPerPopUseCounter() > 0) {
            int value = edge.getDiversityGroupPerPopUseCounter() * config.getDiversityPopIslCost();
            penalties.put(DIVERSITY_POP_ISL_COST, (long) value);
        }

        if (edge.getDestSwitch().getDiversityGroupUseCounter() > 0) {
            int value = edge.getDestSwitch().getDiversityGroupUseCounter() * config.getDiversitySwitchCost();
            penalties.put(DIVERSITY_SWITCH_LATENCY, (long) value);
        }

        if (edge.getAffinityGroupUseCounter() > 0) {
            long value = edge.getAffinityGroupUseCounter() * config.getAffinityIslCost();
            penalties.put(AFFINITY_ISL_LATENCY, value);
        }

        long cost = edge.getCost() == 0 ? config.getDefaultIslCost() : edge.getCost();
        return new PathWeight(cost, penalties);
    }

    private PathWeight weightByLatency(Edge edge, boolean isForProtectedPath) {
        Map<Penalty, Long> penalties = new EnumMap<>(Penalty.class);

        if (edge.isUnderMaintenance()) {
            penalties.put(UNDER_MAINTENANCE, config.getUnderMaintenanceLatencyRaise());
        }

        if (edge.isUnstable()) {
            penalties.put(UNSTABLE, config.getUnstableLatencyRaise());
        }

        if (edge.getDiversityGroupUseCounter() > 0) {
            long value = edge.getDiversityGroupUseCounter() * config.getDiversityIslLatency();
            if (isForProtectedPath) {
                penalties.put(PROTECTED_DIVERSITY_ISL_LATENCY, value);
            } else {
                penalties.put(DIVERSITY_ISL_LATENCY, value);
            }
        }

        if (edge.getDiversityGroupPerPopUseCounter() > 0) {
            int value = edge.getDiversityGroupPerPopUseCounter() * config.getDiversityPopIslCost();
            penalties.put(DIVERSITY_POP_ISL_COST, (long) value);
        }

        if (edge.getDestSwitch().getDiversityGroupUseCounter() > 0) {
            long value = edge.getDestSwitch().getDiversityGroupUseCounter() * config.getDiversitySwitchLatency();
            if (isForProtectedPath) {
                penalties.put(PROTECTED_DIVERSITY_SWITCH_LATENCY, value);
            } else {
                penalties.put(DIVERSITY_SWITCH_LATENCY, value);
            }
        }

        if (edge.getAffinityGroupUseCounter() > 0) {
            long value = edge.getAffinityGroupUseCounter() * config.getAffinityIslLatency();
            penalties.put(AFFINITY_ISL_LATENCY, value);
        }

        long edgeLatency = edge.getLatency() <= 0 ? config.getDefaultIslLatency() : edge.getLatency();
        return new PathWeight(edgeLatency, penalties);
    }

    private PathWeight weightByCostAndAvailableBandwidth(Edge edge) {
        Map<Penalty, Long> penalties = new EnumMap<>(Penalty.class);

        if (edge.isUnderMaintenance()) {
            penalties.put(UNDER_MAINTENANCE, (long) config.getUnderMaintenanceCostRaise());
        }

        if (edge.isUnstable()) {
            penalties.put(UNSTABLE, (long) config.getUnstableCostRaise());
        }

        if (edge.getDiversityGroupUseCounter() > 0) {
            int value = edge.getDiversityGroupUseCounter() * config.getDiversityIslCost();
            penalties.put(DIVERSITY_ISL_LATENCY, (long) value);
        }

        if (edge.getDiversityGroupPerPopUseCounter() > 0) {
            int value = edge.getDiversityGroupPerPopUseCounter() * config.getDiversityPopIslCost();
            penalties.put(DIVERSITY_POP_ISL_COST, (long) value);
        }

        if (edge.getDestSwitch().getDiversityGroupUseCounter() > 0) {
            int value = edge.getDestSwitch().getDiversityGroupUseCounter() * config.getDiversitySwitchCost();
            penalties.put(DIVERSITY_SWITCH_LATENCY, (long) value);
        }

        if (edge.getAffinityGroupUseCounter() > 0) {
            long value = edge.getAffinityGroupUseCounter() * config.getAffinityIslCost();
            penalties.put(AFFINITY_ISL_LATENCY, value);
        }

        long cost = edge.getCost() == 0 ? config.getDefaultIslCost() : edge.getCost();
        return new PathWeight(cost, penalties, edge.getAvailableBandwidth());
    }

    private GetPathsResult convertToGetPathsResult(
            SwitchId srcSwitchId, SwitchId dstSwitchId, FindPathResult findPathResult) {
        return GetPathsResult.builder()
                .forward(convertToPath(srcSwitchId, dstSwitchId, findPathResult.getFoundPath().getLeft(),
                        findPathResult.isBackUpPathComputationWayUsed()))
                .reverse(convertToPath(dstSwitchId, srcSwitchId, findPathResult.getFoundPath().getRight(),
                        findPathResult.isBackUpPathComputationWayUsed()))
                .backUpPathComputationWayUsed(findPathResult.isBackUpPathComputationWayUsed())
                .build();
    }

    private Path convertToPath(SwitchId srcSwitchId, SwitchId dstSwitchId, List<Edge> edges, boolean isBackupPath) {
        return convertToPath(srcSwitchId, dstSwitchId, new FindOneDirectionPathResult(edges, isBackupPath));
    }

    private Path convertToPath(SwitchId srcSwitchId, SwitchId dstSwitchId, FindOneDirectionPathResult pathResult) {
        List<Edge> edges = pathResult.getFoundPath();
        List<Segment> segments = new LinkedList<>();

        long latency = 0L;
        long minAvailableBandwidth = Long.MAX_VALUE;
        for (Edge edge : edges) {
            latency += edge.getLatency();
            minAvailableBandwidth = Math.min(minAvailableBandwidth, edge.getAvailableBandwidth());
            segments.add(convertToSegment(edge));
        }

        return Path.builder()
                .srcSwitchId(srcSwitchId)
                .destSwitchId(dstSwitchId)
                .segments(segments)
                .latency(latency)
                .minAvailableBandwidth(minAvailableBandwidth)
                .isBackupPath(pathResult.isBackUpPathComputationWayUsed())
                .build();
    }

    private Segment convertToSegment(Edge edge) {
        return Segment.builder()
                .srcSwitchId(edge.getSrcSwitch().getSwitchId())
                .srcPort(edge.getSrcPort())
                .destSwitchId(edge.getDestSwitch().getSwitchId())
                .destPort(edge.getDestPort())
                .latency(edge.getLatency())
                .build();
    }

    @Override
    public SwitchId getIntersectionPoint(SwitchId sharedSwitchId, FlowPath... flowPaths) {
        List<LinkedList<SwitchId>> paths = convertFlowPathsToSwitchLists(sharedSwitchId, flowPaths);

        Set<SwitchId> ypointCandidates = new HashSet<>();
        SwitchId ypoint = null;
        SwitchId tmpPoint = sharedSwitchId;

        while (tmpPoint != null) {
            tmpPoint = null;
            ypointCandidates.clear();

            for (LinkedList<SwitchId> path : paths) {
                if (!path.isEmpty()) {
                    ypointCandidates.add(path.poll());
                } else {
                    return ypoint;
                }
            }

            if (ypointCandidates.size() < 2) {
                tmpPoint = ypointCandidates.stream().findAny().orElse(null);
            }

            if (tmpPoint != null) {
                ypoint = tmpPoint;
            }
        }

        return ypoint;
    }

    @VisibleForTesting
    List<LinkedList<SwitchId>> convertFlowPathsToSwitchLists(SwitchId sharedSwitchId, FlowPath... flowPaths) {
        List<LinkedList<SwitchId>> paths = new ArrayList<>();

        for (FlowPath flowPath : flowPaths) {
            List<PathSegment> pathSegments = flowPath.getSegments();
            if (pathSegments == null || pathSegments.isEmpty()) {
                throw new IllegalArgumentException(format("The path '%s' has no path segments", flowPath.getPathId()));
            }

            LinkedList<SwitchId> path = new LinkedList<>();
            path.add(pathSegments.get(0).getSrcSwitchId());
            for (PathSegment pathSegment : pathSegments) {
                path.add(pathSegment.getDestSwitchId());
            }

            if (sharedSwitchId.equals(path.getLast())) {
                Collections.reverse(path);
            } else if (!sharedSwitchId.equals(path.getFirst())) {
                throw new IllegalArgumentException(
                        format("Shared switch '%s' is not an endpoint switch for path '%s'",
                                sharedSwitchId, flowPath.getPathId()));
            }

            paths.add(path);
        }

        return paths;
    }

    @Data
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static class RequestedPath {
        SwitchId srcSwitchId;
        SwitchId dstSwitchId;
        long bandwidth;
        boolean ignoreBandwidth;
        PathComputationStrategy strategy;
        Long maxLatency;
        Long maxLatencyTier2;
        String name;

        RequestedPath(Flow flow) {
            this(flow.getSrcSwitchId(), flow.getDestSwitchId(), flow.getBandwidth(), flow.isIgnoreBandwidth(),
                    flow.getPathComputationStrategy(), flow.getMaxLatency(), flow.getMaxLatencyTier2(),
                    flow.getFlowId());
        }

        RequestedPath(HaFlow haFlow, HaSubFlow subFlow) {
            this(haFlow.getSharedSwitchId(), subFlow.getEndpointSwitchId(), haFlow.getMaximumBandwidth(),
                    haFlow.isIgnoreBandwidth(), haFlow.getPathComputationStrategy(), haFlow.getMaxLatency(),
                    haFlow.getMaxLatencyTier2(), haFlow.getHaFlowId());
        }

        public boolean isOneSwitch() {
            return srcSwitchId.equals(dstSwitchId);
        }
    }
}
