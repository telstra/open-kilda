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

package org.openkilda.pce.impl;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

import org.openkilda.model.SwitchId;
import org.openkilda.pce.impl.model.Edge;
import org.openkilda.pce.impl.model.Node;

import com.google.common.collect.Lists;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final int defaultLinkCost;

    /**
     * Constructs the finder with the specified limit on path depth.
     *
     * @param allowedDepth    the allowed depth for a potential path.
     * @param defaultLinkCost the default cost for links if not defined.
     */
    public BestCostAndShortestPathFinder(int allowedDepth, int defaultLinkCost) {
        this.allowedDepth = allowedDepth;
        this.defaultLinkCost = defaultLinkCost;
    }

    @Override
    public Pair<List<Edge>, List<Edge>> findPathInNetwork(AvailableNetwork network,
                                                        SwitchId startSwitchId, SwitchId endSwitchId)
            throws SwitchNotFoundException {
        Node start = network.getSwitchNode(startSwitchId);
        if (start == null) {
            throw new SwitchNotFoundException(
                    format("SOURCE node doesn't exist. It isn't in the AVAILABLE network: %s", startSwitchId));
        }

        Node end = network.getSwitchNode(endSwitchId);
        if (end == null) {
            throw new SwitchNotFoundException(
                    format("DESTINATION node doesn't exist. It isn't in the AVAILABLE network: %s", endSwitchId));
        }

        List<Edge> forwardPath = getPath(network, startSwitchId, endSwitchId, Integer.MAX_VALUE);
        List<Edge> reversePath = getPath(network, endSwitchId, startSwitchId, Integer.MAX_VALUE, forwardPath);
        return Pair.of(forwardPath, reversePath);
    }

    /**
     * Call this method to find a path from start to end (srcDpid to dstDpid), particularly if you have no idea if the
     * path exists or what the best path is.
     *
     * @return An ordered list that represents the path from start to end, or an empty list
     */
    private List<Edge> getPath(AvailableNetwork network,
                              SwitchId startSwitchId, SwitchId endSwitchId, long maximumCost) {
        long bestCost = maximumCost; // Need to be long because it stores sum of ints.
        SearchNode bestPath = null;

        Deque<SearchNode> toVisit = new LinkedList<>(); // working list
        Map<SwitchId, SearchNode> visited = new HashMap<>();

        toVisit.add(new SearchNode(startSwitchId, allowedDepth, 0, emptyList()));

        while (!toVisit.isEmpty()) {
            SearchNode current = toVisit.pop();

            // Determine if this node is the destination node.
            if (current.dstSw.equals(endSwitchId)) {
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
            for (Edge edge : network.getSwitchNode(current.dstSw).getOutboundEdges()) {
                toVisit.add(current.addNode(edge));
            }
        }

        return (bestPath != null) ? bestPath.parentPath : emptyList();
    }

    /**
     * This is generally called after getPath() to find the path back.  The path back could be asymmetric, but this will
     * increase the odds that we return the symmetric path if it exists. The hint will be used to determine if it
     * exists.  If it does, then use it as the start bestCost and bestPath.  That should help speed things along.
     * <p/>
     * Whereas it's possible that could build up the SearchNodes for this path (if found) and put them into the visited
     * bucket, we'll start without that optimization and decide later whether adding it provides any efficiencies
     *
     * @param hint The path to use as a starting point. It can be in reverse order (we'll reverse it)
     * @return An ordered list that represents the path from start to end.
     */
    private List<Edge> getPath(AvailableNetwork network,
                               SwitchId startSwitchId, SwitchId endSwitchId, long maximumCost, List<Edge> hint) {
        long hintedReverseCost = maximumCost; // Need to be long because it stores sum of ints.
        SearchNode hintedReversePath = null;

        // First, see if the first and last nodes match our start and end, or whether the list
        // needs to be reversed
        if (hint != null && !hint.isEmpty()) {
            SwitchId from = hint.get(0).getSrcSwitch();
            SwitchId to = hint.get(hint.size() - 1).getDestSwitch();
            if (startSwitchId.equals(to) && endSwitchId.equals(from)) {
                log.trace("getPath w/ Hint: Reversing the path from {}->{} to {}->{}", from, to, to, from);
                // looks like hint wasn't reversed .. revers the order, and swap src/dst.
                hint = swapSrcDst(Lists.reverse(hint));
                // re-run from and to .. it should now match.
                from = hint.get(0).getSrcSwitch();
                to = hint.get(hint.size() - 1).getDestSwitch();
            }

            // If the start/end equals from/to, then confirm the path and see if we can use it.
            if (startSwitchId.equals(from) && endSwitchId.equals(to)) {
                log.trace("getPath w/ Hint: hint matches start/ed {}->{}", from, to);
                // need to validate that the path exists .. and if so .. set bestCost and bestPath
                SearchNode best = confirmIsls(network, hint);
                if (best != null) {
                    log.debug("getPath w/ Hint: the hint path EXISTS for {}->{}", from, to);
                    hintedReverseCost = best.parentCost;
                    hintedReversePath = best;
                } else {
                    log.info("getPath w/ Hint: the hint path DOES NOT EXIST for {}->{}, will find new path",
                            from, to);
                }
            }
        }

        List<Edge> altReservePath = getPath(network, startSwitchId, endSwitchId, hintedReverseCost);
        return altReservePath.isEmpty() && hintedReversePath != null ? hintedReversePath.parentPath : altReservePath;
    }

    /**
     * This helper function is used with getPath(hint) and will swap the src and dst of each edge in the list.
     */
    private List<Edge> swapSrcDst(List<Edge> originalEdges) {
        return originalEdges.stream()
                .map(Edge::swap)
                .collect(Collectors.toList());
    }

    /**
     * This helper function is used with getPath(hint) to confirm the hint path exists.
     */
    private SearchNode confirmIsls(AvailableNetwork network, List<Edge> srcEdges) {
        long totalCost = 0;
        LinkedList<Edge> confirmedEdges = new LinkedList<>();
        boolean validPath = true;

        for (Edge srcEdge : srcEdges) {
            Node srcSwitch = network.getSwitchNode(srcEdge.getSrcSwitch());
            if (srcSwitch == null) {
                throw new IllegalStateException(
                        format("confirmIsls: Found a null switch during getPath(hint): %s", srcEdge.getSrcSwitch()));
            }

            Set<Edge> pathsToDst = srcSwitch.getOutboundEdges().stream()
                    .filter(edge -> edge.getDestSwitch().equals(srcEdge.getDestSwitch()))
                    .collect(toSet());
            if (pathsToDst.isEmpty()) {
                log.debug("No ISLS from {} to {}", srcEdge.getSrcSwitch(), srcEdge.getDestSwitch());
            }

            boolean foundThisOne = false;
            for (Edge orig : pathsToDst) {
                if (srcEdge.equals(orig)) {
                    foundThisOne = true;
                    confirmedEdges.add(orig);
                    totalCost += getCost(orig);
                    break; // stop looking, we found the isl
                }
            }
            if (!foundThisOne) {
                validPath = false;
                break; // found an isl that doesn't exist, stop looking for others
            }
        }

        if (validPath) {
            return new SearchNode(
                    confirmedEdges.peekLast().getDestSwitch(),
                    this.allowedDepth - confirmedEdges.size(),
                    totalCost,
                    confirmedEdges);
        }
        return null;
    }

    private int getCost(Edge isl) {
        if (isl.getCost() == 0) {
            log.warn("Found ZERO COST ISL: {}", isl);
            return defaultLinkCost;
        }

        return isl.getCost();
    }

    /**
     * This class facilitates the algorithm by collecting salient pieces of information that are necessary for tracking
     * search data per search node.
     */
    @Value
    private class SearchNode {
        final SwitchId dstSw;
        final int allowedDepth;
        final long parentCost; // Need to be long because it stores sum of ints.
        final List<Edge> parentPath;
        // NB: We could consider tracking the child path as well .. to facilitate
        //     re-calc if we find a better parentPath.

        SearchNode addNode(Edge nextEdge) {
            List<Edge> newParentPath = new ArrayList<>(this.parentPath);
            newParentPath.add(nextEdge);

            return new SearchNode(
                    nextEdge.getDestSwitch(),
                    allowedDepth - 1,
                    parentCost + getCost(nextEdge),
                    newParentPath);
        }
    }
}
