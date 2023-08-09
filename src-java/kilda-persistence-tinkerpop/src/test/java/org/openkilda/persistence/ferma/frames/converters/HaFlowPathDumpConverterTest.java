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

package org.openkilda.persistence.ferma.frames.converters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.history.HaFlowEventDump.HaFlowPathDump;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

public class HaFlowPathDumpConverterTest {
    @Test
    public void roundTrip() {
        HaFlowPathDump source = HaFlowPathDump.builder()
                .haPathId("HA flow path ID")
                .yPointGroupId(GroupId.MIN_FLOW_GROUP_ID.toString())
                .yPointSwitchId("00:03")
                .yPointMeterId(MeterId.LACP_REPLY_METER_ID.toString())
                .timeCreate(Instant.now().minus(1, ChronoUnit.DAYS).toString())
                .timeModify(Instant.now().toString())
                .sharedPointMeterId(MeterId.LACP_REPLY_METER_ID.toString())
                .cookie(FlowSegmentCookie.builder().direction(FlowPathDirection.FORWARD).build().toString())
                .ignoreBandwidth(true)
                .status(FlowPathStatus.ACTIVE.toString())
                .paths(Collections.emptyList())
                .haSubFlows(Collections.emptyList())
                .build();

        HaFlowPathDumpConverter converter = new HaFlowPathDumpConverter();

        HaFlowPathDump target = converter.toEntityAttribute(converter.toGraphProperty(source));

        assertEquals(source, target);
    }
}
