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

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.pce.HaPath;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.HaFlowResources.HaPathResources;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.map.LazyMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
public class FlowPathBuilder {
    private SwitchPropertiesRepository switchPropertiesRepository;
    private KildaConfigurationRepository kildaConfigurationRepository;

    /**
     * Check whether the path and flow path represent the same.
     *
     * @param path the path to evaluate.
     * @param flowPath the flow path to evaluate.
     */
    public boolean isSamePath(Path path, FlowPath flowPath) {
        if (!path.getSrcSwitchId().equals(flowPath.getSrcSwitchId())
                || !path.getDestSwitchId().equals(flowPath.getDestSwitchId())
                || path.getSegments().size() != flowPath.getSegments().size()) {
            return false;
        }

        Iterator<Segment> pathIt = path.getSegments().iterator();
        Iterator<PathSegment> flowPathIt = flowPath.getSegments().iterator();
        while (pathIt.hasNext() && flowPathIt.hasNext()) {
            Path.Segment pathSegment = pathIt.next();
            PathSegment flowSegment = flowPathIt.next();
            if (!pathSegment.getSrcSwitchId().equals(flowSegment.getSrcSwitchId())
                    || !pathSegment.getDestSwitchId().equals(flowSegment.getDestSwitchId())
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
        Set<Segment> pathSegments = buildSegmentSet(path);
        Set<Segment> flowSegments = buildSegmentSet(flowPath);

        return !Sets.intersection(pathSegments, flowSegments).isEmpty();
    }

    /**
     * Check whether the ha-path and ha-flow path overlap.
     *
     * @param haPath the ha-path to evaluate.
     * @param haFlowPath the ha-flow path to evaluate.
     */
    public boolean arePathsOverlapped(HaPath haPath, HaFlowPath haFlowPath) {
        for (Path subPath : haPath.getSubPaths()) {
            Set<Segment> pathSegments = buildSegmentSet(subPath);
            for (FlowPath haFlowSubPath : haFlowPath.getSubPaths()) {
                Set<Segment> haPathSegments = buildSegmentSet(haFlowSubPath);
                if (!Sets.intersection(pathSegments, haPathSegments).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Build a flow path entity for the flow using provided path and resources.
     *
     * @param flow a flow the flow path will be associated with.
     * @param pathResources resources to be used for the flow path.
     * @param path path to be used for the flow path.
     * @param cookie cookie to be used for the flow path.
     * @param forceToIgnoreBandwidth force path to ignore bandwidth.
     * @param sharedBandwidthGroupId a shared bandwidth group to be set for the path
     */
    public FlowPath buildFlowPath(Flow flow, PathResources pathResources, Path path, FlowSegmentCookie cookie,
                                  boolean forceToIgnoreBandwidth, String sharedBandwidthGroupId) {
        List<PathSegment> segments = buildPathSegments(pathResources.getPathId(), path.getSegments(),
                flow.getBandwidth(), flow.isIgnoreBandwidth() || forceToIgnoreBandwidth, sharedBandwidthGroupId);
        return buildFlowPath(flow, pathResources, path.getLatency(), path.getSrcSwitchId(), path.getDestSwitchId(),
                segments, cookie, forceToIgnoreBandwidth, sharedBandwidthGroupId);
    }

    /**
     * Build a flow path entity for the flow using provided resources and segments.
     *
     * @param flow a flow the flow path will be associated with.
     * @param pathResources resources to be used for the flow path.
     * @param pathLatency path to be used for the flow path.
     * @param segments segments to be used for the flow path.
     * @param cookie cookie to be used for the flow path.
     * @param forceToIgnoreBandwidth force path to ignore bandwidth.
     * @param sharedBandwidthGroupId a shared bandwidth group to be set for the path
     */
    public FlowPath buildFlowPath(Flow flow, PathResources pathResources, long pathLatency,
                                  SwitchId srcSwitchId, SwitchId destSwitchId,
                                  List<PathSegment> segments, FlowSegmentCookie cookie,
                                  boolean forceToIgnoreBandwidth, String sharedBandwidthGroupId) {
        Map<SwitchId, Switch> switches = new HashMap<>();
        switches.put(flow.getSrcSwitchId(), flow.getSrcSwitch());
        switches.put(flow.getDestSwitchId(), flow.getDestSwitch());

        Switch srcSwitch = switches.get(srcSwitchId);
        if (srcSwitch == null) {
            throw new IllegalArgumentException(format("Path %s has different end-point %s than flow %s",
                    pathResources.getPathId(), srcSwitchId, flow.getFlowId()));
        }
        Switch destSwitch = switches.get(destSwitchId);
        if (destSwitch == null) {
            throw new IllegalArgumentException(format("Path %s has different end-point %s than flow %s",
                    pathResources.getPathId(), destSwitchId, flow.getFlowId()));
        }

        Map<SwitchId, SwitchProperties> switchProperties = getSwitchProperties(pathResources.getPathId());
        boolean srcWithMultiTable = switchProperties.get(srcSwitch.getSwitchId()) != null
                ? switchProperties.get(srcSwitch.getSwitchId()).isMultiTable()
                : kildaConfigurationRepository.getOrDefault().getUseMultiTable();
        boolean dstWithMultiTable = switchProperties.get(destSwitch.getSwitchId()) != null
                ? switchProperties.get(destSwitch.getSwitchId()).isMultiTable()
                : kildaConfigurationRepository.getOrDefault().getUseMultiTable();

        return FlowPath.builder()
                .pathId(pathResources.getPathId())
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .meterId(pathResources.getMeterId())
                .cookie(cookie)
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth() || forceToIgnoreBandwidth)
                .latency(pathLatency)
                .segments(segments)
                .srcWithMultiTable(srcWithMultiTable)
                .destWithMultiTable(dstWithMultiTable)
                .sharedBandwidthGroupId(sharedBandwidthGroupId)
                .build();
    }

    /**
     * Build a sub path entity for ha-flow path using provided resources and segments.
     *
     * @param haFlow a ha-flow the sub path will be associated with.
     * @param pathResources resources to be used for the sub path.
     * @param path network path for the sub path.
     * @param srcSwitch source switch DB object of the future sub path.
     * @param dstSwitch destination switch DB object of the future sub path.
     * @param cookie cookie to be used for the sub path.
     */
    public FlowPath buildHaSubPath(
            HaFlow haFlow, PathResources pathResources, Path path, Switch srcSwitch, Switch dstSwitch,
            FlowSegmentCookie cookie) {

        if (!srcSwitch.getSwitchId().equals(path.getSrcSwitchId())) {
            throw new IllegalArgumentException(format(
                    "Path %s has different source switch id %s than ha-flow %s. Ha-flow endpoint switch id is %s",
                    pathResources.getPathId(), path.getSrcSwitchId(), haFlow.getHaFlowId(), srcSwitch.getSwitchId()));
        }
        if (!dstSwitch.getSwitchId().equals(path.getDestSwitchId())) {
            throw new IllegalArgumentException(format(
                    "Path %s has different destination switch id %s than ha-flow %s. Ha-flow endpoint switch id is %s",
                    pathResources.getPathId(), path.getDestSwitchId(), haFlow.getHaFlowId(), dstSwitch.getSwitchId()));
        }
        String sharedBandwidthGroupId = haFlow.getHaFlowId();
        List<PathSegment> segments = buildPathSegments(pathResources.getPathId(), path.getSegments(),
                haFlow.getMaximumBandwidth(), haFlow.isIgnoreBandwidth(), sharedBandwidthGroupId);

        return FlowPath.builder()
                .pathId(pathResources.getPathId())
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .meterId(pathResources.getMeterId())
                .cookie(cookie)
                .bandwidth(haFlow.getMaximumBandwidth())
                .ignoreBandwidth(haFlow.isIgnoreBandwidth())
                .latency(path.getLatency())
                .segments(segments)
                .srcWithMultiTable(true)
                .destWithMultiTable(true)
                .sharedBandwidthGroupId(sharedBandwidthGroupId)
                .build();
    }

    /**
     * Build a flow path entity for the flow using provided resources and segments.
     *
     * @param haFlow an HA Flow that will be associated with the created HA flow path.
     * @param pathResources resources to be used for the ha-flow path.
     * @param haPath ha-path to be used for the ha-flow path.
     * @param cookie cookie to be used for the ha-flow path.
     */
    public HaFlowPath buildHaFlowPath(
            HaFlow haFlow, HaPathResources pathResources, HaPath haPath, FlowSegmentCookie cookie) {

        if (!haFlow.getSharedSwitchId().equals(haPath.getSharedSwitchId())) {
            throw new IllegalArgumentException(format("Shared endpoint switch id %s of ha-path %s is not equal to "
                            + "ha-flow %s shared endpoint switch id %s",
                    haPath.getSharedSwitchId(), pathResources.getPathId(), haFlow.getHaFlowId(),
                    haFlow.getSharedSwitchId()));
        }

        Set<SwitchId> haFlowEndpointSwitchIds = haFlow.getHaSubFlows().stream()
                .map(HaSubFlow::getEndpointSwitchId).collect(Collectors.toSet());
        haFlowEndpointSwitchIds.add(haFlow.getSharedSwitchId());
        for (Path subPath : haPath.getSubPaths()) {
            for (SwitchId switchId : new SwitchId[] {subPath.getSrcSwitchId(), subPath.getDestSwitchId()}) {
                if (!haFlowEndpointSwitchIds.contains(switchId)) {
                    throw new IllegalArgumentException(format("Endpoint switch id %s of ha-sub-path %s is not equal "
                                    + "to ha-flow %s endpoint switch ids %s",
                            switchId, subPath, haFlow.getHaFlowId(), haFlowEndpointSwitchIds));
                }
            }
        }

        return HaFlowPath.builder()
                .haPathId(pathResources.getPathId())
                .sharedSwitch(haFlow.getSharedSwitch())
                .yPointSwitchId(haPath.getYPointSwitchId())
                .cookie(cookie)
                .sharedPointMeterId(pathResources.getSharedMeterId())
                .yPointMeterId(pathResources.getYPointMeterId())
                .yPointGroupId(pathResources.getYPointGroupId())
                .bandwidth(haFlow.getMaximumBandwidth())
                .ignoreBandwidth(haFlow.isIgnoreBandwidth())
                .build();
    }

    /**
     * Build a path segments using provided path.
     *
     * @param pathId a pathId the segments will be associated with.
     * @param segments segments to be used for the path.
     * @param bandwidth bandwidth to be used for the segments.
     * @param ignoreBandwidth ignore bandwidth be used for the segments.
     * @param sharedBandwidthGroupId a shared bandwidth group to be set for the segments
     */
    public List<PathSegment> buildPathSegments(PathId pathId, List<Segment> segments, long bandwidth,
                                               boolean ignoreBandwidth, String sharedBandwidthGroupId) {
        Map<SwitchId, SwitchProperties> switchProperties = getSwitchProperties(pathId);

        List<PathSegment> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Path.Segment segment = segments.get(i);

            SwitchProperties srcSwitchProperties = switchProperties.get(segment.getSrcSwitchId());
            SwitchProperties dstSwitchProperties = switchProperties.get(segment.getDestSwitchId());

            result.add(PathSegment.builder()
                    .seqId(i)
                    .pathId(pathId)
                    .srcSwitch(Switch.builder().switchId(segment.getSrcSwitchId()).build())
                    .srcPort(segment.getSrcPort())
                    .srcWithMultiTable(srcSwitchProperties.isMultiTable())
                    .destSwitch(Switch.builder().switchId(segment.getDestSwitchId()).build())
                    .destPort(segment.getDestPort())
                    .destWithMultiTable(dstSwitchProperties.isMultiTable())
                    .latency(segment.getLatency())
                    .bandwidth(bandwidth)
                    .ignoreBandwidth(ignoreBandwidth)
                    .sharedBandwidthGroupId(sharedBandwidthGroupId)
                    .build());
        }

        return result;
    }

    private static Set<Segment> buildSegmentSet(FlowPath flowPath) {
        return flowPath.getSegments().stream()
                .map(segment -> Segment.builder()
                        .srcSwitchId(segment.getSrcSwitchId())
                        .srcPort(segment.getSrcPort())
                        .destSwitchId(segment.getDestSwitchId())
                        .destPort(segment.getDestPort())
                        .build())
                .collect(Collectors.toSet());
    }

    private static Set<Segment> buildSegmentSet(Path path) {
        return path.getSegments().stream()
                .map(segment -> Segment.builder()
                        .srcSwitchId(segment.getSrcSwitchId())
                        .srcPort(segment.getSrcPort())
                        .destSwitchId(segment.getDestSwitchId())
                        .destPort(segment.getDestPort())
                        .build())
                .collect(Collectors.toSet());
    }

    private LazyMap<SwitchId, SwitchProperties> getSwitchProperties(PathId pathId) {
        return LazyMap.lazyMap(new HashMap<>(), switchId ->
                switchPropertiesRepository.findBySwitchId(switchId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                format("Path %s has end-point %s without switch properties", pathId, switchId))));
    }
}
