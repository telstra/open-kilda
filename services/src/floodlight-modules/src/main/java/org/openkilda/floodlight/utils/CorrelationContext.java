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

package org.openkilda.floodlight.utils;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.slf4j.MDC;

import java.io.Closeable;
import java.util.Optional;

/**
 * Associates a given correlationId with the current execution thread.
 * <p>
 * This also passes the correlationId to logger's MDC.
 */
public final class CorrelationContext {

    private static final InheritableThreadLocal<String> ID = new InheritableThreadLocal<>();

    public static String getId() {
        return Optional.ofNullable(ID.get()).orElse(DEFAULT_CORRELATION_ID);
    }

    public static CorrelationContextClosable create(String correlationId) {
        String currentId = ID.get();

        ID.set(correlationId);
        MDC.put(CORRELATION_ID, correlationId);

        return new CorrelationContextClosable(currentId);
    }

    public static class CorrelationContextClosable implements Closeable {

        private final String previousCorrelationId;

        CorrelationContextClosable(String previousCorrelationId) {
            this.previousCorrelationId = previousCorrelationId;
        }

        public void close() {
            if (previousCorrelationId != null) {
                ID.set(previousCorrelationId);
                MDC.put(CORRELATION_ID, previousCorrelationId);
            } else {
                try {
                    MDC.remove(CORRELATION_ID);
                } finally {
                    ID.remove();
                }
            }
        }
    }
}
