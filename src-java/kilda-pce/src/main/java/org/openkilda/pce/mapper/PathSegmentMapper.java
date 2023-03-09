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

package org.openkilda.pce.mapper;

import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.pce.Path;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public abstract class PathSegmentMapper {

    public static PathSegmentMapper INSTANCE = Mappers.getMapper(PathSegmentMapper.class);

    /**
     * Converts PCE's path segment representation into OpenKilda model PathSegment.
     * @param segments PCE path segment
     * @return OpenKilda model path segment
     */
    public List<PathSegment> toPathSegmentList(List<Path.Segment> segments) {
        return segments.stream().map(s ->
                PathSegment.builder()
                        .pathId(new PathId("A virtual PCE path " + s.hashCode()))
                        .srcSwitch(Switch.builder().switchId(s.getSrcSwitchId()).build())
                        .srcPort(s.getSrcPort())
                        .destSwitch(Switch.builder().switchId(s.getDestSwitchId()).build())
                        .destPort(s.getDestPort())
                        .latency(s.getLatency())
                        .build())
                .collect(Collectors.toList());
    }
}
