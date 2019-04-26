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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

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
public class FloodlightModuleConfigurationProvider extends ValidatingConfigurationProvider {
    private static final Logger log = LoggerFactory.getLogger(FloodlightModuleConfigurationProvider.class);

    /**
     * Build ConfigurationProvider instance from config data provided by FL's module context.
     */
    public static FloodlightModuleConfigurationProvider of(FloodlightModuleContext moduleContext,
                                                           IFloodlightModule module) {
        Map<String, String> configData = moduleContext.getConfigParams(module);
        FloodlightModuleConfigurationProvider provider = new FloodlightModuleConfigurationProvider(configData);

        dumpConfigData(module.getClass(), configData);
        return provider;
    }

    /**
     * Build ConfigurationProvider instance for specified floodlight module.
     */
    public static FloodlightModuleConfigurationProvider of(FloodlightModuleContext moduleContext,
                                                           Class<? extends IFloodlightModule> module) {
        Map<String, String> configData = moduleContext.getConfigParams(module);
        FloodlightModuleConfigurationProvider provider = new FloodlightModuleConfigurationProvider(configData);

        dumpConfigData(module, configData);
        return provider;
    }

    protected FloodlightModuleConfigurationProvider(Map<String, String> configData) {
        super(new MapConfigurationSource(configData), new JdkProxyStaticConfigurationFactory());

        EnvironmentFloodlightConfig environmentConfig =
                factory.createConfiguration(EnvironmentFloodlightConfig.class, source);
        String namingPrefix = environmentConfig.getNamingPrefix();
        KafkaNamingStrategy namingStrategy = new KafkaNamingStrategy(namingPrefix != null ? namingPrefix : "");

        // Apply the environment prefix to Kafka topics and groups in the configuration.
        ((JdkProxyStaticConfigurationFactory) factory).setConfigurationValueProcessors(
                singletonList(new KafkaNamingForConfigurationValueProcessor(namingStrategy)));
    }

    private static void dumpConfigData(Class<? extends IFloodlightModule> module, Map<String, String> configData) {
        String delimiter = "\n      ";
        String dump = configData.entrySet().stream()
                .map(entry -> String.format("%s = %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(delimiter));
        log.debug(
                "Dump config properties for {} (raw values, before filter and validation):{}{}",
                module.getName(), delimiter, dump);
    }
}
