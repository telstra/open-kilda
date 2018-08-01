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

package org.openkilda.floodlight.config.provider;

import static java.util.Collections.singletonList;

import org.openkilda.config.EnvironmentConfig;
import org.openkilda.config.naming.KafkaNamingForConfigurationValueProcessor;
import org.openkilda.config.naming.KafkaNamingStrategy;
import org.openkilda.config.provider.ValidatingConfigurationProvider;
import org.openkilda.floodlight.config.EnvironmentFloodlightConfig;

import com.sabre.oss.conf4j.factory.jdkproxy.JdkProxyStaticConfigurationFactory;
import com.sabre.oss.conf4j.source.MapConfigurationSource;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;

/**
 * This class creates a configuration instance and fills it with values from the Floodlight module parameters.
 * <p/>
 * The provider applies {@link KafkaNamingStrategy} with the environment prefix
 * from {@link EnvironmentConfig#getNamingPrefix()} to configuration values which marked for mapping.
 *
 * @see MapConfigurationSource
 * @see JdkProxyStaticConfigurationFactory
 * @see EnvironmentConfig#getNamingPrefix()
 * @see KafkaNamingStrategy
 * @see KafkaNamingForConfigurationValueProcessor
 */
public class ConfigurationProvider extends ValidatingConfigurationProvider {
    public ConfigurationProvider(FloodlightModuleContext context, IFloodlightModule module) {
        super(new MapConfigurationSource(context.getConfigParams(module)), new JdkProxyStaticConfigurationFactory());

        EnvironmentFloodlightConfig environmentConfig =
                factory.createConfiguration(EnvironmentFloodlightConfig.class, source);
        String namingPrefix = environmentConfig.getNamingPrefix();
        KafkaNamingStrategy namingStrategy = new KafkaNamingStrategy(namingPrefix != null ? namingPrefix : "");

        // Apply the environment prefix to Kafka topics and groups in the configuration.
        ((JdkProxyStaticConfigurationFactory) factory).setConfigurationValueProcessors(
                singletonList(new KafkaNamingForConfigurationValueProcessor(namingStrategy)));
    }
}
