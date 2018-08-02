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

package org.openkilda.floodlight.service;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.kafka.KafkaConsumerConfig;

import net.floodlightcontroller.core.module.IFloodlightService;

public class ConfigService implements IFloodlightService {
    private ConfigurationProvider provider;

    private KafkaTopicsConfig topics;
    private KafkaConsumerConfig consumerConfig;

    /**
     * Late module initialization.
     *
     * <p>All FL modules must be initialized before this point.
     */
    public void init(ConfigurationProvider provider) {
        this.provider = provider;
        topics = provider.getConfiguration(KafkaTopicsConfig.class);
        consumerConfig = provider.getConfiguration(KafkaConsumerConfig.class);
    }

    public ConfigurationProvider getProvider() {
        return provider;
    }

    public KafkaConsumerConfig getConsumerConfig() {
        return consumerConfig;
    }

    public KafkaTopicsConfig getTopics() {
        return topics;
    }
}
