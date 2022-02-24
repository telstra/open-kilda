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

package org.openkilda.grpc.speaker.messaging;

import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;
import org.openkilda.grpc.speaker.mapper.NoviflowResponseMapper;
import org.openkilda.grpc.speaker.mapper.RequestMapper;
import org.openkilda.grpc.speaker.service.GrpcSenderService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.MessageCookie;
import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.messaging.command.grpc.GetPacketInOutStatsRequest;
import org.openkilda.messaging.command.grpc.GetSwitchInfoRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.grpc.CreateLogicalPortResponse;
import org.openkilda.messaging.info.grpc.DeleteLogicalPortResponse;
import org.openkilda.messaging.info.grpc.DumpLogicalPortsResponse;
import org.openkilda.messaging.info.grpc.GetPacketInOutStatsResponse;
import org.openkilda.messaging.info.grpc.GetSwitchInfoResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
public class MessageProcessor {

    @Autowired
    KafkaMessageProducer messageProducer;

    @Autowired
    private GrpcSenderService service;

    @Autowired
    RequestMapper requestMapper;

    @Autowired
    NoviflowResponseMapper responseMapper;

    @Value("#{kafkaTopicsConfig.getGrpcResponseTopic()}")
    private String grpcResponseTopic;

    @Value("#{kafkaTopicsConfig.getStatsTopic()}")
    private String statsTopic;

    /**
     * Process request.
     *
     * @param message a message to be processed
     */
    public void processRequest(Message message, String key) {
        if (message instanceof CommandMessage) {
            handleCommandMessage((CommandMessage) message, key);
        } else {
            unhandledMessage(message);
        }
    }

    private void handleCommandMessage(CommandMessage command, String key) {
        CommandData data = command.getData();
        String correlationId = command.getCorrelationId();
        CompletableFuture<Response> result;

        if (data instanceof CreateLogicalPortRequest) {
            result = handleCreateLogicalPortRequest((CreateLogicalPortRequest) data);
        } else if (data instanceof DumpLogicalPortsRequest) {
            result = handleDumpLogicalPortsRequest((DumpLogicalPortsRequest) data);
        } else if (data instanceof GetSwitchInfoRequest) {
            result = handleGetSwitchInfoRequest((GetSwitchInfoRequest) data);
        } else if (data instanceof GetPacketInOutStatsRequest) {
            result = handleGetPacketInOutStatsRequest((GetPacketInOutStatsRequest) data);
        } else if (data instanceof DeleteLogicalPortRequest) {
            result = handleDeleteLogicalPortRequest((DeleteLogicalPortRequest) data);
        } else {
            result = unhandledMessage(command);
        }

        result.thenAccept(response -> sendResponse(response, correlationId, key, command.getCookie()));
    }

    private CompletableFuture<Response> handleCreateLogicalPortRequest(CreateLogicalPortRequest request) {
        CompletableFuture<InfoData> future = service
                .createLogicalPort(request.getAddress(), requestMapper.toLogicalPort(request))
                .thenApply(result -> new CreateLogicalPortResponse(request.getAddress(), result, true));
        return makeResponse(future, String.format(
                "Creating logical port %s on switch %s", request.getLogicalPortNumber(), request.getAddress()));
    }

    private CompletableFuture<Response> handleDumpLogicalPortsRequest(DumpLogicalPortsRequest request) {
        CompletableFuture<InfoData> future = service.dumpLogicalPorts(request.getAddress())
                .thenApply(result -> new DumpLogicalPortsResponse(request.getAddress(), result));
        return makeResponse(future, String.format("Dumping logical ports on switch %s", request.getAddress()));
    }

    private CompletableFuture<Response> handleDeleteLogicalPortRequest(DeleteLogicalPortRequest command) {
        CompletableFuture<InfoData> future = service
                .deleteConfigLogicalPort(command.getAddress(), command.getLogicalPortNumber())
                .thenApply(result -> new DeleteLogicalPortResponse(
                        command.getAddress(), command.getLogicalPortNumber(), result.getDeleted()));
        return makeResponse(future, String.format(
                "Delete logical port %s on switch %s", command.getLogicalPortNumber(), command.getAddress()));
    }

    private CompletableFuture<Response> handleGetSwitchInfoRequest(GetSwitchInfoRequest request) {
        CompletableFuture<InfoData> future = service.getSwitchStatus(request.getAddress())
                .thenApply(result -> new GetSwitchInfoResponse(request.getAddress(), result));
        return makeResponse(future, String.format("Getting switch info for switch %s", request.getAddress()));
    }

    private CompletableFuture<Response> handleGetPacketInOutStatsRequest(GetPacketInOutStatsRequest request) {
        CompletableFuture<InfoData> future = service.getPacketInOutStats(request.getAddress())
                .thenApply(stats -> new GetPacketInOutStatsResponse(request.getSwitchId(), responseMapper.map(stats)));
        return makeResponse(
                future, String.format("Getting switch packet in/out stats for switch %s", request.getAddress()),
                true, statsTopic);
    }

    private CompletableFuture<Response> unhandledMessage(Message message) {
        String errorMessage = String.format("GRPC speaker is unable to handle message %s", message);
        log.error(errorMessage);

        ErrorData payload = new ErrorData(
                ErrorType.INTERNAL_ERROR, errorMessage, "");
        return CompletableFuture.completedFuture(new Response(payload, grpcResponseTopic));
    }

    private CompletableFuture<Response> makeResponse(
            CompletableFuture<InfoData> future, String actionDefinition) {
        return makeResponse(future, actionDefinition, false, grpcResponseTopic);
    }

    private CompletableFuture<Response> makeResponse(
            CompletableFuture<InfoData> future, String actionDefinition, boolean isQuiet, String topic) {
        return future
                .handle((result, error) -> handleResult(result, error, actionDefinition, isQuiet))
                .thenApply(payload -> new Response(payload, topic));
    }

    private MessageData handleResult(InfoData result, Throwable error, String actionDefinition, boolean isQuiet) {
        if (error != null) {
            return handleError(error, actionDefinition);
        }

        reportSuccess(actionDefinition, isQuiet);
        return result;
    }

    private MessageData handleError(Throwable error, String actionDefinition) {
        try {
            throw error;
        } catch (GrpcRequestFailureException e) {
            reportFailure(actionDefinition, String.format(
                    "type=%s code=%s %s", e.getErrorType(), e.getCode(), e.getMessage()));
            return new ErrorData(e.getErrorType(), e.getMessage(), "");
        } catch (CompletionException e) {
            return handleError(e.getCause(), actionDefinition);
        } catch (Throwable e) {
            reportFailure(actionDefinition, e);
            return new ErrorData(ErrorType.INTERNAL_ERROR, e.getMessage(), "");
        }
    }

    private void sendResponse(Response response, String correlationId, String key, MessageCookie cookie) {
        log.debug("GRPC speaker is sending response on request {} with cookie {}: {}", key, cookie, response);
        Message message = makeMessage(response, correlationId, cookie);
        messageProducer.send(response.getTopic(), key, message);
    }

    private Message makeMessage(Response response, String correlationId, MessageCookie cookie) {
        MessageData payload = response.getPayload();
        if (payload instanceof InfoData) {
            return new InfoMessage((InfoData) payload, correlationId, cookie);
        } else if (payload instanceof ErrorData) {
            return new ErrorMessage((ErrorData) payload, correlationId, cookie);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Unexpected/unsupported message payload type: %s", payload.getClass().getName()));
        }
    }

    private void reportSuccess(String actionDefinition, boolean isQuiet) {
        if (isQuiet) {
            log.debug("{} - success", actionDefinition);
        } else {
            log.info("{} - success", actionDefinition);
        }
    }

    private void reportFailure(String actionDefinition, String description) {
        log.error("{} - failure - {}", actionDefinition, description);
    }

    private void reportFailure(String actionDefinition, Throwable error) {
        log.error("{} - failure - internal error {}", actionDefinition, error.getMessage(), error);
    }

    @lombok.Value
    private static class Response {
        MessageData payload;

        String topic;
    }
}
