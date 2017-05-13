package org.bitbucket.openkilda.northbound.config;

import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.northbound.model.HealthCheck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * The Application configuration.
 * This configuration is used for application run.
 * It includes configs of different components via {@link org.springframework.context.annotation.Import} annotation.
 */
@Configuration
@EnableAutoConfiguration
@Import({WebConfig.class, MessageConsumerConfig.class, MessageProducerConfig.class, SecurityConfig.class})
public class AppConfig {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    /**
     * The service name.
     */
    @Value("${service.name}")
    private String serviceName;

    /**
     * The service version.
     */
    @Value("${service.version}")
    private String serviceVersion;

    /**
     * The service description.
     */
    @Value("${service.description}")
    private String serviceDescription;

    /**
     * The Kafka producer instance.
     */
    @Autowired
    private KafkaMessageProducer kafkaMessageProducer;

    /**
     * The health-check info bean.
     *
     * @return the FlowModel instance
     */
    @Bean
    public HealthCheck healthCheck() {
        HealthCheck healthCheck = new HealthCheck(serviceName, serviceVersion, serviceDescription);
        String message = String.format("Starting application: %s-%s", healthCheck.getName(), healthCheck.getVersion());
        logger.info(message);
        return healthCheck;
    }
}
