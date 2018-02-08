/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.messaging;

public interface MessageConsumer<T> {
    /**
     * Kafka message queue poll timeout.
     */
    int POLL_TIMEOUT = 10 * 1000;

    /**
     * Kafka message queue poll pause.
     */
    int POLL_PAUSE = 100;

    /**
     * Polls Kafka message queue.
     *
     * @param correlationId correlation id
     * @return received message
     */
    T poll(final String correlationId);

    /**
     * Clears message queue.
     */
    void clear();
}
