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
import static java.lang.String.format;

import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;
import org.openkilda.grpc.speaker.model.LogicalPortDto;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.noviflow.AuthenticateUser;
import io.grpc.noviflow.CliReply;
import io.grpc.noviflow.LogicalPort;
import io.grpc.noviflow.NoviFlowGrpcGrpc;
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

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<CliReply>() {
            @Override
            public boolean validateResponse(CliReply reply) {
                if (reply.getReplyStatus() != 0) {
                    log.warn("Response code of gRPC request is {}", reply.getReplyStatus());

                    future.completeExceptionally(new GrpcRequestFailureException(
                            format("Error response code: %s", reply.getReplyStatus())));
                }
                return reply.getReplyStatus() == 0;
            }
        };

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
                .addPortno(port.getPortNumber())
                .setLogicalportno(port.getLogicalPortNumber())
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
    public CompletableFuture<Optional<LogicalPort>> showConfigLogicalPort(LogicalPortDto port) {
        LogicalPort request = LogicalPort.newBuilder()
                .setLogicalportno(port.getLogicalPortNumber())
                .addPortno(port.getPortNumber())
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
}
