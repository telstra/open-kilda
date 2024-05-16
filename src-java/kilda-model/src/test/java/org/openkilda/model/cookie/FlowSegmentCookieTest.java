/* Copyright 2020 Telstra Open Source
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

package org.openkilda.model.cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.cookie.CookieBase.CookieType;

import org.junit.jupiter.api.Test;

public class FlowSegmentCookieTest extends GenericCookieTest {
    @Test
    public void flowForwardDirectionFlagLocation() {
        testFieldReadWrite(-1L, ~0x4000_0000_0000_0000L, FlowSegmentCookie.FLOW_FORWARD_DIRECTION_FLAG, 0);
    }

    @Test
    public void flowReverseDirectionFlagLocation() {
        testFieldReadWrite(-1L, ~0x2000_0000_0000_0000L, FlowSegmentCookie.FLOW_REVERSE_DIRECTION_FLAG, 0);
    }

    @Test
    public void effectiveFlowIdFieldLocation() {
        testFieldReadWrite(-1L, ~0x0000_0000_000F_FFFF, FlowSegmentCookie.FLOW_EFFECTIVE_ID_FIELD, 0);
    }

    @Test
    public void ensureNoFieldsIntersection() {
        testFieldsIntersection(FlowSegmentCookie.ALL_FIELDS);
    }

    @Test
    public void changingOfFlowSegmentCookieTypeTest() {
        FlowSegmentCookie flowCookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, 10);
        assertEquals(CookieType.SERVICE_OR_FLOW_SEGMENT, flowCookie.getType());

        FlowSegmentCookie server42Cookie = flowCookie.toBuilder()
                .type(CookieType.SERVER_42_FLOW_RTT_INGRESS)
                .build();
        assertEquals(CookieType.SERVER_42_FLOW_RTT_INGRESS, server42Cookie.getType());
    }

    @Test
    public void changingOfFlowSegmentCookieServer42Test() {

        FlowSegmentCookie flowCookie = FlowSegmentCookie.builder()
                .direction(FlowPathDirection.FORWARD)
                .flowEffectiveId(10)
                .subType(FlowSubType.HA_SUB_FLOW_1)
                .build();

        assertEquals(CookieType.SERVICE_OR_FLOW_SEGMENT, flowCookie.getType());
        assertEquals(FlowSubType.HA_SUB_FLOW_1, flowCookie.getFlowSubType());

        FlowSegmentCookie server42Cookie = flowCookie.toBuilder()
                .haSubFlowServer42(true)
                .build();

        assertTrue(server42Cookie.isHaSubFlowServer42());
        assertEquals(FlowSubType.HA_SUB_FLOW_1, server42Cookie.getFlowSubType());


    }
}

