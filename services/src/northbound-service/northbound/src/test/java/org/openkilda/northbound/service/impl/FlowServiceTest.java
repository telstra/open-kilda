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

package org.openkilda.northbound.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.SwapFlowResponse;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.Vxlan;
import org.openkilda.northbound.MessageExchanger;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload;
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.SwitchService;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.northbound.utils.TestCorrelationIdFactory;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
public class FlowServiceTest {

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private TransitVlanRepository transitVlanRepository;

    @Mock
    private VxlanRepository vxlanRepository;

    @MockBean
    private SwitchService switchService;

    private int requestIdIndex = 0;

    @Autowired
    private CorrelationIdFactory idFactory;

    @Autowired
    private FlowServiceImpl flowService;

    @Autowired
    private MessageExchanger messageExchanger;

    private static final SwitchId TEST_SWITCH_ID_A = new SwitchId(1);
    private static final SwitchId TEST_SWITCH_ID_B = new SwitchId(2);
    private static final SwitchId TEST_SWITCH_ID_C = new SwitchId(3);
    private static final SwitchId TEST_SWITCH_ID_D = new SwitchId(4);
    private static final SwitchId TEST_SWITCH_ID_E = new SwitchId(5);
    private static final String TEST_FLOW_ID_A = "test_flow_id_a";
    private static final String TEST_FLOW_ID_B = "test_flow_id_b";

    private static final int FLOW_A_SRC_PORT = 10;
    private static final int FLOW_A_DST_PORT = 20;
    private static final int FLOW_A_SEGMENT_A_SRC_PORT = 11;
    private static final int FLOW_A_SEGMENT_A_DST_PORT = 15;
    private static final int FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED = 21;
    private static final int FLOW_A_SEGMENT_A_DST_PORT_PROTECTED = 25;
    private static final int FLOW_A_SEGMENT_B_SRC_PORT = 16;
    private static final int FLOW_A_SEGMENT_B_DST_PORT = 19;
    private static final int FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED = 26;
    private static final int FLOW_A_SEGMENT_B_DST_PORT_PROTECTED = 29;
    private static final int FLOW_A_SRC_VLAN = 11;
    private static final int FLOW_A_ENCAP_ID = 12;
    private static final PathId FLOW_A_FORWARD_PATH_ID = new PathId(TEST_FLOW_ID_A + "_forward_path");
    private static final PathId FLOW_A_REVERSE_PATH_ID = new PathId(TEST_FLOW_ID_A + "_reverse_path");
    private static final PathId FLOW_A_FORWARD_PATH_ID_PROTECTED = new PathId(TEST_FLOW_ID_A + "_forward_protect_path");
    private static final PathId FLOW_A_REVERSE_PATH_ID_PROTECTED = new PathId(TEST_FLOW_ID_A + "_reverse_protect_path");
    private static final int FLOW_A_ENCAP_ID_PROTECTED = 22;
    private static final int FLOW_A_DST_VLAN = 14;
    private static final int FLOW_A_FORWARD_METER_ID = 32;
    private static final int FLOW_A_REVERSE_METER_ID = 33;
    private static final int FLOW_A_FORWARD_METER_ID_PROTECTED = 42;
    private static final int FLOW_A_REVERSE_METER_ID_PROTECTED = 43;
    private static final long FLOW_A_FORWARD_COOKIE = 1 | Cookie.FORWARD_FLOW_COOKIE_MASK;
    private static final long FLOW_A_REVERSE_COOKIE = 1 | Cookie.REVERSE_FLOW_COOKIE_MASK;
    private static final long FLOW_A_FORWARD_COOKIE_PROTECTED = 2 | Cookie.FORWARD_FLOW_COOKIE_MASK;
    private static final long FLOW_A_REVERSE_COOKIE_PROTECTED = 2 | Cookie.REVERSE_FLOW_COOKIE_MASK;
    private static final int FLOW_B_SRC_PORT = 1;
    private static final int FLOW_B_SRC_VLAN = 15;
    private static final int FLOW_B_DST_VLAN = 16;
    private static final long FLOW_B_FORWARD_COOKIE = 2 | Cookie.FORWARD_FLOW_COOKIE_MASK;
    private static final long FLOW_B_REVERSE_COOKIE = 2 | Cookie.REVERSE_FLOW_COOKIE_MASK;
    private static final int FLOW_B_FORWARD_METER_ID = 34;
    private static final int FLOW_B_REVERSE_METER_ID = 35;

    @Before
    public void reset() {
        messageExchanger.resetMockedResponses();

        flowService.flowRepository = flowRepository;
        flowService.transitVlanRepository = transitVlanRepository;
        flowService.vxlanRepository = vxlanRepository;
    }

    @Test
    public void swapFlowEndpoint() throws Exception {
        String correlationId = "bulk-flow-update";
        RequestCorrelationId.create(correlationId);

        String firstFlowId = "bulk-flow-1";
        String secondFlowId = "bulk-flow-2";

        FlowEndpointV2 firstEndpoint = new FlowEndpointV2(new SwitchId("ff:00"), 1, 1);
        FlowEndpointV2 secondEndpoint = new FlowEndpointV2(new SwitchId("ff:01"), 2, 2);

        SwapFlowPayload firstFlowPayload = SwapFlowPayload.builder()
                .flowId(firstFlowId)
                .source(firstEndpoint)
                .destination(firstEndpoint)
                .build();

        SwapFlowPayload secondFlowPayload = SwapFlowPayload.builder()
                .flowId(secondFlowId)
                .source(secondEndpoint)
                .destination(secondEndpoint)
                .build();

        SwapFlowEndpointPayload input = new SwapFlowEndpointPayload(firstFlowPayload, secondFlowPayload);

        FlowDto firstResponse = FlowDto.builder()
                .flowId(firstFlowId).bandwidth(10000).description(firstFlowId).state(FlowState.UP)
                .sourceSwitch(new SwitchId("ff:00")).sourcePort(1).sourceVlan(1)
                .destinationSwitch(new SwitchId("ff:01")).destinationPort(2).destinationVlan(2)
                .build();

        FlowDto secondResponse = FlowDto.builder()
                .flowId(secondFlowId).bandwidth(20000).description(secondFlowId).state(FlowState.UP)
                .sourceSwitch(new SwitchId("ff:01")).sourcePort(2).sourceVlan(2)
                .destinationSwitch(new SwitchId("ff:00")).destinationPort(1).destinationVlan(1)
                .build();

        SwapFlowResponse response = new SwapFlowResponse(
                new FlowResponse(firstResponse), new FlowResponse(secondResponse));
        messageExchanger.mockResponse(correlationId, response);

        SwapFlowEndpointPayload result = flowService.swapFlowEndpoint(input).get();
        assertEquals(secondEndpoint, result.getFirstFlow().getDestination());
        assertEquals(firstEndpoint, result.getSecondFlow().getDestination());
    }

    @Test
    public void shouldValidateFlowWithTransitVlanEncapsulation() throws Exception {
        String correlationId = "should-validate-transit-vlan";
        RequestCorrelationId.create(correlationId);

        buildTransitVlanFlow();
        mockSwitchServiceWithTransitVlanRules();

        List<FlowValidationDto> result = flowService.validateFlow(TEST_FLOW_ID_A).get();

        assertEquals(4, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
        assertEquals(0, result.get(2).getDiscrepancies().size());
        assertEquals(0, result.get(3).getDiscrepancies().size());
        assertEquals(3, (int) result.get(0).getFlowRulesTotal());
        assertEquals(3, (int) result.get(1).getFlowRulesTotal());
        assertEquals(2, (int) result.get(2).getFlowRulesTotal());
        assertEquals(2, (int) result.get(3).getFlowRulesTotal());
        assertEquals(10, (int) result.get(0).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(1).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(2).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(3).getSwitchRulesTotal());
    }

    @Test
    public void shouldNotValidateFlowWithTransitVlanEncapsulation() throws Exception {
        String correlationId = "should-not-validate-transit-vlan";
        RequestCorrelationId.create(correlationId);

        buildTransitVlanFlow();
        mockSwitchServiceWithWrongTransitVlanRules();

        List<FlowValidationDto> result = flowService.validateFlow(TEST_FLOW_ID_A).get();

        assertEquals(4, result.size());
        assertEquals(3, result.get(0).getDiscrepancies().size());
        assertEquals(3, result.get(1).getDiscrepancies().size());
        assertEquals(2, result.get(2).getDiscrepancies().size());
        assertEquals(2, result.get(3).getDiscrepancies().size());

        List<String> forwardDiscrepancies = result.get(0).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(forwardDiscrepancies.contains("cookie"));
        assertTrue(forwardDiscrepancies.contains("inVlan"));
        assertTrue(forwardDiscrepancies.contains("outVlan"));

        List<String> reverseDiscrepancies = result.get(1).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(reverseDiscrepancies.contains("inPort"));
        assertTrue(reverseDiscrepancies.contains("outPort"));
        assertTrue(reverseDiscrepancies.contains("meterId"));

        List<String> protectedForwardDiscrepancies = result.get(2).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(protectedForwardDiscrepancies.contains("inVlan"));
        assertTrue(protectedForwardDiscrepancies.contains("outVlan"));

        List<String> protectedReverseDiscrepancies = result.get(3).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(protectedReverseDiscrepancies.contains("outPort"));
        assertTrue(protectedReverseDiscrepancies.contains("inPort"));
    }

    @Test
    public void shouldValidateFlowWithVxlanEncapsulation() throws Exception {
        String correlationId = "should-validate-transit-vlan";
        RequestCorrelationId.create(correlationId);

        buildVxlanFlow();
        mockSwitchServiceWithVxlanRules();

        List<FlowValidationDto> result = flowService.validateFlow(TEST_FLOW_ID_A).get();

        assertEquals(4, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
        assertEquals(0, result.get(2).getDiscrepancies().size());
        assertEquals(0, result.get(3).getDiscrepancies().size());
        assertEquals(3, (int) result.get(0).getFlowRulesTotal());
        assertEquals(3, (int) result.get(1).getFlowRulesTotal());
        assertEquals(2, (int) result.get(2).getFlowRulesTotal());
        assertEquals(2, (int) result.get(3).getFlowRulesTotal());
        assertEquals(10, (int) result.get(0).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(1).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(2).getSwitchRulesTotal());
        assertEquals(10, (int) result.get(3).getSwitchRulesTotal());
    }

    @Test
    public void shouldNotValidateFlowWithVxlanEncapsulation() throws Exception {
        String correlationId = "should-not-validate-vxlan";
        RequestCorrelationId.create(correlationId);

        buildVxlanFlow();
        mockSwitchServiceWithWrongVxlanRules();

        List<FlowValidationDto> result = flowService.validateFlow(TEST_FLOW_ID_A).get();

        assertEquals(4, result.size());
        assertEquals(3, result.get(0).getDiscrepancies().size());
        assertEquals(3, result.get(1).getDiscrepancies().size());
        assertEquals(2, result.get(2).getDiscrepancies().size());
        assertEquals(2, result.get(3).getDiscrepancies().size());

        List<String> forwardDiscrepancies = result.get(0).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(forwardDiscrepancies.contains("cookie"));
        assertTrue(forwardDiscrepancies.contains("tunnelId"));
        assertTrue(forwardDiscrepancies.contains("outVlan"));

        List<String> reverseDiscrepancies = result.get(1).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(reverseDiscrepancies.contains("inPort"));
        assertTrue(reverseDiscrepancies.contains("outPort"));
        assertTrue(reverseDiscrepancies.contains("meterId"));

        List<String> protectedForwardDiscrepancies = result.get(2).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(protectedForwardDiscrepancies.contains("tunnelId"));
        assertTrue(protectedForwardDiscrepancies.contains("outVlan"));

        List<String> protectedReverseDiscrepancies = result.get(3).getDiscrepancies().stream()
                .map(PathDiscrepancyDto::getField)
                .collect(Collectors.toList());
        assertTrue(protectedReverseDiscrepancies.contains("outPort"));
        assertTrue(protectedReverseDiscrepancies.contains("inPort"));
    }

    @Test
    public void shouldValidateOneSwitchFlow() throws Exception {
        String correlationId = "should-validate-one-switch-flow";
        RequestCorrelationId.create(correlationId);

        buildOneSwitchFlow();
        mockSwitchServiceOneSwitchFlow();

        List<FlowValidationDto> result = flowService.validateFlow(TEST_FLOW_ID_B).get();

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).getDiscrepancies().size());
        assertEquals(0, result.get(1).getDiscrepancies().size());
    }

    private void buildTransitVlanFlow() {
        buildFlow(FlowEncapsulationType.TRANSIT_VLAN);

        TransitVlan transitVlan = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .vlan(FLOW_A_ENCAP_ID)
                .build();
        when(transitVlanRepository.findByPathId(FLOW_A_FORWARD_PATH_ID, FLOW_A_REVERSE_PATH_ID))
                .thenReturn(Collections.singletonList(transitVlan));
        when(transitVlanRepository.findByPathId(FLOW_A_REVERSE_PATH_ID, FLOW_A_FORWARD_PATH_ID))
                .thenReturn(Collections.singletonList(transitVlan));

        TransitVlan transitVlanProtected = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .vlan(FLOW_A_ENCAP_ID_PROTECTED)
                .build();
        when(transitVlanRepository.findByPathId(FLOW_A_FORWARD_PATH_ID_PROTECTED,
                FLOW_A_REVERSE_PATH_ID_PROTECTED)).thenReturn(Collections.singletonList(transitVlanProtected));
        when(transitVlanRepository.findByPathId(FLOW_A_REVERSE_PATH_ID_PROTECTED,
                FLOW_A_FORWARD_PATH_ID_PROTECTED)).thenReturn(Collections.singletonList(transitVlanProtected));
    }

    private void buildVxlanFlow() {
        buildFlow(FlowEncapsulationType.VXLAN);

        Vxlan vxlan = Vxlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .vni(FLOW_A_ENCAP_ID)
                .build();
        when(vxlanRepository.findByPathId(FLOW_A_FORWARD_PATH_ID, FLOW_A_REVERSE_PATH_ID))
                .thenReturn(Collections.singletonList(vxlan));
        when(vxlanRepository.findByPathId(FLOW_A_REVERSE_PATH_ID, FLOW_A_FORWARD_PATH_ID))
                .thenReturn(Collections.singletonList(vxlan));

        Vxlan vxlanProtected = Vxlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .vni(FLOW_A_ENCAP_ID_PROTECTED)
                .build();
        when(vxlanRepository.findByPathId(FLOW_A_FORWARD_PATH_ID_PROTECTED,
                FLOW_A_REVERSE_PATH_ID_PROTECTED)).thenReturn(Collections.singletonList(vxlanProtected));
        when(vxlanRepository.findByPathId(FLOW_A_REVERSE_PATH_ID_PROTECTED,
                FLOW_A_FORWARD_PATH_ID_PROTECTED)).thenReturn(Collections.singletonList(vxlanProtected));
    }

    private void buildFlow(FlowEncapsulationType flowEncapsulationType) {
        Switch switchA = Switch.builder().switchId(TEST_SWITCH_ID_A).build();
        Switch switchB = Switch.builder().switchId(TEST_SWITCH_ID_B).build();
        Switch switchC = Switch.builder().switchId(TEST_SWITCH_ID_C).build();

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_A)
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SRC_PORT)
                .srcVlan(FLOW_A_SRC_VLAN)
                .destSwitch(switchC)
                .destPort(FLOW_A_DST_PORT)
                .destVlan(FLOW_A_DST_VLAN)
                .allocateProtectedPath(true)
                .encapsulationType(flowEncapsulationType)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .flow(flow)
                .cookie(new Cookie(FLOW_A_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardFlowPath);

        PathSegment forwardSegmentA = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT)
                .path(forwardFlowPath)
                .build();

        PathSegment forwardSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT)
                .path(forwardFlowPath)
                .build();
        forwardFlowPath.setSegments(Lists.newArrayList(forwardSegmentA, forwardSegmentB));

        FlowPath forwardProtectedFlowPath = FlowPath.builder()
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .flow(flow)
                .cookie(new Cookie(FLOW_A_FORWARD_COOKIE_PROTECTED))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID_PROTECTED))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setProtectedForwardPath(forwardProtectedFlowPath);

        Switch switchE = Switch.builder().switchId(TEST_SWITCH_ID_E).build();
        PathSegment forwardProtectedSegmentA = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED)
                .destSwitch(switchE)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED)
                .path(forwardProtectedFlowPath)
                .build();

        PathSegment forwardProtectedSegmentB = PathSegment.builder()
                .srcSwitch(switchE)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED)
                .path(forwardProtectedFlowPath)
                .build();
        forwardProtectedFlowPath.setSegments(Lists.newArrayList(forwardProtectedSegmentA, forwardProtectedSegmentB));

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(FLOW_A_REVERSE_PATH_ID)
                .flow(flow)
                .cookie(new Cookie(FLOW_A_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_A_REVERSE_METER_ID))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reverseFlowPath);

        PathSegment reverseSegmentA = PathSegment.builder()
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .path(reverseFlowPath)
                .build();

        PathSegment reverseSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .path(reverseFlowPath)
                .build();
        reverseFlowPath.setSegments(Lists.newArrayList(reverseSegmentA, reverseSegmentB));

        FlowPath reverseProtectedFlowPath = FlowPath.builder()
                .pathId(FLOW_A_REVERSE_PATH_ID_PROTECTED)
                .flow(flow)
                .cookie(new Cookie(FLOW_A_REVERSE_COOKIE_PROTECTED))
                .meterId(new MeterId(FLOW_A_REVERSE_METER_ID_PROTECTED))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setProtectedReversePath(reverseProtectedFlowPath);

        PathSegment reverseProtectedSegmentA = PathSegment.builder()
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED)
                .destSwitch(switchE)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED)
                .path(reverseProtectedFlowPath)
                .build();

        PathSegment reverseProtectedSegmentB = PathSegment.builder()
                .srcSwitch(switchE)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED)
                .path(reverseProtectedFlowPath)
                .build();
        reverseProtectedFlowPath.setSegments(Lists.newArrayList(reverseProtectedSegmentA, reverseProtectedSegmentB));

        when(flowRepository.findById(TEST_FLOW_ID_A)).thenReturn(Optional.of(flow));
    }

    private void buildOneSwitchFlow() {
        Switch switchD = Switch.builder().switchId(TEST_SWITCH_ID_D).build();

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_B)
                .srcSwitch(switchD)
                .srcPort(FLOW_B_SRC_PORT)
                .srcVlan(FLOW_B_SRC_VLAN)
                .destSwitch(switchD)
                .destPort(FLOW_B_SRC_PORT)
                .destVlan(FLOW_B_DST_VLAN)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(FLOW_B_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_B_FORWARD_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardFlowPath);

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_reverse_path"))
                .flow(flow)
                .cookie(new Cookie(FLOW_B_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_B_REVERSE_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reverseFlowPath);

        when(flowRepository.findById(TEST_FLOW_ID_B)).thenReturn(Optional.of(flow));
    }

    private void mockSwitchServiceWithTransitVlanRules() {
        SwitchFlowEntries switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                        (long) FLOW_A_FORWARD_METER_ID, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SRC_PORT), 0,
                        getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_A), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), 0, null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_B), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                        (long) FLOW_A_REVERSE_METER_ID, false),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_DST_PORT), 0,
                        getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_C), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 0,
                        null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED), 0,
                        null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_E), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));
    }

    private void mockSwitchServiceWithWrongTransitVlanRules() {
        SwitchFlowEntries switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(123, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0,
                        getFlowSetFieldAction(FLOW_A_ENCAP_ID), (long) FLOW_A_FORWARD_METER_ID, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, 123, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, 123, FLOW_A_ENCAP_ID_PROTECTED,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_A), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 123,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(123), 0, null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_B), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getFlowSetFieldAction(123), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                        123L, false),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_DST_PORT), 0,
                        getFlowSetFieldAction(123), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_C), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED, 123,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 0, null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(123), 0, null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_E), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));
    }

    private void mockSwitchServiceWithVxlanRules() {
        SwitchFlowEntries switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), FLOW_A_ENCAP_ID, null,
                        (long) FLOW_A_FORWARD_METER_ID, true),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, 0, String.valueOf(FLOW_A_SRC_PORT),
                        FLOW_A_ENCAP_ID, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_SRC_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_A), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), FLOW_A_ENCAP_ID, null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), FLOW_A_ENCAP_ID, null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_B), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, 0, String.valueOf(FLOW_A_DST_PORT),
                        FLOW_A_ENCAP_ID, getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), FLOW_A_ENCAP_ID, null,
                        (long) FLOW_A_REVERSE_METER_ID, true),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_DST_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_C), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), FLOW_A_ENCAP_ID_PROTECTED,
                        null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED), FLOW_A_ENCAP_ID_PROTECTED,
                        null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_E), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));
    }

    private void mockSwitchServiceWithWrongVxlanRules() {
        SwitchFlowEntries switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(123, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT),
                        FLOW_A_ENCAP_ID, null, (long) FLOW_A_FORWARD_METER_ID, true),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, 123, 0, String.valueOf(FLOW_A_SRC_PORT), FLOW_A_ENCAP_ID,
                        getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, 123, 0, String.valueOf(FLOW_A_SRC_PORT),
                        FLOW_A_ENCAP_ID_PROTECTED, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_A), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 123, null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, 0,
                        String.valueOf(123), FLOW_A_ENCAP_ID, null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_B), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, 0,
                        String.valueOf(FLOW_A_DST_PORT), FLOW_A_ENCAP_ID, getFlowSetFieldAction(123), null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), FLOW_A_ENCAP_ID, null,
                        123L, true),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_DST_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getFlowSetFieldAction(123), null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_C), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));

        switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 123, null, null, false),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        0, String.valueOf(123), FLOW_A_ENCAP_ID_PROTECTED, null, null, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_E), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));
    }

    private void mockSwitchServiceOneSwitchFlow() {

        SwitchFlowEntries switchFlowEntries = getSwitchFlowEntries(TEST_SWITCH_ID_D,
                getFlowEntry(FLOW_B_FORWARD_COOKIE, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, "in_port", 0,
                        getFlowSetFieldAction(FLOW_B_DST_VLAN), (long) FLOW_B_FORWARD_METER_ID, false),
                getFlowEntry(FLOW_B_REVERSE_COOKIE, FLOW_B_SRC_PORT, FLOW_B_DST_VLAN, "in_port", 0,
                        getFlowSetFieldAction(FLOW_B_SRC_VLAN), (long) FLOW_B_REVERSE_METER_ID, false));

        when(switchService.getRules(eq(TEST_SWITCH_ID_D), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(switchFlowEntries));
    }

    private SwitchFlowEntries getSwitchFlowEntries(SwitchId switchId, FlowEntry... flowEntries) {
        return SwitchFlowEntries.builder()
                .switchId(switchId)
                .flowEntries(Lists.newArrayList(flowEntries))
                .build();
    }

    private FlowEntry getFlowEntry(long cookie, int srcPort, int srcVlan, String dstPort, int tunnelId,
                                   FlowSetFieldAction flowSetFieldAction, Long meterId, boolean tunnelIdIngressRule) {
        return FlowEntry.builder()
                .cookie(cookie)
                .packetCount(7)
                .byteCount(480)
                .version("OF_13")
                .match(FlowMatchField.builder()
                        .inPort(String.valueOf(srcPort))
                        .vlanVid(String.valueOf(srcVlan))
                        .tunnelId(!tunnelIdIngressRule ? String.valueOf(tunnelId) : null)
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .flowOutput(dstPort)
                                .fieldAction(flowSetFieldAction)
                                .pushVxlan(tunnelIdIngressRule ? String.valueOf(tunnelId) : null)
                                .build())
                        .goToMeter(meterId)
                        .build())
                .build();
    }

    private FlowSetFieldAction getFlowSetFieldAction(int dstVlan) {
        return FlowSetFieldAction.builder()
                .fieldName("vlan_vid")
                .fieldValue(String.valueOf(dstVlan))
                .build();
    }

    @TestConfiguration
    @Import(KafkaConfig.class)
    @ComponentScan({
            "org.openkilda.northbound.converter",
            "org.openkilda.northbound.utils"})
    @PropertySource({"classpath:northbound.properties"})
    static class Config {
        @Bean
        public CorrelationIdFactory idFactory() {
            return new TestCorrelationIdFactory();
        }

        @Bean
        public MessagingChannel messagingChannel() {
            return new MessageExchanger();
        }

        @Bean
        public RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        public FlowServiceImpl flowService() {
            return new FlowServiceImpl();
        }
    }
}
