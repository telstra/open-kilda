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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.controller.TestConfig;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint;
import org.openkilda.northbound.dto.v2.yflows.SubFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchSharedEndpointEncapsulation;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation;
import org.openkilda.northbound.service.YFlowService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Collections;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = YFlowControllerV2.class)
@WebAppConfiguration
@ContextConfiguration(classes = {TestConfig.class})
@TestPropertySource("classpath:northbound.properties")
public class YFlowControllerV2Test {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private YFlowService service;

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

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenEndpointVlanIdMaxValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(4096, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenEndpointInnerVlanIdMaxValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(10, 4096, 0, 0);
    }

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenSharedVlanIdMaxValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(10, 0, 4096, 0);
    }

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenSharedInnerVlanIdMaxValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(10, 0, 20, 4096);
    }

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenEndpointVlanIdMinValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(-1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenEndpointInnerVlanIdMinValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(10, -1, 0, 0);
    }

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenSharedVlanIdMinValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(10, 0, -1, 0);
    }

    @Test
    @WithMockUser(username = TestConfig.USERNAME, password = TestConfig.PASSWORD, roles = TestConfig.ROLE)
    public void shouldReturnStatus400WhenSharedInnerVlanIdMinValueIsInvalid() throws Exception {
        verifyCreateRequestFailsOnBadVlanId(10, 0, 20, -1);
    }

    private void verifyCreateRequestFailsOnBadVlanId(
            int endpointVlanId, int endpointInnerVlanId, int sharedVlanId, int sharedInnerVlanId)
            throws Exception {
        YFlowCreatePayload payload = buildTestYFlowCreatePayload()
                .subFlow(SubFlowUpdatePayload.builder()
                        .flowId("sub-alpha")
                        .endpoint(FlowEndpointV2.builder()
                                .switchId(new SwitchId(1))
                                .portNumber(2)
                                .vlanId(endpointVlanId)
                                .innerVlanId(endpointInnerVlanId)
                                .build())
                        .sharedEndpoint(YFlowSharedEndpointEncapsulation.builder()
                                .vlanId(sharedVlanId)
                                .innerVlanId(sharedInnerVlanId)
                                .build())
                        .build())
                .build();

        verifyRequestFailsOnBadVlanId(post("/v2/y-flows")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header("correlation_id", "test")
                .content(objectMapper.writeValueAsString(payload)));
    }

    private void verifyPatchRequestFailsOnBadVlanId(
            FlowPatchEndpoint subEndpoint, YFlowPatchSharedEndpointEncapsulation sharedEndpoint)
            throws Exception {
        YFlowPatchPayload payload = YFlowPatchPayload.builder()
                .subFlows(Collections.singletonList(
                        SubFlowPatchPayload.builder()
                                .endpoint(subEndpoint)
                                .sharedEndpoint(sharedEndpoint)
                                .build()
                ))
                .build();
        verifyRequestFailsOnBadVlanId(patch("/v2/y-flows/dummy-y-flow-id")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header("correlation_id", "test")
                .content(objectMapper.writeValueAsString(payload)));
    }

    private void verifyRequestFailsOnBadVlanId(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        mvc.perform(requestBuilder)
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(service);
    }

    private YFlowCreatePayload.YFlowCreatePayloadBuilder buildTestYFlowCreatePayload() {
        return YFlowCreatePayload.builder()
                .yFlowId("y-flow-id")
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN.name())
                .pathComputationStrategy(PathComputationStrategy.COST.name());
    }
}

