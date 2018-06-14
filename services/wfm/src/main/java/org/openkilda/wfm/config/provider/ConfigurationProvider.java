/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.config.provider;

import static com.sabre.oss.conf4j.source.OptionalValue.absent;
import static com.sabre.oss.conf4j.source.OptionalValue.present;
import static java.util.Objects.requireNonNull;

import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.processor.ConfigurationValueProcessor;
import com.sabre.oss.conf4j.source.ConfigurationSource;
import com.sabre.oss.conf4j.source.OptionalValue;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This class creates a configuration instance and fills it with values from the properties.
 *
 * @see ConfigurationSource
 * @see JdkProxyStaticConfigurationFactory
 */
public class ConfigurationProvider {
    private final ConfigurationSource source;
    private final JdkProxyStaticConfigurationFactory factory;

    public ConfigurationProvider(Properties payload, String... prefixes) {
        this(payload, prefixes, null);
    }

    public ConfigurationProvider(Properties payload, String[] prefixes,
                                 List<ConfigurationValueProcessor> configurationValueProcessors) {
        source = new MultiPrefixConfigurationSource(payload, prefixes);
        factory = new JdkProxyStaticConfigurationFactory();
        if (configurationValueProcessors != null) {
            factory.setConfigurationValueProcessors(configurationValueProcessors);
        }
    }

    /**
     * Creates a configuration class and fills it with values.
     *
     * @param configurationType configuration class.
     * @return configuration instance
     */
    public <T> T getConfiguration(Class<T> configurationType) {
        requireNonNull(configurationType, "configurationType cannot be null");

        return factory.createConfiguration(configurationType, source);
    }

    class MultiPrefixConfigurationSource implements ConfigurationSource {
        private Properties payload;
        private String[] prefixes;

        MultiPrefixConfigurationSource(Properties payload, String[] prefixes) {
            this.payload = requireNonNull(payload, "payload cannot be null");
            this.prefixes = Arrays.stream(prefixes)
                    .filter(StringUtils::isNotBlank)
                    .map(value -> value + ".")
                    .toArray(String[]::new);
        }

        @Override
        public OptionalValue<String> getValue(String key, Map<String, String> attributes) {
            requireNonNull(key, "key cannot be null");

            String value = null;

            for (String prefix : prefixes) {
                value = payload.getProperty(prefix + key);
                if (value != null) {
                    break;
                }
            }

            if (value == null) {
                value = payload.getProperty(key);
            }

            return value != null ? present(value) : absent();
        }
    }
}
