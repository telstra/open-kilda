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

package org.bitbucket.openkilda.northbound.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

/**
 * The Application configuration. This configuration is used for application run. It includes configs of different
 * components via {@link org.springframework.context.annotation.Import} annotation.
 */
@Configuration
@EnableAutoConfiguration(exclude = {ErrorMvcAutoConfiguration.class})
@Import({WebConfig.class, SecurityConfig.class, SwaggerConfig.class,
        MessageConsumerConfig.class, MessageProducerConfig.class})
@ComponentScan({
        "org.bitbucket.openkilda.northbound.controller",
        "org.bitbucket.openkilda.northbound.model",
        "org.bitbucket.openkilda.northbound.service",
        "org.bitbucket.openkilda.northbound.utils"})
public class AppConfig {
}
