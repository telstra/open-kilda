/* Copyright 2018 Telstra Open Source
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

package org.openkilda.controller;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.model.VictoriaStatsReq;
import org.openkilda.service.StatsService;
import org.openkilda.test.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;


@ExtendWith(MockitoExtension.class)
public class StatsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StatsService statsService;

    @InjectMocks
    private StatsController statsController;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);

        mockMvc = MockMvcBuilders.standaloneSetup(statsController).build();
    }

    @Test
    public void getFlowVictoriaStats() {
        try {
            Mockito.when(statsService.getTransformedFlowVictoriaStats(Mockito.anyString(), Mockito.anyString(),
                            Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                            Mockito.anyList(), Mockito.isNull()))
                    .thenReturn(Collections.emptyList());
            mockMvc.perform(
                    post(
                            "/api/stats/flowgraph/{statsType}",
                            "flow")
                            .param("flowId", "flow1")
                            .param("startDate", "123")
                            .param("endDate", "321")
                            .param("step", "30s")
                            .param("metric", "raw_packets")
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            fail();
        }
    }

    @Test
    public void commonVictoriaStats() {
        try {
            VictoriaStatsReq req = new VictoriaStatsReq();
            String reqString = new ObjectMapper().writeValueAsString(req);

            Mockito.when(statsService.getVictoriaStats(Mockito.any()))
                    .thenReturn(Collections.emptyList());
            mockMvc.perform(
                    post("/api/stats/common")
                            .content(reqString)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            fail();
        }
    }

    @Test
    public void switchPortsStats() {
        try {
            VictoriaStatsReq req = new VictoriaStatsReq();
            String reqString = new ObjectMapper().writeValueAsString(req);

            Mockito.when(statsService.getSwitchPortsStats(Mockito.any()))
                    .thenReturn(Collections.emptyList());
            mockMvc.perform(
                    post("/api/stats/switchports")
                            .content(reqString)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            fail();
        }
    }
}
