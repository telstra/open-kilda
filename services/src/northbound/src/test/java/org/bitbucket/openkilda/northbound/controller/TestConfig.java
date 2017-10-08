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

package org.bitbucket.openkilda.northbound.controller;

import org.bitbucket.openkilda.northbound.config.SecurityConfig;
import org.bitbucket.openkilda.northbound.config.WebConfig;
import org.bitbucket.openkilda.northbound.messaging.HealthCheckMessageConsumer;
import org.bitbucket.openkilda.northbound.messaging.MessageConsumer;
import org.bitbucket.openkilda.northbound.messaging.MessageProducer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import java.util.Map;

/**
 * The Test configuration.
 */
@Configuration
@EnableWebSecurity
@Import({WebConfig.class, SecurityConfig.class})
@ComponentScan({
        "org.bitbucket.openkilda.northbound.controller",
        "org.bitbucket.openkilda.northbound.model",
        "org.bitbucket.openkilda.northbound.service",
        "org.bitbucket.openkilda.northbound.utils"})
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
    public HealthCheckMessageConsumer healthCheckMessageConsumer() {
        return new TestHealthCheckMessageMock();
    }

    private class TestHealthCheckMessageMock implements HealthCheckMessageConsumer {

        @Override
        public Map<String, String> poll(String correlationId) {
            return null;
        }

        @Override
        public void clear() {

        }
    }
}
