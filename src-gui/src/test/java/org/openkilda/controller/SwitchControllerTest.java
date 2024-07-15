/* Copyright 2024 Telstra Open Source
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.auth.context.ServerContext;
import org.openkilda.auth.model.RequestContext;
import org.openkilda.log.ActivityLogger;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkParametersDto;
import org.openkilda.model.LinkUnderMaintenanceDto;
import org.openkilda.model.SwitchDetail;
import org.openkilda.model.SwitchInfo;
import org.openkilda.model.SwitchProperty;
import org.openkilda.service.SwitchService;
import org.openkilda.test.CustomMockitoExtension;
import org.openkilda.util.TestFlowMock;
import org.openkilda.util.TestIslMock;
import org.openkilda.util.TestSwitchMock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(CustomMockitoExtension.class)
public class SwitchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SwitchService serviceSwitch;

    @Mock
    private ServerContext serverContext;

    @Mock
    ActivityLogger activityLogger;

    @InjectMocks
    private SwitchController switchController;

    @SuppressWarnings("unused")
    private static final String switchUuid = "00:00:00:00:00:00:00:01";

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(switchController).build();
        RequestContext requestContext = new RequestContext();
        requestContext.setUserId(TestIslMock.USER_ID);
        when(serverContext.getRequestContext()).thenReturn(requestContext);
    }

    @Test
    public void testGetAllSwitchesDetails() {
        List<SwitchInfo> switchesInfo = new ArrayList<>();
        try {
            when(serviceSwitch.getSwitchInfos(false, TestFlowMock.CONTROLLER_FLAG))
                    .thenReturn(switchesInfo);
            mockMvc.perform(get("/api/switch/list").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception exception) {
            fail();
        }
    }

    @Test
    public void testGetAllSwitchFlows() {
        ResponseEntity<List<?>> responseList = new ResponseEntity<List<?>>(HttpStatus.OK);
        try {
            when(serviceSwitch.getPortFlows(TestSwitchMock.SWITCH_ID, TestSwitchMock.PORT,
                    TestFlowMock.CONTROLLER_FLAG)).thenReturn(responseList);
            mockMvc.perform(get("/api/switch/{switchId}/flows", TestSwitchMock.SWITCH_ID)
                    .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void getSwitchDetails() throws Exception {
        List<SwitchDetail> switchDetails = new ArrayList<>();
        switchDetails.add(SwitchDetail.builder().build());
        when(serviceSwitch.getSwitchDetails(TestSwitchMock.SWITCH_ID, TestFlowMock.CONTROLLER_FLAG))
                .thenReturn(switchDetails);
        mockMvc.perform(get("/api/switch/details")
                        .param("switchId", TestSwitchMock.SWITCH_ID)
                        .param("controller", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("[{\"under_maintenance\":false}]"));


        when(serviceSwitch.getSwitchDetails(null, TestFlowMock.CONTROLLER_FLAG))
                .thenReturn(switchDetails);
        mockMvc.perform(get("/api/switch/details")
                        .param("controller", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("[{\"under_maintenance\":false}]"));

        assertTrue(true);
    }

    @Test
    public void testGetSwichLinkDetails() {
        List<SwitchInfo> switchesInfo = new ArrayList<>();
        try {
            when(serviceSwitch.getSwitchInfos(false, TestFlowMock.CONTROLLER_FLAG))
                    .thenReturn(switchesInfo);
            mockMvc.perform(get("/api/switch/links").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testDeleteSwitch() {
        SwitchInfo switcheInfo = new SwitchInfo();
        try {
            when(serviceSwitch.deleteSwitch(TestSwitchMock.SWITCH_ID, false)).thenReturn(switcheInfo);
            mockMvc.perform(delete("/api/switch/{switchId}", TestSwitchMock.SWITCH_ID, true)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception e) {
            System.out.println("exception: " + e.getMessage());
            fail();
        }
    }

    @Test
    public void testDeleteSwitchIfSwitchIdNotPassed() {
        try {
            mockMvc.perform(delete("/api/switch/{switchId}", TestSwitchMock.SWITCH_ID_NULL, true)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            assertTrue(true);
        } catch (Exception e) {
            System.out.println("exception: " + e.getMessage());
            fail();
        }
    }

    @Test
    public void testSwitchMaintenance() throws Exception {
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.setSwitchId(TestSwitchMock.SWITCH_ID);
        switchInfo.setUnderMaintenance(TestSwitchMock.MAINTENANCE_STATUS);
        switchInfo.setEvacuate(TestSwitchMock.EVACUATE_STATUS);
        String inputJson = mapToJson(switchInfo);
        mockMvc.perform(
                post("/api/switch/under-maintenance/{switchId}", TestSwitchMock.SWITCH_ID)
                        .content(inputJson).contentType(MediaType.APPLICATION_JSON)).andExpect(
                status().isOk());
    }


    @Test
    public void testIslMaintenance() throws Exception {
        LinkUnderMaintenanceDto linkUnderMaintenanceDto = new LinkUnderMaintenanceDto();
        linkUnderMaintenanceDto.setSrcPort(TestIslMock.SRC_PORT);
        linkUnderMaintenanceDto.setSrcSwitch(TestIslMock.SRC_SWITCH);
        linkUnderMaintenanceDto.setDstPort(TestIslMock.DST_PORT);
        linkUnderMaintenanceDto.setDstSwitch(TestIslMock.DST_SWITCH);
        linkUnderMaintenanceDto.setUnderMaintenance(TestIslMock.UNDER_MAINTENANE_FLAG);

        String inputJson = mapToJson(linkUnderMaintenanceDto);
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .patch("/api/switch/links/under-maintenance")
                        .content(inputJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);
    }

    @Test
    public void testDeleteIsl() throws Exception {
        LinkParametersDto linkParametersDto = new LinkParametersDto();
        linkParametersDto.setSrcPort(Integer.valueOf(TestIslMock.SRC_PORT));
        linkParametersDto.setSrcSwitch(TestIslMock.SRC_SWITCH);
        linkParametersDto.setDstPort(Integer.valueOf(TestIslMock.DST_PORT));
        linkParametersDto.setDstSwitch(TestIslMock.DST_SWITCH);

        List<IslLinkInfo> islLinkInfo = new ArrayList<IslLinkInfo>();
        String inputJson = mapToJson(linkParametersDto);
        when(serviceSwitch.deleteLink(linkParametersDto, TestIslMock.USER_ID)).thenReturn(islLinkInfo);
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/switch/links")
                        .content(inputJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);
    }

    @Test
    public void testUpdateIslBfdFlag() throws Exception {
        LinkParametersDto linkParametersDto = new LinkParametersDto();
        linkParametersDto.setSrcPort(Integer.valueOf(TestIslMock.SRC_PORT));
        linkParametersDto.setSrcSwitch(TestIslMock.SRC_SWITCH);
        linkParametersDto.setDstPort(Integer.valueOf(TestIslMock.DST_PORT));
        linkParametersDto.setDstSwitch(TestIslMock.DST_SWITCH);
        linkParametersDto.setEnableBfd(TestIslMock.ENABLE_BFD_FLAG);
        String inputJson = mapToJson(linkParametersDto);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                        .patch("/api/switch/link/enable-bfd")
                        .content(inputJson)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

    }

    @Test
    public void testUpdateSwitchPortProperty() throws Exception {
        try {
            SwitchProperty switchProperty = new SwitchProperty();
            switchProperty.setDiscoveryEnabled(true);
            String inputJson = mapToJson(switchProperty);

            MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders
                            .put("/api/switch/{switchId}/ports/{port}/properties",
                                    TestSwitchMock.SWITCH_ID, TestSwitchMock.PORT)
                            .content(inputJson)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andReturn();
            int status = mvcResult.getResponse().getStatus();
            assertEquals(200, status);
        } catch (Exception e) {
            System.out.println("Exception is: " + e);
        }
    }

    @Test
    public void testUpdateSwitchPortPropertyIfSwitchIdNotPassed() throws Exception {
        try {
            SwitchProperty switchProperty = new SwitchProperty();
            switchProperty.setDiscoveryEnabled(true);
            String inputJson = mapToJson(switchProperty);
            mockMvc.perform(
                    put("/api/switch/{switchId}/ports/{port}/properties",
                            TestSwitchMock.SWITCH_ID_NULL, TestSwitchMock.PORT)
                            .content(inputJson).contentType(MediaType.APPLICATION_JSON)).andExpect(
                    status().isNotFound());
            assertTrue(true);
        } catch (Exception e) {
            System.out.println("Exception is: " + e);
            fail();
        }
    }

    @Test
    public void testUpdateSwitchPortPropertyIfPortNotPassed() throws Exception {
        try {
            SwitchProperty switchProperty = new SwitchProperty();
            switchProperty.setDiscoveryEnabled(true);
            String inputJson = mapToJson(switchProperty);
            mockMvc.perform(put("/api/switch/{switchId}/ports/{port}/properties",
                            TestSwitchMock.SWITCH_ID, TestSwitchMock.SWITCH_ID_NULL)
                            .content(inputJson).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            assertTrue(true);
        } catch (Exception ex) {
            System.out.println("Exception is: " + ex);
            fail();
        }
    }

    @Test
    public void testUpdateSwitchPortPropertyIfSwitchPropertyNotPassed() throws Exception {
        try {
            mockMvc.perform(
                    put("/api/switch/{switchId}/ports/{port}/properties",
                            TestSwitchMock.SWITCH_ID, TestSwitchMock.PORT)
                            .contentType(MediaType.APPLICATION_JSON)).andExpect(
                    status().isBadRequest());
            assertTrue(true);
        } catch (Exception e) {
            System.out.println("Exception is: " + e);
            fail();
        }
    }

    @Test
    public void testGetSwitchPortProperties() {
        SwitchProperty switchProperty = new SwitchProperty();
        try {
            when(serviceSwitch.getSwitchPortProperty(TestSwitchMock.SWITCH_ID, TestSwitchMock.PORT))
                    .thenReturn(switchProperty);
            mockMvc.perform(get("/api/switch/{switchId}/ports/{port}/properties", TestSwitchMock.SWITCH_ID,
                            TestSwitchMock.SWITCH_PORT).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testGetSwitchPortPropertiesIfSwitchIdNotPassed() {
        try {
            mockMvc.perform(get("/api/switch/ports/{port}/properties", TestSwitchMock.PORT)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testGetSwitchPortPropertiesIfPortNotPassed() {
        try {
            mockMvc.perform(get("/api/switch/{switchId}/ports/properties", TestSwitchMock.SWITCH_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    protected String mapToJson(Object obj) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(obj);
    }
}
