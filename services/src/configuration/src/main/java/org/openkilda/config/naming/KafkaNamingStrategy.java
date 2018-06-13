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

package org.openkilda.config.naming;

import static java.util.Objects.requireNonNull;

/**
 * This class provides name resolving methods for kafka entities.
 */
public class KafkaNamingStrategy extends PrefixBasedNamingStrategy {

    public KafkaNamingStrategy(String prefix) {
        super(prefix);
    }

    public String kafkaTopicName(String topic) {
        requireNonNull(topic, "topic cannot be null");
        return formatWithPrefix(topic);
    }

    public String kafkaConsumerGroupName(String consumerGroup) {
        requireNonNull(consumerGroup, "consumerGroup cannot be null");
        return formatWithPrefix(consumerGroup);
    }
}
