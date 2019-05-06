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

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.controller.mockdata.TestMockStats;
import org.openkilda.service.StatsService;
import org.openkilda.test.MockitoExtension;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
public class StatsControllerTest {
    
    private MockMvc mockMvc;

    @Mock
    private StatsService statsService;
    
    @InjectMocks
    private StatsController statsController;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(statsController).build();
    }

    @Test
    public void testGetMetersStatsForwardBits() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_BITS, TestMockStats.DIRECTION_FORWARD)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetMetersStatsReverseBits() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_BITS, TestMockStats.DIRECTION_REVERSE)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetMetersStatsForwardReverseBits() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_BITS, TestMockStats.DIRECTION_BOTH).contentType(
                            MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetMetersStatsForwardBytes() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_BYTES, TestMockStats.DIRECTION_FORWARD)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetMetersStatsReverseBytes() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_BYTES, TestMockStats.DIRECTION_REVERSE)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetMetersStatsForwardReverseBytes() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_BYTES, TestMockStats.DIRECTION_BOTH).contentType(
                            MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }


    @Test
    public void testGetMetersStatsForwardPackets() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_PACKETS, TestMockStats.DIRECTION_FORWARD)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetMetersStatsReversePackets() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_PACKETS, TestMockStats.DIRECTION_REVERSE)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetMetersStatsForwardReversePackets() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_PACKETS, TestMockStats.DIRECTION_BOTH)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }


    @Test
    public void testMeterStatsApiIfMetricNotPassed() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE, null,
                            TestMockStats.DIRECTION_BOTH).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testMeterStatsApiIfFlowIdNotPassed() throws Exception {
        try {
            mockMvc.perform(
                    get(
                            "/api/stats/meter/{flowId}/{startDate}/{endDate}/{downsample}/{metric}/{direction}",
                            "", TestMockStats.START_DATE, TestMockStats.END_DATE,
                            TestMockStats.DOWNSAMPLE, null, TestMockStats.DIRECTION_BOTH)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(
                    status().isNotFound());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("exception: " + ex.getMessage());
            assertTrue(false);
        }
    }

    @Test
    public void testGetFlowStats() throws Exception {
        try {
            mockMvc.perform(
                    get("/api/stats/flowid/{flowId}/{startDate}/{endDate}/{downsample}/{metric}",
                            TestMockStats.FLOW_ID, TestMockStats.START_DATE,
                            TestMockStats.END_DATE, TestMockStats.DOWNSAMPLE,
                            TestMockStats.METRIC_BITS, TestMockStats.DIRECTION_FORWARD)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

            assertTrue(true);
        } catch (Exception ex) {
            assertTrue(false);
        }


    }



}
