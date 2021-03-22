/* Copyright 2020 Telstra Open Source
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

import static org.openkilda.grpc.speaker.client.GrpcOperation.DELETE_CONFIG_REMOTE_LOG_SERVER;
import static org.openkilda.grpc.speaker.client.GrpcOperation.DELETE_LOGICAL_PORT;
import static org.openkilda.grpc.speaker.client.GrpcOperation.DUMP_LOGICAL_PORTS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.GET_PACKET_IN_OUT_STATS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.LOGIN;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_CONFIG_LICENSE;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_CONFIG_REMOTE_LOG_SERVER;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_LOGICAL_PORT;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_LOG_MESSAGES_STATUS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_LOG_OF_ERRORS_STATUS;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SET_PORT_CONFIG;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SHOW_CONFIG_LOGICAL_PORT;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SHOW_CONFIG_REMOTE_LOG_SERVER;
import static org.openkilda.grpc.speaker.client.GrpcOperation.SHOW_SWITCH_STATUS;

import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;
import org.openkilda.grpc.speaker.mapper.NoviflowResponseMapper;
import org.openkilda.grpc.speaker.model.ErrorCode;
import org.openkilda.grpc.speaker.model.LicenseDto;
import org.openkilda.grpc.speaker.model.LogMessagesDto;
import org.openkilda.grpc.speaker.model.LogOferrorsDto;
import org.openkilda.grpc.speaker.model.LogicalPortDto;
import org.openkilda.grpc.speaker.model.PortConfigDto;
import org.openkilda.grpc.speaker.model.RemoteLogServerDto;
import org.openkilda.messaging.error.ErrorType;

import com.google.common.net.InetAddresses;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.noviflow.AuthenticateUser;
import io.grpc.noviflow.CliReply;
import io.grpc.noviflow.License;
import io.grpc.noviflow.LogMessages;
import io.grpc.noviflow.LogOferrors;
import io.grpc.noviflow.LogicalPort;
import io.grpc.noviflow.NoviFlowGrpcGrpc;
import io.grpc.noviflow.OnOff;
import io.grpc.noviflow.PacketInOutStats;
import io.grpc.noviflow.PortConfig;
import io.grpc.noviflow.PortMode;
import io.grpc.noviflow.PortPause;
import io.grpc.noviflow.PortSpeed;
import io.grpc.noviflow.RemoteLogServer;
import io.grpc.noviflow.ShowPacketInOutStats;
import io.grpc.noviflow.ShowRemoteLogServer;
import io.grpc.noviflow.StatusSwitch;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The GRPC client session.
 */
@Slf4j
public class GrpcSession implements Closeable {
    private static final int PORT = 50051;

    private final NoviflowResponseMapper mapper;

    private final ManagedChannel channel;
    private final NoviFlowGrpcGrpc.NoviFlowGrpcStub stub;
    private final String address;

    private CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

    public GrpcSession(NoviflowResponseMapper mapper, String address) {
        this.mapper = mapper;
        this.address = address;
        this.channel = makeChannel(address);
        this.stub = NoviFlowGrpcGrpc.newStub(channel);
    }

    /**
     * Plan GRPC channel close.
     */
    public void close() {
        extendChain(() -> {
            log.debug("Perform messaging channel shutdown for switch {}", address);
            channel.shutdownNow();
        }, CompletableFuture.completedFuture(null));
    }

    /**
     * Performs switch login request.
     *
     * @param user the user.
     * @param pass the password.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<CliReply>> login(String user, String pass) {
        Objects.requireNonNull(user, "User name must not be null");
        Objects.requireNonNull(pass, "Password must not be null");

        AuthenticateUser authUser = AuthenticateUser.newBuilder()
                .setUsername(user)
                .setPassword(pass)
                .build();

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, LOGIN);
        extendChain(() -> {
            log.debug("Performs auth user request to switch {} with user {}", address, user);
            stub.setLoginDetails(authUser, observer);
        }, observer.future);
        return observer.future;
    }

    /**
     * Performs switch status request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<StatusSwitch>> showSwitchStatus() {
        log.info("Getting switch status for switch {}", address);

        GrpcResponseObserver<StatusSwitch> observer = new GrpcResponseObserver<>(address, SHOW_SWITCH_STATUS);
        extendChain(() -> stub.showStatusSwitch(StatusSwitch.newBuilder().build(), observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs set logical port config switch request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<CliReply>> setLogicalPort(LogicalPortDto port) {
        Objects.requireNonNull(port.getLogicalPortNumber(), "Logical port number must not be null");
        Objects.requireNonNull(port.getPortNumbers(), "Port number must not be null");
        Objects.requireNonNull(port.getType(), "Logical port type must not be null");

        LogicalPort request = LogicalPort.newBuilder()
                .addAllPortno(port.getPortNumbers())
                .setLogicalportno(port.getLogicalPortNumber())
                .setLogicalporttype(mapper.map(port.getType()))
                .build();

        log.info(
                "About to create logical port={} type={} target-physical-port={} on switch {}",
                port.getLogicalPortNumber(), port.getType(), port.getPortNumbers(), address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_LOGICAL_PORT);
        extendChain(() -> stub.setConfigLogicalPort(request, observer), observer.future);
        return observer.future;
    }

    /**
     * Performs show switch logical port request.
     *
     * @param port the {@link LogicalPortDto} instance.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<LogicalPort>> showConfigLogicalPort(Integer port) {
        Objects.requireNonNull(port, "Port must not be null");
        LogicalPort request = LogicalPort.newBuilder()
                .setLogicalportno(port)
                .build();

        log.info("Reading logical port {} details from the switch {}", port, address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>(address, SHOW_CONFIG_LOGICAL_PORT);
        extendChain(() -> stub.showConfigLogicalPort(request, observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs dump switch logical port request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<List<LogicalPort>> dumpLogicalPorts() {
        log.info("Getting all logical ports on switch {}", address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>(address, DUMP_LOGICAL_PORTS);
        extendChain(() -> {
            LogicalPort request = LogicalPort.newBuilder().build();
            stub.showConfigLogicalPort(request, observer);
        }, observer.future);
        return observer.future;
    }

    /**
     * Deletes a logical port.
     *
     * @param port a logical port number.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> deleteLogicalPort(Integer port) {
        Objects.requireNonNull(port, "Port must not be null");

        LogicalPort logicalPort = LogicalPort.newBuilder()
                .setLogicalportno(port)
                .build();

        log.info("Deleting logical port {} on switch {}", port, address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, DELETE_LOGICAL_PORT);
        extendChain(() -> stub.delConfigLogicalPort(logicalPort, observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Set log messages status on switch.
     *
     * @param logMessagesDto a log messages configuration.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setLogMessagesStatus(LogMessagesDto logMessagesDto) {
        log.info("Change enabling status of log messages for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_LOG_MESSAGES_STATUS);
        LogMessages logMessages = LogMessages.newBuilder()
                .setStatus(OnOff.forNumber(logMessagesDto.getState().getNumber()))
                .build();
        extendChain(() -> stub.setLogMessages(logMessages, observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Set log oferrors status on a switch.
     *
     * @param logOferrorsDto log oferrors data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setLogOfErrorsStatus(LogOferrorsDto logOferrorsDto) {
        log.info("Change enabling status of log OF errors for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_LOG_OF_ERRORS_STATUS);
        LogOferrors logOferrors = LogOferrors.newBuilder()
                .setStatus(OnOff.forNumber(logOferrorsDto.getState().getNumber()))
                .build();
        extendChain(() -> stub.setLogOferrors(logOferrors, observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Shows config of remote log server.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<RemoteLogServer>> showConfigRemoteLogServer() {
        ShowRemoteLogServer showRemoteLogServer = ShowRemoteLogServer.newBuilder().build();

        log.info("Get remote log server for switch {}", address);

        GrpcResponseObserver<RemoteLogServer> observer = new GrpcResponseObserver<>(
                address, SHOW_CONFIG_REMOTE_LOG_SERVER);
        extendChain(() -> stub.showConfigRemoteLogServer(showRemoteLogServer, observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Sets a config of remote log server on a switch.
     *
     * @param remoteServer remote log server data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setConfigRemoteLogServer(RemoteLogServerDto remoteServer) {
        Objects.requireNonNull(remoteServer.getIpAddress(), "IP Address must not be null");
        Objects.requireNonNull(remoteServer.getPort(), "Port must not be null");

        log.info("Set remote log server for switch {}", address);
        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_CONFIG_REMOTE_LOG_SERVER);

        RemoteLogServer logServer = RemoteLogServer.newBuilder()
                .setIpaddr(remoteServer.getIpAddress())
                .setPort(remoteServer.getPort())
                .build();
        extendChain(() -> stub.setConfigRemoteLogServer(logServer, observer), observer.future);

        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Deletes a remote log server configuration.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> deleteConfigRemoteLogServer() {
        log.info("Delete remote log server for switch {}", address);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, DELETE_CONFIG_REMOTE_LOG_SERVER);
        RemoteLogServer logServer = RemoteLogServer.newBuilder().build();
        extendChain(() -> stub.delConfigRemoteLogServer(logServer, observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Sets a port configuration.
     *
     * @param portNumber a port number.
     * @param config a port configuration data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setPortConfig(Integer portNumber, PortConfigDto config) {
        log.info("Set port {} configuration for switch {}", portNumber, address);

        PortConfig.Builder builder = PortConfig.newBuilder()
                .setPortno(portNumber);
        if (config.getQueueId() != null) {
            builder.setQueueid(config.getQueueId());
        }
        if (config.getMinRate() != null) {
            builder.setMinrate(config.getMinRate());
        }
        if (config.getMaxRate() != null) {
            builder.setMaxrate(config.getMaxRate());
        }
        if (config.getWeight() != null) {
            builder.setWeight(config.getWeight());
        }
        if (config.getNativeVId() != null) {
            builder.setNativevid(config.getNativeVId());
        }
        if (config.getSpeed() != null) {
            builder.setSpeed(PortSpeed.forNumber(config.getSpeed().getNumber()));
        }
        if (config.getMode() != null) {
            builder.setMode(PortMode.forNumber(config.getMode().getNumber()));
        }
        if (config.getPause() != null) {
            builder.setPause(PortPause.forNumber(config.getPause().getNumber()));
        }
        if (config.getAutoneg() != null) {
            builder.setAutoneg(OnOff.forNumber(config.getAutoneg().getNumber()));
        }
        if (config.getNoPacketIn() != null) {
            builder.setNopacketin(OnOff.forNumber(config.getNoPacketIn().getNumber()));
        }
        if (config.getPortDown() != null) {
            builder.setPortdown(OnOff.forNumber(config.getPortDown().getNumber()));
        }
        if (config.getTrunk() != null) {
            builder.setTrunk(OnOff.forNumber(config.getTrunk().getNumber()));
        }

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_PORT_CONFIG);
        extendChain(() -> stub.setConfigPort(builder.build(), observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Sets a license configuration.
     *
     * @param licenseDto a license data.
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<CliReply>> setConfigLicense(LicenseDto licenseDto) {
        log.info("Set license for switch {}", address);

        License.Builder licenseBuilder = License.newBuilder();
        if (licenseDto.getLicenseFileName() != null) {
            licenseBuilder.setLicensefile(licenseDto.getLicenseFileName());
        }
        if (licenseDto.getLicenseData() != null) {
            licenseBuilder.setLicensedata(licenseDto.getLicenseData());
        }

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>(address, SET_CONFIG_LICENSE);
        extendChain(() -> stub.setConfigLicense(licenseBuilder.build(), observer), observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    /**
     * Performs packet in out stats request.
     *
     * @return {@link CompletableFuture} with operation result.
     */
    public CompletableFuture<Optional<PacketInOutStats>> getPacketInOutStats() {
        log.info("Getting packet in/out stats for switch {}", address);

        GrpcResponseObserver<PacketInOutStats> observer = new GrpcResponseObserver<>(address, GET_PACKET_IN_OUT_STATS);
        extendChain(
                () -> stub.showStatsPacketInOut(ShowPacketInOutStats.newBuilder().build(), observer),
                observer.future);
        return observer.future
                .thenApply(responses -> responses.stream().findFirst());
    }

    private synchronized void extendChain(Runnable action, CompletableFuture<?> operation) {
        chain = CompletableFuture.allOf(
                chain.whenComplete((v, e) -> action.run()), operation)
                .handle((dummy, e) -> {
                    // clean exceptional status
                    return null;
                });
    }

    private static ManagedChannel makeChannel(String address) {
        return ManagedChannelBuilder.forAddress(verifyHostAddress(address), PORT)
                .usePlaintext()
                .build();
    }

    private static String verifyHostAddress(String address) {
        if (!InetAddresses.isInetAddress(address) && !InetAddresses.isUriInetAddress(address)) {
            throw new GrpcRequestFailureException(ErrorCode.ERRNO_23.getCode(), ErrorCode.ERRNO_23.getMessage(),
                    ErrorType.REQUEST_INVALID);
        }
        return address;
    }
}
