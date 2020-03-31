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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.model.FlowHistory;
import org.openkilda.model.FlowInfo;
import org.openkilda.service.FlowService;
import org.openkilda.test.MockitoExtension;
import org.openkilda.util.TestFlowMock;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
public class FlowControllerTest {
    
    @SuppressWarnings("unused")
    private MockMvc mockMvc;

    @InjectMocks
    private FlowController flowController;
    
    @Mock
    private FlowService flowService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(flowController).build();
    }
        
    @Test
        public void testGetFlowList() {
        try {
            mockMvc.perform(get("/api/flows/list").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception exception) {
            assertTrue(false);
        }
    } 
        
    @Test
        public void testGetFlowListWithRequestParams() {
        List<String> statuses = new ArrayList<>();
        List<FlowInfo> flowInfos = new ArrayList<>();
        try {
            Mockito.when(flowService.getAllFlows(Mockito.anyList(), Mockito.anyBoolean())).thenReturn(flowInfos);
            mockMvc.perform(get("/api/flows/list", statuses, TestFlowMock.CONTROLLER_FLAG)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception exception) {
            assertTrue(false);
        }
    }
             
    @Test
        public void testGetFlowHistory() throws Exception {
        try {
            mockMvc.perform(get("/api/flows/all/history/{flowId}", TestFlowMock.FLOW_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception exception) {
            assertTrue(false);
        }
    }
        
    @Test
        public void testGetFlowHistoryIfRequestParamsPassed() throws Exception {
        List<FlowHistory> flowHistories = new ArrayList<FlowHistory>();
        try {
            when(flowController.getFlowHistory(TestFlowMock.FLOW_ID, TestFlowMock.TIME_FROM, TestFlowMock.TIME_TO))
                .thenReturn(flowHistories);
            mockMvc.perform(get("/api/flows/all/history/{flowId}", TestFlowMock.FLOW_ID)
            .param("timeFrom", TestFlowMock.TIME_TO)
                                .param("timeTo", TestFlowMock.TIME_TO)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk());
                                
            assertTrue(true);
        } catch (Exception exception) {
            assertTrue(false);
        }
    }
        
    @Test
        public void testGetFlowsHistoryIfFlowIdNotPassed() throws Exception {
        List<FlowHistory> flowHistories = new ArrayList<FlowHistory>();
        try {
            when(flowController.getFlowHistory(TestFlowMock.FLOW_ID_NULL, TestFlowMock.TIME_FROM, TestFlowMock.TIME_TO))
                .thenReturn(flowHistories);
            mockMvc.perform(get("/api/flows/all/history/{flowId}", TestFlowMock.FLOW_ID_NULL)
                    .param("timeFrom", TestFlowMock.TIME_FROM)
                    .param("timeTo", TestFlowMock.TIME_TO)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            assertTrue(true);
        } catch (Exception exception) {
            assertTrue(false);
        }
    }
        
    @Test
        public void testGetFlowById() throws Exception {
        FlowInfo flowInfo = new FlowInfo();
        try {
            when(flowService.getFlowById(TestFlowMock.FLOW_ID, TestFlowMock.CONTROLLER_FLAG)).thenReturn(flowInfo);
            mockMvc.perform(get("/api/flows/{flowId}", TestFlowMock.FLOW_ID).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }
    }
        
    @Test
        public void testGetFlowByIdWhenFlowIdNotPassed() throws Exception {
        FlowInfo flowInfo = new FlowInfo();
        try {
            when(flowService.getFlowById(TestFlowMock.FLOW_ID_NULL, TestFlowMock.CONTROLLER_FLAG)).thenReturn(flowInfo);
            mockMvc.perform(get("/api/flows/{flowId}", TestFlowMock.FLOW_ID_NULL, true)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed());
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }
    }
        
    @Test
        public void testGetFlowPath() throws Exception {
        FlowPayload flowPayload = new FlowPayload();
        try {
            when(flowService.getFlowPath(Mockito.anyString())).thenReturn(flowPayload);
            mockMvc.perform(get("/api/flows/path/{flowId}", TestFlowMock.FLOW_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }
    }
        
    @Test
        public void testGetFlowPathWhenFlowIdNotPassed() throws Exception {
        FlowPayload flowPayload = new FlowPayload();
        try {
            when(flowService.getFlowPath(Mockito.anyString())).thenReturn(flowPayload);
            mockMvc.perform(get("/api/flows/get/path/{flowId}", TestFlowMock.FLOW_ID_NULL)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }
    }
        
}
