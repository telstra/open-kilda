/* Copyright 2018 Telstra Open Source
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

package org.openkilda.pce.finder;

import static java.lang.String.format;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.util.FibonacciHeap;
import org.jgrapht.util.FibonacciHeapNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of Dijkstra's shortest path finder algorithm using Fibonacci heap. It passes through all nodes
 * starting from source node and tries to find cheapest (with the lowest weight) path through the graph. If two paths
 * are found them the shortest one will be chosen.
 * The main difference of this implementation from {@link BestCostAndShortestPathFinder} that symmetry of forward and
 * reverse paths is guaranteed.
 */
@Slf4j
public class BestCostSymmetricPathFinder implements PathFinder {

    private final int defaultLinkCost;

    public BestCostSymmetricPathFinder(int defaultLinkCost) {
        this.defaultLinkCost = defaultLinkCost;
    }

    @Override
    public Pair<List<Isl>, List<Isl>> findPathInNetwork(AvailableNetwork network, SwitchId startSwitchId,
                                                        SwitchId endSwitchId) throws UnroutableFlowException {
        Switch srcSwitch = network.getSwitch(startSwitchId);
        Switch dstSwitch = network.getSwitch(endSwitchId);
        if (srcSwitch == null || dstSwitch == null) {
            throw new UnroutableFlowException(format("Switch %s doesn't have links with enough bandwidth",
                    srcSwitch == null ? startSwitchId : endSwitchId));
        }

        List<Isl> forward = getPath(srcSwitch, dstSwitch);
        List<Isl> reverse = backwardPass(forward);
        return Pair.of(forward, reverse);
    }

    private List<Isl> getPath(Switch srcSwitch, Switch dstSwitch) throws UnroutableFlowException {
        FibonacciHeap<Vertex> vertexPerWeight = new FibonacciHeap<>();
        Map<SwitchId, FibonacciHeapNode<Vertex>> seen = new HashMap<>();

        FibonacciHeapNode<Vertex> source = new FibonacciHeapNode<>(new Vertex(srcSwitch, null, null));
        vertexPerWeight.insert(source, 0d);
        seen.put(srcSwitch.getSwitchId(), source);

        while (!vertexPerWeight.isEmpty()) {
            FibonacciHeapNode<Vertex> processingNode = vertexPerWeight.removeMin();
            if (isNodeShouldBeSkipped(processingNode, seen, dstSwitch.getSwitchId())) {
                continue;
            }

            log.debug("Processing neighbours of node {}", processingNode.getData().dpid);
            for (Isl isl : processingNode.getData().outgoingEdges) {
                double weight = processingNode.getKey() + (double) getCostOrDefault(isl.getCost());
                log.debug("Processing neighbour {} weight {}", isl.getDestSwitch().getSwitchId(), weight);
                Switch neighbour = isl.getDestSwitch();

                if (seen.containsKey(neighbour.getSwitchId())) {
                    FibonacciHeapNode<Vertex> neighbourNode = seen.get(neighbour.getSwitchId());
                    relax(processingNode, neighbourNode, weight, isl, vertexPerWeight);
                } else {
                    FibonacciHeapNode<Vertex> neighbourNode =
                            new FibonacciHeapNode<>(new Vertex(neighbour, processingNode.getData(), isl));
                    seen.put(neighbour.getSwitchId(), neighbourNode);
                    vertexPerWeight.insert(neighbourNode, weight);
                }
            }
        }

        if (!seen.containsKey(dstSwitch.getSwitchId())) {
            throw new UnroutableFlowException(format("Can't find a path from %s to %s",
                    srcSwitch.getSwitchId(), dstSwitch.getSwitchId()));
        }

        Vertex target = seen.get(dstSwitch.getSwitchId()).getData();
        return buildPath(source.getData(), target);
    }

    /**
     * Check whether we already reached this node, update a path (predecessor) if weight and distance are better.
     */
    private void relax(FibonacciHeapNode<Vertex> processingNode, FibonacciHeapNode<Vertex> neighbourNode,
                       double weight, Isl islToNeighbour, FibonacciHeap<Vertex> vertexPerWeight) {
        int distance = processingNode.getData().distance + 1;
        if (weight < neighbourNode.getKey()) {
            log.debug("Found cheaper path to {}: was {}, now {}",
                    neighbourNode.getData().dpid, neighbourNode.getKey(), weight);

            vertexPerWeight.decreaseKey(neighbourNode, weight);
            neighbourNode.getData().updatePredecessor(processingNode.getData(), islToNeighbour);
        } else if (weight == neighbourNode.getKey() && neighbourNode.getData().isShorterThan(distance)) {
            log.debug("Found shorter path");
            neighbourNode.getData().updatePredecessor(processingNode.getData(), islToNeighbour);
        }
    }

    private boolean isNodeShouldBeSkipped(FibonacciHeapNode<Vertex> node,
                                          Map<SwitchId, FibonacciHeapNode<Vertex>> seen, SwitchId destination) {
        if (node.getData().dpid.equals(destination)) {
            return true;
        } else if (seen.containsKey(destination)) {
            // check if weight of node's path is bigger than weight of the path to dst node, if so - no sense
            // to continue searching, because total weight won't become cheaper.
            return node.getKey() > seen.get(destination).getKey();
        }
        return false;
    }

    private List<Isl> buildPath(Vertex source, Vertex destination) {
        Vertex node = destination;
        List<Isl> path = new ArrayList<>();
        while (node.predecessor != null && node.dpid.equals(source.dpid)) {
            path.add(node.linkToPredecessor);
            node = node.predecessor;
        }

        return Lists.reverse(path);
    }

    private List<Isl> backwardPass(List<Isl> forward) {
        List<Isl> reversePath = Lists.reverse(forward);
        return reversePath.stream()
                .map(isl -> isl.getSrcSwitch().matchIncomingLink(isl))
                .collect(Collectors.toList());
    }

    private int getCostOrDefault(int cost) {
        return cost != 0 ? cost : defaultLinkCost;
    }

    /**
     * Switch representation as a vertex that linked to another vertex and this relation might be updated if cheaper
     * predecessor is found.
     */
    private final class Vertex {
        private final SwitchId dpid;
        // linked predecessor, allows bind vertexes and then iterates through them in order to get a full path.
        private Vertex predecessor;
        private Isl linkToPredecessor;
        // total distance from source vertex.
        private int distance;
        private List<Isl> outgoingEdges;

        private Vertex(Switch sw, Vertex predecessor, Isl linkToPredecessor) {
            this.dpid = sw.getSwitchId();
            this.outgoingEdges = sw.getOutgoingLinks();
            this.predecessor = predecessor;
            this.linkToPredecessor = linkToPredecessor;

            if (predecessor != null) {
                this.distance = predecessor.distance + 1;
            }
        }

        private void updatePredecessor(Vertex predecessor, Isl linkToPredecessor) {
            this.predecessor = predecessor;
            this.linkToPredecessor = linkToPredecessor;
            this.distance = predecessor.distance + 1;
        }

        private boolean isShorterThan(int another) {
            return this.distance < another;
        }
    }
}
