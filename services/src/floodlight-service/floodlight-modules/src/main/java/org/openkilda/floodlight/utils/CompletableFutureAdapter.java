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

package org.openkilda.floodlight.utils;

import org.openkilda.messaging.MessageContext;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

public class CompletableFutureAdapter<T> extends CompletableFuture<T> {
    private final ListenableFuture<T> listenableFuture;

    public CompletableFutureAdapter(MessageContext context, ListenableFuture<T> listenableFuture) {
        this.listenableFuture = listenableFuture;

        Futures.addCallback(listenableFuture, new FutureCallback<T>() {
            @Override
            public void onSuccess(@Nullable T result) {
                try (CorrelationContext.CorrelationContextClosable closable = CorrelationContext.create(
                        context.getCorrelationId())) {
                    complete(result);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                try (CorrelationContext.CorrelationContextClosable closable = CorrelationContext.create(
                        context.getCorrelationId())) {
                    completeExceptionally(t);
                }
            }
        });
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        super.cancel(mayInterruptIfRunning);
        return listenableFuture.cancel(mayInterruptIfRunning);
    }
}
