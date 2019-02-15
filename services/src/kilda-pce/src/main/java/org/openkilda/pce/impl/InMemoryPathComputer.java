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
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.AvailableNetworkFactory.BuildStrategy;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.PathFinder;
import org.openkilda.pce.model.Edge;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.List;

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

    private PathPair getPath(AvailableNetwork network, Flow flow)
            throws UnroutableFlowException {

        if (flow.getSrcSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            log.info("No path computation for one-switch flow");
            return buildPathPair(flow, 0, emptyList(), 0, emptyList());
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

        return convertToPathPair(flow, biPath);
    }

    private PathPair convertToPathPair(Flow flow, Pair<List<Edge>, List<Edge>> biPath) {
        long forwardPathLatency = 0L;
        List<Path.Segment> forwardSegments = new LinkedList<>();
        for (Edge edge : biPath.getLeft()) {
            forwardPathLatency += edge.getLatency();
            forwardSegments.add(convertToSegment(edge));
        }

        long reversePathLatency = 0L;
        List<Path.Segment> reverseSegments = new LinkedList<>();
        for (Edge edge : biPath.getRight()) {
            reversePathLatency += edge.getLatency();
            reverseSegments.add(convertToSegment(edge));
        }

        return buildPathPair(flow, forwardPathLatency, forwardSegments, reversePathLatency, reverseSegments);
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

    private PathPair buildPathPair(Flow flow,
                                   long forwardPathLatency, List<Segment> forwardSegments,
                                   long reversePathLatency, List<Segment> reverseSegments) {
        return PathPair.builder()
                .forward(Path.builder()
                        .srcSwitchId(flow.getSrcSwitch().getSwitchId())
                        .srcPort(flow.getSrcPort())
                        .destSwitchId(flow.getDestSwitch().getSwitchId())
                        .destPort(flow.getDestPort())
                        .segments(forwardSegments)
                        .latency(forwardPathLatency)
                        .build())
                .reverse(Path.builder()
                        .srcSwitchId(flow.getDestSwitch().getSwitchId())
                        .srcPort(flow.getDestPort())
                        .destSwitchId(flow.getSrcSwitch().getSwitchId())
                        .destPort(flow.getSrcPort())
                        .segments(reverseSegments)
                        .latency(reversePathLatency)
                        .build())
                .build();
    }
}
