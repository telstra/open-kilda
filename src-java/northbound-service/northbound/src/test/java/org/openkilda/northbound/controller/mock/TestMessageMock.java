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

package org.openkilda.northbound.controller.mock;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.error.ErrorType.OPERATION_TIMED_OUT;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.SwapFlowEndpointRequest;
import org.openkilda.messaging.command.switches.SwitchRulesDeleteRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.SwapFlowResponse;
import org.openkilda.messaging.info.switches.SwitchRulesResponse;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.nbtopology.request.FlowReadRequest;
import org.openkilda.messaging.nbtopology.request.FlowsDumpRequest;
import org.openkilda.messaging.nbtopology.request.GetFlowPathRequest;
import org.openkilda.messaging.nbtopology.response.GetFlowPathResponse;
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.SwapFlowEndpointPayload;
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload;
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
    public static final String FLOW_ID = "flow_id_1";
    static final String SECOND_FLOW_ID = "second_flow";
    public static final String FLOW_ID_FROM_PATH = "different_flow_id";
    static final SwitchId SWITCH_ID = new SwitchId("ff:00");
    static final SwitchId SECOND_SWITCH_ID = new SwitchId("ff:01");
    public static final String ERROR_FLOW_ID = "error-flow";
    public static final String TEST_SWITCH_ID = "ff:01";
    public static final long TEST_SWITCH_RULE_COOKIE = 1L;
    public static final FlowEndpointPayload FLOW_ENDPOINT = new FlowEndpointPayload(SWITCH_ID, 1, 1,
            new DetectConnectedDevicesPayload(false, false));
    static final FlowEndpointPayload secondFlowEndpoint = new FlowEndpointPayload(SECOND_SWITCH_ID, 2, 2,
            new DetectConnectedDevicesPayload(false, false));
    public static final FlowEndpointV2 FLOW_PAYLOAD_ENDPOINT = new FlowEndpointV2(SWITCH_ID, 1, 1,
            new DetectConnectedDevicesV2(false, false));
    static final FlowEndpointV2 secondFlowPayloadEndpoint = new FlowEndpointV2(SECOND_SWITCH_ID, 2, 2,
            new DetectConnectedDevicesV2(false, false));

    public static final FlowPayload FLOW = FlowPayload.builder()
            .id(FLOW_ID)
            .source(FLOW_ENDPOINT)
            .destination(FLOW_ENDPOINT)
            .maximumBandwidth(10000)
            .description(FLOW_ID)
            .status(FlowState.UP.getState())
            .build();

    public static final FlowRequestV2 FLOW_REQUEST_V2 = FlowRequestV2.builder()
            .flowId(FLOW_ID)
            .source(FLOW_PAYLOAD_ENDPOINT)
            .destination(FLOW_PAYLOAD_ENDPOINT)
            .build();

    public static final FlowResponsePayload FLOW_RESPONSE_PAYLOAD = FlowResponsePayload.flowResponsePayloadBuilder()
            .id(FLOW_ID)
            .source(FLOW_ENDPOINT)
            .destination(FLOW_ENDPOINT)
            .maximumBandwidth(10000)
            .description(FLOW_ID)
            .status(FlowState.UP.getState())
            .build();

    public static final SwapFlowPayload firstSwapFlow = SwapFlowPayload.builder()
            .flowId(FLOW_ID)
            .source(FLOW_PAYLOAD_ENDPOINT)
            .destination(FLOW_PAYLOAD_ENDPOINT)
            .build();

    public static final SwapFlowPayload secondSwapFlow = SwapFlowPayload.builder()
            .flowId(SECOND_FLOW_ID)
            .source(secondFlowPayloadEndpoint)
            .destination(secondFlowPayloadEndpoint)
            .build();

    public static final SwapFlowEndpointPayload bulkFlow = new SwapFlowEndpointPayload(firstSwapFlow, secondSwapFlow);
    public static final FlowIdStatusPayload FLOW_STATUS = new FlowIdStatusPayload(FLOW_ID, FlowState.UP);
    static final PathInfoData path = new PathInfoData(0L, Collections.emptyList());
    static final List<PathNodePayload> pathPayloadsList = singletonList(new PathNodePayload(SWITCH_ID, 1, 1));
    public static final FlowPathPayload FLOW_PATH = FlowPathPayload.builder()
            .id(FLOW_ID)
            .forwardPath(pathPayloadsList)
            .reversePath(pathPayloadsList)
            .build();
    static final FlowDto flowModel = FlowDto.builder()
            .flowId(FLOW_ID).bandwidth(10000).description(FLOW_ID)
            .sourceSwitch(SWITCH_ID).destinationSwitch(SWITCH_ID)
            .sourcePort(1).destinationPort(1).sourceVlan(1).destinationVlan(1).meterId(1)
            .state(FlowState.UP)
            .build();
    static final FlowDto secondFlowModel = FlowDto.builder()
            .flowId(SECOND_FLOW_ID).bandwidth(20000).description(SECOND_FLOW_ID)
            .sourceSwitch(SECOND_SWITCH_ID).sourcePort(2).sourceVlan(2)
            .destinationSwitch(SECOND_SWITCH_ID).destinationPort(2).destinationVlan(2)
            .state(FlowState.UP)
            .build();

    private static final FlowResponse flowResponse = new FlowResponse(flowModel);
    private static final FlowResponse secondFlowResponse = new FlowResponse(secondFlowModel);
    static final SwapFlowResponse bulkFlowResponse = new SwapFlowResponse(flowResponse, secondFlowResponse);
    static final FlowResponse FLOW_RESPONSE = new FlowResponse(flowModel);
    static final GetFlowPathResponse FLOW_PATH_RESPONSE =
            new GetFlowPathResponse(FlowPathDto.builder()
                    .id(FLOW_ID)
                    .forwardPath(pathPayloadsList)
                    .reversePath(pathPayloadsList)
                    .build());
    private static final SwitchRulesResponse switchRulesResponse =
            new SwitchRulesResponse(singletonList(TEST_SWITCH_RULE_COOKIE));
    private static final Map<String, CommandData> messages = new ConcurrentHashMap<>();

    public static final MessageError DIFFERENT_FLOW_ID_ERROR = new MessageError(DEFAULT_CORRELATION_ID, 0,
            ErrorType.DATA_INVALID.toString(), "flow_id from body and from path are different",
            format("Body flow_id: %s, path flow_id: %s", FLOW_ID, FLOW_ID_FROM_PATH));

    /**
     * Chooses response by request.
     *
     * @param data received from kafka CommandData message payload
     * @return InfoMassage to be send as response payload
     */
    private CompletableFuture<InfoData> formatResponse(final String correlationId, final CommandData data) {
        CompletableFuture<InfoData> result = new CompletableFuture<>();
        if (data instanceof FlowRequest) {
            result.complete(buildFlowResponse((FlowRequest) data));
        } else if (data instanceof FlowDeleteRequest) {
            result.complete(flowResponse);
        } else if (data instanceof FlowReadRequest) {
            result = getReadFlowResponse(((FlowReadRequest) data).getFlowId(), correlationId);
        } else if (data instanceof SwitchRulesDeleteRequest) {
            result = completedFuture(switchRulesResponse);
        } else if (data instanceof SwapFlowEndpointRequest) {
            result = completedFuture(bulkFlowResponse);
        } else {
            return null;
        }

        return result;
    }

    private FlowResponse buildFlowResponse(FlowRequest flowRequest) {
        return new FlowResponse(FlowDto.builder()
                .flowId(flowRequest.getFlowId())
                .bandwidth(flowRequest.getBandwidth())
                .description(flowRequest.getDescription())
                .sourceSwitch(flowRequest.getSource().getSwitchId())
                .destinationSwitch(flowRequest.getDestination().getSwitchId())
                .sourcePort(flowRequest.getSource().getPortNumber())
                .destinationPort(flowRequest.getDestination().getPortNumber())
                .sourceVlan(flowRequest.getSource().getOuterVlanId())
                .destinationVlan(flowRequest.getDestination().getOuterVlanId())
                .meterId(1)
                .state(FlowState.UP)
                .build());
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
        CommandData commandData = ((CommandMessage) message).getData();
        if (commandData instanceof FlowsDumpRequest) {
            return completedFuture(singletonList(FLOW_RESPONSE));
        } else if (commandData instanceof GetFlowPathRequest) {
            return completedFuture(singletonList(FLOW_PATH_RESPONSE));
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
            return completedFuture(FLOW_RESPONSE);
        }
    }
}
