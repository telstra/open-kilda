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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
public class FlowPathBuilder {
    private SwitchRepository switchRepository;

    /**
     * Check whether the path and flow path represent the same.
     *
     * @param path the path to evaluate.
     * @param flowPath the flow path to evaluate.
     */
    public boolean isSamePath(Path path, FlowPath flowPath) {
        if (!path.getSrcSwitchId().equals(flowPath.getSrcSwitch().getSwitchId())
                || !path.getDestSwitchId().equals(flowPath.getDestSwitch().getSwitchId())
                || path.getSegments().size() != flowPath.getSegments().size()) {
            return false;
        }

        Iterator<Segment> pathIt = path.getSegments().iterator();
        Iterator<PathSegment> flowPathIt = flowPath.getSegments().iterator();
        while (pathIt.hasNext() && flowPathIt.hasNext()) {
            Path.Segment pathSegment = pathIt.next();
            PathSegment flowSegment = flowPathIt.next();
            if (!pathSegment.getSrcSwitchId().equals(flowSegment.getSrcSwitch().getSwitchId())
                    || !pathSegment.getDestSwitchId().equals(flowSegment.getDestSwitch().getSwitchId())
                    || pathSegment.getSrcPort() != flowSegment.getSrcPort()
                    || pathSegment.getDestPort() != flowSegment.getDestPort()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check whether the path and flow path overlap.
     *
     * @param path the path to evaluate.
     * @param flowPath the flow path to evaluate.
     */
    public boolean arePathsOverlapped(Path path, FlowPath flowPath) {
        Set<Segment> pathSegments = path.getSegments().stream()
                .map(segment -> segment.toBuilder().latency(0).build())
                .collect(Collectors.toSet());
        Set<Segment> flowSegments = flowPath.getSegments().stream()
                .map(segment -> Segment.builder()
                        .srcSwitchId(segment.getSrcSwitch().getSwitchId())
                        .srcPort(segment.getSrcPort())
                        .destSwitchId(segment.getDestSwitch().getSwitchId())
                        .destPort(segment.getDestPort())
                        .latency(0)
                        .build())
                .collect(Collectors.toSet());

        return !Sets.intersection(pathSegments, flowSegments).isEmpty();
    }

    /**
     * Build a flow path entity for the flow using provided path and resources.
     *
     * @param flow a flow the flow path will be associated with.
     * @param pathResources resources to be used for the flow path.
     * @param path path to be used for the flow path.
     * @param cookie cookie to be used for the flow path.
     */
    public FlowPath buildFlowPath(Flow flow, PathResources pathResources, Path path, Cookie cookie) {
        FlowPath flowPath = FlowPath.builder()
                .flow(flow)
                .pathId(pathResources.getPathId())
                .srcSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getSrcSwitchId()).build()))
                .destSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getDestSwitchId()).build()))
                .meterId(pathResources.getMeterId())
                .cookie(cookie)
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .latency(path.getLatency())
                .build();
        flow.addPaths(flowPath);

        List<PathSegment> segments = path.getSegments().stream()
                .map(segment -> PathSegment.builder()
                        .path(flowPath)
                        .srcSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getSrcSwitchId()).build()))
                        .srcPort(segment.getSrcPort())
                        .destSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getDestSwitchId()).build()))
                        .destPort(segment.getDestPort())
                        .latency(segment.getLatency())
                        .build())
                .collect(Collectors.toList());
        flowPath.setSegments(segments);

        return flowPath;
    }
}
