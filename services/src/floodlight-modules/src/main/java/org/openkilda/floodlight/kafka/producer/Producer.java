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

import org.openkilda.floodlight.kafka.KafkaProducerConfig;
import org.openkilda.messaging.Message;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Producer {

    private static final Logger logger = LoggerFactory.getLogger(Producer.class);

    private final org.apache.kafka.clients.producer.Producer producer;
    private final Map<String, AbstractWorker> workersMap = new HashMap<>();

    public Producer(KafkaProducerConfig kafkaConfig) {
        this(new KafkaProducer<>(kafkaConfig.createKafkaProducerProperties()));
    }

    Producer(org.apache.kafka.clients.producer.Producer producer) {
        this.producer = producer;
    }

    /**
     * Enable guaranteed message order for topic.
     */
    public synchronized void enableGuaranteedOrder(String topic) {
        logger.debug("Enable predictable order for topic {}", topic);
        AbstractWorker worker = getWorker(topic);
        workersMap.put(topic, new OrderAwareWorker(worker));
    }

    /**
     * Disable guaranteed message order for topic.
     */
    public synchronized void disableGuaranteedOrder(String topic) {
        logger.debug(
                "Disable predictable order for topic {} (due to effect of transition period some future messages will "
                + "be forced to have predictable order)", topic);
        getWorker(topic).deactivate(1000);
    }

    /**
     * Disable guaranteed message order for topic, with defined transition period.
     */
    public synchronized void disableGuaranteedOrder(String topic, long transitionPeriod) {
        logger.debug(
                "Disable predictable order for topic {} (transition period {} ms)", topic, transitionPeriod);
        getWorker(topic).deactivate(transitionPeriod);
    }

    public void sendMessageAndTrack(String topic, Message message) {
        getWorker(topic).sendMessage(message, new SendStatusCallback(this, topic, message));
    }

    public SendStatus sendMessage(String topic, Message message) {
        return getWorker(topic).sendMessage(message, null);
    }

    private AbstractWorker getWorker(String topic) {
        AbstractWorker worker = workersMap.computeIfAbsent(
                topic, t -> new DefaultWorker(producer, t));
        if (!worker.isActive()) {
            worker = new DefaultWorker(producer, topic);
            workersMap.put(topic, worker);
        }
        return worker;
    }

    private void reportError(String topic, Message message, Exception exception) {
        logger.error(
                "Fail to send message(correlationId=\"{}\") in kafka topic={}: {}",
                message.getCorrelationId(), topic, exception.toString());
    }

    private static class SendStatusCallback implements Callback {
        private final Producer producer;
        private final String topic;
        private final Message message;

        SendStatusCallback(Producer producer, String topic, Message message) {
            this.producer = producer;
            this.topic = topic;
            this.message = message;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            String error = exception == null ? null : exception.toString();
            logger.debug("{}: {}, {}", this.getClass().getCanonicalName(), metadata, error);

            if (exception == null) {
                return;
            }
            producer.reportError(topic, message, exception);
        }
    }
}
