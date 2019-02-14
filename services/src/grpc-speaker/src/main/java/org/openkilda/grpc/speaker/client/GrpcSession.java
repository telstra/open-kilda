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

package org.openkilda.grpc.speaker.client;

import static com.google.common.base.Preconditions.checkNotNull;

import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.PortConfigDto;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.noviflow.AuthenticateUser;
import io.grpc.noviflow.CliReply;
import io.grpc.noviflow.LogMessages;
import io.grpc.noviflow.LogOferrors;
import io.grpc.noviflow.LogicalPort;
import io.grpc.noviflow.NoviFlowGrpcGrpc;
import io.grpc.noviflow.OnOff;
import io.grpc.noviflow.PortConfig;
import io.grpc.noviflow.PortMode;
import io.grpc.noviflow.PortPause;
import io.grpc.noviflow.PortSpeed;
import io.grpc.noviflow.RemoteLogServer;
import io.grpc.noviflow.ShowRemoteLogServer;
import io.grpc.noviflow.StatusSwitch;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The GRPC client session.
 */
@Slf4j
public class GrpcSession {
    private static final int PORT = 50051;

    private ManagedChannel channel;
    private NoviFlowGrpcGrpc.NoviFlowGrpcStub stub;
    private String address;

    public GrpcSession(String address) {
        this.address = address;
        this.channel = ManagedChannelBuilder.forAddress(address, PORT)
                .usePlaintext()
                .build();
        this.stub = NoviFlowGrpcGrpc.newStub(channel);
    }

    /**
     * Initiates async session shutdown.
     */
    public void shutdown() {
        log.debug("Perform messaging channel shutdown for switch {}", address);
        channel.shutdown();
    }

    /**
     * Performs switch login request.
     *
     * @param user the user.
     * @param pass the password.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<CliReply>> login(String user, String pass) {
        checkNotNull(user);
        checkNotNull(pass);

        AuthenticateUser authUser = AuthenticateUser.newBuilder()
                .setUsername(user)
                .setPassword(pass)
                .build();

        log.debug("Performs auth user request to switch {} with user {}", address, user);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();

        stub.setLoginDetails(authUser, observer);
        return observer.future;
    }

    /**
     * Performs switch status request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<StatusSwitch>> showSwitchStatus() {
        GrpcResponseObserver<StatusSwitch> observer = new GrpcResponseObserver<>();

        log.debug("Getting switch status for switch {}", address);

        stub.showStatusSwitch(StatusSwitch.newBuilder().build(), observer);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs set logical port config switch request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<CliReply>> setLogicalPort(LogicalPortDto port) {
        LogicalPort request = LogicalPort.newBuilder()
                .addAllPortno(port.getPortNumbers())
                .setLogicalportno(port.getLogicalPortNumber())
                .setName(port.getLogicalPortName())
                .build();

        log.debug("About to create logical port: {}", request);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();

        stub.setConfigLogicalPort(request, observer);
        return observer.future;
    }

    /**
     * Performs show switch logical port request.
     *
     * @param port the {@link LogicalPortDto} instance.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<LogicalPort>> showConfigLogicalPort(Integer port) {
        LogicalPort request = LogicalPort.newBuilder()
                .setLogicalportno(port)
                .build();

        log.debug("Reading logical port {} from the switch: {}", port, address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>();
        stub.showConfigLogicalPort(request, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs dump switch logical port request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<LogicalPort>> dumpLogicalPorts() {
        LogicalPort request = LogicalPort.newBuilder().build();

        log.debug("Getting all logical ports for switch: {}", address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>();
        stub.showConfigLogicalPort(request, observer);

        return observer.future;
    }

    public CompletableFuture<Optional<CliReply>> deleteLogicalPort(Integer port) {
        LogicalPort logicalPort = LogicalPort.newBuilder()
                .setLogicalportno(port)
                .build();

        log.debug("Deleting logical port for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();
        stub.delConfigLogicalPort(logicalPort, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    public CompletableFuture<Optional<CliReply>> enableLogMessages(LogMessagesDto logMessagesDto) {
        LogMessages logMessages = LogMessages.newBuilder()
                .setStatus(OnOff.forNumber(logMessagesDto.getState().getNumber()))
                .build();

        log.debug("Change enabling status of log messages for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();
        stub.setLogMessages(logMessages, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    public CompletableFuture<Optional<CliReply>> enableLogOferrors(LogOferrorsDto logOferrorsDto) {
        LogOferrors logOferrors = LogOferrors.newBuilder()
                .setStatus(OnOff.forNumber(logOferrorsDto.getState().getNumber()))
                .build();

        log.debug("Change enabling status of log OF errors for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();
        stub.setLogOferrors(logOferrors, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    public CompletableFuture<Optional<RemoteLogServer>> showConfigRemoteLogServer() {
        ShowRemoteLogServer showRemoteLogServer = ShowRemoteLogServer.newBuilder().build();

        log.debug("Get remote log server for switch {}", address);

        GrpcResponseObserver<RemoteLogServer> observer = new GrpcResponseObserver<>();
        stub.showConfigRemoteLogServer(showRemoteLogServer, observer);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    public CompletableFuture<Optional<CliReply>> setConfigRemoteLogServer(RemoteLogServerDto remoteServer) {
        RemoteLogServer logServer = RemoteLogServer.newBuilder()
                .setIpaddr(remoteServer.getIpAddress())
                .setPort(remoteServer.getPort())
                .build();

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();
        log.debug("Set remote log server for switch {}", address);
        stub.setConfigRemoteLogServer(logServer, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    public CompletableFuture<Optional<CliReply>> deleteConfigRemoteLogServer(RemoteLogServerDto remoteServer) {
        RemoteLogServer logServer = RemoteLogServer.newBuilder()
                .setIpaddr(remoteServer.getIpAddress())
                .setPort(remoteServer.getPort())
                .build();

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();
        log.debug("Delete remote log server for switch {}", address);
        stub.delConfigRemoteLogServer(logServer, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    public CompletableFuture<Optional<CliReply>> setPortConfig(Integer portNumber, PortConfigDto config) {
        PortConfig portConfig = PortConfig.newBuilder()
                .setPortno(portNumber)
                .setQueueid(config.getQueueId())
                .setMinrate(config.getMinRate())
                .setMaxrate(config.getMaxRate())
                .setWeight(config.getWeight())
                .setNativevid(config.getNativeVId())
                .setSpeed(PortSpeed.forNumber(config.getSpeed().getNumber()))
                .setMode(PortMode.forNumber(config.getMode().getNumber()))
                .setPause(PortPause.forNumber(config.getPause().getNumber()))
                .setAutoneg(OnOff.forNumber(config.getAutoneg().getNumber()))
                .setNopacketin(OnOff.forNumber(config.getNoPacketIn().getNumber()))
                .setPortdown(OnOff.forNumber(config.getPortDown().getNumber()))
                .setTrunk(OnOff.forNumber(config.getTrunk().getNumber()))
                .build();

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();
        log.debug("Set port {} configuration for switch {}", portNumber, address);

        stub.setConfigPort(portConfig, observer);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }


}
