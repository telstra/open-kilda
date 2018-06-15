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

import org.openkilda.config.KafkaConsumerGroupConfig;
import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.config.mapping.Mapping;
import org.openkilda.config.mapping.MappingConfigurationValueProcessor;
import org.openkilda.config.mapping.MappingStrategy;

import com.sabre.oss.conf4j.processor.ConfigurationValue;
import com.sabre.oss.conf4j.processor.ConfigurationValueProcessor;

import java.util.function.UnaryOperator;

/**
 * Implementation of {@link ConfigurationValueProcessor} responsible for applying Kafka naming to configuration values.
 * <p/>
 * It performs mapping of configuration values for {@link KafkaTopicsConfig#KAFKA_TOPIC_MAPPING}
 * and {@link KafkaConsumerGroupConfig#KAFKA_CONSUMER_GROUP_MAPPING} targets via provided {@link KafkaNamingStrategy}.
 *
 * @see Mapping
 * @see KafkaTopicsConfig#KAFKA_TOPIC_MAPPING
 * @see KafkaConsumerGroupConfig#KAFKA_CONSUMER_GROUP_MAPPING
 */
public class KafkaNamingForConfigurationValueProcessor implements ConfigurationValueProcessor {
    private MappingConfigurationValueProcessor mappingProcessor;

    public KafkaNamingForConfigurationValueProcessor(KafkaNamingStrategy namingStrategy) {
        SingleMappingStrategy kafkaTopicMapping = new SingleMappingStrategy(
                KafkaTopicsConfig.KAFKA_TOPIC_MAPPING, namingStrategy::kafkaTopicName);
        SingleMappingStrategy kafkaGroupMapping = new SingleMappingStrategy(
                KafkaConsumerGroupConfig.KAFKA_CONSUMER_GROUP_MAPPING, namingStrategy::kafkaConsumerGroupName);

        mappingProcessor = new MappingConfigurationValueProcessor(kafkaTopicMapping, kafkaGroupMapping);
    }


    @Override
    public ConfigurationValue process(ConfigurationValue value) {
        return mappingProcessor.process(value);
    }

    /**
     * Implementation of {@link MappingStrategy} which performs mapping via {@link UnaryOperator} for a single target.
     */
    private static class SingleMappingStrategy implements MappingStrategy {

        private final String target;
        private final UnaryOperator<String> mappingOperator;

        SingleMappingStrategy(String target, UnaryOperator<String> mappingOperator) {
            this.target = requireNonNull(target, "target cannot be null");
            this.mappingOperator = requireNonNull(mappingOperator, "mappingOperator cannot be null");
        }

        @Override
        public boolean isApplicable(String mappingTarget) {
            return target.equals(mappingTarget);
        }

        @Override
        public String apply(String mappingTarget, String value) {
            return isApplicable(mappingTarget) ? mappingOperator.apply(value) : value;
        }
    }
}
