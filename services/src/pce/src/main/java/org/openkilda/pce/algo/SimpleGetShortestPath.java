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

package org.openkilda.pce.algo;

import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.model.SimpleIsl;
import org.openkilda.pce.model.SimpleSwitch;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This algorithm is optimized for finding a bidirectional path between the start and end nodes.
 * It uses elements of depth first, breadth first, and shortest path (weight) algorithms.
 * <p/>
 * For neighboring switches, it assumes there is only one link between them. The network that is
 * passed in should reflect the links that should be traversed.
 * <p/>
 * Algorithm Notes:
 * 1. forward = get path (src, dst)
 * 2. reverse = get path (dst, src, hint=forward)
 * 3 get path:
 *      0. allowedDepth = 35
 *      √ . go breadth first (majority of switches probably have 5 hops or less)
 *      √ . add cost
 *      . compare against bestCost (if greater, then return)
 *      . have we visited this node before?
 *          - if so, was it cheaper? Yes? return.
 *          - No, remove the prior put. We
 *        and any downstream cost (BUT .. maybe we stopped somewhere and need to continue?
 *        Probably could just replace the old one with the current one and add the children as unvisited?
 *        Oooh .. this could cause a loop.. if there are negative costs
 *          - So, should probably track path to this point...
 *      . if node = target, update bestCost, return
 *      . add each neighbor to the investigation list, where neighbor.outbound.dst != current node.
 */
public class SimpleGetShortestPath {

    private static final Logger logger = LoggerFactory.getLogger(SimpleGetShortestPath.class);

    private AvailableNetwork network;
    private SimpleSwitch start;
    private SimpleSwitch end;
    private int allowedDepth;
    private long bestCost = Integer.MAX_VALUE; // Need to be long because it stores sum of ints.
    private SearchNode bestPath = null;

    public SimpleGetShortestPath(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int allowedDepth) {
        this.network = network;
        this.start = network.getSwitches().get(srcDpid);
        this.end = network.getSwitches().get(dstDpid);
        this.allowedDepth = allowedDepth;
        if (this.start == null) {
            logger.warn("SOURCE node doesn't exist. It isn't in the AVAILABLE network: {}", srcDpid);
        }
        if (this.end == null) {
            logger.warn("DESTINATION node doesn't exist. It isn't in the AVAILABLE network: {}", dstDpid);
        }
    }


    /**
     * Call this method to find a path from start to end (srcDpid to dstDpid), particularly if you
     * have no idea if the path exists or what the best path is.
     *
     * @return An ordered list that represents the path from start to end, or an empty list
     */
    public LinkedList<SimpleIsl> getPath() {
        HashMap<SimpleSwitch, SearchNode> toVisitLookup = new HashMap<>();  // constant lookup
        LinkedList<SearchNode> toVisit = new LinkedList<>();                // working list
        HashMap<SimpleSwitch, SearchNode> visited = new HashMap<>();

        if (start != null && end != null) {
            toVisitLookup.put(start, new SearchNode(allowedDepth, 0, start));
            toVisit.add(toVisitLookup.get(start));
        }

        while (toVisit.size() > 0) {
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
            if (prior != null) {
                // We've already visited this one .. keep going only if this is cheaper
                if (current.parentCost >= prior.parentCost) {
                    continue;
                }
            }
            // Either this is the first time, or this is cheaper .. either way, this node should
            // be the one in the visited list
            visited.put(current.dstSw, current);

            // Stop processing entirely if we've gone too far, or over bestCost
            if (current.allowedDepth < 0 || current.parentCost > bestCost) {
                continue;
            }

            // At this stage .. haven't found END, haven't gone too deep, and we are not over cost.
            // So, add the outbound isls.
            for (Set<SimpleIsl> isls: current.dstSw.outbound.values()) {
                for (SimpleIsl isl : isls) {
                    toVisit.add(current.addNode(isl));
                }
            }
        }

        return (bestPath != null) ? bestPath.parentPath : new LinkedList<>();
    }


    /**
     * This is generally called after getPath() to find the path back.  The path back could be
     * asymmetric, but this will increase the odds that we return the symmetric path if it exists.
     * The hint will be used to determine if it exists.  If it does, then use it as the start
     * bestCost and bestPath.  That should help speed things along.
     * <p/>
     * Whereas it's possible that could build up the SearchNodes for this path (if found) and put
     * them into the visited bucket, we'll start without that optimization and decide later whether
     * adding it provides any efficiencies
     *
     * @param hint The path to use as a starting point. It can be in reverse order (we'll reverse it)
     * @return An ordered list that represents the path from start to end.
     */
    public LinkedList<SimpleIsl> getPath(List<SimpleIsl> hint) {
        // First, see if the first and last nodes match our start and end, or whether the list
        // needs to be reversed
        if (hint != null && hint.size() > 0) {
            SimpleSwitch from = network.getSimpleSwitch(hint.get(0).getSrcDpid());
            SimpleSwitch to = network.getSimpleSwitch(hint.get(hint.size() - 1).getDstDpid());
            if (start.equals(to) && end.equals(from)) {
                logger.trace("getPath w/ Hint: Reversing the path from {}->{} to {}->{}", from, to, to, from);
                // looks like hint wasn't reversed .. revers the order, and swap src/dst.
                hint = swapSrcDst(Lists.reverse(hint));
                // re-run from and to .. it should now match.
                from = network.getSimpleSwitch(hint.get(0).getSrcDpid());
                to = network.getSimpleSwitch(hint.get(hint.size() - 1).getDstDpid());
            }

            // If the start/end equals from/to, then confirm the path and see if we can use it.
            if (start.equals(from) && end.equals(to)) {
                logger.trace("getPath w/ Hint: hint matches start/ed {}->{}", from, to);
                // need to validate that the path exists .. and if so .. set bestCost and bestPath
                SearchNode best = confirmIsls(hint);
                if (best != null) {
                    logger.debug("getPath w/ Hint: the hint path EXISTS for {}->{}", from, to);
                    bestCost = best.parentCost;
                    bestPath = best;
                } else {
                    logger.info("getPath w/ Hint: the hint path DOES NOT EXIST for {}->{}, will find new path",
                            from, to);
                }
            }
        }
        return getPath();
    }

    /**
     * This helper function is used with getPath(hint) and will swap the src and dst of each isl in the list.
     */
    private List<SimpleIsl> swapSrcDst(List<SimpleIsl> originalIsls) {
        List<SimpleIsl> mirrorIsls = new ArrayList<>();
        for (SimpleIsl original : originalIsls) {
            // this swaps the src and dst fields
            mirrorIsls.add(new SimpleIsl(original.getDstDpid(), original.getSrcDpid(), original.getDstPort(),
                    original.getSrcPort(), original.getCost(), original.getLatency()));
        }
        return mirrorIsls;
    }

    /**
     * Return other if it exists, Empty Set otherwise.
     *
     * @param other called from confirmIsls .. ensure we don't return null for dst switch.
     * @return other if it exists, Empty Set otherwise.
     */
    private static final Set<SimpleIsl> safeSet(Set<SimpleIsl> other) {
        return other == null ? Collections.EMPTY_SET : other;
    }

    /**
     * This helper function is used with getPath(hint) to confirm the hint path exists.
     */
    private SearchNode confirmIsls(List<SimpleIsl> srcIsls) {
        int totalCost = 0;
        LinkedList<SimpleIsl> confirmedIsls = new LinkedList<>();

        boolean validPath = true;
        for (SimpleIsl i : srcIsls) {
            boolean foundThisOne = false;
            SimpleSwitch srcSwitch =  network.getSimpleSwitch(i.getSrcDpid());
            if (srcSwitch != null) {
                Set<SimpleIsl> pathsToDst = safeSet(srcSwitch.outbound.get(i.getDstDpid()));
                if (pathsToDst.equals(Collections.EMPTY_SET)) {
                    logger.debug("No ISLS from {} to {}", i.getSrcDpid(), i.getDstDpid());
                }
                for (SimpleIsl orig : pathsToDst) {
                    if (i.equals(orig)) {
                        foundThisOne = true;
                        confirmedIsls.add(orig);
                        totalCost += orig.getCost();
                        break; // stop looking, we found the isl
                    }
                }
            } else {
                logger.warn("confirmIsls: Found a null switch during getPath(hint): {}", i.getSrcDpid());
            }
            if (!foundThisOne) {
                validPath = false;
                break; // found an isl that doesn't exist, stop looking for others
            }
        }

        if (validPath) {
            return new SearchNode(this.allowedDepth - confirmedIsls.size(), totalCost,
                    network.getSimpleSwitch(confirmedIsls.peekLast().getDstDpid()), confirmedIsls);
        }
        return null;
    }


    /**
     * This class facilitates the algorithm by collecting salient pieces of information that are
     * necessary for tracking search data per search node.
     */
    private class SearchNode implements Cloneable {
        SimpleSwitch dstSw;
        int allowedDepth;
        long parentCost; // Need to be long because it stores sum of ints.
        LinkedList<SimpleIsl> parentPath;
        // NB: We could consider tracking the child path as well .. to facilitate
        //     re-calc if we find a better parentPath.

        public SearchNode(int allowedDepth, long parentCost, SimpleSwitch dstSw) {
            this(allowedDepth, parentCost, dstSw, new LinkedList<SimpleIsl>());
        }

        public SearchNode(int allowedDepth, long parentCost, SimpleSwitch dstSw, LinkedList<SimpleIsl> parentPath) {
            this.dstSw = dstSw;
            this.allowedDepth = allowedDepth;
            this.parentCost = parentCost;
            this.parentPath = parentPath;
        }

        public SearchNode addNode(SimpleIsl nextIsl) {
            SearchNode newNode = this.clone();
            newNode.parentPath.add(nextIsl);
            newNode.dstSw = network.getSimpleSwitch(nextIsl.getDstDpid());
            newNode.allowedDepth--;
            newNode.parentCost += nextIsl.getCost();
            return newNode;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected SearchNode clone() {
            return new SearchNode(allowedDepth, parentCost, dstSw, (LinkedList<SimpleIsl>) parentPath.clone());
        }
    }

    private void createSearchNode() {

    }
}
