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

package org.openkilda.northbound.controller;

import static org.mockito.Mockito.mock;

import org.openkilda.northbound.config.KafkaConfig;
import org.openkilda.northbound.config.SecurityConfig;
import org.openkilda.northbound.config.WebConfig;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.utils.CorrelationIdFactory;
import org.openkilda.northbound.utils.TestCorrelationIdFactory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.client.RestTemplate;

/**
 * The Test configuration.
 */
@Configuration
@EnableWebSecurity
@Import({WebConfig.class, SecurityConfig.class, KafkaConfig.class})
@ComponentScan(
        basePackages = {
                "org.openkilda.northbound.controller",
                "org.openkilda.northbound.converter",
                "org.openkilda.northbound.service",
                "org.openkilda.northbound.utils"
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = TestConfiguration.class)
        })
@PropertySource({"classpath:northbound.properties"})
public class TestConfig {
    @Bean
    public MessageConsumer messageConsumer() {
        return new TestMessageMock();
    }

    @Bean
    public MessageProducer messageProducer() {
        return new TestMessageMock();
    }

    @Bean
    public RestTemplate restTemplate() {
        return mock(RestTemplate.class);
    }

    @Bean
    public CorrelationIdFactory idFactory() {
        return new TestCorrelationIdFactory();
    }
}
