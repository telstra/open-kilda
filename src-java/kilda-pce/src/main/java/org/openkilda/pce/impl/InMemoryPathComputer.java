/* Copyright 2017 Telstra Open Source
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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.PathFinder;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindPathResult;
import org.openkilda.pce.model.PathWeight;
import org.openkilda.pce.model.WeightFunction;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    public GetPathsResult getPath(Flow flow, Collection<PathId> reusePathsResources)
            throws UnroutableFlowException, RecoverableException {
        AvailableNetwork network = availableNetworkFactory.getAvailableNetwork(flow, reusePathsResources);

        return getPath(network, flow, flow.getPathComputationStrategy());
    }

    private GetPathsResult getPath(AvailableNetwork network, Flow flow, PathComputationStrategy strategy)
            throws UnroutableFlowException {
        if (flow.isOneSwitchFlow()) {
            log.info("No path computation for one-switch flow");
            SwitchId singleSwitchId = flow.getSrcSwitchId();
            return GetPathsResult.builder()
                    .forward(convertToPath(singleSwitchId, singleSwitchId, emptyList()))
                    .reverse(convertToPath(singleSwitchId, singleSwitchId, emptyList()))
                    .backUpPathComputationWayUsed(false)
                    .build();
        }

        WeightFunction weightFunction = getWeightFunctionByStrategy(strategy);
        FindPathResult findPathResult;
        try {
            network.reduceByWeight(weightFunction);

            findPathResult = findPathInNetwork(flow, network, weightFunction, strategy);
        } catch (UnroutableFlowException e) {
            String message = format("Failed to find path with requested bandwidth=%s: %s",
                    flow.isIgnoreBandwidth() ? " ignored" : flow.getBandwidth(), e.getMessage());
            throw new UnroutableFlowException(message, e, flow.getFlowId(), flow.isIgnoreBandwidth());
        }

        return convertToGetPathsResult(flow.getSrcSwitchId(), flow.getDestSwitchId(), findPathResult,
                strategy, flow.getPathComputationStrategy());
    }

    private FindPathResult findPathInNetwork(Flow flow, AvailableNetwork network,
                                             WeightFunction weightFunction,
                                             PathComputationStrategy strategy)
            throws UnroutableFlowException {

        if (MAX_LATENCY.equals(strategy)
                && (flow.getMaxLatency() == null || flow.getMaxLatency() == 0)) {
            strategy = LATENCY;
        }

        switch (strategy) {
            case COST:
            case COST_AND_AVAILABLE_BANDWIDTH:
                return pathFinder.findPathWithMinWeight(network, flow.getSrcSwitchId(),
                        flow.getDestSwitchId(), weightFunction);
            case LATENCY:
                long maxLatency = flow.getMaxLatency() == null || flow.getMaxLatency() == 0
                        ? Long.MAX_VALUE : flow.getMaxLatency();
                long maxLatencyTier2 = flow.getMaxLatencyTier2() == null || flow.getMaxLatencyTier2() == 0
                        ? Long.MAX_VALUE : flow.getMaxLatencyTier2();
                if (maxLatencyTier2 < maxLatency) {
                    log.warn("Bad flow params found: maxLatencyTier2 ({}) should be greater than maxLatency ({}). "
                                    + "Put maxLatencyTier2 = maxLatency during path calculation.",
                            flow.getMaxLatencyTier2(), flow.getMaxLatency());
                    maxLatencyTier2 = maxLatency;
                }
                return pathFinder.findPathWithMinWeightAndLatencyLimits(network, flow.getSrcSwitchId(),
                        flow.getDestSwitchId(), weightFunction, maxLatency, maxLatencyTier2);
            case MAX_LATENCY:
                return pathFinder.findPathWithWeightCloseToMaxWeight(network, flow.getSrcSwitchId(),
                        flow.getDestSwitchId(), weightFunction, flow.getMaxLatency(),
                        Optional.ofNullable(flow.getMaxLatencyTier2()).orElse(0L));
            default:
                throw new UnsupportedOperationException(String.format("Unsupported strategy type %s", strategy));
        }
    }

    @Override
    public List<Path> getNPaths(SwitchId srcSwitchId, SwitchId dstSwitchId, int count,
                                FlowEncapsulationType flowEncapsulationType,
                                PathComputationStrategy pathComputationStrategy, Long maxLatency, Long maxLatencyTier2)
            throws RecoverableException, UnroutableFlowException {
        Flow flow = Flow.builder()
                .flowId("") // just any id, as not used.
                .srcSwitch(Switch.builder().switchId(srcSwitchId).build())
                .destSwitch(Switch.builder().switchId(dstSwitchId).build())
                .ignoreBandwidth(false)
                .encapsulationType(flowEncapsulationType)
                .bandwidth(1) // to get ISLs with non zero available bandwidth
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2)
                .build();

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, Collections.emptyList());

        if (MAX_LATENCY.equals(pathComputationStrategy)
                && (flow.getMaxLatency() == null || flow.getMaxLatency() == 0)) {
            pathComputationStrategy = LATENCY;
        }

        List<List<Edge>> paths;
        switch (pathComputationStrategy) {
            case COST:
            case LATENCY:
            case COST_AND_AVAILABLE_BANDWIDTH:
                paths = pathFinder.findNPathsBetweenSwitches(availableNetwork, srcSwitchId, dstSwitchId, count,
                        getWeightFunctionByStrategy(pathComputationStrategy));
                break;
            case MAX_LATENCY:
                paths = pathFinder.findNPathsBetweenSwitches(availableNetwork, srcSwitchId, dstSwitchId, count,
                        getWeightFunctionByStrategy(pathComputationStrategy),
                        Optional.ofNullable(maxLatency).orElse(0L),
                        Optional.ofNullable(maxLatencyTier2).orElse(0L));
                break;
            default:
                throw new UnsupportedOperationException(String.format(
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
                .map(edges -> convertToPath(srcSwitchId, dstSwitchId, edges))
                .sorted(comparator)
                .limit(count)
                .collect(Collectors.toList());
    }

    private WeightFunction getWeightFunctionByStrategy(PathComputationStrategy strategy) {
        switch (strategy) {
            case COST:
                return this::weightByCost;
            case LATENCY:
            case MAX_LATENCY:
                return this::weightByLatency;
            case COST_AND_AVAILABLE_BANDWIDTH:
                return this::weightByCostAndAvailableBandwidth;
            default:
                throw new UnsupportedOperationException(String.format("Unsupported strategy type %s", strategy));
        }
    }

    private PathWeight weightByCost(Edge edge) {
        long total = edge.getCost() == 0 ? config.getDefaultIslCost() : edge.getCost();
        if (edge.isUnderMaintenance()) {
            total += config.getUnderMaintenanceCostRaise();
        }
        if (edge.isUnstable()) {
            total += config.getUnstableCostRaise();
        }
        total += edge.getDiversityGroupUseCounter() * config.getDiversityIslCost()
                + edge.getDiversityGroupPerPopUseCounter() * config.getDiversityPopIslCost()
                + edge.getDestSwitch().getDiversityGroupUseCounter() * config.getDiversitySwitchCost();
        return new PathWeight(total);
    }

    private PathWeight weightByLatency(Edge edge) {
        long total = edge.getLatency() <= 0 ? config.getDefaultIslLatency() : edge.getLatency();
        if (edge.isUnderMaintenance()) {
            total += config.getUnderMaintenanceLatencyRaise();
        }
        if (edge.isUnstable()) {
            total += config.getUnstableLatencyRaise();
        }
        total += edge.getDiversityGroupUseCounter() * config.getDiversityIslLatency()
                + edge.getDiversityGroupPerPopUseCounter() * config.getDiversityPopIslCost()
                + edge.getDestSwitch().getDiversityGroupUseCounter() * config.getDiversitySwitchLatency();
        return new PathWeight(total);
    }

    private PathWeight weightByCostAndAvailableBandwidth(Edge edge) {
        long total = edge.getCost() == 0 ? config.getDefaultIslCost() : edge.getCost();
        if (edge.isUnderMaintenance()) {
            total += config.getUnderMaintenanceCostRaise();
        }
        if (edge.isUnstable()) {
            total += config.getUnstableCostRaise();
        }
        total += edge.getDiversityGroupUseCounter() * config.getDiversityIslCost()
                + edge.getDiversityGroupPerPopUseCounter() * config.getDiversityPopIslCost()
                + edge.getDestSwitch().getDiversityGroupUseCounter() * config.getDiversitySwitchCost();
        return new PathWeight(total, edge.getAvailableBandwidth());
    }

    private GetPathsResult convertToGetPathsResult(
            SwitchId srcSwitchId, SwitchId dstSwitchId, FindPathResult findPathResult,
            PathComputationStrategy strategy, PathComputationStrategy originalStrategy) {
        return GetPathsResult.builder()
                .forward(convertToPath(srcSwitchId, dstSwitchId, findPathResult.getFoundPath().getLeft()))
                .reverse(convertToPath(dstSwitchId, srcSwitchId, findPathResult.getFoundPath().getRight()))
                .backUpPathComputationWayUsed(findPathResult.isBackUpPathComputationWayUsed()
                        || !Objects.equals(originalStrategy, strategy))
                .build();
    }

    private Path convertToPath(SwitchId srcSwitchId, SwitchId dstSwitchId, List<Edge> edges) {
        List<Path.Segment> segments = new LinkedList<>();

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
                .build();
    }

    private Path.Segment convertToSegment(Edge edge) {
        return Path.Segment.builder()
                .srcSwitchId(edge.getSrcSwitch().getSwitchId())
                .srcPort(edge.getSrcPort())
                .destSwitchId(edge.getDestSwitch().getSwitchId())
                .destPort(edge.getDestPort())
                .latency(edge.getLatency())
                .build();
    }
}
