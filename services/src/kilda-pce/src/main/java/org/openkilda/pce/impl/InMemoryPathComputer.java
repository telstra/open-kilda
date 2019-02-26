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

import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.AvailableNetworkFactory.BuildStrategy;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.PathFinder;
import org.openkilda.pce.model.Edge;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
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

    public InMemoryPathComputer(AvailableNetworkFactory availableNetworkFactory, PathFinder pathFinder) {
        this.availableNetworkFactory = availableNetworkFactory;
        this.pathFinder = pathFinder;
    }

    @Override
    public PathPair getPath(Flow flow, boolean reuseAllocatedFlowBandwidth)
            throws UnroutableFlowException, RecoverableException {
        return getPath(availableNetworkFactory.getAvailableNetwork(flow, reuseAllocatedFlowBandwidth), flow);
    }

    @Override
    public PathPair getPath(Flow flow, boolean reuseAllocatedFlowBandwidth, BuildStrategy buildStrategy)
            throws UnroutableFlowException, RecoverableException {
        return getPath(
                availableNetworkFactory.getAvailableNetwork(flow, reuseAllocatedFlowBandwidth, buildStrategy), flow);
    }

    private PathPair getPath(AvailableNetwork network, Flow flow) throws UnroutableFlowException {
        if (flow.getSrcSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            log.info("No path computation for one-switch flow");
            SwitchId singleSwitchId = flow.getSrcSwitch().getSwitchId();
            return PathPair.builder()
                    .forward(convertToPath(singleSwitchId, singleSwitchId, emptyList()))
                    .reverse(convertToPath(singleSwitchId, singleSwitchId, emptyList()))
                    .build();
        }

        Pair<List<Edge>, List<Edge>> biPath;
        try {
            network.reduceByWeight(pathFinder.getWeightFunction());

            biPath = pathFinder.findPathInNetwork(network, flow.getSrcSwitch().getSwitchId(),
                    flow.getDestSwitch().getSwitchId());
        } catch (UnroutableFlowException e) {
            String message = format("Failed to find path with requested bandwidth=%s: %s",
                    flow.isIgnoreBandwidth() ? " ignored" : flow.getBandwidth(), e.getMessage());
            throw new UnroutableFlowException(message, flow.getFlowId());
        }

        return convertToPathPair(flow.getSrcSwitch().getSwitchId(), flow.getDestSwitch().getSwitchId(), biPath);
    }

    @Override
    public List<Path> getNPaths(SwitchId srcSwitch, SwitchId dstSwitch, int count)
            throws RecoverableException, UnroutableFlowException {
        Flow flow = Flow.builder()
                .srcSwitch(Switch.builder().switchId(srcSwitch).build())
                .destSwitch(Switch.builder().switchId(dstSwitch).build())
                .ignoreBandwidth(false)
                .bandwidth(1) // to get ISLs with non zero available bandwidth
                .build();

        AvailableNetwork availableNetwork = availableNetworkFactory.getAvailableNetwork(flow, false);

        List<List<Edge>> paths =
                pathFinder.findNPathsBetweenSwitches(availableNetwork, srcSwitch, dstSwitch, count);
        return paths.stream()
                .map(pathEdges -> convertToPath(srcSwitch, dstSwitch, pathEdges))
                .sorted(Comparator.comparing(Path::getMinAvailableBandwidth)
                        .thenComparing(Path::getLatency))
                .limit(count)
                .collect(Collectors.toList());
    }

    private PathPair convertToPathPair(SwitchId srcSwitch, SwitchId dstSwitch, Pair<List<Edge>, List<Edge>> biPath) {
        return PathPair.builder()
                .forward(convertToPath(srcSwitch, dstSwitch, biPath.getLeft()))
                .reverse(convertToPath(dstSwitch, srcSwitch, biPath.getRight()))
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
