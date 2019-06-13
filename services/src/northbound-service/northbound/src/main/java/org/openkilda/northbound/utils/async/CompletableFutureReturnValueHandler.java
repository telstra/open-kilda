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

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.support.AsyncHandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Handler that transforms CompletableFuture to DeferredResult in order to be able properly initiate/handle
 * errors and timeouts from both {@link DeferredResult} and {@link CompletableFuture} sides.
 */
public class CompletableFutureReturnValueHandler implements AsyncHandlerMethodReturnValueHandler {

    @Override
    public boolean isAsyncReturnValue(Object returnValue, MethodParameter returnType) {
        return returnValue != null && supportsReturnType(returnType);
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return CompletableFuture.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public void handleReturnValue(Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest) throws Exception {
        if (returnValue != null) {
            CompletableFuture<?> future = (CompletableFuture<?>) returnValue;

            ControlledDeferredResult<?> result = new ControlledDeferredResult<>(future, RequestCorrelationId.getId());
            WebAsyncUtils.getAsyncManager(webRequest)
                    .startDeferredResultProcessing(result, mavContainer);
        } else {
            mavContainer.setRequestHandled(true);
        }
    }

    /**
     * Implementation of {@link DeferredResult} that binds it with {@link CompletableFuture}.
     * @param <T> expected result type.
     */
    private final class ControlledDeferredResult<T> extends DeferredResult<T> {
        private final String correlationId;

        private ControlledDeferredResult(CompletableFuture<T> future, String correlationId) {
            this.correlationId = correlationId;

            future.whenComplete((result, error) -> {
                if (error != null) {
                    setErrorResult(error);
                } else {
                    setResult(result);
                }
            });

            defineTimeoutHandler(future);
        }

        private void defineTimeoutHandler(CompletableFuture<T> future) {
            onTimeout(() -> {
                MessageException errorMessage = new MessageException(correlationId, System.currentTimeMillis(),
                        ErrorType.OPERATION_TIMED_OUT, "No response received", "Timeout exceeded");
                setErrorResult(errorMessage);
                future.completeExceptionally(new TimeoutException());
            });
        }

    }
}
