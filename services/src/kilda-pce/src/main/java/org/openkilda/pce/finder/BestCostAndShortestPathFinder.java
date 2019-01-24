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
import static java.util.Collections.emptyList;

import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.Node;
import org.openkilda.pce.model.WeightFunction;

import com.google.common.collect.Lists;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This algorithm is optimized for finding a bidirectional path between the start and end nodes. It uses elements of
 * depth first, breadth first, and shortest path (weight) algorithms.
 * <p/>
 * For neighboring switches, it assumes there is only one link between them. The network that is passed in should
 * reflect the links that should be traversed.
 * <p/>
 * Algorithm Notes: 1. forward = get path (src, dst) 2. reverse = get path (dst, src, hint=forward) 3 get path: 0.
 * allowedDepth = 35 √ . go breadth first (majority of switches probably have 5 hops or less) √ . add cost . compare
 * against bestCost (if greater, then return) . have we visited this node before? - if so, was it cheaper? Yes? return.
 * - No, remove the prior put. We and any downstream cost (BUT .. maybe we stopped somewhere and need to continue?
 * Probably could just replace the old one with the current one and add the children as unvisited? Oooh .. this could
 * cause a loop.. if there are negative costs - So, should probably track path to this point... . if node = target,
 * update bestCost, return . add each neighbor to the investigation list, where neighbor.outbound.dst != current node.
 */
@Slf4j
public class BestCostAndShortestPathFinder implements PathFinder {
    private final int allowedDepth;
    private final WeightFunction weightFunction;

    /**
     * Constructs the finder with the specified limit on path depth.
     *
     * @param allowedDepth the allowed depth for a potential path.
     * @param weightFunction  the edge weight computing function.
     */
    public BestCostAndShortestPathFinder(int allowedDepth, WeightFunction weightFunction) {
        this.allowedDepth = allowedDepth;
        this.weightFunction = weightFunction;
    }

    @Override
    public WeightFunction getWeightFunction() {
        return weightFunction;
    }

    @Override
    public Pair<List<Edge>, List<Edge>> findPathInNetwork(AvailableNetwork network,
                                                        SwitchId startSwitchId, SwitchId endSwitchId)
            throws UnroutableFlowException {
        Node start = network.getSwitch(startSwitchId);
        Node end = network.getSwitch(endSwitchId);
        if (start == null || end == null) {
            throw new UnroutableFlowException(format("Switch %s doesn't have links with enough bandwidth",
                    start == null ? startSwitchId : endSwitchId));
        }

        List<Edge> forwardPath = getPath(start, end);
        if (forwardPath.isEmpty()) {
            throw new UnroutableFlowException(format("Can't find a path from %s to %s", start, end));
        }

        List<Edge> reversePath = getReversePath(end, start, forwardPath);
        if (reversePath.isEmpty()) {
            throw new UnroutableFlowException(format("Can't find a reverse path from %s to %s. Forward path : %s",
                    end, start, StringUtils.join(forwardPath, ", ")));
        }

        return Pair.of(forwardPath, reversePath);
    }

    /**
     * Call this method to find a path from start to end (srcDpid to dstDpid), particularly if you have no idea if the
     * path exists or what the best path is.
     *
     * @return An ordered list that represents the path from start to end, or an empty list
     */
    private List<Edge> getPath(Node start, Node end) {
        long bestCost = Integer.MAX_VALUE; // Need to be long because it stores sum of ints.
        SearchNode bestPath = null;

        Deque<SearchNode> toVisit = new LinkedList<>(); // working list
        Map<Node, SearchNode> visited = new HashMap<>();

        toVisit.add(new SearchNode(start, allowedDepth, 0, emptyList()));

        while (!toVisit.isEmpty()) {
            SearchNode current = toVisit.pop();

            // Determine if this node is the destination node.
            if (current.dstSw.equals(end)) {
                // We found the destination
                if (current.parentCost < bestCost) {
                    // We found a best path. If we don't get here, then the entire graph will be
                    // searched until we run out of nodes or the depth is reached.
                    bestCost = current.parentCost;
                    bestPath = current;
                }
                // We found dest, no need to keep processing
                continue;
            }

            // Otherwise, if we've been here before, see if this path is better
            SearchNode prior = visited.get(current.dstSw);
            if (prior != null && current.parentCost >= prior.parentCost) {
                continue;
            }

            // Stop processing entirely if we've gone too far, or over bestCost
            if (current.allowedDepth <= 0 || current.parentCost > bestCost) {
                continue;
            }

            // Either this is the first time, or this is cheaper .. either way, this node should
            // be the one in the visited list
            visited.put(current.dstSw, current);


            // At this stage .. haven't found END, haven't gone too deep, and we are not over cost.
            // So, add the outbound isls.
            current.dstSw.getOutgoingLinks().stream()
                    .sorted(Comparator.comparing(edge -> edge.getDestSwitch().getSwitchId()))
                    .forEach(edge -> toVisit.add(current.addNode(edge)));
        }

        return (bestPath != null) ? bestPath.parentPath : new LinkedList<>();
    }

    /**
     * This is generally called after getPath() to find the path back.  The path back could be asymmetric, but this will
     * increase the odds that we return the symmetric path if it exists. The hint will be used to determine if it
     * exists.  If it does, then use it as the start bestCost and bestPath.  That should help speed things along.
     * <p/>
     * Whereas it's possible that could build up the SearchNodes for this path (if found) and put them into the visited
     * bucket, we'll start without that optimization and decide later whether adding it provides any efficiencies
     *
     * @param src a source switch to start searching reverse path.
     * @param dst a switch to which reverse path will be found.
     * @param forwardPath The path to use as a starting point. It can be in reverse order (we'll reverse it)
     * @return An ordered list that represents the path from start to end.
     */
    private List<Edge> getReversePath(Node src, Node dst, List<Edge> forwardPath) {
        // First, see if the first and last nodes match our start and end, or whether the list
        // needs to be reversed

        List<Edge> reversePath = Lists.reverse(forwardPath);
        reversePath = swapSrcDst(reversePath);

        log.debug("Reverse path is available from {} to {}", src.getSwitchId(), dst.getSwitchId());
        return reversePath;
    }

    /**
     * This helper function is used with getPath(hint) and will swap the src and dst of each Edge in the list.
     */
    private List<Edge> swapSrcDst(List<Edge> originalIsls) {
        return originalIsls.stream()
                .map(Edge::swap)
                .collect(Collectors.toList());
    }

    /**
     * This class facilitates the algorithm by collecting salient pieces of information that are necessary for tracking
     * search data per search node.
     */
    @Value
    private class SearchNode {
        final Node dstSw;
        final int allowedDepth;
        final long parentCost; // Need to be long because it stores sum of ints.
        final List<Edge> parentPath;
        // NB: We could consider tracking the child path as well .. to facilitate
        //     re-calc if we find a better parentPath.

        SearchNode addNode(Edge nextIsl) {
            List<Edge> newParentPath = new ArrayList<>(this.parentPath);
            newParentPath.add(nextIsl);

            long weight = parentCost + weightFunction.apply(nextIsl) + nextIsl.getDestSwitch().getCost();
            if (this.parentPath.isEmpty()) {
                weight += nextIsl.getSrcSwitch().getCost();
            }

            return new SearchNode(
                    nextIsl.getDestSwitch(),
                    allowedDepth - 1,
                    weight,
                    newParentPath);
        }
    }
}
