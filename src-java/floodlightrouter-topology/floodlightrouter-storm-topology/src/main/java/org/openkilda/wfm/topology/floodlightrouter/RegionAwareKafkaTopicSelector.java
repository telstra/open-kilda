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

package org.openkilda.wfm.topology.floodlightrouter;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.selector.KafkaTopicSelector;
import org.apache.storm.tuple.Tuple;

@Slf4j
public class RegionAwareKafkaTopicSelector implements KafkaTopicSelector {
    public static final String FIELD_ID_TOPIC = "topic";
    public static final String FIELD_ID_REGION = "region";

    @Override
    public String getTopic(Tuple tuple) {
        String topic;
        String region;
        try {
            topic = tuple.getStringByField(FIELD_ID_TOPIC);
            region = tuple.getStringByField(FIELD_ID_REGION);
        } catch (IndexOutOfBoundsException | ClassCastException e) {
            reportError(tuple, "Can't extract topic and/or region names: {}", e);
            return null;
        }

        if (topic == null) {
            reportError(tuple, "target topic is null");
            return null;
        }

        return formatTopicName(topic, region);
    }

    /**
     * Create region specific kafka-topic name or generic topic name if region is null.
     */
    public static String formatTopicName(String topic, String region) {
        if (Strings.isNullOrEmpty(region)) {
            return topic;
        }
        return String.format("%s_%s", topic, region);
    }

    static void reportError(Tuple tuple, String details, Object... arguments) {
        String source = tuple.getSourceComponent();
        String stream = tuple.getSourceStreamId();
        String message = String.format("Invalid %s(%s) tuple - %s", source, stream, details);
        log.error(message, arguments);
    }
}
