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

package org.openkilda.floodlight.kafka.producer;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWorker {
    private static final Logger log = LoggerFactory.getLogger(AbstractWorker.class);

    private final Producer<String, String> kafkaProducer;
    private final String topic;

    public AbstractWorker(Producer<String, String> kafkaProducer, String topic) {
        this.kafkaProducer = kafkaProducer;
        this.topic = topic;
    }

    /**
     * Serialize and send message into kafka topic.
     */
    public SendStatus sendMessage(Message payload, Callback callback) {
        log.debug("Send kafka message: {} <== {}", getTopic(), payload);
        String json = encode(payload);
        return send(json, callback);
    }

    protected abstract SendStatus send(String payload, Callback callback);

    protected String encode(Message message) {
        String encoded;
        try {
            encoded = Utils.MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(String.format("Can not serialize message: %s", e.toString()), e);
        }

        return encoded;
    }

    protected Producer<String, String> getKafkaProducer() {
        return kafkaProducer;
    }

    protected String getTopic() {
        return topic;
    }

    void deactivate(long transitionPeriod) {}

    boolean isActive() {
        return true;
    }
}
