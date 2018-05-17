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

package org.openkilda.northbound.utils;

import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import java.io.Closeable;
import java.util.Optional;

/**
 * Associates a given correlationId with the current execution thread.
 */
public final class RequestCorrelationId {

    private static final InheritableThreadLocal<String> ID = new InheritableThreadLocal<>();

    public static String getId() {
        return Optional.ofNullable(ID.get()).orElse(DEFAULT_CORRELATION_ID);
    }

    public static RequestCorrelationClosable create(String correlationId) {
        ID.set(correlationId);

        return new RequestCorrelationClosable();
    }

    public static class RequestCorrelationClosable implements Closeable {

        public void close() {
            ID.remove();
        }
    }
}
