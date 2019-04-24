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

import static java.util.stream.Collectors.toList;

import org.openkilda.messaging.payload.flow.OverlappingSegmentsStats;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Sets;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Computes intersection counters for flow group.
 */
public class IntersectionComputer {
    private Set<Edge> mainFlowEdges;
    private Set<SwitchId> mainFlowSwitches;

    // FlowId, List<Edge>
    private Map<String, List<Edge>> otherEdges;

    /**
     * Construct intersection counter for current flow group and target flow.
     *
     * @param mainFlowId the flow id to find overlapping with.
     * @param flowPaths  flow paths in group.
     */
    public IntersectionComputer(String mainFlowId, Collection<FlowPath> flowPaths) {
        List<Edge> mainFlow = flowPaths.stream()
                .filter(e -> e.getFlow().getFlowId().equals(mainFlowId))
                .flatMap(flowPath -> flowPath.getSegments().stream())
                .map(Edge::fromPathSegment)
                .collect(toList());

        mainFlowEdges = new HashSet<>(mainFlow);
        mainFlowSwitches = mainFlow.stream()
                .flatMap(e -> Stream.of(e.getSrcSwitch(), e.getDestSwitch()))
                .collect(Collectors.toSet());

        otherEdges = new HashMap<>();
        flowPaths.stream()
                .filter(e -> !e.getFlow().getFlowId().equals(mainFlowId))
                .forEach(flowPath -> {
                    List<Edge> edges = otherEdges.getOrDefault(flowPath.getFlow().getFlowId(), new ArrayList<>());
                    flowPath.getSegments().forEach(segment -> edges.add(Edge.fromPathSegment(segment)));
                    otherEdges.put(flowPath.getFlow().getFlowId(), edges);
                });
    }

    /**
     * Returns {@link OverlappingSegmentsStats} with all other paths in group.
     *
     * @return {@link OverlappingSegmentsStats} instance.
     */
    public OverlappingSegmentsStats getOverlappingStats() {
        return computeIntersectionCounters(
                otherEdges.values().stream().flatMap(Collection::stream).collect(toList()));
    }

    /**
     * Returns {@link OverlappingSegmentsStats} with {@param flowId} flow path.
     *
     * @return {@link OverlappingSegmentsStats} instance.
     */
    public OverlappingSegmentsStats getOverlappingStats(String flowId) {
        return computeIntersectionCounters(otherEdges.getOrDefault(flowId, Collections.emptyList()));
    }

    OverlappingSegmentsStats computeIntersectionCounters(List<Edge> otherEdges) {
        Set<SwitchId> switches = new HashSet<>();
        Set<Edge> edges = new HashSet<>();
        for (Edge edge : otherEdges) {
            switches.add(edge.getSrcSwitch());
            switches.add(edge.getDestSwitch());
            edges.add(edge);
        }

        int edgesOverlap = Sets.intersection(edges, mainFlowEdges).size();
        int switchesOverlap = Sets.intersection(switches, mainFlowSwitches).size();
        return new OverlappingSegmentsStats(edgesOverlap,
                switchesOverlap,
                percent(edgesOverlap, mainFlowEdges.size()),
                percent(switchesOverlap, mainFlowSwitches.size()));
    }

    private int percent(int n, int from) {
        return (int) ((n * 100.0f) / from);
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
            if (segment.getSrcSwitch().getSwitchId().compareTo(segment.getDestSwitch().getSwitchId()) > 0) {
                return new Edge(segment.getSrcSwitch().getSwitchId(),
                        segment.getSrcPort(),
                        segment.getDestSwitch().getSwitchId(),
                        segment.getDestPort());
            }
            return new Edge(segment.getDestSwitch().getSwitchId(),
                    segment.getDestPort(),
                    segment.getSrcSwitch().getSwitchId(),
                    segment.getSrcPort());
        }
    }
}
