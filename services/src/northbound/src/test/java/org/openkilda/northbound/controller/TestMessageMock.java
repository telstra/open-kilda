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

package org.openkilda.northbound.controller;

import static java.util.Collections.singletonList;
import static org.openkilda.messaging.error.ErrorType.OPERATION_TIMED_OUT;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowReadRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsDumpRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.SwitchChangeType;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.messaging.MessagingChannel;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring component which mocks WorkFlow Manager. This instance listens kafka ingoing requests and sends back
 * appropriate kafka responses. Response type choice is based on request type.
 */
@Component
public class TestMessageMock implements MessagingChannel {
    static final String FLOW_ID = "ff:00";
    static final SwitchId SWITCH_ID = new SwitchId(FLOW_ID);
    static final String ERROR_FLOW_ID = "error-flow";
    static final String TEST_SWITCH_ID = "ff:01";
    static final long TEST_SWITCH_RULE_COOKIE = 1L;
    static final FlowEndpointPayload flowEndpoint = new FlowEndpointPayload(SWITCH_ID, 1, 1);
    static final FlowPayload flow =
            new FlowPayload(FLOW_ID, flowEndpoint, flowEndpoint, 10000, false, false, FLOW_ID, null,
            FlowState.UP.getState());
    static final FlowIdStatusPayload flowStatus = new FlowIdStatusPayload(FLOW_ID, FlowState.UP);
    static final PathInfoData path = new PathInfoData(0L, Collections.emptyList());
    static final List<PathNodePayload> pathPayloadsList =
            Collections.singletonList(new PathNodePayload(SWITCH_ID, 1, 1));
    static final FlowPathPayload flowPath = new FlowPathPayload(FLOW_ID, pathPayloadsList, pathPayloadsList);
    static final FlowDto flowModel = new FlowDto(FLOW_ID, 10000, false, false, 0L, FLOW_ID, null, SWITCH_ID,
            SWITCH_ID, 1, 1, 1, 1, 1, 1, path, FlowState.UP);

    private static final FlowResponse flowResponse = new FlowResponse(flowModel);
    static final FlowReadResponse FLOW_RESPONSE =
            new FlowReadResponse(new BidirectionalFlowDto(flowModel, flowModel));
    private static final SwitchRulesResponse switchRulesResponse =
            new SwitchRulesResponse(singletonList(TEST_SWITCH_RULE_COOKIE));
    private static final Map<String, CommandData> messages = new ConcurrentHashMap<>();
    static final SwitchInfoData SWITCH_INFO_DATA =
            new SwitchInfoData(SWITCH_ID, SwitchChangeType.ACTIVATED, "", "", "", "", false);

    /**
     * Chooses response by request.
     *
     * @param data received from kafka CommandData message payload
     * @return InfoMassage to be send as response payload
     */
    private CompletableFuture<InfoData> formatResponse(final String correlationId, final CommandData data) {
        CompletableFuture<InfoData> result = new CompletableFuture<>();
        if (data instanceof FlowCreateRequest) {
            result.complete(flowResponse);
        } else if (data instanceof FlowDeleteRequest) {
            result.complete(flowResponse);
        } else if (data instanceof FlowUpdateRequest) {
            result.complete(flowResponse);
        } else if (data instanceof FlowReadRequest) {
            result = getReadFlowResponse(((FlowReadRequest) data).getFlowId(), correlationId);
        } else if (data instanceof SwitchRulesDeleteRequest) {
            result = CompletableFuture.completedFuture(switchRulesResponse);
        } else {
            return null;
        }

        return result;
    }

    @Override
    public CompletableFuture<InfoData> sendAndGet(String topic, Message message) {
        if ("error-topic".equals(topic)) {
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    OPERATION_TIMED_OUT, "timeout", "kilda-test");
        } else {
            return formatResponse(message.getCorrelationId(), ((CommandMessage) message).getData());
        }
    }

    @Override
    public CompletableFuture<List<InfoData>> sendAndGetChunked(String topic, Message message) {
        if (((CommandMessage) message).getData() instanceof FlowsDumpRequest) {
            return CompletableFuture.completedFuture(Collections.singletonList(FLOW_RESPONSE));
        } else {
            return null;
        }
    }

    @Override
    public void send(String topic, Message message) {
        if (message instanceof CommandMessage) {
            messages.put(message.getCorrelationId(), ((CommandMessage) message).getData());
        }
    }

    private CompletableFuture<InfoData> getReadFlowResponse(String flowId, String correlationId) {
        if (ERROR_FLOW_ID.equals(flowId)) {
            ErrorMessage error = new ErrorMessage(
                    new ErrorData(ErrorType.NOT_FOUND, "Flow was not found", ERROR_FLOW_ID),
                    0, correlationId, Destination.NORTHBOUND);
            throw new MessageException(error);
        } else {
            return CompletableFuture.completedFuture(FLOW_RESPONSE);
        }
    }
}
