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

import org.openkilda.messaging.command.haflow.HaFlowDto;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest.Type;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlow.HaSharedEndpoint;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HaFlowMapperTest {
    private static final String HA_FLOW_ID = "ha_flow_id";
    private static final String SUB_FLOW_1_NAME = "flow_1";
    private static final String SUB_FLOW_2_NAME = "flow_2";
    private static final String FLOW_3 = "flow_3";
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final int PORT_1 = 1;
    private static final int PORT_2 = 2;
    private static final int PORT_3 = 3;
    private static final int VLAN_1 = 4;
    private static final int VLAN_2 = 5;
    private static final int VLAN_3 = 6;
    private static final int INNER_VLAN_1 = 7;
    private static final int INNER_VLAN_2 = 8;
    private static final int INNER_VLAN_3 = 9;
    private static final int BANDWIDTH = 100;
    private static final long MAX_LATENCY = 10L;
    private static final long MAX_LATENCY_TIER_2 = 20L;
    private static final int PRIORITY = 11;
    private static final String DESC_1 = "desc1";
    private static final String DESC_2 = "desc2";
    private static final String DESC_3 = "desc3";
    private static final HaSubFlowDto SUB_FLOW_1 = new HaSubFlowDto(
            SUB_FLOW_1_NAME, new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1), FlowStatus.UP, DESC_2,
            Instant.MIN, Instant.MAX);
    private static final HaSubFlowDto SUB_FLOW_2 = new HaSubFlowDto(
            SUB_FLOW_2_NAME, new FlowEndpoint(SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2), FlowStatus.IN_PROGRESS,
            DESC_3, Instant.MAX, Instant.MIN);
    private static final FlowEndpoint SHARED_ENDPOINT = new FlowEndpoint(
            SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3);


    private final HaFlowMapper mapper = HaFlowMapper.INSTANCE;

    @Test
    public void haFlowRequestMappingTest() {
        HaFlowRequest request = new HaFlowRequest(
                HA_FLOW_ID, SHARED_ENDPOINT, BANDWIDTH, PathComputationStrategy.COST, FlowEncapsulationType.VXLAN,
                MAX_LATENCY, MAX_LATENCY_TIER_2, true, false, true, PRIORITY, false, DESC_1, true, FLOW_3,
                Lists.newArrayList(SUB_FLOW_1, SUB_FLOW_2), Type.CREATE);
        request.setSubFlows(Lists.newArrayList(SUB_FLOW_1, SUB_FLOW_2));
        HaFlow result = mapper.toHaFlow(request);
        assertEquals(request.getHaFlowId(), result.getHaFlowId());
        assertEquals(request.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(request.getPathComputationStrategy(), result.getPathComputationStrategy());
        assertEquals(request.getEncapsulationType(), result.getEncapsulationType());
        assertEquals(request.getMaxLatency(), result.getMaxLatency());
        assertEquals(request.getMaxLatencyTier2(), result.getMaxLatencyTier2());
        assertEquals(request.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(request.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(request.isPinned(), result.isPinned());
        assertEquals(request.getPriority(), result.getPriority());
        assertEquals(request.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(request.getDescription(), result.getDescription());
        assertEquals(request.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertSharedEndpoint(request.getSharedEndpoint(), result.getSharedEndpoint());
        // subflows must be mapped separately
        assertEquals(0, result.getSubFlows().size());
    }

    @Test
    public void getResponseTest() {
        HaFlow haFlow = new HaFlow(
                HA_FLOW_ID, new HaSharedEndpoint(SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3), BANDWIDTH,
                PathComputationStrategy.COST, FlowEncapsulationType.VXLAN, MAX_LATENCY, MAX_LATENCY_TIER_2, true, false,
                true, PRIORITY, false, DESC_1, true, FlowStatus.UP);
        haFlow.setSubFlows(Sets.newHashSet(
                HaSubFlow.builder().haSubFlowId(SUB_FLOW_1_NAME)
                        .endpointSwitchId(SWITCH_ID_1)
                        .endpointPort(PORT_1)
                        .endpointVlan(VLAN_1)
                        .endpointInnerVlan(INNER_VLAN_1)
                        .status(FlowStatus.UP)
                        .description(DESC_2)
                        .build(),
                HaSubFlow.builder().haSubFlowId(SUB_FLOW_2_NAME)
                        .endpointSwitchId(SWITCH_ID_2)
                        .endpointPort(PORT_2)
                        .endpointVlan(VLAN_2)
                        .endpointInnerVlan(INNER_VLAN_2)
                        .status(FlowStatus.UP)
                        .description(DESC_3)
                        .build()));

        HaFlowDto result = mapper.toHaFlowDto(haFlow);
        assertEquals(HA_FLOW_ID, result.getHaFlowId());
        assertEquals(haFlow.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(haFlow.getPathComputationStrategy(), result.getPathComputationStrategy());
        assertEquals(haFlow.getEncapsulationType(), result.getEncapsulationType());
        assertEquals(MAX_LATENCY, result.getMaxLatency().longValue());
        assertEquals(MAX_LATENCY_TIER_2, result.getMaxLatencyTier2().longValue());
        assertEquals(haFlow.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(haFlow.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(haFlow.isPinned(), result.isPinned());
        assertEquals(haFlow.getPriority(), result.getPriority());
        assertEquals(haFlow.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(haFlow.getDescription(), result.getDescription());
        assertEquals(haFlow.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertEquals(haFlow.getStatus(), result.getStatus());
        assertSharedEndpoint(haFlow.getSharedEndpoint(), result.getSharedEndpoint());
        assertSubFlows(haFlow.getSubFlows(), result.getSubFlows());
    }

    @Test
    public void toHaSubFlowWithId() {
        HaSubFlowDto subFlow = new HaSubFlowDto(
                null, new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1), FlowStatus.UP, DESC_2,
                Instant.MIN, Instant.MAX);
        HaSubFlow result = mapper.toSubFlow(SUB_FLOW_2_NAME, subFlow);
        assertSubFlow(subFlow, SUB_FLOW_2_NAME, result);
    }

    private static void assertSubFlows(Set<HaSubFlow> expectedList, List<HaSubFlowDto> actualSet) {
        assertEquals(expectedList.size(), actualSet.size());
        Map<String, HaSubFlowDto> actualMap = actualSet.stream()
                .collect(Collectors.toMap(HaSubFlowDto::getFlowId, Function.identity()));
        for (HaSubFlow expected : expectedList) {
            assertSubFlow(expected, actualMap.get(expected.getHaSubFlowId()));
        }
    }

    private static void assertSubFlow(HaSubFlowDto expected, String expectedSubFlowId, HaSubFlow actual) {
        assertEquals(expectedSubFlowId, actual.getHaSubFlowId());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpoint().getSwitchId(), actual.getEndpointSwitchId());
        assertEquals(expected.getEndpoint().getPortNumber().intValue(), actual.getEndpointPort());
        assertEquals(expected.getEndpoint().getOuterVlanId(), actual.getEndpointVlan());
        assertEquals(expected.getEndpoint().getInnerVlanId(), actual.getEndpointInnerVlan());
    }

    private static void assertSubFlow(HaSubFlow expected, HaSubFlowDto actual) {
        assertEquals(expected.getHaSubFlowId(), actual.getFlowId());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpointSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(expected.getEndpointPort(), actual.getEndpoint().getPortNumber().intValue());
        assertEquals(expected.getEndpointVlan(), actual.getEndpoint().getOuterVlanId());
        assertEquals(expected.getEndpointInnerVlan(), actual.getEndpoint().getInnerVlanId());
    }

    private void assertSharedEndpoint(FlowEndpoint expected, HaSharedEndpoint actual) {
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
        assertEquals(expected.getPortNumber(), actual.getPortNumber());
        assertEquals(expected.getOuterVlanId(), actual.getOuterVlanId());
        assertEquals(expected.getInnerVlanId(), actual.getInnerVlanId());
    }

    private void assertSharedEndpoint(HaSharedEndpoint expected, FlowEndpoint actual) {
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
        assertEquals(expected.getPortNumber(), actual.getPortNumber());
        assertEquals(expected.getOuterVlanId(), actual.getOuterVlanId());
        assertEquals(expected.getInnerVlanId(), actual.getInnerVlanId());
    }
}
