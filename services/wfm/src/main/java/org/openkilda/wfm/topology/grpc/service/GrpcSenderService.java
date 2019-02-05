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

package org.openkilda.wfm.topology.grpc.service;

import static java.lang.String.format;

import org.openkilda.wfm.topology.grpc.exception.GrpcRequestFailureException;
import org.openkilda.wfm.topology.grpc.model.BaseGrpcRequest;
import org.openkilda.wfm.topology.grpc.model.CreatePortRequest;
import org.openkilda.wfm.topology.grpc.model.GetAllPortsRequest;

import io.grpc.ManagedChannel;
import io.grpc.noviflow.AuthenticateUser;
import io.grpc.noviflow.CliReply;
import io.grpc.noviflow.LogicalPort;
import io.grpc.noviflow.NoviFlowGrpcGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Performs gRPC calls.
 */
@Slf4j
public class GrpcSenderService {

    private NoviFlowGrpcGrpc.NoviFlowGrpcStub stub;

    public GrpcSenderService(ManagedChannel channel) {
        this.stub = NoviFlowGrpcGrpc.newStub(channel);
    }

    /**
     * Create logical port.
     * @param request request data.
     * @return type void, but wrapped into {@link CompletableFuture}, so we can track the execution.
     */
    public CompletableFuture<Void> createPort(CreatePortRequest request) {
        return setLoginDetails(request)
                .thenAccept(Void -> setLogicalPort(request.getPortNumber(), request.getLogicalPortNumber()))
                .thenAccept(Void -> checkPortExistence(request.getPortNumber(),
                        request.getLogicalPortNumber(), request.getAddress()));
    }

    /**
     * Get all available logical ports of the switch.
     * @param request request details.
     * @return list of logical ports wrapped into {@link CompletableFuture}.
     */
    public CompletableFuture<List<LogicalPort>> loadAllPorts(GetAllPortsRequest request) {
        return setLoginDetails(request)
                .thenApply(Void -> getAllPorts(request.getAddress()))
                .thenCompose(ports -> ports);
    }

    private CompletableFuture<?> setLoginDetails(BaseGrpcRequest request) {
        AuthenticateUser user = AuthenticateUser.newBuilder()
                .setUsername(request.getUsername())
                .setPassword(request.getPassword())
                .build();

        log.debug("Setting login details: {}", user);
        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();
        stub.setLoginDetails(user, observer);

        return observer.future;
    }

    private CompletableFuture<?> setLogicalPort(int port, int logicalPort) {
        LogicalPort request = LogicalPort.newBuilder()
                .addPortno(port)
                .setLogicalportno(logicalPort)
                .build();

        log.debug("About to create logical port: {}", request);

        GrpcResponseObserver<CliReply> observer = new GrpcResponseObserver<>();

        stub.setConfigLogicalPort(request, observer);
        return observer.future;
    }

    private CompletableFuture<?> checkPortExistence(int port, int logicalPort, String address) {
        LogicalPort request = LogicalPort.newBuilder()
                .setLogicalportno(logicalPort)
                .addPortno(port)
                .build();

        log.debug("Reading logical ports from the switch: {}", address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>();
        stub.showConfigLogicalPort(request, observer);

        return observer.future
                .thenApply(responses -> responses.stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                format("Port %s was not created for switch %s", logicalPort, address))));
    }

    private CompletableFuture<List<LogicalPort>> getAllPorts(String address) {
        LogicalPort request = LogicalPort.newBuilder()
                .setLogicalportno(0)
                .build();

        log.debug("Reading logical ports from the switch: {}", address);

        GrpcResponseObserver<LogicalPort> observer = new GrpcResponseObserver<>();
        stub.showConfigLogicalPort(request, observer);

        return observer.future;
    }

    private class GrpcResponseObserver<V> implements StreamObserver<V> {

        private CompletableFuture<List<V>> future = new CompletableFuture<>();
        private List<V> responses = new ArrayList<>();

        @Override
        public void onNext(V o) {
            if (o instanceof CliReply) {
                CliReply reply = (CliReply) o;
                if (reply.getReplyStatus() != 0) {
                    log.error("Response code of gRPC request is {}", reply.getReplyStatus());

                    future.completeExceptionally(new GrpcRequestFailureException(
                            format("Error response code: %s", reply.getReplyStatus())));
                }
            } else {
                responses.add(o);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            log.error("Error occurred during sending request", throwable);
            future.completeExceptionally(throwable);
        }

        @Override
        public void onCompleted() {
            log.debug("The request is completed. Received {} responses", responses.size());
            if (!future.isDone()) {
                future.complete(responses);
            }
        }
    }


}
