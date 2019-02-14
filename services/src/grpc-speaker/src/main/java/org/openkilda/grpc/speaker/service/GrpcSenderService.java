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

package org.openkilda.grpc.speaker.service;

import static java.lang.String.format;

import org.openkilda.grpc.speaker.client.GrpcSession;
import org.openkilda.grpc.speaker.exception.GrpcException;
import org.openkilda.grpc.speaker.mapper.NoviflowResponseMapper;
import org.openkilda.grpc.speaker.model.EnableLogMessagesResponse;
import org.openkilda.grpc.speaker.model.GrpcDeleteOperationResponse;
import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.PortConfigDto;
import org.openkilda.grpc.speaker.model.PortConfigSetupResponse;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Performs gRPC calls.
 */
@Slf4j
@Service
public class GrpcSenderService {
    // TODO auth
    private final String name = "kilda";

    private NoviflowResponseMapper mapper;

    public GrpcSenderService(@Autowired NoviflowResponseMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Creates logical port.
     *
     * @param port the port data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<LogicalPort> createLogicalPort(String switchAddress, LogicalPortDto port) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.setLogicalPort(port))
                .thenCompose(e -> sender.showConfigLogicalPort(port.getLogicalPortNumber()))
                .thenApply(portOptional -> portOptional
                        .map(mapper::toLogicalPort)
                        .orElseThrow(() -> new GrpcException(format("Port %s was not created ", port))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    /**
     * Dumps all available logical ports of the switch.
     *
     * @param switchAddress the switch address.
     * @return list of logical ports wrapped into {@link CompletableFuture}.
     */
    public CompletableFuture<List<LogicalPort>> dumpLogicalPorts(String switchAddress) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.dumpLogicalPorts())
                .thenApply(ports -> ports.stream().map(mapper::toLogicalPort).collect(Collectors.toList()))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    /**
     * Gets switch status.
     *
     * @param switchAddress the switch address.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<SwitchInfoStatus> getSwitchStatus(String switchAddress) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.showSwitchStatus())
                .thenApply(statusOptional -> statusOptional
                        .map(mapper::toSwitchInfo)
                        .orElseThrow(() ->
                                new GrpcException(format("Couldn't get status for switch %s", switchAddress))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    /**
     * Gets logical port config.
     *
     * @param switchAddress the switch address.
     * @param port the port data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<LogicalPort> showConfigLogicalPort(String switchAddress, Integer port) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.showConfigLogicalPort(port))
                .thenApply(statusOptional -> statusOptional
                        .map(mapper::toLogicalPort)
                        .orElseThrow(() -> new GrpcException(format("Couldn't get logical port %d for switch %s",
                                port, switchAddress))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    public CompletableFuture<GrpcDeleteOperationResponse> deleteConfigLogicalPort(String switchAddress, Integer port) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.deleteLogicalPort(port))
                .thenApply(optional -> optional
                        .map(value -> new GrpcDeleteOperationResponse(value.getReplyStatus() == 0))
                        .orElseThrow(() -> new GrpcException(format("Could not delete logical port %d for switch %s",
                                port, switchAddress))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    public CompletableFuture<EnableLogMessagesResponse> enableLogMessages(String switchAddress,
                                                                          LogMessagesDto logMessagesDto) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.enableLogMessages(logMessagesDto))
                .thenApply(optional -> optional
                        .map(value -> new EnableLogMessagesResponse(logMessagesDto.getState()))
                        .orElseThrow(() -> new GrpcException(format("Could not set log messages to status: %s",
                                logMessagesDto.getState().toString()))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    public CompletableFuture<EnableLogMessagesResponse> enableLogOferror(String switchAddress,
                                                                         LogOferrorsDto logOferrorsDto) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.enableLogOferrors(logOferrorsDto))
                .thenApply(optional -> optional
                        .map(value -> new EnableLogMessagesResponse(logOferrorsDto.getState()))
                        .orElseThrow(() -> new GrpcException(format("Could not set log OF errors to status: %s",
                                logOferrorsDto.getState().toString()))))
                .whenComplete((e, ex) -> sender.shutdown());

    }

    public CompletableFuture<RemoteLogServer> showConfigRemoteLogServer(String switchAddress) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.showConfigRemoteLogServer())
                .thenApply(optional -> optional
                        .map(mapper::toRemoteLogServer)
                        .orElseThrow(() -> new GrpcException(format("Could not to get remote log server for switch: %s",
                                switchAddress))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    public CompletableFuture<RemoteLogServer> setConfigRemoteLogServer(
            String switchAddress, RemoteLogServerDto remoteLogServerDto) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.setConfigRemoteLogServer(remoteLogServerDto))
                .thenCompose(e -> sender.showConfigRemoteLogServer())
                .thenApply(optional -> optional
                        .map(mapper::toRemoteLogServer)
                        .orElseThrow(() -> new GrpcException(format("Could not set remote log server for switch %s",
                                switchAddress))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    public CompletableFuture<GrpcDeleteOperationResponse> deleteConfigRemoteLogServer(
            String switchAddress, RemoteLogServerDto remoteLogServerDto) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.deleteConfigRemoteLogServer(remoteLogServerDto))
                .thenApply(optional -> optional
                        .map(value -> new GrpcDeleteOperationResponse(value.getReplyStatus() == 0))
                        .orElseThrow(() -> new GrpcException(format("Could not delete remote log server for switch %s",
                                switchAddress))))
                .whenComplete((e, ex) -> sender.shutdown());
    }

    public CompletableFuture<PortConfigSetupResponse> setPortConfig(
            String switchAddress, Integer portNumber, PortConfigDto portConfigDto) {
        GrpcSession sender = new GrpcSession(switchAddress);
        return sender.login(name, name)
                .thenCompose(e -> sender.setPortConfig(portNumber, portConfigDto))
                .thenApply(optional -> optional
                        .map(value -> new PortConfigSetupResponse(value.getReplyStatus() == 0))
                        .orElseThrow(() ->
                                new GrpcException(format("Could not setup port №%d configuration for switch %s",
                                        portNumber, switchAddress))))
                .whenComplete((e, ex) -> sender.shutdown());
    }
}
