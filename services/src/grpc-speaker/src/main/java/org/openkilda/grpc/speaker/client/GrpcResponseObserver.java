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

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class GrpcResponseObserver<V> implements StreamObserver<V> {

    protected CompletableFuture<List<V>> future = new CompletableFuture<>();
    protected List<V> responses = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onNext(V reply) {
        log.debug("Retrieved message: {} ", reply);

        if (validateResponse(reply)) {
            responses.add(reply);
        }
    }

    public boolean validateResponse(V reply) {
        return true;
    }

    @Override
    public void onError(Throwable throwable) {
        log.warn("Error occurred during sending request", throwable);
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
