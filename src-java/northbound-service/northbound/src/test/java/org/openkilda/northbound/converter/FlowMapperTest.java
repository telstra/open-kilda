/* Copyright 2021 Telstra Open Source
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.openkilda.northbound.converter.PingMapper.TIMEOUT_ERROR_MESSAGE;

import org.openkilda.messaging.command.flow.FlowMirrorPointCreateRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRequest.Type;
import org.openkilda.messaging.info.flow.FlowMirrorPointResponse;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.UniFlowPingResponse;
import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPatch;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.messaging.model.PingMeters;
import org.openkilda.messaging.nbtopology.response.FlowMirrorPointsDumpResponse;
import org.openkilda.messaging.nbtopology.response.FlowMirrorPointsDumpResponse.FlowMirrorPoint;
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowStatusDetails;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointsResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint;
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowStatistics;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ExtendWith(SpringExtension.class)
public class FlowMapperTest {
    public static final int ENCAPSULATION_ID = 18;
    private static final String FLOW_ID = "flow1";
    private static final String DIVERSE_FLOW_ID = "flow2";
    private static final String AFFINITY_FLOW_ID = "flow3";
    private static final SwitchId SRC_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId DST_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int SRC_PORT = 1;
    private static final int DST_PORT = 2;
    private static final int SRC_VLAN = 3;
    private static final int DST_VLAN = 4;
    private static final int SRC_INNER_VLAN = 5;
    private static final int DST_INNER_VLAN = 6;
    private static final int BANDWIDTH = 1000;
    private static final boolean IGNORE_BANDWIDTH = true;
    private static final boolean STRICT_BANDWIDTH = true;
    private static final boolean PERIODIC_PINGS = true;
    private static final boolean ALLOCATE_PROTECTED_PATH = true;
    private static final boolean PINNED = true;
    private static final Long LATENCY = 10_000_000L;
    private static final Long LATENCY_TIER2 = 11_000_000L;
    private static final Integer PRIORITY = 15;
    private static final String DESCRIPTION = "Description";
    private static final String ENCAPSULATION_TYPE = "transit_vlan";
    private static final String PATH_COMPUTATION_STRATEGY = "latency";
    private static final String TARGET_PATH_COMPUTATION_STRATEGY = "cost";
    private static final long COOKIE = 16;
    private static final DetectConnectedDevicesV2 SRC_DETECT_CONNECTED_DEVICES = new DetectConnectedDevicesV2(
            true, false);
    private static final DetectConnectedDevicesV2 DST_DETECT_CONNECTED_DEVICES = new DetectConnectedDevicesV2(
            false, true);

    private static final DetectConnectedDevicesPayload SRC_DETECT_CONNECTED_DEVICES_PAYLOAD
            = new DetectConnectedDevicesPayload(true, false);
    private static final DetectConnectedDevicesPayload DST_DETECT_CONNECTED_DEVICES_PAYLOAD
            = new DetectConnectedDevicesPayload(false, true);
    private static final FlowEndpointPayload SRC_FLOW_ENDPOINT_PAYLOAD
            = new FlowEndpointPayload(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, SRC_DETECT_CONNECTED_DEVICES_PAYLOAD);
    private static final FlowEndpointPayload DST_FLOW_ENDPOINT_PAYLOAD
            = new FlowEndpointPayload(DST_SWITCH_ID, DST_PORT, DST_VLAN, DST_DETECT_CONNECTED_DEVICES_PAYLOAD);
    private static final FlowStatistics FLOW_STATISTICS = new FlowStatistics(ImmutableSet.of(1, 5, 100));
    private static final FlowCreatePayload FLOW_CREATE_PAYLOAD
            = new FlowCreatePayload(FLOW_ID, SRC_FLOW_ENDPOINT_PAYLOAD, DST_FLOW_ENDPOINT_PAYLOAD, BANDWIDTH,
            IGNORE_BANDWIDTH, PERIODIC_PINGS, ALLOCATE_PROTECTED_PATH, DESCRIPTION, "created", "lastUpdated",
            DIVERSE_FLOW_ID, "status", LATENCY, PRIORITY, PINNED, ENCAPSULATION_TYPE, PATH_COMPUTATION_STRATEGY);
    private static final FlowUpdatePayload FLOW_UPDATE_PAYLOAD
            = new FlowUpdatePayload(FLOW_ID, SRC_FLOW_ENDPOINT_PAYLOAD, DST_FLOW_ENDPOINT_PAYLOAD, BANDWIDTH,
            IGNORE_BANDWIDTH, PERIODIC_PINGS, ALLOCATE_PROTECTED_PATH, DESCRIPTION, "created", "lastUpdated",
            DIVERSE_FLOW_ID, "status", LATENCY, PRIORITY, PINNED, ENCAPSULATION_TYPE, PATH_COMPUTATION_STRATEGY);

    private static final String MIRROR_POINT_ID_A = "mirror_point_id_a";
    private static final String MIRROR_POINT_ID_B = "mirror_point_id_b";
    private static final String MIRROR_POINT_DIRECTION_A = "forward";
    private static final String MIRROR_POINT_DIRECTION_B = "reverse";

    private static final long MS_TO_NS_MULTIPLIER = 1000000L;

    public static final String ERROR_MESSAGE = "Error";
    public static final String CREATE_TIME = "123";
    public static final String UPDATE_TIME = "345";
    public static final int METER_ID = 17;
    public static final FlowStatusDetails FLOW_STATUS_DETAILS = new FlowStatusDetails(
            FlowPathStatus.ACTIVE, FlowPathStatus.DEGRADED);
    public static final String FLOW_ID_2 = "flow2";
    public static final Long FORWARD_LATENCY = 18L;
    public static final Long REVERSE_LATENCY = 19L;
    public static final String Y_FLOW_ID = "y_flow_id";

    @Autowired
    private FlowMapper flowMapper;

    @Test
    public void testFlowRequestV2Mapping() {
        FlowRequestV2 flowRequestV2 = FlowRequestV2.builder()
                .flowId(FLOW_ID)
                .encapsulationType(ENCAPSULATION_TYPE)
                .source(new FlowEndpointV2(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, SRC_DETECT_CONNECTED_DEVICES))
                .destination(new FlowEndpointV2(DST_SWITCH_ID, DST_PORT, DST_VLAN, DST_DETECT_CONNECTED_DEVICES))
                .description(DESCRIPTION)
                .maximumBandwidth(BANDWIDTH)
                .maxLatency(LATENCY)
                .maxLatencyTier2(LATENCY_TIER2)
                .priority(PRIORITY)
                .diverseFlowId(DIVERSE_FLOW_ID)
                .statistics(FLOW_STATISTICS)
                .build();
        FlowRequest flowRequest = flowMapper.toFlowRequest(flowRequestV2);

        Assertions.assertEquals(FLOW_ID, flowRequest.getFlowId());
        Assertions.assertEquals(SRC_SWITCH_ID, flowRequest.getSource().getSwitchId());
        Assertions.assertEquals(SRC_PORT, (int) flowRequest.getSource().getPortNumber());
        Assertions.assertEquals(SRC_VLAN, flowRequest.getSource().getOuterVlanId());
        Assertions.assertEquals(DST_SWITCH_ID, flowRequest.getDestination().getSwitchId());
        Assertions.assertEquals(DST_PORT, (int) flowRequest.getDestination().getPortNumber());
        Assertions.assertEquals(DST_VLAN, flowRequest.getDestination().getOuterVlanId());
        Assertions.assertEquals(FlowEncapsulationType.TRANSIT_VLAN, flowRequest.getEncapsulationType());
        Assertions.assertEquals(DESCRIPTION, flowRequest.getDescription());
        Assertions.assertEquals(BANDWIDTH, flowRequest.getBandwidth());
        Assertions.assertEquals(LATENCY * MS_TO_NS_MULTIPLIER, (long) flowRequest.getMaxLatency()); // ms to ns
        Assertions.assertEquals(LATENCY_TIER2 * MS_TO_NS_MULTIPLIER, (long) flowRequest.getMaxLatencyTier2());
        Assertions.assertEquals(PRIORITY, flowRequest.getPriority());
        Assertions.assertEquals(DIVERSE_FLOW_ID, flowRequest.getDiverseFlowId());
        Assertions.assertEquals(SRC_DETECT_CONNECTED_DEVICES.isLldp(),
                flowRequest.getDetectConnectedDevices().isSrcLldp());
        Assertions.assertEquals(SRC_DETECT_CONNECTED_DEVICES.isArp(),
                flowRequest.getDetectConnectedDevices().isSrcArp());
        Assertions.assertEquals(DST_DETECT_CONNECTED_DEVICES.isLldp(),
                flowRequest.getDetectConnectedDevices().isDstLldp());
        Assertions.assertEquals(DST_DETECT_CONNECTED_DEVICES.isArp(),
                flowRequest.getDetectConnectedDevices().isDstArp());
        assertThat(flowRequest.getVlanStatistics(), containsInAnyOrder(FLOW_STATISTICS.getVlans().toArray()));
    }

    @Test
    public void testFlowResponseV2Mapping() {
        FlowDto flowDto = new FlowDto(FLOW_ID, BANDWIDTH, true, false, true, false, COOKIE, DESCRIPTION, CREATE_TIME,
                UPDATE_TIME, SRC_SWITCH_ID, DST_SWITCH_ID, SRC_PORT, DST_PORT, SRC_VLAN, DST_VLAN, SRC_INNER_VLAN,
                DST_INNER_VLAN, METER_ID, ENCAPSULATION_ID, FlowState.UP, FLOW_STATUS_DETAILS, "UP", LATENCY,
                LATENCY_TIER2, PRIORITY, true, FlowEncapsulationType.TRANSIT_VLAN, new DetectConnectedDevicesDto(),
                PathComputationStrategy.COST, PathComputationStrategy.LATENCY, Sets.newHashSet(FLOW_ID_2),
                new HashSet<>(), FLOW_ID_2, DST_SWITCH_ID, new ArrayList<>(), FORWARD_LATENCY, REVERSE_LATENCY,
                Instant.MIN, Y_FLOW_ID, FLOW_STATISTICS.getVlans());

        FlowResponseV2 response = flowMapper.toFlowResponseV2(flowDto);
        Assertions.assertEquals(FLOW_ID, response.getFlowId());
        Assertions.assertEquals(BANDWIDTH, response.getMaximumBandwidth());
        Assertions.assertTrue(response.isIgnoreBandwidth());
        Assertions.assertFalse(response.isStrictBandwidth());
        Assertions.assertTrue(response.isPeriodicPings());
        Assertions.assertFalse(response.isAllocateProtectedPath());
        Assertions.assertEquals(CREATE_TIME, response.getCreated());
        Assertions.assertEquals(UPDATE_TIME, response.getLastUpdated());
        Assertions.assertEquals(SRC_SWITCH_ID, response.getSource().getSwitchId());
        Assertions.assertEquals(SRC_PORT, response.getSource().getPortNumber().intValue());
        Assertions.assertEquals(SRC_VLAN, response.getSource().getVlanId());
        Assertions.assertEquals(SRC_INNER_VLAN, response.getSource().getInnerVlanId());
        Assertions.assertEquals(DST_SWITCH_ID, response.getDestination().getSwitchId());
        Assertions.assertEquals(DST_PORT, response.getDestination().getPortNumber().intValue());
        Assertions.assertEquals(DST_VLAN, response.getDestination().getVlanId());
        Assertions.assertEquals(DST_INNER_VLAN, response.getDestination().getInnerVlanId());
        Assertions.assertEquals(FlowState.UP.toString(), response.getStatus());
        Assertions.assertEquals("Up", response.getStatusDetails().getMainPath());
        Assertions.assertEquals("degraded", response.getStatusDetails().getProtectedPath());
        Assertions.assertEquals("UP", response.getStatusInfo());
        Assertions.assertEquals((Long) (LATENCY / 1_000_000), response.getMaxLatency());
        Assertions.assertEquals((Long) (LATENCY_TIER2 / 1_000_000), response.getMaxLatencyTier2());
        Assertions.assertEquals(PRIORITY, response.getPriority());
        Assertions.assertTrue(response.isPinned());
        Assertions.assertEquals(FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase(),

                response.getEncapsulationType());
        Assertions.assertEquals(PathComputationStrategy.COST.toString().toLowerCase(),

                response.getPathComputationStrategy());
        Assertions.assertEquals(PathComputationStrategy.LATENCY.toString().toLowerCase(),

                response.getTargetPathComputationStrategy());
        Assertions.assertEquals(Sets.newHashSet(FLOW_ID_2), response.getDiverseWith());
        Assertions.assertEquals(FLOW_ID_2, response.getAffinityWith());
        Assertions.assertEquals(FLOW_STATISTICS.getVlans(), response.getStatistics().getVlans());
    }

    @Test
    public void testFlowEndpointV2WithoutConnectedDevicesBuilder() {
        FlowEndpointV2 flowEndpointV2 = FlowEndpointV2.builder()
                .switchId(SRC_SWITCH_ID)
                .portNumber(SRC_PORT)
                .vlanId(SRC_VLAN)
                .build();
        Assertions.assertNotNull(flowEndpointV2.getDetectConnectedDevices());
        Assertions.assertFalse(flowEndpointV2.getDetectConnectedDevices().isArp());
        Assertions.assertFalse(flowEndpointV2.getDetectConnectedDevices().isLldp());
    }

    @Test
    public void testFlowEndpointV2WithoutConnectedDevices2Constructor() {
        FlowEndpointV2 flowEndpointV2 = new FlowEndpointV2(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, null);
        Assertions.assertNotNull(flowEndpointV2.getDetectConnectedDevices());
        Assertions.assertFalse(flowEndpointV2.getDetectConnectedDevices().isArp());
        Assertions.assertFalse(flowEndpointV2.getDetectConnectedDevices().isLldp());
    }

    @Test
    public void testFlowRequestV2InvalidEncapsulation() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FlowRequestV2 flowRequestV2 = FlowRequestV2.builder()
                    .flowId(FLOW_ID)
                    .encapsulationType("abc")
                    .source(new FlowEndpointV2(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, SRC_DETECT_CONNECTED_DEVICES))
                    .destination(new FlowEndpointV2(DST_SWITCH_ID, DST_PORT, DST_VLAN, DST_DETECT_CONNECTED_DEVICES))
                    .build();
            flowMapper.toFlowRequest(flowRequestV2);
        });
    }

    @Test
    public void testFlowCreatePayloadToFlowRequest() {
        FlowRequest flowRequest = flowMapper.toFlowCreateRequest(FLOW_CREATE_PAYLOAD);
        Assertions.assertEquals(FLOW_CREATE_PAYLOAD.getDiverseFlowId(), flowRequest.getDiverseFlowId());
        Assertions.assertEquals(Type.CREATE, flowRequest.getType());
        assertFlowDtos(FLOW_CREATE_PAYLOAD, flowRequest);
    }

    @Test
    public void testFlowUpdatePayloadToFlowRequest() {
        FlowRequest flowRequest = flowMapper.toFlowUpdateRequest(FLOW_UPDATE_PAYLOAD);
        Assertions.assertEquals(FLOW_UPDATE_PAYLOAD.getDiverseFlowId(), flowRequest.getDiverseFlowId());
        Assertions.assertEquals(Type.UPDATE, flowRequest.getType());
        assertFlowDtos(FLOW_UPDATE_PAYLOAD, flowRequest);
    }

    private void assertFlowDtos(FlowPayload expected, FlowRequest actual) {
        Assertions.assertEquals(expected.getId(), actual.getFlowId());
        Assertions.assertEquals(expected.getMaximumBandwidth(), actual.getBandwidth());
        Assertions.assertEquals(expected.isIgnoreBandwidth(), actual.isIgnoreBandwidth());
        Assertions.assertEquals(expected.isAllocateProtectedPath(), actual.isAllocateProtectedPath());
        Assertions.assertEquals(expected.isPeriodicPings(), actual.isPeriodicPings());
        Assertions.assertEquals(expected.isPinned(), actual.isPinned());
        Assertions.assertEquals(expected.getDescription(), actual.getDescription());
        Assertions.assertEquals(expected.getMaxLatency() * MS_TO_NS_MULTIPLIER,
                (long) actual.getMaxLatency()); // ms to ns
        Assertions.assertEquals(expected.getPriority(), actual.getPriority());
        Assertions.assertEquals(expected.getEncapsulationType(), actual.getEncapsulationType().name().toLowerCase());
        Assertions.assertEquals(expected.getPathComputationStrategy(), actual.getPathComputationStrategy());

        Assertions.assertEquals(expected.getSource().getDatapath(), actual.getSource().getSwitchId());
        Assertions.assertEquals(expected.getSource().getPortNumber(), actual.getSource().getPortNumber());
        Assertions.assertEquals(expected.getSource().getVlanId(), (Integer) actual.getSource().getOuterVlanId());

        Assertions.assertEquals(expected.getSource().getDetectConnectedDevices().isLldp(),
                actual.getDetectConnectedDevices().isSrcLldp());
        Assertions.assertEquals(expected.getSource().getDetectConnectedDevices().isArp(),
                actual.getDetectConnectedDevices().isSrcArp());

        Assertions.assertEquals(expected.getDestination().getDatapath(), actual.getDestination().getSwitchId());
        Assertions.assertEquals(expected.getDestination().getPortNumber(), actual.getDestination().getPortNumber());
        Assertions.assertEquals(expected.getDestination().getVlanId(),
                (Integer) actual.getDestination().getOuterVlanId());

        Assertions.assertEquals(expected.getDestination().getDetectConnectedDevices().isLldp(),
                actual.getDetectConnectedDevices().isDstLldp());
        Assertions.assertEquals(expected.getDestination().getDetectConnectedDevices().isArp(),
                actual.getDetectConnectedDevices().isDstArp());
    }

    @Test
    public void testFlowPatchDtoToFlowDto() {
        FlowPatchDto flowPatchDto = new FlowPatchDto(LATENCY, PRIORITY, PERIODIC_PINGS,
                TARGET_PATH_COMPUTATION_STRATEGY);
        FlowPatch flowPatch = flowMapper.toFlowPatch(flowPatchDto);
        Assertions.assertEquals(flowPatchDto.getMaxLatency() * MS_TO_NS_MULTIPLIER, (long) flowPatch.getMaxLatency());
        Assertions.assertEquals(flowPatchDto.getPriority(), flowPatch.getPriority());
        Assertions.assertEquals(flowPatchDto.getPeriodicPings(), flowPatch.getPeriodicPings());
        Assertions.assertEquals(flowPatchDto.getTargetPathComputationStrategy(),
                flowPatch.getTargetPathComputationStrategy().name().toLowerCase());
    }

    @Test
    public void testFlowPatchV2ToFlowDto() {
        FlowPatchV2 flowPatchDto = new FlowPatchV2(
                new FlowPatchEndpoint(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, SRC_INNER_VLAN, SRC_DETECT_CONNECTED_DEVICES),
                new FlowPatchEndpoint(DST_SWITCH_ID, DST_PORT, DST_VLAN, DST_INNER_VLAN, DST_DETECT_CONNECTED_DEVICES),
                (long) BANDWIDTH, IGNORE_BANDWIDTH, STRICT_BANDWIDTH, PERIODIC_PINGS, DESCRIPTION,
                LATENCY, LATENCY_TIER2, PRIORITY, DIVERSE_FLOW_ID, AFFINITY_FLOW_ID, PINNED, ALLOCATE_PROTECTED_PATH,
                ENCAPSULATION_TYPE, PATH_COMPUTATION_STRATEGY, TARGET_PATH_COMPUTATION_STRATEGY, FLOW_STATISTICS);
        FlowPatch flowPatch = flowMapper.toFlowPatch(flowPatchDto);

        Assertions.assertEquals(flowPatchDto.getSource().getSwitchId(), flowPatch.getSource().getSwitchId());
        Assertions.assertEquals(flowPatchDto.getSource().getPortNumber(), flowPatch.getSource().getPortNumber());
        Assertions.assertEquals(flowPatchDto.getSource().getVlanId(), flowPatch.getSource().getVlanId());
        Assertions.assertEquals(flowPatchDto.getSource().getInnerVlanId(), flowPatch.getSource().getInnerVlanId());
        Assertions.assertEquals(flowPatchDto.getSource().getDetectConnectedDevices().isLldp(),
                flowPatch.getSource().getTrackLldpConnectedDevices());
        Assertions.assertEquals(flowPatchDto.getSource().getDetectConnectedDevices().isArp(),
                flowPatch.getSource().getTrackArpConnectedDevices());
        Assertions.assertEquals(flowPatchDto.getDestination().getSwitchId(), flowPatch.getDestination().getSwitchId());
        Assertions.assertEquals(flowPatchDto.getDestination().getPortNumber(),
                flowPatch.getDestination().getPortNumber());
        Assertions.assertEquals(flowPatchDto.getDestination().getVlanId(), flowPatch.getDestination().getVlanId());
        Assertions.assertEquals(flowPatchDto.getDestination().getInnerVlanId(),
                flowPatch.getDestination().getInnerVlanId());
        Assertions.assertEquals(flowPatchDto.getDestination().getDetectConnectedDevices().isLldp(),
                flowPatch.getDestination().getTrackLldpConnectedDevices());
        Assertions.assertEquals(flowPatchDto.getDestination().getDetectConnectedDevices().isArp(),
                flowPatch.getDestination().getTrackArpConnectedDevices());
        Assertions.assertEquals(flowPatchDto.getMaxLatency() * MS_TO_NS_MULTIPLIER,
                (long) flowPatch.getMaxLatency());
        Assertions.assertEquals(flowPatchDto.getMaxLatencyTier2() * MS_TO_NS_MULTIPLIER,
                (long) flowPatch.getMaxLatencyTier2());
        Assertions.assertEquals(flowPatchDto.getPriority(), flowPatch.getPriority());
        Assertions.assertEquals(flowPatchDto.getPeriodicPings(), flowPatch.getPeriodicPings());
        Assertions.assertEquals(flowPatchDto.getTargetPathComputationStrategy(),
                flowPatch.getTargetPathComputationStrategy().name().toLowerCase());
        Assertions.assertEquals(flowPatchDto.getDiverseFlowId(), flowPatch.getDiverseFlowId());
        Assertions.assertEquals(flowPatchDto.getAffinityFlowId(), flowPatch.getAffinityFlowId());
        Assertions.assertEquals(flowPatchDto.getMaximumBandwidth(), flowPatch.getBandwidth());
        Assertions.assertEquals(flowPatchDto.getAllocateProtectedPath(), flowPatch.getAllocateProtectedPath());
        Assertions.assertEquals(flowPatchDto.getPinned(), flowPatch.getPinned());
        Assertions.assertEquals(flowPatchDto.getIgnoreBandwidth(), flowPatch.getIgnoreBandwidth());
        Assertions.assertEquals(flowPatchDto.getStrictBandwidth(), flowPatch.getStrictBandwidth());
        Assertions.assertEquals(flowPatchDto.getDescription(), flowPatch.getDescription());
        Assertions.assertEquals(flowPatchDto.getEncapsulationType(),
                flowPatch.getEncapsulationType().name().toLowerCase());
        Assertions.assertEquals(flowPatchDto.getPathComputationStrategy(),
                flowPatch.getPathComputationStrategy().name().toLowerCase());
        assertThat(flowPatch.getVlanStatistics(),
                containsInAnyOrder(flowPatchDto.getStatistics().getVlans().toArray()));
    }

    @Test
    public void testFlowMirrorPointCreatePayloadToFlowMirrorPointCreateRequest() {
        FlowMirrorPointPayload payload = FlowMirrorPointPayload.builder()
                .mirrorPointId(MIRROR_POINT_ID_A)
                .mirrorPointDirection(MIRROR_POINT_DIRECTION_A)
                .mirrorPointSwitchId(SRC_SWITCH_ID)
                .sinkEndpoint(new FlowEndpointV2(DST_SWITCH_ID, DST_PORT, DST_VLAN, DST_INNER_VLAN))
                .build();

        FlowMirrorPointCreateRequest request = flowMapper.toFlowMirrorPointCreateRequest(FLOW_ID, payload);

        Assertions.assertEquals(FLOW_ID, request.getFlowId());
        Assertions.assertEquals(payload.getMirrorPointId(), request.getMirrorPointId());
        Assertions.assertEquals(payload.getMirrorPointDirection(),
                request.getMirrorPointDirection().toString().toLowerCase());
        Assertions.assertEquals(payload.getMirrorPointSwitchId(), request.getMirrorPointSwitchId());
        Assertions.assertEquals(payload.getSinkEndpoint().getSwitchId(), request.getSinkEndpoint().getSwitchId());
        Assertions.assertEquals(payload.getSinkEndpoint().getPortNumber(), request.getSinkEndpoint().getPortNumber());
        Assertions.assertEquals(payload.getSinkEndpoint().getVlanId(), request.getSinkEndpoint().getOuterVlanId());
        Assertions.assertEquals(payload.getSinkEndpoint().getInnerVlanId(), request.getSinkEndpoint().getInnerVlanId());
    }

    @Test
    public void testFlowMirrorPointResponseToFlowMirrorPointCreatePayload() {
        FlowMirrorPointResponse response = FlowMirrorPointResponse.builder()
                .flowId(FLOW_ID)
                .mirrorPointId(MIRROR_POINT_ID_A)
                .mirrorPointDirection(MIRROR_POINT_DIRECTION_A)
                .mirrorPointSwitchId(SRC_SWITCH_ID)
                .sinkEndpoint(FlowEndpoint.builder()
                        .switchId(DST_SWITCH_ID)
                        .portNumber(DST_PORT)
                        .outerVlanId(DST_VLAN)
                        .innerVlanId(DST_INNER_VLAN)
                        .build())
                .build();

        FlowMirrorPointResponseV2 apiResponse = flowMapper.toFlowMirrorPointResponseV2(response);

        Assertions.assertEquals(response.getFlowId(), apiResponse.getFlowId());
        Assertions.assertEquals(response.getMirrorPointId(), apiResponse.getMirrorPointId());
        Assertions.assertEquals(response.getMirrorPointDirection(), apiResponse.getMirrorPointDirection());
        Assertions.assertEquals(response.getMirrorPointSwitchId(), apiResponse.getMirrorPointSwitchId());
        Assertions.assertEquals(response.getSinkEndpoint().getSwitchId(), apiResponse.getSinkEndpoint().getSwitchId());
        Assertions.assertEquals(response.getSinkEndpoint().getPortNumber(),
                apiResponse.getSinkEndpoint().getPortNumber());
        Assertions.assertEquals(response.getSinkEndpoint().getOuterVlanId(),
                apiResponse.getSinkEndpoint().getVlanId());
        Assertions.assertEquals(response.getSinkEndpoint().getInnerVlanId(),
                apiResponse.getSinkEndpoint().getInnerVlanId());
    }

    @Test
    public void testFlowMirrorPointsDumpResponseToFlowMirrorPointsResponseV2() {
        FlowMirrorPoint firstPoint = FlowMirrorPoint.builder()
                .mirrorPointId(MIRROR_POINT_ID_A)
                .mirrorPointDirection(MIRROR_POINT_DIRECTION_A)
                .mirrorPointSwitchId(SRC_SWITCH_ID)
                .sinkEndpoint(FlowEndpoint.builder()
                        .switchId(SRC_SWITCH_ID)
                        .portNumber(SRC_PORT)
                        .outerVlanId(SRC_VLAN)
                        .innerVlanId(SRC_INNER_VLAN)
                        .build())
                .build();
        FlowMirrorPoint secondPoint = FlowMirrorPoint.builder()
                .mirrorPointId(MIRROR_POINT_ID_B)
                .mirrorPointDirection(MIRROR_POINT_DIRECTION_B)
                .mirrorPointSwitchId(SRC_SWITCH_ID)
                .sinkEndpoint(FlowEndpoint.builder()
                        .switchId(DST_SWITCH_ID)
                        .portNumber(DST_PORT)
                        .outerVlanId(DST_VLAN)
                        .innerVlanId(DST_INNER_VLAN)
                        .build())
                .build();
        List<FlowMirrorPoint> points = Lists.newArrayList(firstPoint, secondPoint);
        FlowMirrorPointsDumpResponse response = FlowMirrorPointsDumpResponse.builder()
                .flowId(FLOW_ID)
                .points(points)
                .build();

        FlowMirrorPointsResponseV2 apiResponse = flowMapper.toFlowMirrorPointsResponseV2(response);

        Assertions.assertEquals(response.getFlowId(), apiResponse.getFlowId());
        Assertions.assertEquals(2, apiResponse.getPoints().size());

        FlowMirrorPointPayload firstPayload = apiResponse.getPoints().get(0);
        Assertions.assertEquals(firstPoint.getMirrorPointId(), firstPayload.getMirrorPointId());
        Assertions.assertEquals(firstPoint.getMirrorPointDirection(), firstPayload.getMirrorPointDirection());
        Assertions.assertEquals(firstPoint.getMirrorPointSwitchId(), firstPayload.getMirrorPointSwitchId());
        Assertions.assertEquals(firstPoint.getSinkEndpoint().getSwitchId(),
                firstPayload.getSinkEndpoint().getSwitchId());
        Assertions.assertEquals(firstPoint.getSinkEndpoint().getPortNumber(),
                firstPayload.getSinkEndpoint().getPortNumber());
        Assertions.assertEquals(firstPoint.getSinkEndpoint().getOuterVlanId(),
                firstPayload.getSinkEndpoint().getVlanId());
        Assertions.assertEquals(firstPoint.getSinkEndpoint().getInnerVlanId(),
                firstPayload.getSinkEndpoint().getInnerVlanId());

        FlowMirrorPointPayload secondPayload = apiResponse.getPoints().get(1);
        Assertions.assertEquals(secondPoint.getMirrorPointId(), secondPayload.getMirrorPointId());
        Assertions.assertEquals(secondPoint.getMirrorPointDirection(), secondPayload.getMirrorPointDirection());
        Assertions.assertEquals(secondPoint.getMirrorPointSwitchId(), secondPayload.getMirrorPointSwitchId());
        Assertions.assertEquals(secondPoint.getSinkEndpoint().getSwitchId(),
                secondPayload.getSinkEndpoint().getSwitchId());
        Assertions.assertEquals(secondPoint.getSinkEndpoint().getPortNumber(),
                secondPayload.getSinkEndpoint().getPortNumber());
        Assertions.assertEquals(secondPoint.getSinkEndpoint().getOuterVlanId(),
                secondPayload.getSinkEndpoint().getVlanId());
        Assertions.assertEquals(secondPoint.getSinkEndpoint().getInnerVlanId(),
                secondPayload.getSinkEndpoint().getInnerVlanId());
    }

    @Test
    public void testPingOutput() {
        FlowPingResponse response = new FlowPingResponse(
                FLOW_ID, new UniFlowPingResponse(false, Errors.TIMEOUT, null, null),
                new UniFlowPingResponse(true, null, new PingMeters(1, 2, 3), null), ERROR_MESSAGE);
        PingOutput output = flowMapper.toPingOutput(response);

        Assertions.assertEquals(response.getFlowId(), output.getFlowId());
        Assertions.assertEquals(response.getError(), output.getError());

        Assertions.assertEquals(response.getForward().isPingSuccess(), output.getForward().isPingSuccess());
        Assertions.assertEquals(0, output.getForward().getLatency());
        Assertions.assertEquals(TIMEOUT_ERROR_MESSAGE, output.getForward().getError());

        Assertions.assertEquals(response.getReverse().isPingSuccess(), output.getReverse().isPingSuccess());
        Assertions.assertEquals(1, output.getReverse().getLatency());
        Assertions.assertNull(output.getReverse().getError());
    }

    @Test
    public void testVlanStatisticsMapping() {
        Set<Integer> vlanStatistics = new HashSet<>();
        vlanStatistics.add(5);
        FlowEndpointV2 endpointV2 = new FlowEndpointV2(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, SRC_DETECT_CONNECTED_DEVICES);

        FlowDto sourceDto = FlowDto.builder()
                .vlanStatistics(vlanStatistics)
                .state(FlowState.IN_PROGRESS)
                .flowId("some id")
                .build();

        FlowResponseV2 result = flowMapper.generatedMap(sourceDto, endpointV2, endpointV2);

        assertThat(result.getStatistics(), is(notNullValue()));
        assertThat(result.getStatistics().getVlans(), containsInAnyOrder(vlanStatistics.toArray()));
    }

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
