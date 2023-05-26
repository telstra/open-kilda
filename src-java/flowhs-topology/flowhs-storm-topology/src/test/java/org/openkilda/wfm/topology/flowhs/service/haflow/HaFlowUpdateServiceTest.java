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

package org.openkilda.wfm.topology.flowhs.service.haflow;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.PathComputationStrategy.COST;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest.HaFlowPartialUpdateRequestBuilder;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest.Type;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.command.haflow.HaSubFlowPartialUpdateDto;
import org.openkilda.messaging.command.haflow.HaSubFlowPartialUpdateDto.HaSubFlowPartialUpdateDtoBuilder;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint.FlowPartialUpdateEndpointBuilder;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;
import org.openkilda.wfm.topology.flowhs.service.AbstractHaFlowTest;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.ArrayList;

public class HaFlowUpdateServiceTest extends AbstractHaFlowTest<FlowSegmentRequest> {
    private static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1);
    private static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2);
    private static final Switch SWITCH_3 = buildSwitch(SWITCH_ID_3);
    private static final HaFlow HA_FLOW = buildHaFlow();
    private static final HaFlowPartialUpdateRequest PATCH_REQUEST = HaFlowPartialUpdateRequest.builder()
            .haFlowId(HA_FLOW.getHaFlowId())
            .sharedEndpoint(new FlowPartialUpdateEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1))
            .maximumBandwidth(BANDWIDTH_2)
            .pathComputationStrategy(LATENCY)
            .maxLatency(MAX_LATENCY_2)
            .maxLatencyTier2(MAX_LATENCY_TIER_2_2)
            .encapsulationType(TRANSIT_VLAN)
            .ignoreBandwidth(false)
            .periodicPings(true)
            .pinned(false)
            .priority(PRIORITY_2)
            .strictBandwidth(true)
            .description(DESCRIPTION_2)
            .allocateProtectedPath(false)
            .subFlows(newArrayList(
                    HaSubFlowPartialUpdateDto.builder()
                            .flowId(SUB_FLOW_ID_1)
                            .endpoint(new FlowPartialUpdateEndpoint(SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2))
                            .description(DESCRIPTION_1)
                            .build(),
                    HaSubFlowPartialUpdateDto.builder()
                            .flowId(SUB_FLOW_ID_2)
                            .endpoint(new FlowPartialUpdateEndpoint(SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3))
                            .description(DESCRIPTION_2)
                            .build()))
            .diverseFlowId(HA_FLOW_ID_2)
            .build();
    public static final String STATUS_INFO = "some info";


    @Mock
    private FlowGenericCarrier carrier;
    HaFlowUpdateService service;

    @Before
    public void init() {
        carrier = mock(FlowGenericCarrier.class);
        service = new HaFlowUpdateService(carrier, persistenceManager, pathComputer, flowResourcesManager,
                ruleManager, 0, 3, 0, 0);
    }

    @Test
    public void isFullUpdateRequiredEmptyPatchHaFlow() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredHaPathId() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().haFlowId(HA_FLOW_ID_1).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().haFlowId(HA_FLOW_ID_2).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSharedSwitch() {
        assertTrue(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().switchId(SWITCH_ID_1).build()).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().switchId(SWITCH_ID_3).build()).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSharedPort() {
        assertTrue(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().portNumber(PORT_1).build()).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().portNumber(PORT_3).build()).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSharedVlan() {
        assertTrue(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().vlanId(VLAN_1).build()).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().vlanId(VLAN_3).build()).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSharedInnerVlan() {
        assertTrue(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().innerVlanId(INNER_VLAN_1).build()).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().sharedEndpoint(
                endpointBuilder().innerVlanId(INNER_VLAN_3).build()).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredBandwidth() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().maximumBandwidth(BANDWIDTH_1).build(), HA_FLOW));
        assertTrue(service.isFullUpdateRequired(patchBuilder().maximumBandwidth(BANDWIDTH_2).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredPathComputationStrategy() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().pathComputationStrategy(COST).build(), HA_FLOW));
        assertTrue(service.isFullUpdateRequired(patchBuilder().pathComputationStrategy(LATENCY).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredMaxLatencyStrategy() {
        HaFlow haFlow = new HaFlow(HA_FLOW);
        haFlow.setPathComputationStrategy(MAX_LATENCY);

        assertFalse(service.isFullUpdateRequired(buildPatch(MAX_LATENCY, MAX_LATENCY_1, null), haFlow));
        assertTrue(service.isFullUpdateRequired(buildPatch(MAX_LATENCY, MAX_LATENCY_2, null), haFlow));
        assertFalse(service.isFullUpdateRequired(buildPatch(MAX_LATENCY, null, MAX_LATENCY_TIER_2_1), haFlow));
        assertTrue(service.isFullUpdateRequired(buildPatch(MAX_LATENCY, null, MAX_LATENCY_TIER_2_2), haFlow));
        assertFalse(service.isFullUpdateRequired(buildPatch(MAX_LATENCY, MAX_LATENCY_1, MAX_LATENCY_TIER_2_1), haFlow));
        assertTrue(service.isFullUpdateRequired(buildPatch(MAX_LATENCY, MAX_LATENCY_2, MAX_LATENCY_TIER_2_2), haFlow));
    }

    @Test
    public void isFullUpdateRequireLatencyStrategy() {
        HaFlow haFlow = new HaFlow(HA_FLOW);
        haFlow.setPathComputationStrategy(LATENCY);

        assertFalse(service.isFullUpdateRequired(buildPatch(LATENCY, MAX_LATENCY_1, null), haFlow));
        assertTrue(service.isFullUpdateRequired(buildPatch(LATENCY, MAX_LATENCY_2, null), haFlow));
        assertFalse(service.isFullUpdateRequired(buildPatch(LATENCY, null, MAX_LATENCY_TIER_2_1), haFlow));
        assertTrue(service.isFullUpdateRequired(buildPatch(LATENCY, null, MAX_LATENCY_TIER_2_2), haFlow));
        assertFalse(service.isFullUpdateRequired(buildPatch(LATENCY, MAX_LATENCY_1, MAX_LATENCY_TIER_2_1), haFlow));
        assertTrue(service.isFullUpdateRequired(buildPatch(LATENCY, MAX_LATENCY_2, MAX_LATENCY_TIER_2_2), haFlow));
    }

    @Test
    public void isFullUpdateRequiredEncapsulationType() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().encapsulationType(VXLAN).build(), HA_FLOW));
        assertTrue(service.isFullUpdateRequired(patchBuilder().encapsulationType(TRANSIT_VLAN).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredMaxLatency() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().maxLatency(MAX_LATENCY_1).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().maxLatency(MAX_LATENCY_2).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredMaxLatencyTier2() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().maxLatencyTier2(MAX_LATENCY_TIER_2_1).build(),
                HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().maxLatencyTier2(MAX_LATENCY_TIER_2_2).build(),
                HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredIgnoreBandwidth() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().ignoreBandwidth(true).build(), HA_FLOW));
        assertTrue(service.isFullUpdateRequired(patchBuilder().ignoreBandwidth(false).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredPeriodicPings() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().periodicPings(true).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().periodicPings(false).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredPinned() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().pinned(true).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().pinned(false).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredPriority() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().priority(PRIORITY_1).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().priority(PRIORITY_2).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredStrictBandwidth() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().strictBandwidth(true).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().strictBandwidth(false).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredStrictDescription() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().description(DESCRIPTION_1).build(), HA_FLOW));
        assertFalse(service.isFullUpdateRequired(patchBuilder().description(DESCRIPTION_2).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredProtected() {
        assertFalse(service.isFullUpdateRequired(patchBuilder().allocateProtectedPath(true).build(), HA_FLOW));
        assertTrue(service.isFullUpdateRequired(patchBuilder().allocateProtectedPath(false).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredDiverseFlow() {
        assertTrue(service.isFullUpdateRequired(patchBuilder().diverseFlowId(HA_FLOW_ID_3).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredEmptySubFlow() {
        ArrayList<HaSubFlowPartialUpdateDto> subFlows = newArrayList(subFlowBuilder(SUB_FLOW_ID_1).build());
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(subFlows).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowDescription() {
        HaSubFlowPartialUpdateDto subFlow1 = subFlowBuilder(SUB_FLOW_ID_1).description(DESCRIPTION_1).build();
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow1)).build(), HA_FLOW));
        HaSubFlowPartialUpdateDto subFlow2 = subFlowBuilder(SUB_FLOW_ID_1).description(DESCRIPTION_2).build();
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow2)).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowStatus() {
        HaSubFlowPartialUpdateDto subFlow1 = subFlowBuilder(SUB_FLOW_ID_1).status(FlowStatus.UP).build();
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow1)).build(), HA_FLOW));
        HaSubFlowPartialUpdateDto subFlow2 = subFlowBuilder(SUB_FLOW_ID_1).status(FlowStatus.DOWN).build();
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow2)).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowTimeCreate() {
        HaSubFlowPartialUpdateDto subFlow = subFlowBuilder(SUB_FLOW_ID_1).timeCreate(Instant.MIN).build();
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow)).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowTimeUpdate() {
        HaSubFlowPartialUpdateDto subFlow = subFlowBuilder(SUB_FLOW_ID_1).timeUpdate(Instant.MAX).build();
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow)).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowSwitchId() {
        HaSubFlowPartialUpdateDto subFlow1 = buildSubFlowWithEndpoint(endpointBuilder().switchId(SWITCH_ID_1));
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow1)).build(), HA_FLOW));
        HaSubFlowPartialUpdateDto subFlow2 = buildSubFlowWithEndpoint(endpointBuilder().switchId(SWITCH_ID_2));
        assertTrue(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow2)).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowPort() {
        HaSubFlowPartialUpdateDto subFlow1 = buildSubFlowWithEndpoint(endpointBuilder().portNumber(PORT_1));
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow1)).build(), HA_FLOW));
        HaSubFlowPartialUpdateDto subFlow2 = buildSubFlowWithEndpoint(endpointBuilder().portNumber(PORT_2));
        assertTrue(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow2)).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowVlan() {
        HaSubFlowPartialUpdateDto subFlow1 = buildSubFlowWithEndpoint(endpointBuilder().vlanId(VLAN_1));
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow1)).build(), HA_FLOW));
        HaSubFlowPartialUpdateDto subFlow2 = buildSubFlowWithEndpoint(endpointBuilder().vlanId(VLAN_2));
        assertTrue(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow2)).build(), HA_FLOW));
    }

    @Test
    public void isFullUpdateRequiredSubFlowInnerVlan() {
        HaSubFlowPartialUpdateDto subFlow1 = buildSubFlowWithEndpoint(endpointBuilder().innerVlanId(INNER_VLAN_1));
        assertFalse(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow1)).build(), HA_FLOW));
        HaSubFlowPartialUpdateDto subFlow2 = buildSubFlowWithEndpoint(endpointBuilder().innerVlanId(INNER_VLAN_2));
        assertTrue(service.isFullUpdateRequired(patchBuilder().subFlows(newArrayList(subFlow2)).build(), HA_FLOW));
    }

    @Test
    public void updateHaFlowPartiallyDifferentPathRequest() {
        HaFlow haFlowForUpdate = new HaFlow(HA_FLOW);
        service.updateHaFlowPartially(haFlowForUpdate, PATCH_REQUEST);

        // These fields must be updated
        assertEquals(PATCH_REQUEST.getHaFlowId(), haFlowForUpdate.getHaFlowId());
        assertEquals(PATCH_REQUEST.getMaxLatency(), haFlowForUpdate.getMaxLatency());
        assertEquals(PATCH_REQUEST.getMaxLatencyTier2(), haFlowForUpdate.getMaxLatencyTier2());
        assertEquals(PATCH_REQUEST.getPeriodicPings(), haFlowForUpdate.isPeriodicPings());
        assertEquals(PATCH_REQUEST.getPinned(), haFlowForUpdate.isPinned());
        assertEquals(PATCH_REQUEST.getPriority(), haFlowForUpdate.getPriority());
        assertEquals(PATCH_REQUEST.getStrictBandwidth(), haFlowForUpdate.isStrictBandwidth());
        assertEquals(PATCH_REQUEST.getDescription(), haFlowForUpdate.getDescription());

        // These fields must not be updated
        assertNotEquals(PATCH_REQUEST.getSharedEndpoint().getSwitchId(),
                haFlowForUpdate.getSharedEndpoint().getSwitchId());
        assertNotEquals(PATCH_REQUEST.getSharedEndpoint().getPortNumber(),
                haFlowForUpdate.getSharedEndpoint().getPortNumber());
        assertNotEquals(PATCH_REQUEST.getSharedEndpoint().getVlanId().intValue(),
                haFlowForUpdate.getSharedEndpoint().getOuterVlanId());
        assertNotEquals(PATCH_REQUEST.getSharedEndpoint().getInnerVlanId().intValue(),
                haFlowForUpdate.getSharedEndpoint().getInnerVlanId());
        assertNotEquals(PATCH_REQUEST.getMaximumBandwidth().longValue(), haFlowForUpdate.getMaximumBandwidth());
        assertNotEquals(PATCH_REQUEST.getPathComputationStrategy(), haFlowForUpdate.getPathComputationStrategy());
        assertNotEquals(PATCH_REQUEST.getEncapsulationType(), haFlowForUpdate.getEncapsulationType());
        assertNotEquals(PATCH_REQUEST.getIgnoreBandwidth(), haFlowForUpdate.isIgnoreBandwidth());
        assertNotEquals(PATCH_REQUEST.getAllocateProtectedPath(), haFlowForUpdate.isAllocateProtectedPath());
        assertEquals(2, haFlowForUpdate.getHaSubFlows().size());
        assertEquals(PATCH_REQUEST.getSubFlows().size(), haFlowForUpdate.getHaSubFlows().size());
        for (HaSubFlowPartialUpdateDto patchSubFlow : PATCH_REQUEST.getSubFlows()) {
            assertSubFlow(patchSubFlow, haFlowForUpdate.getHaSubFlowOrThrowException(patchSubFlow.getFlowId()));
        }
    }

    @Test
    public void updateChangedFieldsDifferentPathRequest() {
        HaFlowRequest haFlowRequest = HaFlowMapper.INSTANCE.toHaFlowRequest(HA_FLOW, HA_FLOW_ID_3, Type.UPDATE);
        service.updateChangedFields(haFlowRequest, PATCH_REQUEST);

        assertEquals(PATCH_REQUEST.getHaFlowId(), haFlowRequest.getHaFlowId());
        assertEquals(PATCH_REQUEST.getMaxLatency(), haFlowRequest.getMaxLatency());
        assertEquals(PATCH_REQUEST.getMaxLatencyTier2(), haFlowRequest.getMaxLatencyTier2());
        assertEquals(PATCH_REQUEST.getPeriodicPings(), haFlowRequest.isPeriodicPings());
        assertEquals(PATCH_REQUEST.getPinned(), haFlowRequest.isPinned());
        assertEquals(PATCH_REQUEST.getPriority(), haFlowRequest.getPriority());
        assertEquals(PATCH_REQUEST.getStrictBandwidth(), haFlowRequest.isStrictBandwidth());
        assertEquals(PATCH_REQUEST.getDescription(), haFlowRequest.getDescription());
        assertEquals(PATCH_REQUEST.getSharedEndpoint().getSwitchId(),
                haFlowRequest.getSharedEndpoint().getSwitchId());
        assertEquals(PATCH_REQUEST.getSharedEndpoint().getPortNumber(),
                haFlowRequest.getSharedEndpoint().getPortNumber());
        assertEquals(PATCH_REQUEST.getSharedEndpoint().getVlanId().intValue(),
                haFlowRequest.getSharedEndpoint().getOuterVlanId());
        assertEquals(PATCH_REQUEST.getSharedEndpoint().getInnerVlanId().intValue(),
                haFlowRequest.getSharedEndpoint().getInnerVlanId());
        assertEquals(PATCH_REQUEST.getMaximumBandwidth().longValue(), haFlowRequest.getMaximumBandwidth());
        assertEquals(PATCH_REQUEST.getPathComputationStrategy(), haFlowRequest.getPathComputationStrategy());
        assertEquals(PATCH_REQUEST.getEncapsulationType(), haFlowRequest.getEncapsulationType());
        assertEquals(PATCH_REQUEST.getIgnoreBandwidth(), haFlowRequest.isIgnoreBandwidth());
        assertEquals(PATCH_REQUEST.getAllocateProtectedPath(), haFlowRequest.isAllocateProtectedPath());
        assertEquals(2, haFlowRequest.getSubFlows().size());
        assertEquals(PATCH_REQUEST.getSubFlows().size(), haFlowRequest.getSubFlows().size());
        for (HaSubFlowPartialUpdateDto patchSubFlow : PATCH_REQUEST.getSubFlows()) {
            assertSubFlow(patchSubFlow, haFlowRequest.getHaSubFlow(patchSubFlow.getFlowId()));
        }
    }

    private void assertSubFlow(HaSubFlowPartialUpdateDto patchRequest, HaSubFlow actual) {
        assertEquals(patchRequest.getFlowId(), actual.getHaSubFlowId());
        assertEquals(patchRequest.getDescription(), actual.getDescription());

        assertNotEquals(patchRequest.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertNotEquals(patchRequest.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertNotEquals(patchRequest.getEndpoint().getVlanId().intValue(), actual.getEndpoint().getOuterVlanId());
        assertNotEquals(patchRequest.getEndpoint().getInnerVlanId().intValue(), actual.getEndpoint().getInnerVlanId());
    }

    private void assertSubFlow(HaSubFlowPartialUpdateDto patchRequest, HaSubFlowDto actual) {
        assertEquals(patchRequest.getFlowId(), actual.getFlowId());
        assertEquals(patchRequest.getDescription(), actual.getDescription());
        assertEquals(patchRequest.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(patchRequest.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertEquals(patchRequest.getEndpoint().getVlanId().intValue(), actual.getEndpoint().getOuterVlanId());
        assertEquals(patchRequest.getEndpoint().getInnerVlanId().intValue(), actual.getEndpoint().getInnerVlanId());
    }

    private static HaFlowPartialUpdateRequest buildPatch(
            PathComputationStrategy strategy, Long maxLatency, Long maxLatencyTier2) {
        return patchBuilder()
                .pathComputationStrategy(strategy)
                .maxLatency(maxLatency)
                .maxLatencyTier2(maxLatencyTier2).build();
    }


    private static HaFlow buildHaFlow() {
        HaFlow haFlow = new HaFlow(
                HA_FLOW_ID_1, SWITCH_3, PORT_3, VLAN_3, INNER_VLAN_3, BANDWIDTH_1,
                COST, VXLAN, MAX_LATENCY_1, MAX_LATENCY_TIER_2_1, true, false,
                true, PRIORITY_1, false, DESCRIPTION_1, true, FlowStatus.UP, STATUS_INFO, GROUP_ID_1.toString(),
                GROUP_ID_2.toString());
        haFlow.setHaSubFlows(Sets.newHashSet(
                HaSubFlow.builder().haSubFlowId(SUB_FLOW_ID_1)
                        .endpointSwitch(SWITCH_1)
                        .endpointPort(PORT_1)
                        .endpointVlan(VLAN_1)
                        .endpointInnerVlan(INNER_VLAN_1)
                        .status(FlowStatus.UP)
                        .description(DESCRIPTION_2)
                        .build(),
                HaSubFlow.builder().haSubFlowId(SUB_FLOW_ID_2)
                        .endpointSwitch(SWITCH_2)
                        .endpointPort(PORT_2)
                        .endpointVlan(VLAN_2)
                        .endpointInnerVlan(INNER_VLAN_2)
                        .status(FlowStatus.UP)
                        .description(DESCRIPTION_3)
                        .build()));
        return haFlow;
    }

    private static HaFlowPartialUpdateRequestBuilder patchBuilder() {
        return HaFlowPartialUpdateRequest.builder();
    }

    private static FlowPartialUpdateEndpointBuilder endpointBuilder() {
        return FlowPartialUpdateEndpoint.builder();
    }

    private static HaSubFlowPartialUpdateDtoBuilder subFlowBuilder(String subFlowId) {
        return HaSubFlowPartialUpdateDto.builder().flowId(subFlowId);
    }

    private static HaSubFlowPartialUpdateDto buildSubFlowWithEndpoint(
            FlowPartialUpdateEndpointBuilder endpointBuilder) {
        return HaSubFlowPartialUpdateDto.builder().flowId(SUB_FLOW_ID_1).endpoint(endpointBuilder.build()).build();
    }
}
