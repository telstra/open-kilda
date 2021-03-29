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

package org.openkilda.bluegreen.kafka;

import java.util.Map;

public final class Utils {
    private Utils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Kafka message header to specify message version.
     */
    public static final String MESSAGE_VERSION_HEADER = "kafka.message.version.header";
    /**
     * Property name for Kafka consumer to specify component name for consumer interceptor.
     */
    public static final String CONSUMER_COMPONENT_NAME_PROPERTY = "kafka.consumer.messaging.component.name.property";
    /**
     * Property name for Kafka consumer to specify run ID for consumer interceptor.
     */
    public static final String CONSUMER_RUN_ID_PROPERTY = "kafka.consumer.messaging.run.id.property";
    /**
     * Property name for Kafka consumer to specify zookeeper connection string.
     */
    public static final String CONSUMER_ZOOKEEPER_CONNECTION_STRING_PROPERTY =
            "kafka.consumer.messaging.zookeeper.connecting.string.property";

    /**
     * Property name for Kafka consumer to specify zookeeper reconnection delay interval.
     */
    public static final String CONSUMER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY =
            "kafka.consumer.messaging.zookeeper.reconnection.delay.property";

    /**
     * Property name for Kafka producer to specify component name for producer interceptor.
     */
    public static final String PRODUCER_COMPONENT_NAME_PROPERTY = "kafka.producer.messaging.component.name.property";
    /**
     * Property name for Kafka producer to specify run ID for producer interceptor.
     */
    public static final String PRODUCER_RUN_ID_PROPERTY = "kafka.producer.messaging.run.id.property";
    /**
     * Property name for Kafka producer to specify zookeeper connection string.
     */
    public static final String PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY =
            "kafka.producer.messaging.zookeeper.connecting.string.property";
    /**
     * Property name for Kafka producer to specify zookeeper reconnection delay interval.
     */
    public static final String PRODUCER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY =
            "kafka.producer.messaging.zookeeper.reconnection.delay.property";

    /**
     * Returns value from map by key, throws exception otherwise.
     *
     * @param map map with keys and values
     * @param key key
     * @param clazz value will be cast to this class
     * @return value cast to the clazz
     */
    public static <T> T getValue(Map<String, ?> map, String key, Class<T> clazz) {
        if (map.containsKey(key)) {
            return clazz.cast(map.get(key));
        } else {
            throw new IllegalArgumentException(String.format("Missed property %s in map %s", key, map));
        }
    }
}
