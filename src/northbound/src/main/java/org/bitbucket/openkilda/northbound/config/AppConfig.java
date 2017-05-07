package org.bitbucket.openkilda.northbound.config;

import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.northbound.model.HealthCheck;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        //kafkaMessageProducer.send(message);
        return healthCheck;
    }

    /**
     * Object mapper bean.
     *
     * @return the ObjectMapper instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);
        return mapper;
    }
}
