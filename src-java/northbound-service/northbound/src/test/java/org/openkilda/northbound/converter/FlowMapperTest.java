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
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
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
    private static final Long LATENCY = 10L;
    private static final Integer PRIORITY = 15;
    private static final String DESCRIPTION = "Description";
    private static final String ENCAPSULATION_TYPE = "transit_vlan";
    private static final DetectConnectedDevicesV2 SRC_DETECT_CONNECTED_DEVICES = new DetectConnectedDevicesV2(
            true, false);
    private static final DetectConnectedDevicesV2 DST_DETECT_CONNECTED_DEVICES = new DetectConnectedDevicesV2(
            false, true);

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
}
