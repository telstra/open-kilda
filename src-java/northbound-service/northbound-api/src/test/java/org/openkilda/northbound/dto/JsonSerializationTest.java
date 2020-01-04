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

package org.openkilda.northbound.dto;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

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
import org.openkilda.northbound.dto.v1.switches.MetersSyncDto;
import org.openkilda.northbound.dto.v1.switches.MetersValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncDto;
import org.openkilda.northbound.dto.v1.switches.RulesSyncResult;
import org.openkilda.northbound.dto.v1.switches.RulesValidationDto;
import org.openkilda.northbound.dto.v1.switches.RulesValidationResult;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchSyncResult;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v1.switches.UnderMaintenanceDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

public class JsonSerializationTest {

    private static final String SWITCH_ID = "switch-test";
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
                FLOW_ID, true, singletonList(0L), singletonList(1L), singletonList(discrepancyDto), 10, 11, 2, 4);
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
        LinkDto dto = new LinkDto(-1, 1, 0, 0, 0, LinkStatus.DISCOVERED, LinkStatus.DISCOVERED, 0, false, false,
                "bfd-session-status", singletonList(new PathDto(SWITCH_ID, 1, 0, 10L)));
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
        SwitchSyncResult dto = new SwitchSyncResult(rules, meters);
        assertEquals(dto, pass(dto, SwitchSyncResult.class));
    }

    @Test
    public void switchValidateResultTest() throws IOException {
        RulesValidationDto rules = new RulesValidationDto(singletonList(0L), singletonList(1L),
                singletonList(2L), singletonList(3L));
        MetersValidationDto meters = new MetersValidationDto(emptyList(), emptyList(), emptyList(), emptyList());
        SwitchValidationResult dto = new SwitchValidationResult(rules, meters);
        assertEquals(dto, pass(dto, SwitchValidationResult.class));
    }

    @Test
    public void switchDtoTest() throws IOException {
        SwitchDto dto = new SwitchDto(SWITCH_ID, "address-test", 37040, "host", "desc", "state", false, "of_version",
                "manufacturer", "hardware", "software", "serial_number");
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
}
