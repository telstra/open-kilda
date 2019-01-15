package org.openkilda.functionaltests.config

import static com.sabre.oss.conf4j.spring.Conf4jSpringConstants.CONF4J_CONFIGURATION_VALUE_PROCESSORS

import org.openkilda.config.EnvironmentConfig
import org.openkilda.config.KafkaTopicsConfig
import org.openkilda.config.naming.KafkaNamingForConfigurationValueProcessor
import org.openkilda.config.naming.KafkaNamingStrategy

import com.sabre.oss.conf4j.processor.ConfigurationValueProcessor
import com.sabre.oss.conf4j.spring.annotation.ConfigurationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableAutoConfiguration(exclude = [ErrorMvcAutoConfiguration])
@ConfigurationType(name = "kafkaTopicsConfig", value = KafkaTopicsConfig)
@ConfigurationType(EnvironmentConfig)
class KafkaTopicNameConfig {

    @Bean(CONF4J_CONFIGURATION_VALUE_PROCESSORS)
    List<ConfigurationValueProcessor> configurationValueProcessors(EnvironmentConfig environmentConfig) {
        KafkaNamingStrategy namingStrategy = new KafkaNamingStrategy(environmentConfig.getNamingPrefix() ?: "")

        // Apply the environment prefix to Kafka topics and groups in the configuration.
        return [new KafkaNamingForConfigurationValueProcessor(namingStrategy)]
    }
}
