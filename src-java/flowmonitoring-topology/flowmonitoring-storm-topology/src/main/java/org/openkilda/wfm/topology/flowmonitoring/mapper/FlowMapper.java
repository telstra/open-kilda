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
import org.openkilda.wfm.share.utils.TimestampHelper;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowPathLatency;
import org.openkilda.wfm.topology.flowmonitoring.model.FlowState;
import org.openkilda.wfm.topology.flowmonitoring.model.Link;

import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Mapper
public abstract class FlowMapper {
    public static final FlowMapper INSTANCE = Mappers.getMapper(FlowMapper.class);

    @Mapping(target = "forwardPath", source = "forwardPath.segments")
    @Mapping(target = "reversePath", source = "reversePath.segments")
    @Mapping(target = "forwardPathLatency", ignore = true)
    @Mapping(target = "reversePathLatency", ignore = true)
    public abstract FlowState toFlowState(Flow flow);

    @Mapping(target = "forwardPath", source = "info.flowPath.forwardPath")
    @Mapping(target = "reversePath", source = "info.flowPath.reversePath")
    @Mapping(target = "forwardPathLatency", ignore = true)
    @Mapping(target = "reversePathLatency", ignore = true)
    public abstract FlowState toFlowState(UpdateFlowCommand info);

    /**
     * Convert flow path representation.
     */
    protected List<Link> toLinks(List<PathSegment> pathSegments) {
        if (pathSegments == null) {
            return Collections.emptyList();
        }
        return pathSegments.stream().map(this::toLink).collect(Collectors.toList());
    }

    /**
     * Convert flow path representation.
     */
    protected List<Link> pathNodesToLinks(List<PathNodePayload> pathSegments) {
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

    public abstract Link toLink(PathSegment pathSegment);

    /**
     * Convert {@link FlowRttStatsData} to {@link FlowPathLatency}.
     */
    public FlowPathLatency toFlowPathLatency(@NonNull FlowRttStatsData data) {
        FlowPathLatency latency = new FlowPathLatency();
        latency.setTimestamp(TimestampHelper.noviflowTimestampToInstant(data.getT1()));
        latency.setLatency(TimestampHelper.noviflowTimestampsToDuration(data.getT0(), data.getT1()));
        return latency;
    }
}
