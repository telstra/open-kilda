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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.command.haflow.HaFlowDto;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest.Type;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HaFlowMapperTest {
    private static final String HA_FLOW_ID_1 = "ha_flow_id_1";
    private static final String HA_FLOW_ID_2 = "ha_flow_id_2";
    private static final String SUB_FLOW_ID_1 = "sub_flow_1";
    private static final String SUB_FLOW_ID_2 = "sub_flow_2";
    private static final String Y_FLOW_ID_1 = "y_flow_1";
    private static final String Y_FLOW_ID_2 = "y_flow_2";
    private static final String FLOW_1 = "flow_1";
    private static final String FLOW_2 = "flow_2";
    private static final String FLOW_3 = "flow_3";
    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    private static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    private static final Switch SWITCH_1 = Switch.builder().switchId(SWITCH_ID_1).build();
    private static final Switch SWITCH_2 = Switch.builder().switchId(SWITCH_ID_2).build();
    private static final Switch SWITCH_3 = Switch.builder().switchId(SWITCH_ID_3).build();
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
    private static final String GROUP_1 = "group_1";
    private static final String GROUP_2 = "group_2";
    private static final HaSubFlowDto SUB_FLOW_1 = new HaSubFlowDto(
            SUB_FLOW_ID_1, new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1), FlowStatus.UP, DESC_2,
            Instant.MIN, Instant.MAX);
    private static final HaSubFlowDto SUB_FLOW_2 = new HaSubFlowDto(
            SUB_FLOW_ID_2, new FlowEndpoint(SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2), FlowStatus.IN_PROGRESS,
            DESC_3, Instant.MAX, Instant.MIN);
    private static final FlowEndpoint SHARED_ENDPOINT = new FlowEndpoint(
            SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3);

    private final HaFlowMapper mapper = HaFlowMapper.INSTANCE;

    @Mock
    FlowRepository flowRepository;

    @Mock
    HaFlowRepository haFlowRepository;

    @Before
    public void init() {
        flowRepository = mock(FlowRepository.class);
        haFlowRepository = mock(HaFlowRepository.class);
    }

    @Test
    public void haFlowRequestMappingTest() {
        HaFlowRequest request = new HaFlowRequest(
                HA_FLOW_ID_1, SHARED_ENDPOINT, BANDWIDTH, PathComputationStrategy.COST, FlowEncapsulationType.VXLAN,
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
        assertEquals(request.getSharedEndpoint().getSwitchId(), result.getSharedSwitchId());
        assertEquals(request.getSharedEndpoint().getPortNumber().intValue(), result.getSharedPort());
        assertEquals(request.getSharedEndpoint().getOuterVlanId(), result.getSharedOuterVlan());
        assertEquals(request.getSharedEndpoint().getInnerVlanId(), result.getSharedInnerVlan());
        assertEquals("Subflows must be mapped separately", 0, result.getHaSubFlows().size());
    }

    @Test
    public void getResponseTest() {
        HaFlow haFlow = new HaFlow(
                HA_FLOW_ID_1, SWITCH_3, PORT_3, VLAN_3, INNER_VLAN_3, BANDWIDTH,
                PathComputationStrategy.COST, FlowEncapsulationType.VXLAN, MAX_LATENCY, MAX_LATENCY_TIER_2, true, false,
                true, PRIORITY, false, DESC_1, true, FlowStatus.UP, GROUP_1, GROUP_2);
        haFlow.setHaSubFlows(Sets.newHashSet(
                HaSubFlow.builder().haSubFlowId(SUB_FLOW_ID_1)
                        .endpointSwitch(SWITCH_1)
                        .endpointPort(PORT_1)
                        .endpointVlan(VLAN_1)
                        .endpointInnerVlan(INNER_VLAN_1)
                        .status(FlowStatus.UP)
                        .description(DESC_2)
                        .build(),
                HaSubFlow.builder().haSubFlowId(SUB_FLOW_ID_2)
                        .endpointSwitch(SWITCH_2)
                        .endpointPort(PORT_2)
                        .endpointVlan(VLAN_2)
                        .endpointInnerVlan(INNER_VLAN_2)
                        .status(FlowStatus.UP)
                        .description(DESC_3)
                        .build()));

        when(flowRepository.findByDiverseGroupId(anyString())).thenReturn(new ArrayList<>());
        when(haFlowRepository.findHaFlowsIdByDiverseGroupId(anyString())).thenReturn(new ArrayList<>());

        HaFlowDto result = mapper.toHaFlowDto(haFlow, flowRepository, haFlowRepository);
        assertEquals(HA_FLOW_ID_1, result.getHaFlowId());
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
        assertEquals(haFlow.getSharedSwitchId(), result.getSharedEndpoint().getSwitchId());
        assertEquals(haFlow.getSharedPort(), result.getSharedEndpoint().getPortNumber().intValue());
        assertEquals(haFlow.getSharedOuterVlan(), result.getSharedEndpoint().getOuterVlanId());
        assertEquals(haFlow.getSharedInnerVlan(), result.getSharedEndpoint().getInnerVlanId());
        assertTrue(result.getDiverseWithFlows().isEmpty());
        assertTrue(result.getDiverseWithYFlows().isEmpty());
        assertTrue(result.getDiverseWithHaFlows().isEmpty());
        assertSubFlows(haFlow.getHaSubFlows(), result.getSubFlows());
    }

    @Test
    public void toHaFlowDtoWithDiversityTest() {
        HaFlow haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID_1)
                .sharedSwitch(SWITCH_1)
                .diverseGroupId(GROUP_1)
                .build();

        when(flowRepository.findByDiverseGroupId(anyString()))
                .thenReturn(Lists.newArrayList(
                        buildFlow(FLOW_1), buildFlow(FLOW_2),
                        buildYSubFlow(SUB_FLOW_ID_1, Y_FLOW_ID_1), buildYSubFlow(SUB_FLOW_ID_2, Y_FLOW_ID_2)));
        when(haFlowRepository.findHaFlowsIdByDiverseGroupId(anyString()))
                .thenReturn(Lists.newArrayList(HA_FLOW_ID_1, HA_FLOW_ID_2));

        HaFlowDto result = mapper.toHaFlowDto(haFlow, flowRepository, haFlowRepository);
        assertEquals(HA_FLOW_ID_1, result.getHaFlowId());
        assertEquals(Sets.newHashSet(FLOW_1, FLOW_2), result.getDiverseWithFlows());
        assertEquals(Sets.newHashSet(Y_FLOW_ID_1, Y_FLOW_ID_2), result.getDiverseWithYFlows());
        assertEquals(Sets.newHashSet(HA_FLOW_ID_2), result.getDiverseWithHaFlows());
    }

    @Test
    public void toHaSubFlowWithId() {
        HaSubFlowDto subFlow = new HaSubFlowDto(
                null, new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1), FlowStatus.UP, DESC_2,
                Instant.MIN, Instant.MAX);
        HaSubFlow result = mapper.toSubFlow(SUB_FLOW_ID_2, subFlow);
        assertSubFlow(subFlow, SUB_FLOW_ID_2, result);
    }

    @Test
    public void toHaSubFlowTest() {
        HaSubFlowDto subFlow = new HaSubFlowDto(
                SUB_FLOW_ID_1, new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1), FlowStatus.UP, DESC_2,
                Instant.MIN, Instant.MAX);
        HaSubFlow result = mapper.toSubFlow(subFlow);
        assertSubFlow(subFlow, subFlow.getFlowId(), result);
    }

    @Test
    public void toRequestedFlowsTest() {
        List<HaSubFlowDto> subFlows = Lists.newArrayList(SUB_FLOW_1, SUB_FLOW_2);
        HaFlowRequest request = new HaFlowRequest(
                HA_FLOW_ID_1, SHARED_ENDPOINT, BANDWIDTH, PathComputationStrategy.COST, FlowEncapsulationType.VXLAN,
                MAX_LATENCY, MAX_LATENCY_TIER_2, true, false, true, PRIORITY, false, DESC_1, true, FLOW_3,
                subFlows, Type.CREATE);
        request.setSubFlows(Lists.newArrayList(SUB_FLOW_1, SUB_FLOW_2));

        Collection<RequestedFlow> requestedFlows = mapper.toRequestedFlows(request);
        assertEquals(2, requestedFlows.size());
        Map<String, RequestedFlow> requestedFlowMap = requestedFlows.stream()
                .collect(Collectors.toMap(RequestedFlow::getFlowId, Function.identity()));
        assertEquals(Sets.newHashSet(SUB_FLOW_1.getFlowId(), SUB_FLOW_2.getFlowId()), requestedFlowMap.keySet());

        for (HaSubFlowDto subFlow : subFlows) {
            assertSubFlow(
                    subFlow, HA_FLOW_ID_1, SHARED_ENDPOINT.getSwitchId(), SHARED_ENDPOINT.getPortNumber(),
                    SHARED_ENDPOINT.getOuterVlanId(), SHARED_ENDPOINT.getInnerVlanId(),
                    FlowEncapsulationType.VXLAN, BANDWIDTH, true, false, true, false, true, PRIORITY, MAX_LATENCY,
                    MAX_LATENCY_TIER_2, PathComputationStrategy.COST, requestedFlowMap.get(subFlow.getFlowId()));
        }
    }

    private static void assertSubFlows(List<HaSubFlow> expectedList, List<HaSubFlowDto> actualSet) {
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

    private static void assertSubFlow(
            HaSubFlowDto expectedSubFlow, String haFlowId, SwitchId srcSwitchId,
            int srcPort, int srcVlan, int srcInnerVLan, FlowEncapsulationType encapsulation,
            int bandwidth, boolean ignoreBandwidth, boolean strictBandwidth, boolean pinned, boolean periodicPings,
            boolean allocateProtected, Integer priority, Long maxLatency, Long maxLatencyTier2,
            PathComputationStrategy strategy, RequestedFlow actual) {
        assertEquals(haFlowId, actual.getHaFlowId());
        assertEquals(expectedSubFlow.getFlowId(), actual.getFlowId());
        assertEquals(srcSwitchId, actual.getSrcSwitch());
        assertEquals(srcPort, actual.getSrcPort());
        assertEquals(srcVlan, actual.getSrcVlan());
        assertEquals(srcInnerVLan, actual.getSrcInnerVlan());
        assertEquals(expectedSubFlow.getEndpoint().getSwitchId(), actual.getDestSwitch());
        assertEquals(expectedSubFlow.getEndpoint().getPortNumber().intValue(), actual.getDestPort());
        assertEquals(expectedSubFlow.getEndpoint().getOuterVlanId(), actual.getDestVlan());
        assertEquals(expectedSubFlow.getEndpoint().getInnerVlanId(), actual.getDestInnerVlan());
        assertEquals(expectedSubFlow.getDescription(), actual.getDescription());
        assertEquals(new DetectConnectedDevices(), actual.getDetectConnectedDevices());
        assertEquals(encapsulation, actual.getFlowEncapsulationType());
        assertEquals(bandwidth, actual.getBandwidth());
        assertEquals(ignoreBandwidth, actual.isIgnoreBandwidth());
        assertEquals(strictBandwidth, actual.isStrictBandwidth());
        assertEquals(pinned, actual.isPinned());
        assertEquals(periodicPings, actual.isPeriodicPings());
        assertEquals(allocateProtected, actual.isAllocateProtectedPath());
        assertEquals(priority, actual.getPriority());
        assertEquals(maxLatency, actual.getMaxLatency());
        assertEquals(maxLatencyTier2, actual.getMaxLatencyTier2());
        assertEquals(strategy, actual.getPathComputationStrategy());
        assertNull(actual.getYFlowId());
    }

    private static Flow buildFlow(String flowId) {
        return Flow.builder().flowId(flowId).srcSwitch(SWITCH_1).destSwitch(SWITCH_2).diverseGroupId(GROUP_1).build();
    }

    private static Flow buildYSubFlow(String flowId, String yFlowId) {
        return Flow.builder().flowId(flowId).yFlowId(yFlowId).srcSwitch(SWITCH_1).destSwitch(SWITCH_2)
                .diverseGroupId(GROUP_1).build();
    }
}
