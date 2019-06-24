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
import org.openkilda.grpc.speaker.mapper.RequestMapper;
import org.openkilda.grpc.speaker.service.GrpcSenderService;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.messaging.command.grpc.GetSwitchInfoRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.grpc.CreateLogicalPortResponse;
import org.openkilda.messaging.info.grpc.DumpLogicalPortsResponse;
import org.openkilda.messaging.info.grpc.GetSwitchInfoResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageProcessor {

    @Autowired
    KafkaMessageProducer messageProducer;

    @Autowired
    private GrpcSenderService service;

    @Autowired
    RequestMapper requestMapper;

    @Value("#{kafkaTopicsConfig.getGrpcSpeakerResponseTopic()}")
    private String responseTopic;

    /**
     * Process request.
     *
     * @param message a command data.
     * @param key a kafka key.
     */
    public void processRequest(CommandMessage message, String key) {
        CommandData command = message.getData();
        if (command instanceof CreateLogicalPortRequest) {
            handleCreateLogicalPortRequest((CreateLogicalPortRequest) command, message.getCorrelationId(), key);
        } else if (command instanceof DumpLogicalPortsRequest) {
            handleDumpLogicalPortsRequest((DumpLogicalPortsRequest) command, message.getCorrelationId(), key);
        } else if (command instanceof GetSwitchInfoRequest) {
            handleGetSwitchInfoRequest((GetSwitchInfoRequest) command, message.getCorrelationId(), key);
        } else {
            log.error("Unable to handle '{}' request - handler not found.", command);
        }
    }

    private void handleCreateLogicalPortRequest(CreateLogicalPortRequest req, String correlationId, String key) {
        service.createLogicalPort(req.getAddress(), requestMapper.toLogicalPort(req))
                .thenAccept(port -> sendResponse(
                        new CreateLogicalPortResponse(req.getAddress(), port, true), correlationId, key))
                .whenComplete((e, ex) -> {
                    if (ex != null) {
                        sendErrorResponse((GrpcRequestFailureException) ex.getCause(), correlationId, key);
                    }
                });
    }

    private void handleDumpLogicalPortsRequest(DumpLogicalPortsRequest req, String correlationId, String key) {
        service.dumpLogicalPorts(req.getAddress())
                .thenAccept(ports -> sendResponse(
                        new DumpLogicalPortsResponse(req.getAddress(), ports), correlationId, key))
                .whenComplete((e, ex) -> {
                    if (ex != null) {
                        sendErrorResponse((GrpcRequestFailureException) ex.getCause(), correlationId, key);
                    }
                });
    }

    private void handleGetSwitchInfoRequest(GetSwitchInfoRequest req, String correlationId, String key) {
        service.getSwitchStatus(req.getAddress())
                .thenAccept(switchInfo -> sendResponse(
                        new GetSwitchInfoResponse(req.getAddress(), switchInfo), correlationId, key))
                .whenComplete((e, ex) -> {
                    if (ex != null) {
                        sendErrorResponse((GrpcRequestFailureException) ex.getCause(), correlationId, key);
                    }
                });
    }

    private void sendResponse(InfoData data, String correlationId, String key) {
        InfoMessage message = new InfoMessage(data, System.currentTimeMillis(), correlationId);
        messageProducer.send(responseTopic, key, message);
    }

    private void sendErrorResponse(GrpcRequestFailureException ex, String correlationId, String key) {
        ErrorData data = new ErrorData(ex.getErrorType(), ex.getMessage(), "");
        ErrorMessage error = new ErrorMessage(data, System.currentTimeMillis(), correlationId);
        messageProducer.send(responseTopic, key, error);
    }
}
