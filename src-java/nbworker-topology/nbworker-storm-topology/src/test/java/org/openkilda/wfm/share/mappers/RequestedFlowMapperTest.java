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

package org.openkilda.wfm.share.mappers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RequestedFlowMapperTest {

    public static final String FLOW_ID = "flow_id";
    public static final SwitchId SRC_SWITCH_ID = new SwitchId("1");
    public static final SwitchId DST_SWITCH_ID = new SwitchId("2");
    public static final SwitchId LOOP_SWITCH_ID = new SwitchId("3");
    public static final Integer SRC_PORT = 1;
    public static final Integer DST_PORT = 2;
    public static final int SRC_VLAN = 3;
    public static final int DST_VLAN = 4;
    public static final int SRC_INNER_VLAN = 5;
    public static final int DST_INNER_VLAN = 6;
    public static final Integer PRIORITY = 7;
    public static final String DESCRIPTION = "description";
    public static final int BANDWIDTH = 1000;
    public static final Long MAX_LATENCY = 200L;
    public static final Long MAX_LATENCY_TIER_2 = 400L;
    public static final String PATH_COMPUTATION_STRATEGY = PathComputationStrategy.COST.toString().toLowerCase();
    private static final Set<Integer> STATISTICS = new HashSet<>(Collections.singletonList(8080));

    private static final Flow FLOW = Flow.builder()
            .flowId(FLOW_ID)
            .srcSwitch(Switch.builder().switchId(SRC_SWITCH_ID).build())
            .srcPort(SRC_PORT)
            .srcVlan(SRC_VLAN)
            .srcInnerVlan(SRC_INNER_VLAN)
            .destSwitch(Switch.builder().switchId(DST_SWITCH_ID).build())
            .destPort(DST_PORT)
            .destVlan(DST_VLAN)
            .destInnerVlan(DST_INNER_VLAN)
            .priority(PRIORITY)
            .description(DESCRIPTION)
            .bandwidth(BANDWIDTH)
            .maxLatency(MAX_LATENCY)
            .maxLatencyTier2(MAX_LATENCY_TIER_2)
            .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
            .pathComputationStrategy(PathComputationStrategy.COST)
            .loopSwitchId(LOOP_SWITCH_ID)
            .detectConnectedDevices(
                    new DetectConnectedDevices(true, true, true, true, true, true, true, true))
            .pinned(true)
            .allocateProtectedPath(true)
            .ignoreBandwidth(true)
            .periodicPings(true)
            .vlanStatistics(STATISTICS)
            .build();

    private static final FlowRequest FLOW_REQUEST = FlowRequest.builder()
            .flowId(FLOW_ID)
            .source(FlowEndpoint.builder()
                    .switchId(SRC_SWITCH_ID)
                    .portNumber(SRC_PORT)
                    .outerVlanId(SRC_VLAN)
                    .innerVlanId(SRC_INNER_VLAN)
                    .build())
            .destination(FlowEndpoint.builder()
                    .switchId(DST_SWITCH_ID)
                    .portNumber(DST_PORT)
                    .outerVlanId(DST_VLAN)
                    .innerVlanId(DST_INNER_VLAN)
                    .build())
            .detectConnectedDevices(new DetectConnectedDevicesDto(true, true, true, true, true, true, true, true))
            .bandwidth(BANDWIDTH)
            .ignoreBandwidth(true)
            .periodicPings(true)
            .allocateProtectedPath(true)
            .description(DESCRIPTION)
            .maxLatency(MAX_LATENCY)
            .maxLatencyTier2(MAX_LATENCY_TIER_2)
            .priority(PRIORITY)
            .pinned(true)
            .encapsulationType(org.openkilda.messaging.payload.flow.FlowEncapsulationType.VXLAN)
            .pathComputationStrategy(PATH_COMPUTATION_STRATEGY)
            .loopSwitchId(LOOP_SWITCH_ID)
            .vlanStatistics(STATISTICS)
            .build();

    @Test
    public void mapFlowToFlowRequestTest() {
        FlowRequest flowRequest = RequestedFlowMapper.INSTANCE.toFlowRequest(FLOW);
        assertEquals(FLOW_ID, flowRequest.getFlowId());
        assertEquals(SRC_SWITCH_ID, flowRequest.getSource().getSwitchId());
        assertEquals(SRC_PORT, flowRequest.getSource().getPortNumber());
        assertEquals(SRC_VLAN, flowRequest.getSource().getOuterVlanId());
        assertEquals(SRC_INNER_VLAN, flowRequest.getSource().getInnerVlanId());
        assertEquals(DST_SWITCH_ID, flowRequest.getDestination().getSwitchId());
        assertEquals(DST_PORT, flowRequest.getDestination().getPortNumber());
        assertEquals(DST_VLAN, flowRequest.getDestination().getOuterVlanId());
        assertEquals(DST_INNER_VLAN, flowRequest.getDestination().getInnerVlanId());
        assertEquals(PRIORITY, flowRequest.getPriority());
        assertEquals(DESCRIPTION, flowRequest.getDescription());
        assertEquals(BANDWIDTH, flowRequest.getBandwidth());
        assertEquals(MAX_LATENCY, flowRequest.getMaxLatency());
        assertEquals(MAX_LATENCY_TIER_2, flowRequest.getMaxLatencyTier2());
        assertEquals(org.openkilda.messaging.payload.flow.FlowEncapsulationType.TRANSIT_VLAN,
                flowRequest.getEncapsulationType());
        assertEquals(PATH_COMPUTATION_STRATEGY, flowRequest.getPathComputationStrategy());
        assertEquals(LOOP_SWITCH_ID, flowRequest.getLoopSwitchId());
        assertTrue(flowRequest.isPinned());
        assertTrue(flowRequest.isAllocateProtectedPath());
        assertTrue(flowRequest.isIgnoreBandwidth());
        assertTrue(flowRequest.isPeriodicPings());
        assertEquals(new DetectConnectedDevicesDto(true, true, true, true, true, true, true, true),
                flowRequest.getDetectConnectedDevices());
        assertThat(flowRequest.getVlanStatistics(), equalTo(STATISTICS));
    }

    @Test
    public void mapFlowRequestToFlowTest() {
        Flow flow = RequestedFlowMapper.INSTANCE.toFlow(FLOW_REQUEST);
        assertEquals(FLOW_ID, flow.getFlowId());
        assertEquals(SRC_SWITCH_ID, flow.getSrcSwitchId());
        assertEquals(SRC_PORT.intValue(), flow.getSrcPort());
        assertEquals(SRC_VLAN, flow.getSrcVlan());
        assertEquals(SRC_INNER_VLAN, flow.getSrcInnerVlan());
        assertEquals(DST_SWITCH_ID, flow.getDestSwitchId());
        assertEquals(DST_PORT.intValue(), flow.getDestPort());
        assertEquals(DST_VLAN, flow.getDestVlan());
        assertEquals(DST_INNER_VLAN, flow.getDestInnerVlan());
        assertEquals(PRIORITY, flow.getPriority());
        assertEquals(DESCRIPTION, flow.getDescription());
        assertEquals(BANDWIDTH, flow.getBandwidth());
        assertEquals(MAX_LATENCY, flow.getMaxLatency());
        assertEquals(MAX_LATENCY_TIER_2, flow.getMaxLatencyTier2());
        assertEquals(FlowEncapsulationType.VXLAN, flow.getEncapsulationType());
        assertEquals(PathComputationStrategy.COST, flow.getPathComputationStrategy());
        assertEquals(LOOP_SWITCH_ID, flow.getLoopSwitchId());
        assertTrue(flow.isPinned());
        assertTrue(flow.isAllocateProtectedPath());
        assertTrue(flow.isIgnoreBandwidth());
        assertTrue(flow.isPeriodicPings());
        assertEquals(new DetectConnectedDevices(true, true, true, true, true, true, true, true),
                flow.getDetectConnectedDevices());
        assertThat(flow.getVlanStatistics(), containsInAnyOrder(STATISTICS.toArray()));
    }
}
