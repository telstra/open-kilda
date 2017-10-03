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
