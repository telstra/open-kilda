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

package org.openkilda.floodlight.service.kafka;

import org.openkilda.messaging.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class KafkaProducerService implements IKafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);



    private int failedSendMessageCounter;
    private Producer<String, String> producer;
    private final Map<String, AbstractWorker> workersMap = new HashMap<>();
    private final ObjectMapper jsonObjectMapper = new ObjectMapper();

    @Override
    public void setup(FloodlightModuleContext moduleContext) {
        producer = moduleContext.getServiceImpl(KafkaUtilityService.class).makeProducer();
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
        produce(encode(topic, message), new SendStatusCallback(this, topic, message));
    }

    public void sendMessageAndTrack(String topic, String key, Message message) {
        produce(encode(topic, key, message), new SendStatusCallback(this, topic, message));
    }

    /**
     * Push message into kafka-broker and do not control operation result.
     *
     * <p>Caller can check operation result by himself using returned {@link SendStatusCallback} object.
     */
    public SendStatus sendMessage(String topic, Message message) {
        SendStatus sendStatus = produce(encode(topic, message), null);
        return sendStatus;
    }

    protected SendStatus produce(ProducerRecord<String, String> record, Callback callback) {
        logger.debug("Send kafka message: {} <== key:{} value:{}", record.topic(), record.key(), record.value());
        return getWorker(record.topic())
                .send(record, callback);
    }

    private ProducerRecord<String, String> encode(String topic, Message payload) {
        return encode(topic, null, payload);
    }

    private ProducerRecord<String, String> encode(String topic, String key, Message payload) {
        return new ProducerRecord<>(topic, key, encodeValue(payload));
    }

    private String encodeValue(Message message) {
        String encoded;
        try {
            encoded = jsonObjectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(String.format("Can not serialize message: %s", e.toString()), e);
        }

        return encoded;
    }

    /**
     * get failed sent messages count since last run.
     * @return
     */
    public int getFailedSendMessageCounter() {
        return failedSendMessageCounter;
    }

    private synchronized AbstractWorker getWorker(String topic) {
        AbstractWorker worker = workersMap.computeIfAbsent(
                topic, t -> new DefaultWorker(producer));
        if (!worker.isActive()) {
            worker = new DefaultWorker(producer);
            workersMap.put(topic, worker);
        }
        return worker;
    }

    private static class SendStatusCallback implements Callback {
        private final KafkaProducerService service;
        private final String topic;
        private final Message message;

        SendStatusCallback(KafkaProducerService service, String topic, Message message) {
            this.service = service;
            this.topic = topic;
            this.message = message;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            String error = exception == null ? null : exception.toString();
            logger.debug("{}: {}, {}", this.getClass().getCanonicalName(), metadata, error);

            if (exception == null) {
                service.failedSendMessageCounter = 0;
                return;
            }
            service.failedSendMessageCounter++;
            logger.error(
                    "Fail to send message(correlationId=\"{}\") in kafka topic={}: {}",
                    message.getCorrelationId(), topic, exception);
        }
    }
}
