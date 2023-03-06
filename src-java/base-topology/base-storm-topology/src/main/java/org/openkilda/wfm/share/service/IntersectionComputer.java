/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.service;

import org.openkilda.messaging.payload.flow.OverlappingSegmentsStats;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Value;
import org.apache.commons.collections4.iterators.ReverseListIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes intersection counters for flow paths in flow group.
 */
public class IntersectionComputer {
    private Set<Edge> targetPathEdges;
    private Set<SwitchId> targetPathSwitches;

    private Map<PathId, Set<Edge>> otherEdges;

    /**
     * Construct intersection counter for current flow group and target flow and flow path.
     *
     * @param targetFlowId the flow id to filter other paths belongs to target flow.
     * @param targetForwardPathId the forward  path id to find overlapping with.
     * @param targetReversePathId the reverse path id to find overlapping with.
     * @param paths paths in the flow group.
     */
    public IntersectionComputer(String targetFlowId, PathId targetForwardPathId, PathId targetReversePathId,
                                Collection<FlowPath> paths) {
        List<Edge> targetPath = paths.stream()
                .flatMap(path -> path.getSegments().stream())
                .filter(e -> e.getPathId().equals(targetForwardPathId)
                        || e.getPathId().equals(targetReversePathId))
                .map(Edge::fromPathSegment)
                .collect(Collectors.toList());

        targetPathEdges = new HashSet<>(targetPath);
        targetPathSwitches = targetPath.stream()
                .flatMap(e -> Stream.of(e.getSrcSwitch(), e.getDestSwitch()))
                .collect(Collectors.toSet());

        otherEdges = paths.stream()
                .filter(e -> !e.getFlow().getFlowId().equals(targetFlowId))
                .flatMap(path -> path.getSegments().stream())
                .collect(Collectors.groupingBy(
                        PathSegment::getPathId,
                        Collectors.mapping(Edge::fromPathSegment, Collectors.toSet())
                ));
    }

    /**
     * Returns {@link OverlappingSegmentsStats} between target path id and other flow paths in the group.
     *
     * @return {@link OverlappingSegmentsStats} instance.
     */
    public OverlappingSegmentsStats getOverlappingStats() {
        return computeIntersectionCounters(
                otherEdges.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    /**
     * Returns {@link OverlappingSegmentsStats} with {@param pathId} flow path.
     *
     * @return {@link OverlappingSegmentsStats} instance.
     */
    public OverlappingSegmentsStats getOverlappingStats(PathId forwardPathId, PathId reversePathId) {
        Set<Edge> edges = new HashSet<>(otherEdges.getOrDefault(forwardPathId, Collections.emptySet()));
        edges.addAll(otherEdges.getOrDefault(reversePathId, Collections.emptySet()));
        return computeIntersectionCounters(edges);
    }

    OverlappingSegmentsStats computeIntersectionCounters(Set<Edge> otherEdges) {
        Set<SwitchId> switches = new HashSet<>();
        Set<Edge> edges = new HashSet<>();
        for (Edge edge : otherEdges) {
            switches.add(edge.getSrcSwitch());
            switches.add(edge.getDestSwitch());
            edges.add(edge);
        }

        int edgesOverlap = Sets.intersection(edges, targetPathEdges).size();
        int switchesOverlap = Sets.intersection(switches, targetPathSwitches).size();
        return new OverlappingSegmentsStats(edgesOverlap,
                switchesOverlap,
                percent(edgesOverlap, targetPathEdges.size()),
                percent(switchesOverlap, targetPathSwitches.size()));
    }

    private int percent(int n, int from) {
        return (int) ((n * 100.0f) / from);
    }

    /**
     * Calculates is overlapping between primary and protected flow segments exists.
     *
     * @param primaryFlowSegments primary flow segments.
     * @param protectedFlowSegments protected flow segments.
     * @return is overlapping flag.
     */
    public static boolean isProtectedPathOverlaps(
            List<PathSegment> primaryFlowSegments, List<PathSegment> protectedFlowSegments) {
        Set<Edge> primaryEdges = primaryFlowSegments.stream().map(Edge::fromPathSegment).collect(Collectors.toSet());
        Set<Edge> protectedEdges = protectedFlowSegments.stream().map(Edge::fromPathSegment)
                .collect(Collectors.toSet());

        return !Sets.intersection(primaryEdges, protectedEdges).isEmpty();
    }

    /**
     * Calculates intersection (shared part) among the paths starting from the source endpoint.
     *
     * @param paths the paths to examine.
     * @return the overlapping path segments.
     */
    public static List<PathSegment> calculatePathIntersectionFromSource(Collection<FlowPath> paths) {
        if (paths.size() < 2) {
            throw new IllegalArgumentException("At least 2 paths must be provided");
        }

        SwitchId source = null;
        for (FlowPath path : paths) {
            List<PathSegment> segments = path.getSegments();
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("All paths mustn't be empty");
            }
            // Check that all paths have the same source.
            if (source == null) {
                source = segments.get(0).getSrcSwitchId();
            } else if (!segments.get(0).getSrcSwitchId().equals(source)) {
                throw new IllegalArgumentException("All paths must have the same source endpoint");
            }
        }

        // Gather iterators of all paths' segments.
        List<Iterator<PathSegment>> pathSegmentIterators = paths.stream()
                .map(FlowPath::getSegments)
                .map(List::iterator)
                .collect(Collectors.toList());
        return getLongestIntersectionOfSegments(pathSegmentIterators);
    }

    /**
     * Calculates intersection (shared part) among the paths starting from the dest endpoint.
     *
     * @param paths the paths to examine.
     * @return the overlapping path segments.
     */
    public static List<PathSegment> calculatePathIntersectionFromDest(Collection<FlowPath> paths) {
        if (paths.size() < 2) {
            throw new IllegalArgumentException("At least 2 paths must be provided");
        }

        SwitchId dest = null;
        for (FlowPath path : paths) {
            List<PathSegment> segments = path.getSegments();
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("All paths mustn't be empty");
            }
            // Check that all paths have the same source.
            if (dest == null) {
                dest = segments.get(segments.size() - 1).getDestSwitchId();
            } else if (!segments.get(segments.size() - 1).getDestSwitchId().equals(dest)) {
                throw new IllegalArgumentException("All paths must have the same dest endpoint");
            }
        }

        // Gather iterators of all paths' segments.
        List<Iterator<PathSegment>> pathSegmentIterators = paths.stream()
                .map(FlowPath::getSegments)
                .map(ReverseListIterator::new)
                .collect(Collectors.toList());
        return Lists.reverse(getLongestIntersectionOfSegments(pathSegmentIterators));
    }

    private static List<PathSegment> getLongestIntersectionOfSegments(List<Iterator<PathSegment>> pathSegments) {
        List<PathSegment> result = new ArrayList<>();
        // Iterate over the first path's segments and check other paths' segments.
        Iterator<PathSegment> firstPathSegmentIterator = pathSegments.get(0);
        while (firstPathSegmentIterator.hasNext()) {
            PathSegment firstPathSegment = firstPathSegmentIterator.next();
            for (Iterator<PathSegment> it : pathSegments.subList(1, pathSegments.size())) {
                if (it.hasNext()) {
                    PathSegment anotherSegment = it.next();
                    if (!firstPathSegment.getSrcSwitchId().equals(anotherSegment.getSrcSwitchId())
                            || firstPathSegment.getSrcPort() != anotherSegment.getSrcPort()
                            || !firstPathSegment.getDestSwitchId().equals(anotherSegment.getDestSwitchId())
                            || firstPathSegment.getDestPort() != anotherSegment.getDestPort()) {
                        return result;
                    }
                } else {
                    return result;
                }
            }
            result.add(firstPathSegment);
        }
        return result;
    }

    /**
     * Edge representation. Constraint: srcSwitch is always not less than destSwitch
     */
    @Value
    static class Edge {
        private SwitchId srcSwitch;
        private int srcPort;
        private SwitchId destSwitch;
        private int destPort;

        static Edge fromPathSegment(PathSegment segment) {
            if (segment.getSrcSwitchId().compareTo(segment.getDestSwitchId()) > 0) {
                return new Edge(segment.getSrcSwitchId(),
                        segment.getSrcPort(),
                        segment.getDestSwitchId(),
                        segment.getDestPort());
            }
            return new Edge(segment.getDestSwitchId(),
                    segment.getDestPort(),
                    segment.getSrcSwitchId(),
                    segment.getSrcPort());
        }
    }
}
