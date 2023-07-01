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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.info.flow.HaFlowPingResponse;
import org.openkilda.messaging.info.flow.SubFlowPingPayload;
import org.openkilda.messaging.info.flow.UniSubFlowPingPayload;
import org.openkilda.messaging.model.FlowDirection;
import org.openkilda.messaging.model.PingMeters;
import org.openkilda.wfm.topology.ping.model.Group;
import org.openkilda.wfm.topology.ping.model.Group.Type;
import org.openkilda.wfm.topology.ping.model.GroupId;
import org.openkilda.wfm.topology.ping.model.PingContext;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.List;

public class OnDemandResultManagerTest {
    private static final String HA_FLOW_ID = "haFlowId";
    private static final String SUB_FLOW_1_NAME = HA_FLOW_ID + "-a";
    private static final String SUB_FLOW_2_NAME = HA_FLOW_ID + "-b";

    @Test
    public void buildHaFlowPingResponse() {
        OnDemandResultManager onDemandResultManager = new OnDemandResultManager();
        List<PingContext> records = generatePingContexts();

        Group group = new Group(new GroupId(4), Type.HA_FLOW, records);
        HaFlowPingResponse response = onDemandResultManager.buildHaFlowPingResponse(group);

        List<SubFlowPingPayload> subFlows = Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_1_NAME,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(true, null, 1)),
                new SubFlowPingPayload(SUB_FLOW_2_NAME,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(true, null, 1)));

        HaFlowPingResponse expectedResponse = new HaFlowPingResponse(HA_FLOW_ID, null, subFlows);

        Assertions.assertEquals(expectedResponse, response);
    }

    private List<PingContext> generatePingContexts() {
        return Lists.newArrayList(PingContext.builder()
                .haFlowId(HA_FLOW_ID)
                .haSubFlowId(SUB_FLOW_1_NAME)
                .direction(FlowDirection.FORWARD)
                .meters(new PingMeters(1L, 2L, 3L))
                .build(),
        PingContext.builder()
                .haFlowId(HA_FLOW_ID)
                .haSubFlowId(SUB_FLOW_1_NAME)
                .direction(FlowDirection.REVERSE)
                .meters(new PingMeters(1L, 2L, 3L))
                .build(),
        PingContext.builder()
                .haFlowId(HA_FLOW_ID)
                .haSubFlowId(SUB_FLOW_2_NAME)
                .direction(FlowDirection.FORWARD)
                .meters(new PingMeters(1L, 2L, 3L))
                .build(),
        PingContext.builder()
                .haFlowId(HA_FLOW_ID)
                .haSubFlowId(SUB_FLOW_2_NAME)
                .direction(FlowDirection.REVERSE)
                .meters(new PingMeters(1L, 2L, 3L))
                .build());
    }
}
