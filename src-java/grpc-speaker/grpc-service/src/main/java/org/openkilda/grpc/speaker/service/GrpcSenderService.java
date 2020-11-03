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
import org.openkilda.grpc.speaker.model.LicenseDto;
import org.openkilda.grpc.speaker.model.LicenseResponse;
import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.PacketInOutStatsResponse;
import org.openkilda.grpc.speaker.model.PortConfigDto;
import org.openkilda.grpc.speaker.model.PortConfigSetupResponse;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${grpc.user}")
    private String name;

    @Value("${grpc.pass}")
    private String password;

    private final NoviflowResponseMapper mapper;

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
        try (GrpcSession session = makeSession(switchAddress)) {
            session.setLogicalPort(port);
            return session.showConfigLogicalPort(port.getLogicalPortNumber())
                    .thenApply(portOptional -> portOptional
                            .map(mapper::map)
                            .orElseThrow(() -> new GrpcException(format("Port %s was not created ", port))));
        }
    }

    /**
     * Dumps all available logical ports of the switch.
     *
     * @param switchAddress the switch address.
     * @return list of logical ports wrapped into {@link CompletableFuture}.
     */
    public CompletableFuture<List<LogicalPort>> dumpLogicalPorts(String switchAddress) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.dumpLogicalPorts()
                    .thenApply(ports -> ports.stream().map(mapper::map).collect(Collectors.toList()));
        }
    }

    /**
     * Gets switch status.
     *
     * @param switchAddress the switch address.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<SwitchInfoStatus> getSwitchStatus(String switchAddress) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.showSwitchStatus()
                    .thenApply(statusOptional -> statusOptional
                            .map(mapper::map)
                            .orElseThrow(() ->
                                    new GrpcException(format("Couldn't get status for switch %s", switchAddress))));
        }
    }

    /**
     * Gets logical port config.
     *
     * @param switchAddress the switch address.
     * @param port the port data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<LogicalPort> showConfigLogicalPort(String switchAddress, Integer port) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.showConfigLogicalPort(port)
                    .thenApply(statusOptional -> statusOptional
                            .map(mapper::map)
                            .orElseThrow(() -> new GrpcException(format("Couldn't get logical port %d for switch %s",
                                    port, switchAddress))));
        }
    }

    /**
     * Deletes logical port config.
     *
     * @param switchAddress the switch address.
     * @param port the port number.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<GrpcDeleteOperationResponse> deleteConfigLogicalPort(String switchAddress, Integer port) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.deleteLogicalPort(port)
                    .thenApply(optional -> optional
                            .map(value -> new GrpcDeleteOperationResponse(value.getReplyStatus() == 0))
                            .orElseThrow(() -> new GrpcException(
                                    format("Could not delete logical port %d for switch %s", port, switchAddress))));
        }
    }

    /**
     * Enable log messages.
     *
     * @param switchAddress a switch address.
     * @param logMessagesDto a log messages data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<EnableLogMessagesResponse> enableLogMessages(String switchAddress,
                                                                          LogMessagesDto logMessagesDto) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.setLogMessagesStatus(logMessagesDto)
                    .thenApply(optional -> optional
                            .map(value -> new EnableLogMessagesResponse(logMessagesDto.getState()))
                            .orElseThrow(() -> new GrpcException(format("Could not set log messages to status: %s",
                                    logMessagesDto.getState().toString()))));
        }
    }

    /**
     * Enable log oferrors.
     *
     * @param switchAddress a switch address.
     * @param logOferrorsDto a log oferros data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<EnableLogMessagesResponse> enableLogOfError(String switchAddress,
                                                                         LogOferrorsDto logOferrorsDto) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.setLogOfErrorsStatus(logOferrorsDto)
                    .thenApply(optional -> optional
                            .map(value -> new EnableLogMessagesResponse(logOferrorsDto.getState()))
                            .orElseThrow(() -> new GrpcException(format("Could not set log OF errors to status: %s",
                                    logOferrorsDto.getState().toString()))));
        }
    }

    /**
     * Gets a config of a remote log server.
     *
     * @param switchAddress a switch address.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<RemoteLogServer> showConfigRemoteLogServer(String switchAddress) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.showConfigRemoteLogServer()
                    .thenApply(optional -> optional
                            .map(mapper::map)
                            .orElseThrow(() -> new GrpcException(
                                    format("Could not to get remote log server for switch: %s", switchAddress))));
        }
    }

    /**
     * Set a config of a remote log server.
     *
     * @param switchAddress a switch address.
     * @param remoteLogServerDto a remote log server data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<RemoteLogServer> setConfigRemoteLogServer(
            String switchAddress, RemoteLogServerDto remoteLogServerDto) {
        try (GrpcSession session = makeSession(switchAddress)) {
            session.setConfigRemoteLogServer(remoteLogServerDto);
            return session.showConfigRemoteLogServer()
                    .thenApply(optional -> optional
                            .map(mapper::map)
                            .orElseThrow(() -> new GrpcException(format("Could not set remote log server for switch %s",
                                    switchAddress))));
        }
    }

    /**
     * Delete configuration of a remote log server.
     *
     * @param switchAddress a switch address.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<GrpcDeleteOperationResponse> deleteConfigRemoteLogServer(
            String switchAddress) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.deleteConfigRemoteLogServer()
                    .thenApply(optional -> optional
                            .map(value -> new GrpcDeleteOperationResponse(value.getReplyStatus() == 0))
                            .orElseThrow(() -> new GrpcException(
                                    format("Could not delete remote log server for switch %s", switchAddress))));
        }
    }

    /**
     * Sets a port configuration.
     *
     * @param switchAddress a switch address.
     * @param portNumber a port number.
     * @param portConfigDto a port configuration data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<PortConfigSetupResponse> setPortConfig(
            String switchAddress, Integer portNumber, PortConfigDto portConfigDto) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.setPortConfig(portNumber, portConfigDto)
                    .thenApply(optional -> optional
                            .map(value -> new PortConfigSetupResponse(value.getReplyStatus() == 0))
                            .orElseThrow(() ->
                                    new GrpcException(format("Could not setup port №%d configuration for switch %s",
                                            portNumber, switchAddress))));
        }
    }

    /**
     * Sets a config license.
     *
     * @param switchAddress switch address.
     * @param licenseDto a license data.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<LicenseResponse> setConfigLicense(String switchAddress, LicenseDto licenseDto) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.setConfigLicense(licenseDto)
                    .thenApply(optional -> optional
                            .map(value -> new LicenseResponse(value.getReplyStatus() == 0))
                            .orElseThrow(() ->
                                    new GrpcException(format("Could not setup license for switch %s", switchAddress))));
        }
    }

    /**
     * Gets packet in out stats.
     *
     * @param switchAddress switch address.
     * @return {@link CompletableFuture} with the execution result.
     */
    public CompletableFuture<PacketInOutStatsResponse> getPacketInOutStats(String switchAddress) {
        try (GrpcSession session = makeSession(switchAddress)) {
            return session.getPacketInOutStats()
                    .thenApply(statusOptional -> statusOptional
                            .map(mapper::map)
                            .orElseThrow(() ->
                                    new GrpcException(format(
                                            "Couldn't get packet in out stats for switch %s", switchAddress))));
        }
    }

    private GrpcSession makeSession(String address) {
        GrpcSession session = new GrpcSession(mapper, address);
        session.login(name, password);
        return session;
    }
}
