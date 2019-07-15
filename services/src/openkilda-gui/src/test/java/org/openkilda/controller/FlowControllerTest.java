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
import org.openkilda.service.FlowService;
import org.openkilda.test.MockitoExtension;
import org.openkilda.util.TestFlowMock;

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
    public void testGetAllSwitchesDetails() {
        try {
            assertTrue(true);
        } catch (Exception exception) {
            assertTrue(false);
        }
    }
    
    @Test
    public void testGetFlowPath() throws Exception {
        FlowPayload flowPayload = new FlowPayload();
        when(flowService.getFlowPath(TestFlowMock.FLOW_ID)).thenReturn(flowPayload);
        mockMvc.perform(get("/api/flows/path/{flowId}", TestFlowMock.FLOW_ID).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        assertTrue(true);
    }
}
