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

package org.openkilda;

import static java.util.Collections.singletonList;

import org.openkilda.config.EnvironmentConfig;
import org.openkilda.config.KafkaConfig;
import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.config.naming.KafkaNamingForConfigurationValueProcessor;
import org.openkilda.config.naming.KafkaNamingStrategy;

import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.PropertiesConfigurationSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class KafkaParameters {
    private static final String CONFIG_FILE = "/atdd.properties";

    private final KafkaConfig kafkaConfig;
    private final KafkaTopicsConfig kafkaTopicsConfig;

    public KafkaParameters() throws IOException {
        Properties properties = new Properties();

        try (InputStream input = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IOException("Can't find " + CONFIG_FILE);
            }
            properties.load(input);
        }

        PropertiesConfigurationSource source = new PropertiesConfigurationSource(properties);
        JdkProxyStaticConfigurationFactory factory = new JdkProxyStaticConfigurationFactory();

        EnvironmentConfig environmentConfig = factory.createConfiguration(EnvironmentConfig.class, source);
        String namingPrefix = environmentConfig.getNamingPrefix();
        KafkaNamingStrategy namingStrategy = new KafkaNamingStrategy(namingPrefix != null ? namingPrefix : "");

        // Apply the environment prefix to Kafka topics and groups in the configuration.
        factory.setConfigurationValueProcessors(
                singletonList(new KafkaNamingForConfigurationValueProcessor(namingStrategy)));

        kafkaConfig = factory.createConfiguration(KafkaConfig.class, source);
        kafkaTopicsConfig = factory.createConfiguration(KafkaTopicsConfig.class, source);
    }

    public String getBootstrapServers() {
        return kafkaConfig.getHosts();
    }

    public String getControlTopic() {
        return kafkaTopicsConfig.getCtrlTopic();
    }

    public String getDiscoTopic() {
        return kafkaTopicsConfig.getTopoDiscoTopic();
    }
}
