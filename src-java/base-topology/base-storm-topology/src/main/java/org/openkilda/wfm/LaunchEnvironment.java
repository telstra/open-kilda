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

package org.openkilda.wfm;

import static java.util.Collections.singletonList;

import org.openkilda.config.EnvironmentConfig;
import org.openkilda.config.naming.KafkaNamingForConfigurationValueProcessor;
import org.openkilda.config.naming.KafkaNamingStrategy;
import org.openkilda.wfm.config.naming.TopologyNamingStrategy;
import org.openkilda.wfm.config.provider.MultiPrefixConfigurationProvider;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.topology.Topology;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.storm.flux.model.TopologyDef;
import org.apache.storm.flux.parser.FluxParser;
import org.kohsuke.args4j.CmdLineException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class LaunchEnvironment {
    private static String CLI_OVERLAY = "cli";

    public final CliArguments cli;
    @Getter
    private final Properties properties;
    @Getter
    private final TopologyDef topologyDefinition;

    public LaunchEnvironment(String[] args) throws CmdLineException, ConfigurationException {
        cli = new CliArguments(args);
        log.info("Environment for '{}' topology: {}, {}", cli.getTopologyName(), cli.getTopologyDefinitionFile(),
                cli.getExtraConfiguration());
        properties = loadPropertiesAndOverlayWithExtra(cli.getExtraConfiguration());
        topologyDefinition = Optional.ofNullable(cli.getTopologyDefinitionFile())
                .map(file -> loadTopologyDef(file, properties)).orElse(null);
    }

    private Properties loadPropertiesAndOverlayWithExtra(File[] extraConfiguration) throws ConfigurationException {
        Properties result = new Properties();
        try (InputStream resource = this.getClass().getResourceAsStream(Topology.TOPOLOGY_PROPERTIES)) {
            result.load(resource);
        } catch (IOException e) {
            throw new ConfigurationException("Unable to load default properties.", e);
        }

        for (File path : extraConfiguration) {
            Properties override = new Properties(properties);
            try (FileInputStream source = new FileInputStream(path)) {
                override.load(source);
            } catch (IOException e) {
                throw new ConfigurationException(String.format("Unable to load properties from %s", path), e);
            }
            result.putAll(override);
        }

        result.setProperty(CLI_OVERLAY + ".local", cli.getIsLocal() ? "true" : "false");
        if (cli.getLocalExecutionTime() != null) {
            result.setProperty(CLI_OVERLAY + ".local.execution.time", cli.getLocalExecutionTime().toString());
        }

        return result;
    }

    private TopologyDef loadTopologyDef(File topologyDefFile, Properties properties) {
        try {
            return FluxParser.parseFile(topologyDefFile.getAbsolutePath(), false, true, properties, false);
        } catch (Exception e) {
            log.info("Unable to load topology definition file {}", topologyDefFile, e);
            return null;
        }
    }

    public String getTopologyName() {
        return cli.getTopologyName();
    }

    /**
     * Returns a provider for configuration entities.
     * <p/>
     * The provider is pre-configured for the environment settings and CLI options.
     *
     * @param prefixes is an ordered list of property prefixes to be used for configuration entries lookup.
     */
    public MultiPrefixConfigurationProvider getConfigurationProvider(String... prefixes) {
        String[] prefixesWithOverlay = ArrayUtils.addAll(ArrayUtils.toArray(CLI_OVERLAY), prefixes);

        // Apply Kafka naming to Kafka topics and groups in the configuration.
        return new MultiPrefixConfigurationProvider(properties, prefixesWithOverlay,
                singletonList(new KafkaNamingForConfigurationValueProcessor(getKafkaNamingStrategy())));
    }

    public KafkaNamingStrategy getKafkaNamingStrategy() {
        return new KafkaNamingStrategy(getEnvironmentPrefix());
    }

    public TopologyNamingStrategy getTopologyNamingStrategy() {
        return new TopologyNamingStrategy(getEnvironmentPrefix());
    }

    private String getEnvironmentPrefix() {
        MultiPrefixConfigurationProvider configurationProvider = new MultiPrefixConfigurationProvider(properties,
                ArrayUtils.toArray(CLI_OVERLAY));

        EnvironmentConfig environmentConfig = configurationProvider.getConfiguration(EnvironmentConfig.class);
        return environmentConfig.getNamingPrefix() != null ? environmentConfig.getNamingPrefix() : "";
    }

    public void setupOverlay(Properties overlay) {
        properties.putAll(overlay);
    }
}
