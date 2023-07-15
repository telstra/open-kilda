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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.Path.Segment;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class PathSegmentMapperTest {

    @Test
    public void toPathSegmentTest() {
        Segment segment1 = Segment.builder()
                .latency(12L)
                .srcSwitchId(new SwitchId("00:01"))
                .srcPort(1)
                .destSwitchId(new SwitchId("00:02"))
                .destPort(2)
                .build();

        Segment segment2 = Segment.builder()
                .latency(22L)
                .srcSwitchId(new SwitchId("00:02"))
                .srcPort(3)
                .destSwitchId(new SwitchId("00:03"))
                .destPort(4)
                .build();

        List<PathSegment> actualMappedSegments =
                PathSegmentMapper.INSTANCE.toPathSegmentList(Arrays.asList(segment1, segment2));

        Set<PathSegment> expectedSegments = Sets.newHashSet(
                PathSegment.builder()
                        .pathId(new PathId("A virtual PCE path " + segment1.hashCode()))
                        .latency(12L)
                        .srcSwitch(Switch.builder()
                                .switchId(new SwitchId("00:01"))
                                .build())
                        .srcPort(1)
                        .destSwitch(Switch.builder()
                                .switchId(new SwitchId("00:02"))
                                .build())
                        .destPort(2)
                        .build(),
                PathSegment.builder()
                        .pathId(new PathId("A virtual PCE path " + segment2.hashCode()))
                        .latency(22L)
                        .srcSwitch(Switch.builder()
                                .switchId(new SwitchId("00:02"))
                                .build())
                        .srcPort(3)
                        .destSwitch(Switch.builder()
                                .switchId(new SwitchId("00:03"))
                                .build())
                        .destPort(4)
                        .build());

        assertEquals(actualMappedSegments.size(), 2);
        assertEquals(expectedSegments, Sets.newHashSet(actualMappedSegments));
    }
}
