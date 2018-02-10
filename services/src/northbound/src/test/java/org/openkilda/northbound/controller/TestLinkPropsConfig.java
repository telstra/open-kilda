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

import org.openkilda.northbound.config.SecurityConfig;
import org.openkilda.northbound.config.WebConfig;
import org.openkilda.northbound.messaging.HealthCheckMessageConsumer;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.mockito.Mockito.mock;

/**
 * The Test configuration.
 */
@Configuration
@EnableWebSecurity
@Import({WebConfig.class, SecurityConfig.class})
@ComponentScan({
        "org.openkilda.northbound.controller",
        "org.openkilda.northbound.converter",
        "org.openkilda.northbound.service",
        "org.openkilda.northbound.utils"})
@PropertySource({"classpath:northbound.properties"})
public class TestLinkPropsConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
