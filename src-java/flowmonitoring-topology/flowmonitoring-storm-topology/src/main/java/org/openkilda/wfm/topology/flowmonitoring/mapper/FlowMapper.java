/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowmonitoring.mapper;

import org.openkilda.messaging.info.flow.UpdateFlowCommand;
import org.openkilda.messaging.info.stats.FlowRttStatsData;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.PathSegment;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowPathLatency;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowState;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FlowMapper {
    FlowMapper INSTANCE = Mappers.getMapper(FlowMapper.class);

    @Mapping(target = "forwardPath", source = "forwardPath.segments")
    @Mapping(target = "reversePath", source = "reversePath.segments")
    FlowState toFlowState(Flow flow);

    @Mapping(target = "forwardPath", source = "info.flowPath.forwardPath")
    @Mapping(target = "reversePath", source = "info.flowPath.reversePath")
    FlowState toFlowState(UpdateFlowCommand info);

    /**
     * Convert flow path representation.
     */
    default List<Link> toLinks(List<PathSegment> pathSegments) {
        if (pathSegments == null) {
            return Collections.emptyList();
        }
        return pathSegments.stream().map(this::toLink).collect(Collectors.toList());
    }

    /**
     * Convert flow path representation.
     */
    default List<Link> pathNodesToLinks(List<PathNodePayload> pathSegments) {
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < pathSegments.size() - 1; i++) {
            links.add(Link.builder()
                    .srcSwitchId(pathSegments.get(i).getSwitchId())
                    .srcPort(pathSegments.get(i).getOutputPort())
                    .destSwitchId(pathSegments.get(i + 1).getSwitchId())
                    .destPort(pathSegments.get(i + 1).getInputPort())
                    .build());
        }
        return links;
    }

    Link toLink(PathSegment pathSegment);

    @Mapping(target = "timestamp", expression = "java(org.openkilda.wfm.share.utils.TimestampHelper"
            + ".noviflowTimestampToInstant(data.getT1()))")
    @Mapping(target = "latency", expression = "java(org.openkilda.wfm.share.utils.TimestampHelper"
            + ".noviflowTimestampsToDuration(data.getT0(), data.getT1()))")
    FlowPathLatency toFlowPathLatency(FlowRttStatsData data);
}
