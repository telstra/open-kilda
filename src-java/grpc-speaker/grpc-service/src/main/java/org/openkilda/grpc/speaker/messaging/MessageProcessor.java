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
import org.openkilda.grpc.speaker.model.PacketInOutStatsResponse;
import org.openkilda.grpc.speaker.service.GrpcSenderService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.messaging.command.grpc.GetPacketInOutStatsRequest;
import org.openkilda.messaging.command.grpc.GetSwitchInfoRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
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

    // TODO error handling

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

        if (data instanceof CreateLogicalPortRequest) {
            handleCreateLogicalPortRequest((CreateLogicalPortRequest) data, correlationId, key);
        } else if (data instanceof DumpLogicalPortsRequest) {
            handleDumpLogicalPortsRequest((DumpLogicalPortsRequest) data, correlationId, key);
        } else if (data instanceof GetSwitchInfoRequest) {
            handleGetSwitchInfoRequest((GetSwitchInfoRequest) data, correlationId, key);
        } else if (data instanceof GetPacketInOutStatsRequest) {
            handleGetPacketInOutStatsRequest((GetPacketInOutStatsRequest) data, correlationId, key);
        } else if (data instanceof DeleteLogicalPortRequest) {
            handleDeleteLogicalPortRequest((DeleteLogicalPortRequest) data, command.getCorrelationId(), key);
        } else {
            unhandledMessage(command);
        }
    }

    private void handleCreateLogicalPortRequest(CreateLogicalPortRequest request, String correlationId, String key) {
        log.info("Creating logical port {} on switch {}", request.getLogicalPortNumber(), request.getAddress());
        service.createLogicalPort(request.getAddress(), requestMapper.toLogicalPort(request))
                .thenAccept(port -> sendResponse(
                        new CreateLogicalPortResponse(request.getAddress(), port, true), correlationId, key));
    }

    private void handleDumpLogicalPortsRequest(DumpLogicalPortsRequest request, String correlationId, String key) {
        log.debug("Dumping logical ports on switch {}", request.getAddress());
        service.dumpLogicalPorts(request.getAddress())
                .thenAccept(ports -> sendResponse(
                        new DumpLogicalPortsResponse(request.getAddress(), ports), correlationId, key));
    }

    private void handleGetSwitchInfoRequest(GetSwitchInfoRequest request, String correlationId, String key) {
        log.debug("Getting switch info for switch {}", request.getAddress());
        service.getSwitchStatus(request.getAddress())
                .thenAccept(status -> sendResponse(
                        new GetSwitchInfoResponse(request.getAddress(), status), correlationId, key));
    }

    private void handleGetPacketInOutStatsRequest(
            GetPacketInOutStatsRequest request, String correlationId, String key) {
        log.debug("Getting switch packet in out stats for switch {}", request.getAddress());
        service.getPacketInOutStats(request.getAddress())
                .thenAccept(stats -> sendPacketInOutStatsResponse(request, stats, correlationId, key));
    }

    private void sendPacketInOutStatsResponse(
            GetPacketInOutStatsRequest request, PacketInOutStatsResponse stats, String correlationId, String key) {
        GetPacketInOutStatsResponse data = new GetPacketInOutStatsResponse(
                request.getSwitchId(), responseMapper.map(stats));
        sendResponse(data, correlationId, key);
    }

    private void handleDeleteLogicalPortRequest(DeleteLogicalPortRequest command, String correlationId, String key) {
        service.deleteConfigLogicalPort(command.getAddress(), command.getLogicalPortNumber())
                .thenAccept(port -> sendResponse(
                        new DeleteLogicalPortResponse(command.getAddress(), command.getLogicalPortNumber(),
                                port.getDeleted()), correlationId, key))
                .whenComplete((e, ex) -> {
                    if (ex != null) {
                        sendErrorResponse((GrpcRequestFailureException) ex.getCause(), correlationId, key);
                    }
                });
    }

    private void sendResponse(InfoData data, String correlationId, String key) {
        InfoMessage message = new InfoMessage(data, System.currentTimeMillis(), correlationId);
        messageProducer.send(grpcResponseTopic, key, message);
    }

    private void sendErrorResponse(GrpcRequestFailureException ex, String correlationId, String key) {
        ErrorData data = new ErrorData(ex.getErrorType(), ex.getMessage(), "");
        ErrorMessage error = new ErrorMessage(data, System.currentTimeMillis(), correlationId);
        messageProducer.send(grpcResponseTopic, key, error);
    }

    private void unhandledMessage(Message message) {
        log.error("GRPC speaker is unable to handle message {}", message);
    }
}
