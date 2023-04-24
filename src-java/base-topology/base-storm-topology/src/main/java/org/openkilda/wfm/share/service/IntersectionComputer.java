/* Copyright 2023 Telstra Open Source
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes intersection counters for flow paths in flow group.
 */
public class IntersectionComputer {
    private final Set<Edge> targetPathEdges = new HashSet<>();
    private final Set<SwitchId> targetPathSwitches = new HashSet<>();

    private final Map<PathId, Set<Edge>> otherEdges = new HashMap<>();
    private final Map<PathId, Set<SwitchId>> otherSwitches = new HashMap<>();

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
        paths.forEach(path -> {
            if (path.getPathId().equals(targetForwardPathId) || path.getPathId().equals(targetReversePathId)) {
                handleTargetPath(path);
            }
            if (!path.getFlow().getFlowId().equals(targetFlowId)) {
                handleAnotherPath(path);
            }
        });
    }

    /**
     * Returns {@link OverlappingSegmentsStats} between target path id and other flow paths in the group.
     *
     * @return {@link OverlappingSegmentsStats} instance.
     */
    public OverlappingSegmentsStats getOverlappingStats() {
        return computeIntersectionCounters(
                otherEdges.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()),
                otherSwitches.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())
        );
    }

    /**
     * Returns {@link OverlappingSegmentsStats} with {@param pathId} flow path.
     *
     * @return {@link OverlappingSegmentsStats} instance.
     */
    public OverlappingSegmentsStats getOverlappingStats(PathId forwardPathId, PathId reversePathId) {
        Set<Edge> edges = new HashSet<>(otherEdges.getOrDefault(forwardPathId, Collections.emptySet()));
        edges.addAll(otherEdges.getOrDefault(reversePathId, Collections.emptySet()));
        Set<SwitchId> switches = new HashSet<>(otherSwitches.getOrDefault(forwardPathId, Collections.emptySet()));
        switches.addAll(otherSwitches.getOrDefault(reversePathId, Collections.emptySet()));
        return computeIntersectionCounters(edges, switches);
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

    private void handleTargetPath(FlowPath path) {
        if (path.isOneSwitchPath()) {
            targetPathSwitches.add(path.getSrcSwitchId());
        } else {
            path.getSegments().forEach(segment -> {
                targetPathEdges.add(Edge.fromPathSegment(segment));
                targetPathSwitches.add(segment.getSrcSwitchId());
                targetPathSwitches.add(segment.getDestSwitchId());
            });
        }
    }

    private void handleAnotherPath(FlowPath path) {
        Set<SwitchId> switches = new HashSet<>();
        Set<Edge> edges = new HashSet<>();
        if (path.isOneSwitchPath()) {
            switches.add(path.getSrcSwitchId());
        } else {
            path.getSegments().forEach(segment -> {
                switches.add(segment.getSrcSwitchId());
                switches.add(segment.getDestSwitchId());
                edges.add(Edge.fromPathSegment(segment));
            });
        }
        if (!switches.isEmpty()) {
            otherSwitches.put(path.getPathId(), switches);
        }
        if (!edges.isEmpty()) {
            otherEdges.put(path.getPathId(), edges);
        }
    }

    private OverlappingSegmentsStats computeIntersectionCounters(Set<Edge> edges, Set<SwitchId> switches) {
        int edgesOverlap = Sets.intersection(edges, targetPathEdges).size();
        int switchesOverlap = Sets.intersection(switches, targetPathSwitches).size();
        return new OverlappingSegmentsStats(edgesOverlap,
                switchesOverlap,
                percent(edgesOverlap, targetPathEdges.size()),
                percent(switchesOverlap, targetPathSwitches.size()));
    }

    private int percent(int overlap, int total) {
        if (total != 0) {
            return (int) ((overlap * 100.0d) / total);
        }
        return 0;
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
        SwitchId srcSwitch;
        int srcPort;
        SwitchId destSwitch;
        int destPort;

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
