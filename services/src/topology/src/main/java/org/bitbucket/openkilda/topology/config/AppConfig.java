package org.bitbucket.openkilda.topology.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The Application configuration.
 * This configuration is used for application run.
 * It includes configs of different components via {@link org.springframework.context.annotation.Import} annotation.
 */
@Configuration
@EnableAutoConfiguration
@Import({WebConfig.class, SecurityConfig.class, Neo4jConfig.class,
        MessageProducerConfig.class, MessageConsumerConfig.class})
public class AppConfig {
}
