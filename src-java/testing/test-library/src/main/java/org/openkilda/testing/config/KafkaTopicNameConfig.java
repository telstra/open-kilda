/* Copyright 2024 Telstra Open Source
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

package org.openkilda.testing.config;

import static com.sabre.oss.conf4j.spring.Conf4jSpringConstants.CONF4J_CONFIGURATION_VALUE_PROCESSORS;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;

import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import org.openkilda.config.EnvironmentConfig;
import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.config.naming.KafkaNamingForConfigurationValueProcessor;
import org.openkilda.config.naming.KafkaNamingStrategy;

import com.sabre.oss.conf4j.processor.ConfigurationValueProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * The Application configuration. This configuration is used for application run. It includes configs of different
 * components via {@link org.springframework.context.annotation.Import} annotation.
 */
@Configuration
public class KafkaTopicNameConfig {

    private KafkaTopicsConfig kafkaTopicsConfig;
    private EnvironmentConfig environmentConfig;

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

    @Bean(CONF4J_CONFIGURATION_VALUE_PROCESSORS)
    List<ConfigurationValueProcessor> configurationValueProcessors(EnvironmentConfig environmentConfig) {
        String namingPrefix = environmentConfig.getNamingPrefix();
        KafkaNamingStrategy namingStrategy = new KafkaNamingStrategy(namingPrefix != null ? namingPrefix : "");

        // Apply the environment prefix to Kafka topics and groups in the configuration.
        return singletonList(new KafkaNamingForConfigurationValueProcessor(namingStrategy));
    }
}
