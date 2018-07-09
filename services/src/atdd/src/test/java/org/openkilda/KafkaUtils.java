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

package org.openkilda;


import static java.lang.String.format;
import static org.openkilda.messaging.Destination.CTRL_CLIENT;
import static org.openkilda.messaging.Destination.WFM_CTRL;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.ctrl.CtrlRequest;
import org.openkilda.messaging.ctrl.CtrlResponse;
import org.openkilda.messaging.ctrl.RequestData;
import org.openkilda.messaging.ctrl.state.visitor.DumpStateManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.internals.NoOpConsumerRebalanceListener;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;


public class KafkaUtils {

    private KafkaParameters settings = new KafkaParameters();
    private final Properties connectDefaults;

    public KafkaUtils() throws IOException {
        connectDefaults = new Properties();
        connectDefaults.put("bootstrap.servers", settings.getBootstrapServers());
        connectDefaults.put("client.id", "ATDD");
        connectDefaults.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        connectDefaults.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    }

    public RecordMetadata postMessage(String topic, Message message)
            throws IOException, ExecutionException, InterruptedException {

        try (KafkaProducer<Object, Object> producer = new KafkaProducer<>(connectDefaults)) {
            String messageString = MAPPER.writeValueAsString(message);
            return producer.send(new ProducerRecord<>(topic, messageString)).get();
        } catch (JsonProcessingException exception) {
            System.out.println(format("Error during json serialization: %s",
                    exception.getMessage()));
            exception.printStackTrace();
            throw exception;
        } catch (InterruptedException | ExecutionException exception) {
            System.out.println(format("Error during KafkaProducer::send: %s",
                    exception.getMessage()));
            exception.printStackTrace();
            throw exception;
        }
    }

    public void pollMessage(String topic) {
        KafkaConsumer<String, String> consumer = createConsumer();
        consumer.subscribe(Collections.singletonList(topic));
        ConsumerRecords<String, String> records = consumer.poll(5000);
    }

    public KafkaConsumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ATDD");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");
        return new KafkaConsumer<>(props);
    }

    public DumpStateManager getStateDumpsFromBolts() {
        long timestamp = System.currentTimeMillis();
        String correlationId = format("atdd-%d", timestamp);
        CtrlRequest dumpRequest = new CtrlRequest("*", new RequestData("dump"), timestamp,
                correlationId, WFM_CTRL);
        try {
            RecordMetadata postedMessage = postMessage(settings.getControlTopic(), dumpRequest);
            KafkaConsumer<String, String> consumer = createConsumer();
            try {
                consumer.subscribe(Collections.singletonList(settings.getControlTopic()),
                        new NoOpConsumerRebalanceListener() {
                            @Override
                            public void onPartitionsAssigned(
                                    Collection<TopicPartition> partitions) {
                                System.out.println("Seek to offset: " + postedMessage.offset());
                                for (TopicPartition topicPartition : partitions) {
                                    consumer.seek(topicPartition, postedMessage.offset());
                                }
                            }
                        });

                List<CtrlResponse> buffer = new ArrayList<>();

                final int BOLT_COUNT = 4;
                final int NUMBER_OF_ATTEMPTS = 5;
                int attempt = 0;
                while (buffer.size() < BOLT_COUNT && attempt++ < NUMBER_OF_ATTEMPTS) {
                    for (ConsumerRecord<String, String> record : consumer.poll(1000)) {
                        System.out.println(
                                "Received message: (" + record.key() + ", " + record.value()
                                        + ") at offset " + record.offset());

                        Message message = MAPPER.readValue(record.value(), Message.class);
                        if (message.getDestination() == CTRL_CLIENT && message.getCorrelationId()
                                .equals(correlationId)) {
                            buffer.add((CtrlResponse) message);
                        }
                    }
                }
                return DumpStateManager.fromResponsesList(buffer);
            } finally {
                consumer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Sends a clearState ctrl request to the topology component.
     */
    public CtrlResponse clearTopologyComponentState(String topology, String componentId) {
        String ctrlRoute = format("%s/%s", topology, componentId);
        long timestamp = System.currentTimeMillis();
        String correlationId = format("atdd-%d", timestamp);

        CtrlRequest clearStateRequest = new CtrlRequest(ctrlRoute, new RequestData("clearState"), timestamp,
                correlationId, WFM_CTRL);
        try {
            RecordMetadata postedMessage = postMessage(settings.getControlTopic(), clearStateRequest);
            try (KafkaConsumer<String, String> consumer = createConsumer()) {
                consumer.subscribe(Collections.singletonList(settings.getControlTopic()),
                        new NoOpConsumerRebalanceListener() {
                            @Override
                            public void onPartitionsAssigned(
                                    Collection<TopicPartition> partitions) {
                                for (TopicPartition topicPartition : partitions) {
                                    consumer.seek(topicPartition, postedMessage.offset());
                                }
                            }
                        });

                final int NUMBER_OF_ATTEMPTS = 5;
                int attempt = 0;
                while (attempt++ < NUMBER_OF_ATTEMPTS) {
                    for (ConsumerRecord<String, String> record : consumer.poll(1000)) {
                        Message message = MAPPER.readValue(record.value(), Message.class);
                        if (message.getDestination() == CTRL_CLIENT
                                && message.getCorrelationId().equals(correlationId)) {
                            return (CtrlResponse) message;
                        }
                    }
                }

                return null;
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            throw new TopologyCtrlProcessingException(format("Unable to clear state on '%s'.", ctrlRoute), e);
        }
    }

    public Properties getConnectDefaults() {
        return connectDefaults;
    }
}
