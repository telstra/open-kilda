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

package org.openkilda.server42.control.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;

@Slf4j
@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic toStorm(@Value("${openkilda.server42.control.kafka.topic.to_storm}") String name) {
        return TopicBuilder.name(name).build();
    }

    @Bean
    public NewTopic fromStorm(@Value("${openkilda.server42.control.kafka.topic.from_storm}") String name) {
        return TopicBuilder.name(name).build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> kafkaConsumerFactory,
            @Autowired KafkaRecordFilter kafkaRecordFilter,
            CommonErrorHandler commonErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);
        // didn't find annotation
        factory.setRecordFilterStrategy(kafkaRecordFilter);
        factory.setCommonErrorHandler(commonErrorHandler);
        return factory;
    }

    @Bean
    public KafkaListenerErrorHandler kafkaListenerErrorHandler() {
        return (message, exception) -> {
            log.warn("this is error:", exception);
            log.warn("this is message: headers: {}, payload: {}", message.getHeaders(), message.getPayload());
            return null;
        };
    }

    @Bean
    CommonErrorHandler commonErrorHandler() {
        return new KafkaErrorHandler();
    }

    @Slf4j
    public static class KafkaErrorHandler implements CommonErrorHandler {
        @Override
        public void handleOtherException(Exception thrownException, Consumer<?, ?> consumer,
                                         MessageListenerContainer container, boolean batchListener) {
            log.warn("I am here!");
        }

        @Override
        public boolean handleOne(Exception thrownException, ConsumerRecord<?, ?> record, Consumer<?, ?> consumer,
                                 MessageListenerContainer container) {
            log.warn("I am here!");
            return true;
        }

        @Override
        public void handleRemaining(Exception thrownException, List<ConsumerRecord<?, ?>> records,
                                    Consumer<?, ?> consumer, MessageListenerContainer container) {
            log.warn("I am here!");
        }

        @Override
        public void handleBatch(Exception thrownException, ConsumerRecords<?, ?> data, Consumer<?, ?> consumer,
                                MessageListenerContainer container, Runnable invokeListener) {
            log.warn("I am here!");
        }
    }
}
