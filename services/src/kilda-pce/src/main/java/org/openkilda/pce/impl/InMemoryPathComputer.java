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
    public PathPair getPath(Flow flow, boolean reuseAllocatedFlowResources)
            throws UnroutableFlowException, RecoverableException {
        return getPath(availableNetworkFactory.getAvailableNetwork(flow, reuseAllocatedFlowResources), flow);
    }

    private PathPair getPath(AvailableNetwork network, Flow flow)
            throws UnroutableFlowException {

        if (flow.getSrcSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            log.info("No path computation for one-switch flow");
            return PathPair.builder()
                    .forward(new FlowPath(0, Collections.emptyList(), null))
                    .reverse(new FlowPath(0, Collections.emptyList(), null))
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

    private PathPair convertToPathPair(Pair<List<Edge>, List<Edge>> biPath) {
        long latency = 0L;
        List<FlowPath.Node> forwardNodes = new LinkedList<>();
        List<FlowPath.Node> reverseNodes = new LinkedList<>();

        int seqId = 0;
        List<Edge> forwardIsl = biPath.getLeft();
        for (Edge edge : forwardIsl) {
            latency += edge.getLatency();
            forwardNodes.add(FlowPath.Node.builder()
                    .switchId(edge.getSrcSwitch().getSwitchId())
                    .portNo(edge.getSrcPort())
                    .seqId(seqId++)
                    .segmentLatency(edge.getLatency())
                    .build());
            forwardNodes.add(FlowPath.Node.builder()
                    .switchId(edge.getDestSwitch().getSwitchId())
                    .portNo(edge.getDestPort())
                    .seqId(seqId++)
                    .build());
        }

        seqId = 0;
        List<Edge> reverseIsl = biPath.getRight();
        for (Edge edge : reverseIsl) {
            reverseNodes.add(FlowPath.Node.builder()
                    .switchId(edge.getSrcSwitch().getSwitchId())
                    .portNo(edge.getSrcPort())
                    .seqId(seqId++)
                    .segmentLatency(edge.getLatency())
                    .build());
            reverseNodes.add(FlowPath.Node.builder()
                    .switchId(edge.getDestSwitch().getSwitchId())
                    .portNo(edge.getDestPort())
                    .seqId(seqId++)
                    .build());
        }

        return PathPair.builder()
                .forward(new FlowPath(latency, forwardNodes, null))
                .reverse(new FlowPath(latency, reverseNodes, null))
                .build();
    }
}
