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
import static java.util.stream.Collectors.toSet;

import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindOneDirectionPathResult;
import org.openkilda.pce.model.FindPathResult;
import org.openkilda.pce.model.Node;
import org.openkilda.pce.model.PathWeight;
import org.openkilda.pce.model.PathWeight.Penalty;
import org.openkilda.pce.model.WeightFunction;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This algorithm is optimized for finding a bidirectional path between the start and end nodes. It uses elements of
 * depth first, breadth first, and shortest path (weight) algorithms.
 * <p/>
 * For neighboring switches, it assumes there is only one link between them. The network that is passed in should
 * reflect the links that should be traversed.
 * <p/>
 * Algorithm Notes: 1. forward = get path (src, dst) 2. reverse = get path (dst, src, hint=forward) 3 get path: 0.
 * allowedDepth = 35 √ . go breadth first (majority of switches probably have 5 hops or less) √ . add weight . compare
 * against bestWeight (if greater, then return) . have we visited this node before? - if so was it cheaper? Yes? return.
 * - No, remove the prior put. We and any downstream weight (BUT .. maybe we stopped somewhere and need to continue?
 * Probably could just replace the old one with the current one and add the children as unvisited? Oooh .. this could
 * cause a loop.. if there are negative weights - So, should probably track path to this point... . if node = target,
 * update bestWeight, return . add each neighbor to the investigation list, where neighbor.outbound.dst != current node.
 */
@Slf4j
public class BestWeightAndShortestPathFinder implements PathFinder {
    private final int allowedDepth;

    /**
     * Constructs the finder with the specified limit on path depth.
     *
     * @param allowedDepth the allowed depth for a potential path.
     */
    public BestWeightAndShortestPathFinder(int allowedDepth) {
        this.allowedDepth = allowedDepth;
    }

    @Override
    public FindPathResult findPathWithMinWeight(AvailableNetwork network,
                                                SwitchId startSwitchId, SwitchId endSwitchId,
                                                WeightFunction weightFunction)
            throws UnroutableFlowException {
        Node start = network.getSwitch(startSwitchId);
        Node end = network.getSwitch(endSwitchId);
        return findPath(network, startSwitchId, endSwitchId, () -> findOneDirectionPath(start, end, weightFunction));
    }

    @Override
    public FindPathResult findPathWithMinWeightAndLatencyLimits(AvailableNetwork network,
                                                                SwitchId startSwitchId, SwitchId endSwitchId,
                                                                WeightFunction weightFunction,
                                                                long maxLatency, long latencyLimit)
            throws UnroutableFlowException {
        Node start = network.getSwitch(startSwitchId);
        Node end = network.getSwitch(endSwitchId);
        return findPath(network, startSwitchId, endSwitchId,
                () -> findOneDirectionPathWithLatencyLimits(start, end, weightFunction, maxLatency, latencyLimit));
    }

    @Override
    public FindPathResult findPathWithWeightCloseToMaxWeight(AvailableNetwork network,
                                                             SwitchId startSwitchId, SwitchId endSwitchId,
                                                             WeightFunction weightFunction,
                                                             long maxWeight, long backUpMaxWeight)
            throws UnroutableFlowException {
        Node start = network.getSwitch(startSwitchId);
        Node end = network.getSwitch(endSwitchId);
        return findPath(network, startSwitchId, endSwitchId,
                () -> findOneDirectionPath(start, end, weightFunction, maxWeight, backUpMaxWeight));
    }

    private FindPathResult findPath(AvailableNetwork network, SwitchId startSwitchId,
                                    SwitchId endSwitchId, Supplier<FindOneDirectionPathResult> getPath)
            throws UnroutableFlowException {
        Node start = network.getSwitch(startSwitchId);
        Node end = network.getSwitch(endSwitchId);
        if (start == null || end == null) {
            throw new UnroutableFlowException(format("Switch %s doesn't have links with enough bandwidth",
                    start == null ? startSwitchId : endSwitchId));
        }

        FindOneDirectionPathResult pathFindResult = getPath.get();
        List<Edge> forwardPath = pathFindResult.getFoundPath();
        if (forwardPath.isEmpty()) {

            throw new UnroutableFlowException(format("Can't find a path from %s to %s. %s", start, end,
                    FinderUtils.reasonsToString(pathFindResult.getPathNotFoundReasons())),
                    pathFindResult.getPathNotFoundReasons());
        }

        List<Edge> reversePath = getReversePath(end, start, forwardPath);
        if (reversePath.isEmpty()) {

            throw new UnroutableFlowException(format("Can't find a reverse path from %s to %s. "
                    + "Forward path : %s. %s", end, start, StringUtils.join(forwardPath, ", "),
                    FinderUtils.reasonsToString(pathFindResult.getPathNotFoundReasons())),
                    pathFindResult.getPathNotFoundReasons());
        }

        return FindPathResult.builder()
                .foundPath(Pair.of(forwardPath, reversePath))
                .backUpPathComputationWayUsed(pathFindResult.isBackUpPathComputationWayUsed())
                .build();
    }

    @Override
    public List<FindOneDirectionPathResult> findNPathsBetweenSwitches(
            AvailableNetwork network, SwitchId startSwitchId, SwitchId endSwitchId, int count,
            WeightFunction weightFunction) throws UnroutableFlowException {
        Node end = network.getSwitch(endSwitchId);
        return findNPathsBetweenSwitches(network, startSwitchId, endSwitchId, count, weightFunction,
                (Node start) -> findOneDirectionPath(start, end, weightFunction), Long.MAX_VALUE, Long.MAX_VALUE);
    }

    @Override
    public List<FindOneDirectionPathResult> findNPathsBetweenSwitches(
            AvailableNetwork network, SwitchId startSwitchId, SwitchId endSwitchId, int count,
            WeightFunction weightFunction, long maxWeight, long backUpMaxWeight) throws UnroutableFlowException {
        Node end = network.getSwitch(endSwitchId);
        return findNPathsBetweenSwitches(network, startSwitchId, endSwitchId, count, weightFunction,
                (Node start) -> findOneDirectionPath(start, end, weightFunction, maxWeight, backUpMaxWeight),
                maxWeight, backUpMaxWeight);
    }

    /**
     * Find N (or less) best paths. To find N paths Yen's algorithm is used.
     *
     * @return an list of N (or less) best paths.
     */
    private List<FindOneDirectionPathResult> findNPathsBetweenSwitches(
            AvailableNetwork network, SwitchId startSwitchId, SwitchId endSwitchId, int count,
            WeightFunction weightFunction, Function<Node, FindOneDirectionPathResult> getPath,
            long maxWeight, long backUpMaxWeight) throws UnroutableFlowException {

        Node start = network.getSwitch(startSwitchId);
        Node end = network.getSwitch(endSwitchId);
        if (start == null || end == null) {
            throw new UnroutableFlowException(format("Switch %s doesn't have links with enough bandwidth",
                    start == null ? startSwitchId : endSwitchId));
        }

        // Determine the shortest path from the start to the end.
        List<FindOneDirectionPathResult> bestPaths = new ArrayList<>();
        FindOneDirectionPathResult firstPath = getPath.apply(start);

        if (firstPath.getFoundPath().isEmpty()) {
            return bestPaths;
        }
        bestPaths.add(firstPath);

        // Initialize the set to store the potential kth shortest path.
        // Use LinkedHashSet to have deterministic results.
        Set<FindOneDirectionPathResult> potentialKthShortestPaths = new LinkedHashSet<>();

        for (int k = 1; k < count; k++) {
            FindOneDirectionPathResult bestPath = bestPaths.get(k - 1);
            List<Edge> edgesList = bestPath.getFoundPath();
            for (int i = 0; i < edgesList.size(); i++) {
                // Spur node is retrieved from the previous k-shortest path.
                Node spurNode = edgesList.get(i).getSrcSwitch();
                // The sequence of edges from the start to the spur node (without spur node).
                List<Edge> rootPath = new ArrayList<>(edgesList.subList(0, i));

                Set<Edge> removedEdges = new HashSet<>();
                // Remove the links that are part of the previous shortest paths which share the same root path.
                for (FindOneDirectionPathResult pathResult : bestPaths) {
                    List<Edge> path = pathResult.getFoundPath();
                    if (path.size() > i && rootPath.equals(path.subList(0, i))
                            && spurNode.equals(path.get(i).getSrcSwitch())) {
                        removedEdges.add(path.get(i));
                        removeEdge(path.get(i));
                    }
                }

                for (Edge edge : rootPath) {
                    edge.getSrcSwitch().remove();
                }

                // Calculate the spur path from the spur node to the end.
                FindOneDirectionPathResult pathFromSpurNode = getPath.apply(spurNode);
                if (!pathFromSpurNode.getFoundPath().isEmpty()) {
                    List<Edge> totalPath = new ArrayList<>(rootPath);
                    // Entire path is made up of the root path and spur path.
                    totalPath.addAll(pathFromSpurNode.getFoundPath());
                    // Add the potential k-shortest path to the heap.
                    long totalPathWeight = totalPath.stream().map(weightFunction)
                            .map(PathWeight::getTotalWeight)
                            .reduce(0L, Long::sum);
                    // Filtering by maxWeight.
                    if (totalPathWeight < maxWeight) {
                        potentialKthShortestPaths.add(new FindOneDirectionPathResult(totalPath, false));
                    } else if (totalPathWeight < backUpMaxWeight) {
                        potentialKthShortestPaths.add(new FindOneDirectionPathResult(totalPath, true));
                    }
                }

                // Add back the edges and nodes that were removed from the graph.
                for (Edge edge : removedEdges) {
                    restoreEdge(edge);
                }
                for (Edge edge : rootPath) {
                    edge.getSrcSwitch().restore();
                }
            }

            if (potentialKthShortestPaths.isEmpty()) {
                break;
            }

            // Add the lowest weight path becomes the k-shortest path.
            FindOneDirectionPathResult newBestPath =
                    getBestPotentialKthShortestPath(potentialKthShortestPaths, bestPaths, weightFunction);
            if (newBestPath != null) {
                bestPaths.add(newBestPath);
            }
        }

        return bestPaths;
    }

    private FindOneDirectionPathResult getBestPotentialKthShortestPath(Set<FindOneDirectionPathResult> potentialPaths,
                                                                       List<FindOneDirectionPathResult> bestPaths,
                                                                       WeightFunction weightFunction) {
        FindOneDirectionPathResult bestKthShortestPath = null;
        long bestAvailableBandwidth = Long.MIN_VALUE;
        long bestWeight = Long.MAX_VALUE;

        for (FindOneDirectionPathResult pathResult : potentialPaths) {
            List<Edge> path = pathResult.getFoundPath();
            long currentAvailableBandwidth = getMinAvailableBandwidth(path);
            long currentWeight = getTotalWeight(path, weightFunction);
            if (!bestPaths.contains(pathResult) && (currentAvailableBandwidth > bestAvailableBandwidth
                    || (currentAvailableBandwidth == bestAvailableBandwidth && currentWeight < bestWeight))) {
                bestAvailableBandwidth = currentAvailableBandwidth;
                bestWeight = currentWeight;
                bestKthShortestPath = pathResult;
            }
        }

        if (bestKthShortestPath != null) {
            potentialPaths.remove(bestKthShortestPath);
        }

        return bestKthShortestPath;
    }

    private long getMinAvailableBandwidth(List<Edge> path) {
        return path.stream().mapToLong(Edge::getAvailableBandwidth).min().orElse(Long.MIN_VALUE);
    }

    private long getTotalWeight(List<Edge> path, WeightFunction weightFunction) {
        if (path.isEmpty()) {
            return Long.MAX_VALUE;
        }
        return path.stream().map(weightFunction).mapToLong(PathWeight::getTotalWeight).sum();
    }

    private void removeEdge(Edge edge) {
        edge.getSrcSwitch().getOutgoingLinks().remove(edge);
        edge.getDestSwitch().getIncomingLinks().remove(edge);

        Edge reverseEdge = edge.swap();
        reverseEdge.getSrcSwitch().getOutgoingLinks().remove(reverseEdge);
        reverseEdge.getDestSwitch().getIncomingLinks().remove(reverseEdge);
    }

    private void restoreEdge(Edge edge) {
        edge.getSrcSwitch().getOutgoingLinks().add(edge);
        edge.getDestSwitch().getIncomingLinks().add(edge);

        Edge reverseEdge = edge.swap();
        reverseEdge.getSrcSwitch().getOutgoingLinks().add(reverseEdge);
        reverseEdge.getDestSwitch().getIncomingLinks().add(reverseEdge);
    }

    private FindOneDirectionPathResult findOneDirectionPath(Node start, Node end, WeightFunction weightFunction) {
        FindOneDirectionPathResult pathResult = getPath(start, end, weightFunction);
        pathResult.setBackUpPathComputationWayUsed(false);
        return pathResult;
    }

    private FindOneDirectionPathResult findOneDirectionPath(Node start, Node end, WeightFunction weightFunction,
                                                            long maxWeight, long backUpMaxWeight) {
        FindOneDirectionPathResult pathResult = getPath(start, end, weightFunction, maxWeight);
        Map<FailReasonType, FailReason> reasons = pathResult.getPathNotFoundReasons();

        boolean backUpPathComputationWayUsed = false;

        if (pathResult.getFoundPath().isEmpty()) {
            pathResult = getPath(start, end, weightFunction, backUpMaxWeight);
            backUpPathComputationWayUsed = true;
        }

        return FindOneDirectionPathResult.builder()
                .foundPath(pathResult.getFoundPath())
                .pathNotFoundReasons(reasons)
                .backUpPathComputationWayUsed(backUpPathComputationWayUsed)
                .build();
    }

    private FindOneDirectionPathResult findOneDirectionPathWithLatencyLimits(Node start, Node end,
                                                                             WeightFunction weightFunction,
                                                                             long maxLatency, long latencyLimit) {
        FindOneDirectionPathResult pathResult = getPath(start, end, weightFunction);

        long pathLatency = pathResult.getFoundPath().stream().mapToLong(Edge::getLatency).sum();
        pathResult.setBackUpPathComputationWayUsed(pathLatency > maxLatency);
        if (pathLatency > latencyLimit) {

            pathResult.getPathNotFoundReasons().put(FailReasonType.LATENCY_LIMIT,
                    new FailReason(FailReasonType.LATENCY_LIMIT,
                    format("Requested path must have latency %sms or lower, but best path has latency %sms",
                            TimeUnit.NANOSECONDS.toMillis(latencyLimit), TimeUnit.NANOSECONDS.toMillis(pathLatency))));
            pathResult.setFoundPath(emptyList());
        }

        return pathResult;
    }

    /**
     * Call this method to find a path from start to end (srcDpid to dstDpid), particularly if you have no idea if the
     * path exists or what the best path is.
     *
     * @return A pair of ordered lists that represents the path from start to end, or an empty list
     */
    private FindOneDirectionPathResult getPath(Node start, Node end, WeightFunction weightFunction) {
        PathWeight bestWeight = new PathWeight(Long.MAX_VALUE);
        SearchNode bestPath = null;

        Deque<SearchNode> toVisit = new LinkedList<>(); // working list
        Map<Node, SearchNode> visited = new HashMap<>();

        Map<FailReasonType, FailReason> reasons = new HashMap<>();

        boolean destinationFound = false;
        toVisit.add(new SearchNode(weightFunction, start, allowedDepth, new PathWeight(), emptyList()));

        while (!toVisit.isEmpty()) {
            SearchNode current = toVisit.pop();
            if (log.isTraceEnabled()) {
                log.trace("Going to visit node {} with weight {}.",
                        current.dstSw, current.getParentWeight().getTotalWeight());
            }

            if (isContainHardDiversityPenalties(current.parentWeight)) {
                // Have not to use path with hard diversity penalties
                reasons.put(FailReasonType.HARD_DIVERSITY_PENALTIES,
                        new FailReason(FailReasonType.HARD_DIVERSITY_PENALTIES));
                continue;
            }

            // Determine if this node is the destination node.
            if (current.dstSw.equals(end)) {
                // We found the destination
                if (current.parentWeight.compareTo(bestWeight) < 0) {
                    // We found a best path. If we don't get here, then the entire graph will be
                    // searched until we run out of nodes or the depth is reached.
                    bestWeight = current.parentWeight;
                    bestPath = current;
                }
                // We found dest, no need to keep processing
                if (log.isTraceEnabled()) {
                    log.trace("Found destination using {} with path {}", current.dstSw, current.parentPath);
                }
                destinationFound = true;
                continue;
            }

            // Otherwise, if we've been here before, see if this path is better
            SearchNode prior = visited.get(current.dstSw);
            if (prior != null && current.parentWeight.compareTo(prior.parentWeight) >= 0) {
                continue;
            }

            // Stop processing entirely if we've gone too far, or over bestWeight
            if (current.allowedDepth <= 0 || current.parentWeight.compareTo(bestWeight) > 0) {
                if (log.isTraceEnabled()) {
                    log.trace("Skip node {} processing", current.dstSw);
                }
                if (current.allowedDepth <= 0) {
                    reasons.put(FailReasonType.ALLOWED_DEPTH_EXCEEDED,
                            new FailReason(FailReasonType.ALLOWED_DEPTH_EXCEEDED));
                }
                destinationFound = true; //to no add the reason
                continue;
            }

            // Either this is the first time, or this one has less weight .. either way, this node should
            // be the one in the visited list
            visited.put(current.dstSw, current);
            if (log.isTraceEnabled()) {
                log.trace("Save new path to node {} and process it's outgoing links", current.dstSw);
            }

            // At this stage .. haven't found END, haven't gone too deep, and we are not over weight.
            // So, add the outbound isls.
            current.dstSw.getOutgoingLinks().stream()
                    .sorted(Comparator.comparing(edge -> edge.getDestSwitch().getSwitchId()))
                    .forEach(edge -> toVisit.add(current.addNode(edge)));
        }

        if (!destinationFound) {
            reasons.put(FailReasonType.NO_CONNECTION, new FailReason(FailReasonType.NO_CONNECTION));
        }

        List<Edge> path = (bestPath != null) ? bestPath.parentPath : new LinkedList<>();

        return new FindOneDirectionPathResult(path, reasons);
    }

    private FindOneDirectionPathResult getPath(Node start, Node end, WeightFunction weightFunction, long maxWeight) {
        SearchNodeAndReasons desiredPath = getDesiredPath(start, end, weightFunction, maxWeight);

        List<Edge> foundPath = (desiredPath.searchNode != null)
                ? desiredPath.searchNode.getParentPath() : new LinkedList<>();

        SearchNodeAndReasons desiredReversePath = getDesiredPath(end, start, weightFunction, maxWeight);

        if (desiredReversePath.searchNode != null && (desiredPath.searchNode == null
                || desiredReversePath.searchNode.parentWeight.compareTo(desiredPath.searchNode.parentWeight) > 0)) {
            foundPath = getReversePath(start, end, desiredReversePath.searchNode.getParentPath());
        }

        return new FindOneDirectionPathResult(foundPath, desiredPath.reasons);
    }

    /**
     * Finds a path whose weight is less than maxWeight and as close to maxWeight as possible.
     *
     * @return A desired path from start to end as SearchNode representation, or null
     */
    private SearchNodeAndReasons getDesiredPath(Node start, Node end, WeightFunction weightFunction, long maxWeight) {
        SearchNode desiredPath = null;
        Map<FailReasonType, FailReason> reasons = new HashMap<>();

        Deque<SearchNode> toVisit = new LinkedList<>();
        Map<Node, SearchNode> visited = new HashMap<>();

        boolean destinationFound = false;

        toVisit.add(new SearchNode(weightFunction, start, allowedDepth, new PathWeight(), emptyList()));


        while (!toVisit.isEmpty()) {
            SearchNode current = toVisit.pop();
            PathWeight currentPathWeight = current.getParentWeight();
            if (log.isTraceEnabled()) {
                log.trace("Going to visit node {} with weight {}.", current.dstSw, currentPathWeight);
            }
            // Leave if the path contains this node
            if (current.containsSwitch(current.dstSw.getSwitchId())) {
                if (log.isTraceEnabled()) {
                    log.trace("Skip node {} already in the path", current.dstSw);
                }
                continue;
            }

            // Shift the current weight relative to maxWeight
            final long shiftedCurrentWeight = Math.abs(maxWeight - current.parentWeight.getBaseWeight());

            // Determine if this node is the destination node.
            if (current.dstSw.equals(end)) {
                // We found the destination
                destinationFound = true;
                if (current.parentWeight.getBaseWeight() < maxWeight) {
                    // We found a best path. If we don't get here, then the entire graph will be
                    // searched until we run out of nodes or the depth is reached.
                    if (isContainHardDiversityPenalties(current.parentWeight)) {
                        // Have not to use path with hard diversity penalties
                        reasons.put(FailReasonType.HARD_DIVERSITY_PENALTIES,
                                new FailReason(FailReasonType.HARD_DIVERSITY_PENALTIES));
                        continue;
                    }

                    if (desiredPath == null) {
                        desiredPath = current;
                        log.trace("Found first path to node {} with base weight {} and penalties {}",
                                current.dstSw, current.parentWeight.getBaseWeight(),
                                current.parentWeight.getPenaltiesWeight());
                        continue;
                    }

                    if (currentPathWeight.getPenaltiesWeight() < desiredPath.parentWeight.getPenaltiesWeight()) {
                        if (log.isTraceEnabled()) {
                            log.trace("Found path with best penalties {} instead of {} penalties. Base weight is {}",
                                    currentPathWeight.getPenaltiesWeight(),
                                    desiredPath.parentWeight.getPenaltiesWeight(),
                                    current.parentWeight.getBaseWeight());
                        }
                        desiredPath = current;
                        continue;
                    }

                    if (currentPathWeight.getPenaltiesWeight() == desiredPath.parentWeight.getPenaltiesWeight()
                            && currentPathWeight.getBaseWeight() > desiredPath.parentWeight.getBaseWeight()) {
                        if (log.isTraceEnabled()) {
                            log.trace("Found path with same penalties {}, but different weight {} instead of {}",
                                    currentPathWeight.getPenaltiesWeight(), currentPathWeight.getBaseWeight(),
                                    desiredPath.parentWeight.getBaseWeight());
                        }
                        desiredPath = current;
                        continue;
                    }
                } else {
                    if (reasons.containsKey(FailReasonType.MAX_WEIGHT_EXCEEDED)) {
                        Long lastWeight = reasons.get(FailReasonType.MAX_WEIGHT_EXCEEDED).getWeight();
                        if (lastWeight == null || current.parentWeight.getBaseWeight() < lastWeight) {
                            reasons.put(FailReasonType.MAX_WEIGHT_EXCEEDED,
                                    new FailReason(FailReasonType.MAX_WEIGHT_EXCEEDED,
                                            current.parentWeight.getBaseWeight()));
                        }
                    } else {
                        reasons.put(FailReasonType.MAX_WEIGHT_EXCEEDED,
                                new FailReason(FailReasonType.MAX_WEIGHT_EXCEEDED,
                                        current.parentWeight.getBaseWeight()));
                    }
                }

                // We found dest, no need to keep processing
                if (log.isTraceEnabled()) {
                    log.trace("Found destination using {} with path {}", current.dstSw, current.parentPath);
                }
                continue;
            }

            // Stop processing entirely if we've gone too far, or over maxWeight
            if (current.allowedDepth <= 0 || current.parentWeight.getBaseWeight() >= maxWeight) {
                if (current.allowedDepth <= 0) {
                    reasons.put(FailReasonType.ALLOWED_DEPTH_EXCEEDED,
                            new FailReason(FailReasonType.ALLOWED_DEPTH_EXCEEDED));
                } else if (!reasons.containsKey(FailReasonType.MAX_WEIGHT_EXCEEDED)) {
                    reasons.put(FailReasonType.MAX_WEIGHT_EXCEEDED,
                            new FailReason(FailReasonType.MAX_WEIGHT_EXCEEDED));
                }
                destinationFound = true;
                continue;
            }


            // Otherwise, if we've been here before, see if this path is better
            SearchNode prior = visited.get(current.dstSw);
            // Use non-greedy way to save visited nodes to fix issue mentioned in docs/design/pce/max-latency-issue
            if (prior != null && shiftedCurrentWeight < Math.abs(maxWeight - prior.parentWeight.getBaseWeight())) {

                // Should check with penalties too
                if (currentPathWeight.getTotalWeight() > prior.getParentWeight().getTotalWeight()) {
                    if (log.isTraceEnabled()) {
                        log.trace("Skip node {} processing", current.dstSw);
                    }
                    continue;
                }
            }

            // Either this is the first time, or this one has less weight .. either way, this node should
            // be the one in the visited list
            visited.put(current.dstSw, current);
            if (log.isTraceEnabled()) {
                log.trace("Save new path to node {} and process it's outgoing links", current.dstSw);
            }

            // At this stage .. haven't found END, haven't gone too deep, and we are not over weight.
            // So, add the outbound isls.
            PathWeight desiredWeight = desiredPath == null ? null : desiredPath.getParentWeight();
            current.dstSw.getOutgoingLinks().stream()
                    // We can skip path if its penalties are higher than bestPath penalties
                    .filter(edge -> filterEdgeByPenalties(edge, current.parentWeight, desiredWeight, weightFunction))
                    // should firstly process edges with big weights to guarantee they will not be skipped.
                    // See unit test shouldUseSlowLinkInsidePath.
                    .sorted(Comparator.comparing(weightFunction).reversed()
                            .thenComparing(edge -> edge.getDestSwitch().getSwitchId()))
                    .forEach(edge -> toVisit.add(current.addNode(edge)));
        }

        if (!destinationFound) {
            reasons.put(FailReasonType.NO_CONNECTION, new FailReason(FailReasonType.NO_CONNECTION));
        }

        return new SearchNodeAndReasons(desiredPath, reasons);
    }

    private static boolean filterEdgeByPenalties(
            Edge edge, PathWeight currentWeight, PathWeight desiredWeight, WeightFunction weightFunction) {
        if (desiredWeight == null) {
            return true;
        }
        PathWeight nextPathWeight = currentWeight.add(weightFunction.apply(edge));
        return nextPathWeight.getPenaltiesWeight() <= desiredWeight.getPenaltiesWeight();
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

        if (isPathEndpointsCorrect(src, dst, reversePath)) {
            if (isPathValid(reversePath)) {
                log.debug("Reverse path is available from {} to {}", src.getSwitchId(), dst.getSwitchId());
            } else {
                log.warn(format("Failed to find symmetric reverse path from %s to %s. Forward path: %s",
                        src.getSwitchId(), dst.getSwitchId(), StringUtils.join(forwardPath, ", ")));
            }
        }
        return reversePath;
    }

    private boolean isPathEndpointsCorrect(Node src, Node dst, List<Edge> path) {
        return Objects.equals(src, path.get(0).getSrcSwitch())
                && Objects.equals(dst, path.get(path.size() - 1).getDestSwitch());
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
     * This helper function is used with getReversePath(hint) to confirm the hint path exists.
     */
    private boolean isPathValid(List<Edge> path) {
        boolean validPath = true;

        for (Edge i : path) {
            Node srcSwitch = i.getSrcSwitch();

            Set<Edge> pathsToDst = srcSwitch.getOutgoingLinks().stream()
                    .filter(link -> link.getDestSwitch().equals(i.getDestSwitch()))
                    .collect(toSet());
            if (pathsToDst.isEmpty()) {
                log.debug("No ISLS from {} to {}", i.getSrcSwitch(), i.getDestSwitch());
            }

            boolean foundThisOne = false;
            for (Edge orig : pathsToDst) {
                if (i.getSrcSwitch().getSwitchId().equals(orig.getSrcSwitch().getSwitchId())
                        && i.getSrcPort() == orig.getSrcPort()
                        && i.getDestSwitch().getSwitchId().equals(orig.getDestSwitch().getSwitchId())
                        && i.getDestPort() == orig.getDestPort()) {
                    foundThisOne = true;
                    break; // stop looking, we found the Edge
                }
            }
            if (!foundThisOne) {
                validPath = false;
                break; // found an Edge that doesn't exist, stop looking for others
            }
        }

        return validPath;
    }

    /**
     * This class facilitates the algorithm by collecting salient pieces of information that are necessary for tracking
     * search data per search node.
     */
    @Value
    private static class SearchNode {
        final WeightFunction weightFunction;
        final Node dstSw;
        final int allowedDepth;
        final PathWeight parentWeight;
        final List<Edge> parentPath;
        // NB: We could consider tracking the child path as well .. to facilitate
        //     re-calc if we find a better parentPath.

        SearchNode addNode(Edge nextEdge) {
            List<Edge> newParentPath = new ArrayList<>(this.parentPath);
            newParentPath.add(nextEdge);

            PathWeight weight = parentWeight.add(weightFunction.apply(nextEdge));

            return new SearchNode(
                    weightFunction,
                    nextEdge.getDestSwitch(),
                    allowedDepth - 1,
                    weight,
                    newParentPath);
        }

        boolean containsSwitch(SwitchId switchId) {
            return parentPath.stream().anyMatch(s -> s.getSrcSwitch().getSwitchId().equals(switchId));
        }
    }

    private long getPathWeightDiversityPenaltiesValue(PathWeight pathWeight) {
        return pathWeight.getPenaltyValue(PathWeight.Penalty.DIVERSITY_ISL_LATENCY)
                + pathWeight.getPenaltyValue(PathWeight.Penalty.DIVERSITY_SWITCH_LATENCY)
                + pathWeight.getPenaltyValue(PathWeight.Penalty.DIVERSITY_POP_ISL_COST);
    }

    private boolean isContainHardDiversityPenalties(PathWeight pathWeight) {
        long penaltiesSum = pathWeight.getPenaltyValue(Penalty.PROTECTED_DIVERSITY_ISL_LATENCY)
                + pathWeight.getPenaltyValue(Penalty.PROTECTED_DIVERSITY_SWITCH_LATENCY);
        return penaltiesSum > 0;
    }

    @AllArgsConstructor
    private static class SearchNodeAndReasons {
        private SearchNode searchNode;
        private Map<FailReasonType, FailReason> reasons;
    }
}
