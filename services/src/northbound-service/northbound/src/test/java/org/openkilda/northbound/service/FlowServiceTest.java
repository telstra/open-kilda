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

package org.openkilda.northbound.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.SwapFlowResponse;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.SwapFlowEndpointPayload;
import org.openkilda.messaging.payload.flow.SwapFlowPayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.MessageExchanger;
import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.impl.FlowServiceImpl;
import org.openkilda.northbound.service.impl.SwitchServiceImpl;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.RequestCorrelationId;
import org.openkilda.northbound.utils.TestCorrelationIdFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
public class FlowServiceTest {
    private int requestIdIndex = 0;

    @Autowired
    private CorrelationIdFactory idFactory;

    @Autowired
    private FlowService flowService;

    @Autowired
    private MessageExchanger messageExchanger;

    @Before
    public void reset() {
        messageExchanger.resetMockedResponses();

        String lastRequestId = idFactory.produceChained("dummy");
        lastRequestId = lastRequestId.substring(0, lastRequestId.indexOf(':')).trim();
        requestIdIndex = Integer.valueOf(lastRequestId) + 1;
    }

    @Test
    public void swapFlowEndpoint() throws Exception {
        String correlationId = "bulk-flow-update";
        RequestCorrelationId.create(correlationId);

        String firstFlowId = "bulk-flow-1";
        String secondFlowId = "bulk-flow-2";

        FlowEndpointPayload firstEndpoint = new FlowEndpointPayload(new SwitchId("ff:00"), 1, 1);
        FlowEndpointPayload secondEndpoint = new FlowEndpointPayload(new SwitchId("ff:01"), 2, 2);

        SwapFlowPayload firstFlowPayload = SwapFlowPayload.builder()
                .flowId(firstFlowId)
                .source(firstEndpoint)
                .destination(firstEndpoint)
                .build();

        SwapFlowPayload secondFlowPayload = SwapFlowPayload.builder()
                .flowId(secondFlowId)
                .source(secondEndpoint)
                .destination(secondEndpoint)
                .build();

        SwapFlowEndpointPayload input = new SwapFlowEndpointPayload(firstFlowPayload, secondFlowPayload);

        FlowDto firstResponse = FlowDto.builder()
                .flowId(firstFlowId).bandwidth(10000).description(firstFlowId).state(FlowState.UP)
                .sourceSwitch(new SwitchId("ff:00")).sourcePort(1).sourceVlan(1)
                .destinationSwitch(new SwitchId("ff:01")).destinationPort(2).destinationVlan(2)
                .build();

        FlowDto secondResponse = FlowDto.builder()
                .flowId(secondFlowId).bandwidth(20000).description(secondFlowId).state(FlowState.UP)
                .sourceSwitch(new SwitchId("ff:01")).sourcePort(2).sourceVlan(2)
                .destinationSwitch(new SwitchId("ff:00")).destinationPort(1).destinationVlan(1)
                .build();

        SwapFlowResponse response = new SwapFlowResponse(
                new FlowResponse(firstResponse), new FlowResponse(secondResponse));
        messageExchanger.mockResponse(correlationId, response);

        SwapFlowEndpointPayload result = flowService.swapFlowEndpoint(input).get();
        assertEquals(secondEndpoint, result.getFirstFlow().getDestination());
        assertEquals(firstEndpoint, result.getSecondFlow().getDestination());
    }

    @TestConfiguration
    @Import(KafkaConfig.class)
    @ComponentScan({
            "org.openkilda.northbound.converter",
            "org.openkilda.northbound.utils"})
    @PropertySource({"classpath:northbound.properties"})
    static class Config {
        @Bean
        public CorrelationIdFactory idFactory() {
            return new TestCorrelationIdFactory();
        }

        @Bean
        public MessagingChannel messagingChannel() {
            return new MessageExchanger();
        }

        @Bean
        public RestTemplate restTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        public FlowService flowService() {
            return new FlowServiceImpl();
        }

        @Bean
        public SwitchService switchService() {
            return new SwitchServiceImpl();
        }
    }
}
