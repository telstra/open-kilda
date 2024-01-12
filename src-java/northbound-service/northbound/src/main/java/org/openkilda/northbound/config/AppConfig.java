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

package org.openkilda.northbound.config;

import static com.sabre.oss.conf4j.spring.Conf4jSpringConstants.CONF4J_CONFIGURATION_VALUE_PROCESSORS;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;

import org.openkilda.config.EnvironmentConfig;
import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.config.naming.KafkaNamingForConfigurationValueProcessor;
import org.openkilda.config.naming.KafkaNamingStrategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.processor.ConfigurationValueProcessor;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * The Application configuration. This configuration is used for application run. It includes configs of different
 * components via {@link org.springframework.context.annotation.Import} annotation.
 */
@Configuration
@EnableAutoConfiguration(exclude = {ErrorMvcAutoConfiguration.class})
@Import({WebConfig.class, SecurityConfig.class, SwaggerConfig.class,
        MessageConsumerConfig.class, MessageProducerConfig.class})
@ComponentScan({
        "org.openkilda.northbound.controller",
        "org.openkilda.northbound.converter",
        "org.openkilda.northbound.service",
        "org.openkilda.northbound.utils"})
//@ConfigurationType(name = "kafkaTopicsConfig", value = KafkaTopicsConfig.class)
//@ConfigurationType(name = "kafkaGroupConfig", value = KafkaNorthboundConfig.class)
//@ConfigurationType(EnvironmentConfig.class)
public class AppConfig {
    private KafkaTopicsConfig kafkaTopicsConfig;
    private KafkaNorthboundConfig kafkaNorthboundConfig;
    private EnvironmentConfig environmentConfig;

    @Bean(name = "kafkaGroupConfig")
    public KafkaNorthboundConfig getKafkaNorthboundConfig() {
        if (kafkaNorthboundConfig != null) {
            return kafkaNorthboundConfig;
        } else {
            synchronized (this) {
                kafkaNorthboundConfig = new JdkProxyStaticConfigurationFactory()
                        .createConfiguration(KafkaNorthboundConfig.class, new MapConfigurationSource(emptyMap()));
                return kafkaNorthboundConfig;
            }
        }
    }

    @Bean(CONF4J_CONFIGURATION_VALUE_PROCESSORS)
    List<ConfigurationValueProcessor> configurationValueProcessors(EnvironmentConfig environmentConfig) {
        String namingPrefix = environmentConfig.getNamingPrefix();
        KafkaNamingStrategy namingStrategy = new KafkaNamingStrategy(namingPrefix != null ? namingPrefix : "");

        // Apply the environment prefix to Kafka topics and groups in the configuration.
        return singletonList(new KafkaNamingForConfigurationValueProcessor(namingStrategy));
    }

    @Bean
    public EnvironmentConfig getEnvironmentConfig() {
        if (environmentConfig != null) {
            return environmentConfig;
        } else {
            synchronized (this) {
                environmentConfig = new JdkProxyStaticConfigurationFactory()
                        .createConfiguration(EnvironmentConfig.class, new MapConfigurationSource(emptyMap()));
                return environmentConfig;
            }
        }
    }

    @Bean(name = "kafkaTopicsConfig")
    public KafkaTopicsConfig getKafkaTopicsConfig() {
        if (kafkaTopicsConfig != null) {
            return kafkaTopicsConfig;
        } else {
            synchronized (this) {
                kafkaTopicsConfig = new JdkProxyStaticConfigurationFactory()
                        .createConfiguration(KafkaTopicsConfig.class, new MapConfigurationSource(emptyMap()));
                return kafkaTopicsConfig;
            }
        }
    }


    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
