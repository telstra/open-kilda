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

import org.openkilda.grpc.speaker.exception.GrpcRequestFailureException;
import org.openkilda.grpc.speaker.model.ErrorCode;

import io.grpc.noviflow.ResponseMarker;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class GrpcResponseObserver<V> implements StreamObserver<V> {

    private final String switchAddress;
    private final GrpcOperation operation;
    protected CompletableFuture<List<V>> future;
    protected List<V> responses;

    public GrpcResponseObserver(String switchAddress, GrpcOperation operation) {
        this.switchAddress = switchAddress;
        this.operation = operation;
        this.future = new CompletableFuture<>();
        this.responses = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public void onNext(V reply) {
        log.debug("Retrieved message: {} from switch {}", reply, switchAddress);

        if (validateResponse(reply)) {
            responses.add(reply);
        }
    }

    private boolean validateResponse(V reply) {
        if (reply instanceof ResponseMarker) {
            int replyStatus = ((ResponseMarker) reply).getReplyStatus();

            if (replyStatus != 0) {
                ErrorCode errorCode = ErrorCode.getByCode(replyStatus);
                log.warn("GRPC operation {} on switch {} failed. Response code of gRPC request is {}: {}",
                        operation, switchAddress, replyStatus, errorCode.getMessage());

                future.completeExceptionally(new GrpcRequestFailureException(replyStatus,
                        errorCode.getMessage()));
            }
            return replyStatus == 0;
        }
        return true;
    }

    @Override
    public void onError(Throwable throwable) {
        log.warn(String.format("Error occurred during sending of GRPC request %s on switch %s",
                operation, switchAddress), throwable);
        future.completeExceptionally(throwable);
    }

    @Override
    public void onCompleted() {
        log.debug("GRPC request {} is completed. Received {} responses from switch {}",
                operation, responses.size(), switchAddress);
        if (!future.isDone()) {
            future.complete(responses);
        }
    }
}
