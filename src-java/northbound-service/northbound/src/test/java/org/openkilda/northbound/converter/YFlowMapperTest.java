/* Copyright 2022 Telstra Open Source
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

package org.openkilda.northbound.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.YFlowDto;
import org.openkilda.messaging.command.yflow.YFlowPartialUpdateRequest;
import org.openkilda.messaging.command.yflow.YFlowPartialUpdateSharedEndpoint;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.info.flow.SubFlowPingPayload;
import org.openkilda.messaging.info.flow.UniSubFlowPingPayload;
import org.openkilda.messaging.info.flow.YFlowPingResponse;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint;
import org.openkilda.northbound.dto.v2.yflows.SubFlow;
import org.openkilda.northbound.dto.v2.yflows.SubFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpoint;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpointEncapsulation;
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;

@RunWith(SpringRunner.class)
public class YFlowMapperTest {
    public static final String ERROR_MESSAGE = "Error";
    public static final String Y_FLOW_ID = "y_flow_id";
    public static final String SUB_FLOW_1_NAME = "flow_1";
    public static final String SUB_FLOW_2_NAME = "flow_2";
    public static final String FLOW_3 = "flow_3";
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final int PORT_1 = 1;
    public static final int PORT_2 = 2;
    public static final int PORT_3 = 3;
    public static final int VLAN_1 = 4;
    public static final int VLAN_2 = 5;
    public static final int VLAN_3 = 6;
    public static final int VLAN_4 = 7;
    public static final int VLAN_5 = 8;
    public static final int BANDWIDTH = 100;
    public static final long MILLION = 1_000_000L;
    public static final long MAX_LATENCY = 10L;
    public static final long MAX_LATENCY_TIER_2 = 20L;
    public static final int PRIORITY = 15;
    public static final String DESC_1 = "desc1";
    public static final String DESC_2 = "desc2";
    public static final String DESC_3 = "desc3";
    public static final SubFlowUpdatePayload SUB_FLOW_1 = new SubFlowUpdatePayload(SUB_FLOW_1_NAME,
            new FlowEndpointV2(SWITCH_ID_1, PORT_1, VLAN_1),
            new YFlowSharedEndpointEncapsulation(VLAN_4, VLAN_5), DESC_2);
    public static final SubFlowUpdatePayload SUB_FLOW_2 = new SubFlowUpdatePayload(SUB_FLOW_2_NAME,
            new FlowEndpointV2(SWITCH_ID_2, PORT_2, VLAN_2),
            new YFlowSharedEndpointEncapsulation(VLAN_3, VLAN_4), DESC_3);

    @Autowired
    private YFlowMapper mapper;

    @Test
    public void createRequestTest() {
        YFlowCreatePayload request = new YFlowCreatePayload(Y_FLOW_ID, new YFlowSharedEndpoint(SWITCH_ID_3, PORT_3),
                BANDWIDTH, PathComputationStrategy.COST.name(), FlowEncapsulationType.VXLAN.name(), MAX_LATENCY,
                MAX_LATENCY_TIER_2, true, false, true, PRIORITY, false, DESC_1, true, FLOW_3,
                Lists.newArrayList(SUB_FLOW_1, SUB_FLOW_2));

        YFlowRequest result = mapper.toYFlowCreateRequest(request);
        assertEquals(request.getYFlowId(), result.getYFlowId());
        assertEquals(request.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(request.getPathComputationStrategy(), result.getPathComputationStrategy().toString());
        assertEquals(request.getEncapsulationType(), result.getEncapsulationType().toString());
        assertEquals((Long) (request.getMaxLatency() * MILLION), result.getMaxLatency());
        assertEquals((Long) (request.getMaxLatencyTier2() * MILLION), result.getMaxLatencyTier2());
        assertEquals(request.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(request.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(request.isPinned(), result.isPinned());
        assertEquals(request.getPriority(), result.getPriority());
        assertEquals(request.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(request.getDescription(), result.getDescription());
        assertEquals(request.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertEquals(request.getDiverseFlowId(), result.getDiverseFlowId());
        assertSharedEndpoint(request.getSharedEndpoint(), result.getSharedEndpoint());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(request.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(request.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void updateRequestTest() {
        YFlowUpdatePayload request = YFlowUpdatePayload.builder()
                .sharedEndpoint(new YFlowSharedEndpoint(SWITCH_ID_3, PORT_3))
                .maximumBandwidth(BANDWIDTH)
                .pathComputationStrategy(PathComputationStrategy.COST.name())
                .encapsulationType(FlowEncapsulationType.VXLAN.name())
                .maxLatency(MAX_LATENCY)
                .maxLatencyTier2(MAX_LATENCY_TIER_2)
                .ignoreBandwidth(true)
                .periodicPings(false).pinned(true)
                .priority(PRIORITY)
                .strictBandwidth(false)
                .description(DESC_1)
                .allocateProtectedPath(true)
                .diverseFlowId(FLOW_3)
                .subFlows(Lists.newArrayList(SUB_FLOW_1, SUB_FLOW_2))
                .build();

        YFlowRequest result = mapper.toYFlowUpdateRequest(Y_FLOW_ID, request);
        assertEquals(Y_FLOW_ID, result.getYFlowId());
        assertEquals(request.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(request.getPathComputationStrategy(), result.getPathComputationStrategy().toString());
        assertEquals(request.getEncapsulationType(), result.getEncapsulationType().toString());
        assertEquals((Long) (request.getMaxLatency() * MILLION), result.getMaxLatency());
        assertEquals((Long) (request.getMaxLatencyTier2() * MILLION), result.getMaxLatencyTier2());
        assertEquals(request.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(request.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(request.isPinned(), result.isPinned());
        assertEquals(request.getPriority(), result.getPriority());
        assertEquals(request.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(request.getDescription(), result.getDescription());
        assertEquals(request.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertEquals(request.getDiverseFlowId(), result.getDiverseFlowId());
        assertSharedEndpoint(request.getSharedEndpoint(), result.getSharedEndpoint());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(request.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(request.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void patchRequestTest() {
        YFlowPatchPayload request = new YFlowPatchPayload(new YFlowPatchSharedEndpoint(SWITCH_ID_3, PORT_3),
                (long) BANDWIDTH, PathComputationStrategy.COST.name(), FlowEncapsulationType.VXLAN.name(), MAX_LATENCY,
                MAX_LATENCY_TIER_2, true, false, true, PRIORITY, false, DESC_1, true, FLOW_3,
                Lists.newArrayList(
                        new SubFlowPatchPayload(SUB_FLOW_1_NAME,
                                new FlowPatchEndpoint(SWITCH_ID_1, PORT_1, VLAN_1, VLAN_2,
                                        new DetectConnectedDevicesV2(true, true)),
                                new YFlowPatchSharedEndpointEncapsulation(VLAN_3, VLAN_4), DESC_2),
                        new SubFlowPatchPayload(SUB_FLOW_2_NAME,
                                new FlowPatchEndpoint(SWITCH_ID_2, PORT_2, VLAN_4, VLAN_5,
                                        new DetectConnectedDevicesV2(true, true)),
                                new YFlowPatchSharedEndpointEncapsulation(VLAN_2, VLAN_3), DESC_3)));

        YFlowPartialUpdateRequest result = mapper.toYFlowPatchRequest(Y_FLOW_ID, request);
        assertEquals(Y_FLOW_ID, result.getYFlowId());
        assertEquals(request.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(request.getPathComputationStrategy(), result.getPathComputationStrategy().toString());
        assertEquals(request.getEncapsulationType(), result.getEncapsulationType().toString());
        assertEquals((Long) (request.getMaxLatency() * MILLION), result.getMaxLatency());
        assertEquals((Long) (request.getMaxLatencyTier2() * MILLION), result.getMaxLatencyTier2());
        assertEquals(request.getIgnoreBandwidth(), result.getIgnoreBandwidth());
        assertEquals(request.getPeriodicPings(), result.getPeriodicPings());
        assertEquals(request.getPinned(), result.getPinned());
        assertEquals(request.getPriority(), result.getPriority());
        assertEquals(request.getStrictBandwidth(), result.getStrictBandwidth());
        assertEquals(request.getDescription(), result.getDescription());
        assertEquals(request.getAllocateProtectedPath(), result.getAllocateProtectedPath());
        assertEquals(request.getDiverseFlowId(), result.getDiverseFlowId());
        assertSharedEndpoint(request.getSharedEndpoint(), result.getSharedEndpoint());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(request.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(request.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void getResponseTest() {
        YFlowDto response = new YFlowDto();
        response.setYFlowId(Y_FLOW_ID);
        response.setSharedEndpoint(new FlowEndpoint(SWITCH_ID_3, PORT_3));
        response.setMaximumBandwidth(BANDWIDTH);
        response.setPathComputationStrategy(PathComputationStrategy.COST);
        response.setEncapsulationType(FlowEncapsulationType.VXLAN);
        response.setMaxLatency(MAX_LATENCY * MILLION);
        response.setMaxLatencyTier2(MAX_LATENCY_TIER_2 * MILLION);
        response.setIgnoreBandwidth(true);
        response.setPeriodicPings(false);
        response.setPinned(true);
        response.setPriority(PRIORITY);
        response.setStrictBandwidth(false);
        response.setDescription(DESC_1);
        response.setAllocateProtectedPath(true);
        response.setYPoint(SWITCH_ID_2);
        response.setProtectedPathYPoint(SWITCH_ID_3);
        response.setDiverseWithFlows(Sets.newHashSet(FLOW_3));
        response.setSubFlows(Lists.newArrayList(
                SubFlowDto.builder().flowId(SUB_FLOW_1_NAME)
                        .endpoint(new FlowEndpoint(SWITCH_ID_1, PORT_1))
                        .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(VLAN_2, VLAN_3))
                        .status(FlowStatus.UP)
                        .description(DESC_2)
                        .timeCreate(Instant.MIN)
                        .timeUpdate(Instant.MAX)
                        .build(),
                SubFlowDto.builder().flowId(SUB_FLOW_2_NAME)
                        .endpoint(new FlowEndpoint(SWITCH_ID_2, PORT_2))
                        .sharedEndpoint(new SubFlowSharedEndpointEncapsulation(VLAN_3, VLAN_4))
                        .status(FlowStatus.UP)
                        .description(DESC_3)
                        .timeCreate(Instant.MAX)
                        .timeUpdate(Instant.MIN)
                        .build()));

        YFlow result = mapper.toYFlow(response);
        assertEquals(Y_FLOW_ID, result.getYFlowId());
        assertEquals(response.getMaximumBandwidth(), result.getMaximumBandwidth());
        assertEquals(response.getPathComputationStrategy().toString().toLowerCase(),
                result.getPathComputationStrategy());
        assertEquals(response.getEncapsulationType().toString().toLowerCase(), result.getEncapsulationType());
        assertEquals(MAX_LATENCY, result.getMaxLatency().longValue());
        assertEquals(MAX_LATENCY_TIER_2, result.getMaxLatencyTier2().longValue());
        assertEquals(response.isIgnoreBandwidth(), result.isIgnoreBandwidth());
        assertEquals(response.isPeriodicPings(), result.isPeriodicPings());
        assertEquals(response.isPinned(), result.isPinned());
        assertEquals(response.getPriority(), result.getPriority());
        assertEquals(response.isStrictBandwidth(), result.isStrictBandwidth());
        assertEquals(response.getDescription(), result.getDescription());
        assertEquals(response.isAllocateProtectedPath(), result.isAllocateProtectedPath());
        assertEquals(response.getYPoint(), result.getYPoint());
        assertEquals(response.getProtectedPathYPoint(), result.getProtectedPathYPoint());
        assertEquals(response.getDiverseWithFlows(), result.getDiverseWithFlows());

        assertEquals(2, result.getSubFlows().size());
        assertSubFlow(response.getSubFlows().get(0), result.getSubFlows().get(0));
        assertSubFlow(response.getSubFlows().get(1), result.getSubFlows().get(1));
    }

    @Test
    public void pingResultTest() {
        YFlowPingResponse response = new YFlowPingResponse(Y_FLOW_ID, false, ERROR_MESSAGE, Lists.newArrayList(
                new SubFlowPingPayload(SUB_FLOW_1_NAME,
                        new UniSubFlowPingPayload(true, null, 1),
                        new UniSubFlowPingPayload(false, Errors.TIMEOUT, 2)),
                new SubFlowPingPayload(SUB_FLOW_2_NAME,
                        new UniSubFlowPingPayload(false, Errors.DEST_NOT_AVAILABLE, 3),
                        new UniSubFlowPingPayload(true, null, 4))));

        YFlowPingResult result = mapper.toPingResult(response);
        assertEquals(response.getYFlowId(), result.getYFlowId());
        assertEquals(response.isPingSuccess(), result.isPingSuccess());
        assertEquals(response.getError(), result.getError());
        assertEquals(response.getSubFlows().size(), result.getSubFlows().size());
        assertSubFlowPingPayload(response.getSubFlows().get(0), result.getSubFlows().get(0));
    }

    private void assertSubFlowPingPayload(
            SubFlowPingPayload expected, org.openkilda.northbound.dto.v2.yflows.SubFlowPingPayload actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertUniSubFlowPingPayload(expected.getForward(), actual.getForward());
        assertUniSubFlowPingPayload(expected.getReverse(), actual.getReverse());
    }

    private void assertUniSubFlowPingPayload(
            UniSubFlowPingPayload expected, org.openkilda.northbound.dto.v2.yflows.UniSubFlowPingPayload actual) {
        assertEquals(expected.isPingSuccess(), actual.isPingSuccess());
        assertEquals(expected.getLatency(), actual.getLatency());
        assertError(expected, actual);
    }

    private void assertError(
            UniSubFlowPingPayload expected, org.openkilda.northbound.dto.v2.yflows.UniSubFlowPingPayload actual) {
        if (expected.getError() == null) {
            assertNull(actual.getError());
        } else if (Errors.TIMEOUT.equals(expected.getError())) {
            assertEquals(PingMapper.TIMEOUT_ERROR_MESSAGE, actual.getError());
        } else if (Errors.DEST_NOT_AVAILABLE.equals(expected.getError())) {
            assertEquals(PingMapper.ENDPOINT_NOT_AVAILABLE_ERROR_MESSAGE, actual.getError());
        } else {
            throw new AssertionError(String.format("Unknown error type: %s", expected.getError()));
        }
    }

    private void assertSubFlow(SubFlowUpdatePayload expected, SubFlowDto actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(expected.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertEquals(expected.getEndpoint().getVlanId(), actual.getEndpoint().getOuterVlanId());
        assertEquals(expected.getEndpoint().getInnerVlanId(), actual.getEndpoint().getInnerVlanId());
        assertEquals(expected.getSharedEndpoint().getVlanId(), actual.getSharedEndpoint().getVlanId());
        assertEquals(expected.getSharedEndpoint().getInnerVlanId(), actual.getSharedEndpoint().getInnerVlanId());
    }

    private void assertSubFlow(SubFlowPatchPayload expected, SubFlowPartialUpdateDto actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(expected.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertEquals(expected.getEndpoint().getVlanId(), actual.getEndpoint().getVlanId());
        assertEquals(expected.getEndpoint().getInnerVlanId(), actual.getEndpoint().getInnerVlanId());
        assertEquals(expected.getSharedEndpoint().getVlanId(), actual.getSharedEndpoint().getVlanId());
        assertEquals(expected.getSharedEndpoint().getInnerVlanId(), actual.getSharedEndpoint().getInnerVlanId());
    }

    private void assertSubFlow(SubFlowDto expected, SubFlow actual) {
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getEndpoint().getSwitchId(), actual.getEndpoint().getSwitchId());
        assertEquals(expected.getEndpoint().getPortNumber(), actual.getEndpoint().getPortNumber());
        assertEquals(expected.getEndpoint().getOuterVlanId(), actual.getEndpoint().getVlanId());
        assertEquals(expected.getEndpoint().getInnerVlanId(), actual.getEndpoint().getInnerVlanId());
        assertEquals(expected.getSharedEndpoint().getVlanId(), actual.getSharedEndpoint().getVlanId());
        assertEquals(expected.getSharedEndpoint().getInnerVlanId(), actual.getSharedEndpoint().getInnerVlanId());
    }

    private void assertSharedEndpoint(YFlowSharedEndpoint expected, FlowEndpoint actual) {
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
        assertEquals(expected.getPortNumber(), actual.getPortNumber().intValue());
    }

    private void assertSharedEndpoint(YFlowPatchSharedEndpoint expected, YFlowPartialUpdateSharedEndpoint actual) {
        assertEquals(expected.getSwitchId(), actual.getSwitchId());
        assertEquals(expected.getPortNumber(), actual.getPortNumber());
    }

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
