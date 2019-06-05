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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.service.IService;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

import java.util.Properties;

public class KafkaUtilityService implements IService {
    private final KafkaChannel owner;

    public KafkaUtilityService(KafkaChannel owner) {
        this.owner = owner;
    }

    /**
     * Create new kafka-consumer and apply setting from {@link KafkaConsumerSetup} argument.
     */
    public Consumer<String, String> makeConsumer(KafkaConsumerSetup setup) {
        Properties config = setup.applyConfig(owner.getConfig().consumerProperties());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config);
        setup.applyInstance(consumer);
        return consumer;
    }

    public Producer<String, String> makeProducer() {
        return new KafkaProducer<>(owner.getConfig().producerProperties());
    }

    public KafkaChannel getKafkaChannel() {
        return owner;
    }

    public boolean isTestingMode() {
        return owner.getConfig().isTestingMode();
    }

    @Override
    public void setup(FloodlightModuleContext moduleContext) throws FloodlightModuleException {
        // there is nothing to initialize here
    }
}
