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

import static org.junit.Assert.assertEquals;

import org.openkilda.model.FlowStatus;
import org.openkilda.model.history.HaFlowEventDump.HaSubFlowDump;
import org.openkilda.model.history.HaFlowEventDump.HaSubFlowDumpWrapper;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class HaSubFlowDumpWrapperConverterTest {

    @Test
    public void roundTrip() {
        HaSubFlowDumpWrapper source = HaSubFlowDumpWrapper.builder()
                .haSubFlowDumpList(Lists.newArrayList(createHaSubFlowDump("1"),
                        createHaSubFlowDump("2")))
                .build();

        HaSubFlowDumpWrapperConverter converter = new HaSubFlowDumpWrapperConverter();

        HaSubFlowDumpWrapper target = converter.toEntityAttribute(converter.toGraphProperty(source));

        assertEquals(source, target);
    }

    private HaSubFlowDump createHaSubFlowDump(String id) {
        return HaSubFlowDump.builder()
                .haSubFlowId(id)
                .haFlowId("HA flow ID")
                .endpointVlan(1001)
                .endpointInnerVlan(2001)
                .status(FlowStatus.UP)
                .endpointPort(12)
                .description("some description")
                .timeCreate(Instant.now().minus(1, ChronoUnit.HOURS).toString())
                .endpointSwitchId("00:00:00:00:00:00:12")
                .timeModify(Instant.now().toString())
                .build();
    }
}
