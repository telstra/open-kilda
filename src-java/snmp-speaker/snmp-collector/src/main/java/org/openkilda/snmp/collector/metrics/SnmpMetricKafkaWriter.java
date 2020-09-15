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

package org.openkilda.snmp.collector.metrics;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.snmp.metric.SnmpMetricData;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;

@Component
@Profile("!dev")
public class SnmpMetricKafkaWriter implements SnmpMetricWriter {
    private static Logger LOG = LoggerFactory.getLogger(SnmpMetricKafkaWriter.class);
    private static String SNMP_COLLECTOR_CORRELATION_ID = "snmp-speaker-collector";

    @Autowired
    private BlockingQueue<SnmpMetricData> metricQueue;

    @Value("${kafka.client.id")
    private String clientId;

    @Value("${kafka.topic:snmp}")
    private String topic;

    @Value("${kafka.bootstrap.servers}")
    private String[] bootstrapServers;

    @Value("${kafka.key.serializer}")
    private String keySerializer;

    @Value("${kafka.value.serializer}")
    private String valueSerializer;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        Map<String, Object> kafkaConfig = new HashMap<>();
        kafkaConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Arrays.asList(bootstrapServers));
        LOG.info("Using kafka bootstrap servers {}", String.join(",", bootstrapServers));

        kafkaConfig.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        kafkaConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        kafkaConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);

        Producer<String, String> producer = new KafkaProducer<>(kafkaConfig);
        LOG.info("Successfully create Kafka producer");


        try {
            while (true) {
                SnmpMetricData metricData = metricQueue.take();
                LOG.debug("Received metric {}", metricData.toString());

                ProducerRecord<String, String> record = new ProducerRecord<>(topic,
                        mapper.writeValueAsString(
                                new InfoMessage(metricData,
                                System.currentTimeMillis(),
                                SNMP_COLLECTOR_CORRELATION_ID)));

                RecordMetadata meta = producer.send(record).get();
            }

        } catch (InterruptedException | JsonProcessingException | ExecutionException e) {
            LOG.error(String.format("%s task encountered exception. The task will stop"), e);
        } finally {
            producer.flush();
            producer.close();
        }
    }

}
