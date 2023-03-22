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

package org.openkilda.wfm.topology.flowhs.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YFlowRequestMapperTest {
    public static final PathComputationStrategy REQUEST_COMPUTATION_STRATEGY =
            PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH;
    private static final SwitchId SUB_FLOW_SWITCH_ID_1 = new SwitchId("00:01");
    private static final String SUB_FLOW_1_ID = "subflow1 id";
    private static final String SUB_FLOW_DESCRIPTION = "subflow description";
    private static final Instant SUB_FLOW_TIME_UPDATE = Instant.ofEpochMilli(100_000);
    private static final int SUB_FLOW_SHARED_END_POINT_VLAN_ID = 12;
    private static final int SUB_FLOW_SHARED_END_POINT_INNER_VLAN_ID = 13;
    private static final SubFlowSharedEndpointEncapsulation SUB_FLOW_SHARED_ENDPOINT_ENCAPSULATION =
            new SubFlowSharedEndpointEncapsulation(SUB_FLOW_SHARED_END_POINT_VLAN_ID,
                    SUB_FLOW_SHARED_END_POINT_INNER_VLAN_ID);
    private static final int SUB_FLOW_PORT_NUMBER = 1;
    private static final int SUB_FLOW_INNER_VLAN = 1000;
    private static final int SUB_FLOW_OUTER_VLAN = 1001;
    private static final FlowEndpoint SUB_FLOW_END_POINT =
            new FlowEndpoint(SUB_FLOW_SWITCH_ID_1, SUB_FLOW_PORT_NUMBER, SUB_FLOW_OUTER_VLAN, SUB_FLOW_INNER_VLAN);
    private static final FlowStatus SUB_FLOW_STATUS = FlowStatus.UP;
    public static final YFlowRequest.Type REQUEST_TYPE = YFlowRequest.Type.CREATE;
    public static final FlowEncapsulationType REQUEST_ENCAPSULATION_TYPE = FlowEncapsulationType.TRANSIT_VLAN;
    public static final long REQUEST_MAXIMUM_BANDWIDTH = 100500L;
    public static final boolean REQUEST_IGNORE_BANDWIDTH = true;
    public static final boolean REQUEST_STRICT_BANDWIDTH = true;
    public static final boolean REQUEST_PINNED = true;
    public static final int REQUEST_PRIORITY = 2;
    public static final long REQUEST_MAX_LATENCY = 42L;
    public static final long REQUEST_MAX_LATENCY_TIER_2 = 84L;
    public static final boolean REQUEST_PERIODIC_PINGS = true;
    public static final boolean REQUEST_ALLOCATE_PROTECTED_PATH = true;
    private static final SwitchId REQUEST_SWITCH_ID = new SwitchId("00:02");
    private static final int REQUEST_PORT_NUMBER = 50;
    public static final FlowEndpoint REQUEST_SHARED_END_POINT =
            new FlowEndpoint(REQUEST_SWITCH_ID, REQUEST_PORT_NUMBER);
    public static final String REQUEST_Y_FLOW_ID = "request y-flow id";
    public static final String REQUEST_DESCRIPTION = "request description";
    public static final String REQUEST_DIVERSE_FLOW_ID = "request diverse flow id";

    @Test
    public void toRequestedFlow() {
        List<SubFlowDto> subFlowDtos = Collections.singletonList(createSubFlowDto());
        YFlowRequest request = createYflowRequest(subFlowDtos);

        RequestedFlow actualRequestedFlow = new ArrayList<>(
                new YFlowRequestMapperImpl().toRequestedFlows(request)).get(0);

        // taken from the request
        assertEquals(REQUEST_SWITCH_ID, actualRequestedFlow.getSrcSwitch());
        assertEquals(REQUEST_PORT_NUMBER, actualRequestedFlow.getSrcPort());
        assertEquals(REQUEST_ENCAPSULATION_TYPE, actualRequestedFlow.getFlowEncapsulationType());
        assertEquals(REQUEST_MAXIMUM_BANDWIDTH, actualRequestedFlow.getBandwidth());
        assertEquals(REQUEST_IGNORE_BANDWIDTH, actualRequestedFlow.isIgnoreBandwidth());
        assertEquals(REQUEST_STRICT_BANDWIDTH, actualRequestedFlow.isStrictBandwidth());
        assertEquals(REQUEST_PINNED, actualRequestedFlow.isPinned());
        assertEquals(Long.valueOf(REQUEST_PRIORITY), Long.valueOf(actualRequestedFlow.getPriority()));
        assertEquals(Long.valueOf(REQUEST_MAX_LATENCY), actualRequestedFlow.getMaxLatency());
        assertEquals(Long.valueOf(REQUEST_MAX_LATENCY_TIER_2), actualRequestedFlow.getMaxLatencyTier2());
        assertEquals(REQUEST_PERIODIC_PINGS, actualRequestedFlow.isPeriodicPings());
        assertEquals(REQUEST_COMPUTATION_STRATEGY, actualRequestedFlow.getPathComputationStrategy());
        assertEquals(REQUEST_ALLOCATE_PROTECTED_PATH, actualRequestedFlow.isAllocateProtectedPath());
        assertNotEquals("A diverse flow id from the request is ignored",
                REQUEST_DIVERSE_FLOW_ID, actualRequestedFlow.getDiverseFlowId());
        assertNull(actualRequestedFlow.getDiverseFlowId());

        // taken from subflows
        assertEquals(SUB_FLOW_1_ID, actualRequestedFlow.getFlowId());
        assertEquals(SUB_FLOW_SHARED_END_POINT_VLAN_ID, actualRequestedFlow.getSrcVlan());
        assertEquals(SUB_FLOW_SHARED_END_POINT_INNER_VLAN_ID, actualRequestedFlow.getSrcInnerVlan());
        assertEquals(SUB_FLOW_SWITCH_ID_1, actualRequestedFlow.getDestSwitch());
        assertEquals(SUB_FLOW_PORT_NUMBER, actualRequestedFlow.getDestPort());
        assertEquals(SUB_FLOW_OUTER_VLAN, actualRequestedFlow.getDestVlan());
        assertEquals(SUB_FLOW_INNER_VLAN, actualRequestedFlow.getDestInnerVlan());
        assertEquals(SUB_FLOW_DESCRIPTION, actualRequestedFlow.getDescription());
        assertNotEquals(REQUEST_DESCRIPTION, actualRequestedFlow.getDescription());
        assertEquals(SUB_FLOW_SHARED_END_POINT_VLAN_ID, actualRequestedFlow.getSrcVlan());
        assertEquals(SUB_FLOW_SHARED_END_POINT_INNER_VLAN_ID, actualRequestedFlow.getSrcInnerVlan());
    }

    private YFlowRequest createYflowRequest(List<SubFlowDto> subFlowDtos) {
        return YFlowRequest.builder()
                .allocateProtectedPath(REQUEST_ALLOCATE_PROTECTED_PATH)
                .diverseFlowId(REQUEST_DIVERSE_FLOW_ID)
                .description(REQUEST_DESCRIPTION)
                .encapsulationType(REQUEST_ENCAPSULATION_TYPE)
                .ignoreBandwidth(REQUEST_IGNORE_BANDWIDTH)
                .maximumBandwidth(REQUEST_MAXIMUM_BANDWIDTH)
                .maxLatency(REQUEST_MAX_LATENCY)
                .maxLatencyTier2(REQUEST_MAX_LATENCY_TIER_2)
                .pathComputationStrategy(REQUEST_COMPUTATION_STRATEGY)
                .periodicPings(REQUEST_PERIODIC_PINGS)
                .pinned(REQUEST_PINNED)
                .priority(REQUEST_PRIORITY)
                .sharedEndpoint(REQUEST_SHARED_END_POINT)
                .strictBandwidth(REQUEST_STRICT_BANDWIDTH)
                .subFlows(subFlowDtos)
                .type(REQUEST_TYPE)
                .yFlowId(REQUEST_Y_FLOW_ID)
                .build();
    }

    private SubFlowDto createSubFlowDto() {


        return SubFlowDto.builder()
                .endpoint(SUB_FLOW_END_POINT)
                .description(SUB_FLOW_DESCRIPTION)
                .flowId(SUB_FLOW_1_ID)
                .timeUpdate(SUB_FLOW_TIME_UPDATE)
                .sharedEndpoint(SUB_FLOW_SHARED_ENDPOINT_ENCAPSULATION)
                .status(SUB_FLOW_STATUS)
                .build();
    }
}
