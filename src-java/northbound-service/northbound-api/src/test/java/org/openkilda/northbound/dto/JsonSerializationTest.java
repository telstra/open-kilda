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

package org.openkilda.northbound.dto;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PathDiscrepancyDto;
import org.openkilda.northbound.dto.v1.flows.PingInput;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v1.flows.UniFlowPingOutput;
import org.openkilda.northbound.dto.v1.links.LinkDto;
import org.openkilda.northbound.dto.v1.links.LinkPropsDto;
import org.openkilda.northbound.dto.v1.links.LinkStatus;
import org.openkilda.northbound.dto.v1.links.PathDto;
import org.openkilda.northbound.dto.v1.switches.DeleteMeterResult;
import org.openkilda.northbound.dto.v1.switches.GroupsSyncDto;
import org.openkilda.northbound.dto.v1.switches.GroupsValidationDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortsSyncDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortsValidationDto;
import org.openkilda.northbound.dto.v1.switches.MetersSyncDto;
import org.openkilda.northbound.dto.v1.switches.MetersValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchLocationDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v1.switches.UnderMaintenanceDto;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

public class JsonSerializationTest {

    private static final String SWITCH_ID = "de:ad:be:ef:de:ad:be:ef";
    private static final String FLOW_ID = "flow-test";

    private final ObjectMapper mapper = new ObjectMapper();

    private <T> T pass(T entity, Class<T> clazz) throws IOException {
        return mapper.readValue(mapper.writeValueAsString(entity), clazz);
    }

    @Test
    public void pathDiscrepancyDtoTest() throws IOException {
        PathDiscrepancyDto dto = new PathDiscrepancyDto("rule", "field", "expected", "actual");
        assertEquals(dto, pass(dto, PathDiscrepancyDto.class));
    }

    @Test
    public void flowValidationDtoTest() throws IOException {
        PathDiscrepancyDto discrepancyDto = new PathDiscrepancyDto("rule", "field", "expected", "actual");
        FlowValidationDto dto = new FlowValidationDto(
                FLOW_ID, true, singletonList(0L), singletonList(1L), singletonList(discrepancyDto), 10, 11, 2, 4,
                true, true);
        assertEquals(dto, pass(dto, FlowValidationDto.class));
    }

    @Test
    public void uniFlowPingOutputTest() throws IOException {
        UniFlowPingOutput dto = new UniFlowPingOutput(true, "err-test", 10);
        assertEquals(dto, pass(dto, UniFlowPingOutput.class));
    }

    @Test
    public void verificationInputTest() throws IOException {
        PingInput dto = new PingInput(10);
        assertEquals(dto, pass(dto, PingInput.class));
    }

    @Test
    public void verificationOutputTest() throws IOException {
        UniFlowPingOutput verification = new UniFlowPingOutput(true, "err-test", 10);
        PingOutput dto = new PingOutput(FLOW_ID, verification, verification, "error");
        assertEquals(dto, pass(dto, PingOutput.class));
    }


    @Test
    public void linksDtoTest() throws IOException {
        LinkDto dto = new LinkDto(-1, 1, 0, 0, 0, LinkStatus.DISCOVERED,
                LinkStatus.DISCOVERED, LinkStatus.FAILED, 0, false, false,
                "bfd-session-status", singletonList(new PathDto(SWITCH_ID, 1, 0, 10L)), null);
        assertEquals(dto, pass(dto, LinkDto.class));
    }

    @Test
    public void pathDtoTest() throws IOException {
        PathDto dto = new PathDto(SWITCH_ID, 1, 0, 10L);
        assertEquals(dto, pass(dto, PathDto.class));
    }

    @Test
    public void linksPropsDtoTest() throws IOException {
        LinkPropsDto dto = new LinkPropsDto(SWITCH_ID, 0, SWITCH_ID, 1, Collections.singletonMap("key", "val"));
        assertEquals(dto, pass(dto, LinkPropsDto.class));
    }


    @Test
    public void deleteMeterResultTest() throws IOException {
        DeleteMeterResult dto = new DeleteMeterResult(true);
        assertEquals(dto, pass(dto, DeleteMeterResult.class));
    }

    @Test
    public void rulesValidationResultTest() throws IOException {
        RulesValidationResult dto = new RulesValidationResult(
                singletonList(0L), singletonList(1L), singletonList(2L));
        assertEquals(dto, pass(dto, RulesValidationResult.class));
    }

    @Test
    public void rulesSyncResultTest() throws IOException {
        RulesSyncResult dto = new RulesSyncResult(
                singletonList(0L), singletonList(1L), singletonList(2L), singletonList(3L));
        assertEquals(dto, pass(dto, RulesSyncResult.class));
    }

    @Test
    public void switchSyncResultTest() throws IOException {
        RulesSyncDto rules = new RulesSyncDto(singletonList(0L), singletonList(1L), singletonList(2L),
                singletonList(3L), singletonList(4L), singletonList(5L));
        MetersSyncDto meters = new MetersSyncDto(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList());
        GroupsSyncDto groups = new GroupsSyncDto(emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList());
        LogicalPortsSyncDto logicalPorts = new LogicalPortsSyncDto(emptyList(), emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), "");
        SwitchSyncResult dto = new SwitchSyncResult(rules, meters, groups, logicalPorts);
        assertEquals(dto, pass(dto, SwitchSyncResult.class));
    }

    @Test
    public void switchValidateResultTest() throws IOException {
        RulesValidationDto rules = new RulesValidationDto(singletonList(0L), singletonList(1L),
                singletonList(2L), singletonList(3L));
        MetersValidationDto meters = MetersValidationDto.empty();
        GroupsValidationDto groups = GroupsValidationDto.empty();
        LogicalPortsValidationDto logicalPorts = new LogicalPortsValidationDto(
                emptyList(), emptyList(), emptyList(), emptyList(), "");
        SwitchValidationResult dto = SwitchValidationResult.builder()
                .rules(rules)
                .meters(meters)
                .groups(groups)
                .logicalPorts(logicalPorts)
                .build();
        assertEquals(dto, pass(dto, SwitchValidationResult.class));
    }

    @Test
    public void switchDtoTest() throws IOException {
        SwitchDto dto = new SwitchDto(new SwitchId(SWITCH_ID), "address-test", 37040, "host", "desc",
                SwitchChangeType.ACTIVATED, false, "of_version",
                "manufacturer", "hardware", "software", "serial_number", "pop",
                new SwitchLocationDto(48.860611, 2.337633, "street", "city", "country"));
        assertEquals(dto, pass(dto, SwitchDto.class));
    }

    @Test
    public void switchUnderMaintenanceDtoTest() throws IOException {
        UnderMaintenanceDto dto = new UnderMaintenanceDto(false, false);
        assertEquals(dto, pass(dto, UnderMaintenanceDto.class));
    }

    @Test
    public void batchResultsTest() throws IOException {
        BatchResults dto = new BatchResults(1, 0, singletonList("qwerty"));
        assertEquals(dto, pass(dto, BatchResults.class));
    }

    @Test
    public void yFlowCreatePayloadTest() throws IOException {
        YFlowCreatePayload origin = YFlowCreatePayload.builder()
                .yFlowId("dummy-flowId")
                .sharedEndpoint(YFlowSharedEndpoint.builder()
                        .switchId(new SwitchId(1)).portNumber(2)
                        .build())
                .maximumBandwidth(3000)
                .pathComputationStrategy(PathComputationStrategy.COST.name())
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN.name())
                .maxLatency(1000L)
                .maxLatencyTier2(2000L)
                .ignoreBandwidth(false).periodicPings(false).pinned(false)
                .priority(5).strictBandwidth(true).description("y-flow dummy description")
                .allocateProtectedPath(false).diverseFlowId("diverse-with")
                .subFlow(SubFlowUpdatePayload.builder()
                        .flowId("sub-flow-alpha")
                        .endpoint(FlowEndpointV2.builder()
                                .switchId(new SwitchId(2))
                                .portNumber(5)
                                .vlanId(20).innerVlanId(30)
                                .build())
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder()
                                .vlanId(100).innerVlanId(200)
                                .build())
                        .description("sub-alpha-description")
                        .build())
                .subFlow(SubFlowUpdatePayload.builder()
                        .flowId("sub-flow-beta")
                        .endpoint(FlowEndpointV2.builder()
                                .switchId(new SwitchId(3))
                                .portNumber(6)
                                .vlanId(21).innerVlanId(31)
                                .build())
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder()
                                .vlanId(101).innerVlanId(201)
                                .build())
                        .description("sub-beta-description")
                        .build())
                .build();
        assertEquals(origin, pass(origin, YFlowCreatePayload.class));
    }

    @Test
    public void yFlowUpdatePayloadTest() throws IOException {
        YFlowUpdatePayload origin = YFlowUpdatePayload.builder()
                .sharedEndpoint(YFlowSharedEndpoint.builder()
                        .switchId(new SwitchId(1)).portNumber(2)
                        .build())
                .maximumBandwidth(3000)
                .pathComputationStrategy(PathComputationStrategy.COST.name())
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN.name())
                .maxLatency(1000L)
                .maxLatencyTier2(2000L)
                .ignoreBandwidth(false).periodicPings(false).pinned(false)
                .priority(5).strictBandwidth(true).description("y-flow dummy description")
                .allocateProtectedPath(false).diverseFlowId("diverse-with")
                .subFlow(SubFlowUpdatePayload.builder()
                        .flowId("sub-flow-alpha")
                        .endpoint(FlowEndpointV2.builder()
                                .switchId(new SwitchId(2))
                                .portNumber(5)
                                .vlanId(20).innerVlanId(30)
                                .build())
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder()
                                .vlanId(100).innerVlanId(200)
                                .build())
                        .description("sub-alpha-description")
                        .build())
                .subFlow(SubFlowUpdatePayload.builder()
                        .flowId("sub-flow-beta")
                        .endpoint(FlowEndpointV2.builder()
                                .switchId(new SwitchId(3))
                                .portNumber(6)
                                .vlanId(21).innerVlanId(31)
                                .build())
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder()
                                .vlanId(101).innerVlanId(201)
                                .build())
                        .description("sub-beta-description")
                        .build())
                .build();
        assertEquals(origin, pass(origin, YFlowUpdatePayload.class));
    }
}
