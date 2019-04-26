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

package org.openkilda.northbound.utils.async;

import org.openkilda.messaging.info.InfoData;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Common utility class for processing async requests/responses.
 */
public final class AsyncUtils {

    /**
     * Returns a new CompletionStage that, when this stage completes normally, is executed with this stage's collected
     * responses.
     * @param responses list of async responses.
     * @param responseType expected response type.
     * @param <T> type.
     * @return the new CompletionStage.
     */
    public static <T> CompletableFuture<List<T>> collectResponses(List<CompletableFuture<?>> responses,
                                                                  Class<T> responseType) {
        return CompletableFuture.allOf(responses.toArray(new CompletableFuture[] {}))
                .thenApply(done ->
                        responses.stream()
                                .map(request -> request.getNow(null))
                                .filter(Objects::nonNull)
                                .map(responseType::cast)
                                .collect(Collectors.toList()));
    }

    /**
     * Returns a new CompletionStage that, when this stage completes normally, is executed with this stage's collected
     * lists of chunked responses.
     * @param responses list of chunked async responses.
     * @param responseType expected response type.
     * @param <T> type.
     * @return the new CompletionStage.
     */
    public static <T> CompletableFuture<List<T>> collectChunkedResponses(
            List<CompletableFuture<List<InfoData>>> responses, Class<T> responseType) {
        return CompletableFuture.allOf(responses.toArray(new CompletableFuture[] {}))
                .thenApply(done ->
                        responses.stream()
                                .map(request -> request.getNow(null))
                                .flatMap(Collection::stream)
                                .filter(Objects::nonNull)
                                .map(responseType::cast)
                                .collect(Collectors.toList()));
    }

    private AsyncUtils() {
    }
}
