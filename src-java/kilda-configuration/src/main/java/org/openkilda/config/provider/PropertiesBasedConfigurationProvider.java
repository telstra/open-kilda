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

package org.openkilda.config.provider;

import com.sabre.oss.conf4j.factory.ConfigurationFactory;
import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.ConfigurationSource;
import com.sabre.oss.conf4j.source.PropertiesConfigurationSource;

import java.io.IOException;
import java.util.Properties;

/**
 * Simple implementation of {@link ConfigurationProvider} based on a properties resource.
 *
 * @see Properties
 * @see PropertiesConfigurationSource
 * @see JdkProxyStaticConfigurationFactory
 */
public class PropertiesBasedConfigurationProvider implements ConfigurationProvider {
    private ConfigurationSource source;
    private ConfigurationFactory factory;

    public PropertiesBasedConfigurationProvider() {
        this(new Properties());
    }

    public PropertiesBasedConfigurationProvider(Properties properties) {
        source = new PropertiesConfigurationSource(properties);
        factory = new JdkProxyStaticConfigurationFactory();
    }

    public PropertiesBasedConfigurationProvider(String propertiesResource) throws IOException {
        Properties properties = new Properties();
        properties.load(this.getClass().getClassLoader().getResourceAsStream(propertiesResource));
        source = new PropertiesConfigurationSource(properties);
        factory = new JdkProxyStaticConfigurationFactory();
    }

    @Override
    public <T> T getConfiguration(Class<T> configurationType) {
        return factory.createConfiguration(configurationType, source);
    }
}
