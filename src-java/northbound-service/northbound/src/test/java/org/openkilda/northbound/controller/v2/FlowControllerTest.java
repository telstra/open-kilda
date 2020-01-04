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

package org.openkilda.northbound.controller.v2;

import static org.junit.Assert.assertEquals;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.northbound.controller.TestConfig;
import org.openkilda.northbound.controller.v1.TestMessageMock;
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

@RunWith(SpringRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:northbound.properties")
public class FlowControllerTest {
    private static final String USERNAME = "kilda";
    private static final String PASSWORD = "kilda";
    private static final String ROLE = "ADMIN";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Before
    public void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        RequestCorrelationId.create(DEFAULT_CORRELATION_ID);
    }

    @Test
    @WithMockUser(username = USERNAME, password = PASSWORD, roles = ROLE)
    public void bulkUpdateFlow() throws Exception {

        MvcResult mvcResult = mockMvc.perform(post("/v2/flows/swap-endpoint")
                .header(CORRELATION_ID, testCorrelationId())
                .contentType(APPLICATION_JSON_VALUE)
                .content(MAPPER.writeValueAsString(TestMessageMock.bulkFlow)))
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON_UTF8_VALUE))
                .andReturn();
        SwapFlowEndpointPayload response
                = MAPPER.readValue(result.getResponse().getContentAsString(), SwapFlowEndpointPayload.class);
        assertEquals(TestMessageMock.bulkFlow.getFirstFlow(), response.getFirstFlow());
        assertEquals(TestMessageMock.bulkFlow.getSecondFlow(), response.getSecondFlow());
    }

    private static String testCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
