/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.share.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.FailableConsumer;

import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
public class CarrierContext<T> {
    private final Deque<T> queue = new ArrayDeque<>();

    /**
     * Access context value.
     */
    public T getContext() {
        T value = queue.peek();
        if (value == null) {
            throw new IllegalStateException("Attempt to access carrier context without defining it's value in advance");
        }
        return value;
    }

    /**
     * Add new entry into queue.
     */
    public void apply(T payload, FailableConsumer<T, Exception> handler) {
        try {
            applyUnsafe(payload, handler);
        } catch (Exception e) {
            throwUnexpectedException(e);
        }
    }

    /**
     * Add new entry into queue, can throw exceptions.
     */
    public void applyUnsafe(T payload, FailableConsumer<T, Exception> handler) throws Exception {
        queue.push(payload);
        log.trace("Set carrier context to: {}", payload);
        try {
            handler.accept(payload);
        } finally {
            queue.pop();
            log.trace("Reset carrier context to: {}", queue.peek());
        }
    }

    public void throwUnexpectedException(Exception cause) {
        throw new IllegalStateException("Got unexpected exception", cause);
    }
}
