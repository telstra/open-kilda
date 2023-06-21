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

package org.openkilda.wfm.topology.flowhs.utils;

import static java.util.Collections.emptyList;

import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.flow.resources.HaFlowResources.HaPathResources;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaFlowPathIds;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaPathIdsPairBuilder;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flowhs.model.CrossingPaths;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public final class HaFlowUtils {
    private HaFlowUtils() {
    }

    /**
     * Builds HA resources object.
     */
    public static HaFlowResources buildHaResources(
            HaFlowPath forwardPath, HaFlowPath reversePath, EncapsulationResources encapsulationResources) {
        return HaFlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getFlowEffectiveId())
                .forward(buildHaPathResources(forwardPath, encapsulationResources))
                .reverse(buildHaPathResources(reversePath, encapsulationResources))
                .build();
    }

    private static HaPathResources buildHaPathResources(
            HaFlowPath haFlowPath, EncapsulationResources encapsulationResources) {
        return HaPathResources.builder()
                .pathId(haFlowPath.getHaPathId())
                .sharedMeterId(haFlowPath.getSharedPointMeterId())
                .yPointMeterId(haFlowPath.getYPointMeterId())
                .yPointGroupId(haFlowPath.getYPointGroupId())
                .encapsulationResources(encapsulationResources)
                .subPathIds(buildSubPathIdMap(haFlowPath.getSubPaths()))
                .subPathMeters(buildSubPathMeterIdMap(haFlowPath.getSubPaths()))
                .build();
    }

    private static Map<String, PathId> buildSubPathIdMap(Collection<FlowPath> subPaths) {
        Map<String, PathId> result = new HashMap<>();
        if (subPaths != null) {
            for (FlowPath subPath : subPaths) {
                if (result.containsKey(subPath.getHaSubFlowId())) {
                    log.warn("Found several paths which are belongs to HA-sub flow {}. Paths: {}, {}",
                            subPath.getHaSubFlowId(), result.get(subPath.getHaSubFlowId()), subPath.getPathId());
                }
                result.put(subPath.getHaSubFlowId(), subPath.getPathId());
            }
        }
        return result;
    }

    private static Map<String, MeterId> buildSubPathMeterIdMap(Collection<FlowPath> subPaths) {
        Map<String, MeterId> result = new HashMap<>();
        if (subPaths != null) {
            for (FlowPath subPath : subPaths) {
                if (result.containsKey(subPath.getHaSubFlowId())) {
                    log.warn("Found several paths which are belongs to HA-sub flow {}. Paths: {}, {}",
                            subPath.getHaSubFlowId(), result.get(subPath.getHaSubFlowId()), subPath.getPathId());
                }
                result.put(subPath.getHaSubFlowId(), subPath.getMeterId());
            }
        }
        return result;
    }

    /**
     * Returns PathId or null.
     */
    public static PathId getPathId(HaFlowPath path) {
        return path != null ? path.getHaPathId() : null;
    }

    /**
     * Builds CrossingPaths object from flowPaths.
     */
    public static CrossingPaths definePaths(List<FlowPath> flowPaths) {
        List<FlowPath> nonEmptyPaths = flowPaths.stream()
                .filter(flowPath -> !flowPath.getSegments().isEmpty())
                .collect(Collectors.toList());
        PathInfoData sharedPath = FlowPathMapper.INSTANCE.map(nonEmptyPaths.size() >= 2
                ? IntersectionComputer.calculatePathIntersectionFromSource(nonEmptyPaths) : emptyList());

        List<SubFlowPathDto> subFlowPaths = flowPaths.stream()
                .map(flowPath -> new SubFlowPathDto(flowPath.getHaSubFlowId(), FlowPathMapper.INSTANCE.map(flowPath)))
                .collect(Collectors.toList());

        return CrossingPaths.builder()
                .sharedPath(sharedPath)
                .subFlowPaths(subFlowPaths)
                .build();
    }

    /**
     * Builds CrossingPaths object from haFlowPath.
     */
    public static CrossingPaths buildCrossingPaths(HaFlowPath haFlowPath, CrossingPaths defaultValue) {
        return Optional.ofNullable(haFlowPath)
                .map(HaFlowPath::getSubPaths)
                .map(HaFlowUtils::definePaths)
                .orElse(defaultValue);
    }

    /**
     * Builds HaPathIdsPair.
     */
    public static HaPathIdsPair buildPathIds(HaFlowPath forward, HaFlowPath reverse) {
        HaPathIdsPairBuilder haPathIdsPairBuilder = HaPathIdsPair.builder();
        if (forward != null) {
            haPathIdsPairBuilder.forward(buildPathIds(forward));
        }
        if (reverse != null) {
            haPathIdsPairBuilder.reverse(buildPathIds(reverse));
        }
        if (forward != null || reverse != null) {
            return haPathIdsPairBuilder.build();
        }
        return null;
    }

    private static HaFlowPathIds buildPathIds(HaFlowPath haFlowPath) {
        return HaFlowPathIds.builder()
                .haPathId(haFlowPath.getHaPathId())
                .subPathIds(buildSubPathIdMap(haFlowPath.getSubPaths()))
                .build();
    }


    /**
     * Returns forward PathId or null.
     */
    public static PathId getForwardPathId(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getForward)
                .map(HaFlowPathIds::getHaPathId)
                .orElse(null);
    }

    /**
     * Returns reverse PathId or null.
     */
    public static PathId getReversePathId(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getReverse)
                .map(HaFlowPathIds::getHaPathId)
                .orElse(null);
    }

    /**
     * Returns forward sub path IDs or null.
     */
    public static Collection<PathId> getForwardSubPathIds(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getForward)
                .map(HaFlowUtils::getSubPathIds)
                .orElse(new ArrayList<>());
    }

    /**
     * Returns reverse sub path IDs or null.
     */
    public static Collection<PathId> getReverseSubPathIds(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getReverse)
                .map(HaFlowUtils::getSubPathIds)
                .orElse(new ArrayList<>());
    }

    private static Collection<PathId> getSubPathIds(HaFlowPathIds haFlowPathIds) {
        return Optional.ofNullable(haFlowPathIds)
                .map(HaFlowPathIds::getSubPathIds)
                .map(Map::values)
                .orElse(new ArrayList<>());
    }
}
