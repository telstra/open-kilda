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

package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * Created by jonv on 6/3/17.
 */
public class KafkaMessageProducer implements IFloodlightModule, IFloodlightService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageProducer.class);
    private KafkaProducer<String, String> producer;

    /*
     * IFloodlightModule Methods
     */

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return Collections.singletonList(KafkaMessageProducer.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return Collections.singletonMap(KafkaMessageProducer.class, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return Collections.singletonList(IFloodlightProviderService.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        Map<String, String> configParameters = context.getConfigParams(this);
        Properties kafkaProps = new Properties();
        kafkaProps.put("bootstrap.servers", configParameters.get("bootstrap-servers"));
        kafkaProps.put("acks", "all");
        kafkaProps.put("retries", 0);
        kafkaProps.put("batch.size", 16384);
        kafkaProps.put("buffer.memory", 33554432);
        kafkaProps.put("linger.ms", 10);
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer<>(kafkaProps);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {
    }

    /**
     * Send the message to Kafka.
     *
     * @param topic   topic to post the message to
     * @param message message to pose
     */
    public void postMessage(final String topic, final Message message) {
        try {
            String messageString = MAPPER.writeValueAsString(message);
            // (crimi) - uncomment for development only .. some messages (stats) fill up the log too quickly
            // logger.debug("Posting: topic={}, message={}", topic, messageString);
            producer.send(new ProducerRecord<>(topic, messageString));
        } catch (JsonProcessingException e) {
            logger.error("Can not serialize message: {}", message, e);
        }
    }
}
