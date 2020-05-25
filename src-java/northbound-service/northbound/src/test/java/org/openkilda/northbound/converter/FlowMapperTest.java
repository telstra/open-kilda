/* Copyright 2019 Telstra Open Source
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRequest.Type;
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;

import org.junit.Test;
import org.mapstruct.factory.Mappers;

public class FlowMapperTest {
    private static final String FLOW_ID = "flow1";
    private static final String DIVERSE_FLOW_ID = "flow2";
    private static final SwitchId SRC_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId DST_SWITCH_ID = new SwitchId("00:00:00:00:00:00:00:02");
    private static final int SRC_PORT = 1;
    private static final int DST_PORT = 2;
    private static final int SRC_VLAN = 3;
    private static final int DST_VLAN = 4;
    private static final int BANDWIDTH = 1000;
    private static final boolean IGNORE_BANDWIDTH = true;
    private static final boolean PERIODIC_PINGS = true;
    private static final boolean ALLOCATE_PROTECTED_PATH = true;
    private static final boolean PINNED = true;
    private static final Long LATENCY = 10L;
    private static final Integer PRIORITY = 15;
    private static final String DESCRIPTION = "Description";
    private static final String ENCAPSULATION_TYPE = "transit_vlan";
    private static final String PATH_COMPUTATION_STRATEGY = "path_computation_strategy";
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
    private static final FlowCreatePayload FLOW_CREATE_PAYLOAD
            = new FlowCreatePayload(FLOW_ID, SRC_FLOW_ENDPOINT_PAYLOAD, DST_FLOW_ENDPOINT_PAYLOAD, BANDWIDTH,
            IGNORE_BANDWIDTH, PERIODIC_PINGS, ALLOCATE_PROTECTED_PATH, DESCRIPTION, "created", "lastUpdated",
            DIVERSE_FLOW_ID, "status", LATENCY, PRIORITY, PINNED, ENCAPSULATION_TYPE, PATH_COMPUTATION_STRATEGY);
    private static final FlowUpdatePayload FLOW_UPDATE_PAYLOAD
            = new FlowUpdatePayload(FLOW_ID, SRC_FLOW_ENDPOINT_PAYLOAD, DST_FLOW_ENDPOINT_PAYLOAD, BANDWIDTH,
            IGNORE_BANDWIDTH, PERIODIC_PINGS, ALLOCATE_PROTECTED_PATH, DESCRIPTION, "created", "lastUpdated",
            DIVERSE_FLOW_ID, "status", LATENCY, PRIORITY, PINNED, ENCAPSULATION_TYPE, PATH_COMPUTATION_STRATEGY);

    private FlowMapper flowMapper = Mappers.getMapper(FlowMapper.class);

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
                .priority(PRIORITY)
                .diverseFlowId(DIVERSE_FLOW_ID)
                .build();
        FlowRequest flowRequest = flowMapper.toFlowRequest(flowRequestV2);

        assertEquals(FLOW_ID, flowRequest.getFlowId());
        assertEquals(SRC_SWITCH_ID, flowRequest.getSource().getSwitchId());
        assertEquals(SRC_PORT, (int) flowRequest.getSource().getPortNumber());
        assertEquals(SRC_VLAN, flowRequest.getSource().getOuterVlanId());
        assertEquals(DST_SWITCH_ID, flowRequest.getDestination().getSwitchId());
        assertEquals(DST_PORT, (int) flowRequest.getDestination().getPortNumber());
        assertEquals(DST_VLAN, flowRequest.getDestination().getOuterVlanId());
        assertEquals(FlowEncapsulationType.TRANSIT_VLAN, flowRequest.getEncapsulationType());
        assertEquals(DESCRIPTION, flowRequest.getDescription());
        assertEquals(BANDWIDTH, flowRequest.getBandwidth());
        assertEquals(LATENCY, flowRequest.getMaxLatency());
        assertEquals(PRIORITY, flowRequest.getPriority());
        assertEquals(DIVERSE_FLOW_ID, flowRequest.getDiverseFlowId());
        assertEquals(SRC_DETECT_CONNECTED_DEVICES.isLldp(), flowRequest.getDetectConnectedDevices().isSrcLldp());
        assertEquals(SRC_DETECT_CONNECTED_DEVICES.isArp(), flowRequest.getDetectConnectedDevices().isSrcArp());
        assertEquals(DST_DETECT_CONNECTED_DEVICES.isLldp(), flowRequest.getDetectConnectedDevices().isDstLldp());
        assertEquals(DST_DETECT_CONNECTED_DEVICES.isArp(), flowRequest.getDetectConnectedDevices().isDstArp());
    }

    @Test
    public void testFlowEndpointV2WithoutConnectedDevicesBuilder() {
        FlowEndpointV2 flowEndpointV2 = FlowEndpointV2.builder()
                .switchId(SRC_SWITCH_ID)
                .portNumber(SRC_PORT)
                .vlanId(SRC_VLAN)
                .build();
        assertNotNull(flowEndpointV2.getDetectConnectedDevices());
        assertFalse(flowEndpointV2.getDetectConnectedDevices().isArp());
        assertFalse(flowEndpointV2.getDetectConnectedDevices().isLldp());
    }

    @Test
    public void testFlowEndpointV2WithoutConnectedDevices2Constructor() {
        FlowEndpointV2 flowEndpointV2 = new FlowEndpointV2(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, null);
        assertNotNull(flowEndpointV2.getDetectConnectedDevices());
        assertFalse(flowEndpointV2.getDetectConnectedDevices().isArp());
        assertFalse(flowEndpointV2.getDetectConnectedDevices().isLldp());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFlowRequestV2InvalidEncapsulation() {
        FlowRequestV2 flowRequestV2 = FlowRequestV2.builder()
                .flowId(FLOW_ID)
                .encapsulationType("abc")
                .source(new FlowEndpointV2(SRC_SWITCH_ID, SRC_PORT, SRC_VLAN, SRC_DETECT_CONNECTED_DEVICES))
                .destination(new FlowEndpointV2(DST_SWITCH_ID, DST_PORT, DST_VLAN, DST_DETECT_CONNECTED_DEVICES))
                .build();
        flowMapper.toFlowRequest(flowRequestV2);
    }

    @Test
    public void testFlowPayloadToFlowRequest() {
        FlowRequest flowRequest = flowMapper.toFlowRequest(FLOW_CREATE_PAYLOAD);
        assertFlowDtos(FLOW_CREATE_PAYLOAD, flowRequest);
    }

    @Test
    public void testFlowCreatePayloadToFlowRequest() {
        FlowRequest flowRequest = flowMapper.toFlowCreateRequest(FLOW_CREATE_PAYLOAD);
        assertEquals(FLOW_CREATE_PAYLOAD.getDiverseFlowId(), flowRequest.getDiverseFlowId());
        assertEquals(Type.CREATE, flowRequest.getType());
        assertFlowDtos(FLOW_CREATE_PAYLOAD, flowRequest);
    }

    @Test
    public void testFlowUpdatePayloadToFlowRequest() {
        FlowRequest flowRequest = flowMapper.toFlowUpdateRequest(FLOW_UPDATE_PAYLOAD);
        assertEquals(FLOW_UPDATE_PAYLOAD.getDiverseFlowId(), flowRequest.getDiverseFlowId());
        assertEquals(Type.UPDATE, flowRequest.getType());
        assertFlowDtos(FLOW_UPDATE_PAYLOAD, flowRequest);
    }

    private void assertFlowDtos(FlowPayload expected, FlowRequest actual) {
        assertEquals(expected.getId(), actual.getFlowId());
        assertEquals(expected.getMaximumBandwidth(), actual.getBandwidth());
        assertEquals(expected.isIgnoreBandwidth(), actual.isIgnoreBandwidth());
        assertEquals(expected.isAllocateProtectedPath(), actual.isAllocateProtectedPath());
        assertEquals(expected.isPeriodicPings(), actual.isPeriodicPings());
        assertEquals(expected.isPinned(), actual.isPinned());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getMaxLatency(), actual.getMaxLatency());
        assertEquals(expected.getPriority(), actual.getPriority());
        assertEquals(expected.getEncapsulationType(), actual.getEncapsulationType().name().toLowerCase());
        assertEquals(expected.getPathComputationStrategy(), actual.getPathComputationStrategy());

        assertEquals(expected.getSource().getDatapath(), actual.getSource().getSwitchId());
        assertEquals(expected.getSource().getPortNumber(), actual.getSource().getPortNumber());
        assertEquals(expected.getSource().getVlanId(), (Integer) actual.getSource().getOuterVlanId());

        assertEquals(expected.getSource().getDetectConnectedDevices().isLldp(),
                actual.getDetectConnectedDevices().isSrcLldp());
        assertEquals(expected.getSource().getDetectConnectedDevices().isArp(),
                actual.getDetectConnectedDevices().isSrcArp());

        assertEquals(expected.getDestination().getDatapath(), actual.getDestination().getSwitchId());
        assertEquals(expected.getDestination().getPortNumber(), actual.getDestination().getPortNumber());
        assertEquals(expected.getDestination().getVlanId(), (Integer) actual.getDestination().getOuterVlanId());

        assertEquals(expected.getDestination().getDetectConnectedDevices().isLldp(),
                actual.getDetectConnectedDevices().isDstLldp());
        assertEquals(expected.getDestination().getDetectConnectedDevices().isArp(),
                actual.getDetectConnectedDevices().isDstArp());
    }
}
