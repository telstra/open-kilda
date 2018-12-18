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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.PathFinder;
import org.openkilda.pce.model.Edge;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
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
    public PathPair getPath(Flow flow, boolean reuseAllocatedFlowResources)
            throws UnroutableFlowException, RecoverableException {
        return getPath(availableNetworkFactory.getAvailableNetwork(flow, reuseAllocatedFlowResources), flow);
    }

    private PathPair getPath(AvailableNetwork network, Flow flow)
            throws UnroutableFlowException {

        if (flow.getSrcSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            log.info("No path computation for one-switch flow");
            return PathPair.builder()
                    .forward(new FlowPath(0, 0L, Collections.emptyList(), null))
                    .reverse(new FlowPath(0, 0L, Collections.emptyList(), null))
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

        return convertToPathPair(biPath);
    }

    @Override
    public List<FlowPath> getNPaths(SwitchId srcSwitch, SwitchId dstSwitch, int count)
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
                .map(this::convertToFlowPath)
                .sorted(Comparator.comparing(FlowPath::getMinAvailableBandwidth)
                                  .thenComparing(FlowPath::getLatency))
                .limit(count)
                .collect(Collectors.toList());
    }

    private PathPair convertToPathPair(Pair<List<Edge>, List<Edge>> biPath) {
        FlowPath forward = convertToFlowPath(biPath.getLeft());
        FlowPath reverse = convertToFlowPath(biPath.getRight());

        // for backward compatibility
        reverse.setLatency(forward.getLatency());

        return PathPair.builder()
                .forward(forward)
                .reverse(reverse)
                .build();
    }

    private FlowPath convertToFlowPath(List<Edge> edges) {
        List<FlowPath.Node> nodes = new LinkedList<>();

        int seqId = 0;
        long latency = 0L;
        long minAvailableBandwidth = Long.MAX_VALUE;
        for (Edge edge : edges) {
            latency += edge.getLatency();
            minAvailableBandwidth = Math.min(minAvailableBandwidth, edge.getAvailableBandwidth());
            nodes.add(FlowPath.Node.builder()
                    .switchId(edge.getSrcSwitch().getSwitchId())
                    .portNo(edge.getSrcPort())
                    .seqId(seqId++)
                    .segmentLatency(edge.getLatency())
                    .build());
            nodes.add(FlowPath.Node.builder()
                    .switchId(edge.getDestSwitch().getSwitchId())
                    .portNo(edge.getDestPort())
                    .seqId(seqId++)
                    .build());
        }
        return new FlowPath(latency, minAvailableBandwidth, nodes, null);
    }
}
