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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.model.SwitchId;
import org.openkilda.northbound.controller.TestConfig;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = YFlowControllerV2.class)
@WebAppConfiguration
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource("classpath:northbound.properties")
public class YFlowControllerV2Test {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldPassCreateRequestWhenPayloadIsValid() throws Exception {
        YFlowCreatePayload payload = buildTestYFlowCreatePayload()
                .build();

        String body = objectMapper.writeValueAsString(payload);

        mvc.perform(post("/y-flows")
                        .contentType("application/json")
                        .header("correlation_id", "test")
                        .content(body))
                .andExpect(status().isOk());
    }

    @Ignore
    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnsStatus400WhenEncapsulationTypeNull() throws Exception {
        YFlowCreatePayload payload = buildTestYFlowCreatePayload()
                .encapsulationType(null)
                .build();

        String body = objectMapper.writeValueAsString(payload);

        mvc.perform(post("/y-flows")
                        .contentType("application/json")
                        .header("correlation_id", "test")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Ignore
    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnsStatus400WhenPathComputationStrategyNull() throws Exception {
        YFlowCreatePayload payload = buildTestYFlowCreatePayload()
                .pathComputationStrategy(null)
                .build();

        String body = objectMapper.writeValueAsString(payload);

        mvc.perform(post("/y-flows")
                        .contentType("application/json")
                        .header("correlation_id", "test")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private YFlowCreatePayload.YFlowCreatePayloadBuilder buildTestYFlowCreatePayload() {
        return YFlowCreatePayload.builder()
                .sharedEndpoint(new YFlowSharedEndpoint(new SwitchId(1), 1))
                .encapsulationType("vlan")
                .pathComputationStrategy("cost");
    }
}
