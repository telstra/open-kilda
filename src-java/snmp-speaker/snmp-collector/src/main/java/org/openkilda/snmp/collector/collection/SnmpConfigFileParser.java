/* Copyright 2020 Telstra Open Source
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

package org.openkilda.snmp.collector.collection;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import org.openkilda.snmp.collector.collection.data.SnmpCollection;
import org.openkilda.snmp.collector.collection.data.SnmpCollectionGroup;
import org.openkilda.snmp.collector.collection.data.SnmpMetricGroup;
import org.openkilda.snmp.collector.collection.data.SnmpSystemDefinition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class SnmpConfigFileParser parses SNMP metrics collection config files for related objects. It also monitors
 * config file changes and refreshes the config objects
 */
@Component
public class SnmpConfigFileParser implements Runnable {

    private static Logger LOG = LoggerFactory.getLogger(SnmpConfigFileParser.class);

    // were are the config files located stuff.
    private final String baseConfigDir;
    private final String collectionConfigFile;
    private final String modulesDir;

    // collectionConfigPath  is where the top level config file is
    private final Path collectionConfigPath;
    private final Path collectionGroupPath;


    // the high level config file: what mib modules we are interested.
    private SnmpCollection snmpCollection;

    // from which config file we get all the collection groups and system definitions.
    private final Map<Path, Set<String>> collectionGroupPathMapping = new HashMap<>();
    private final Map<Path, Set<String>> systemDefinitionPathMapping = new HashMap<>();

    // the classification of a system will determine metrics applicable to it
    // SystemDefinition keyed by its name
    private final Map<String, SnmpSystemDefinition> systemDefinitions = new HashMap<>();

    // the list of all available groups, filtered by names from SnmpCollection,
    // i.e., we only keep groups from interested modules.
    private final Map<String, SnmpMetricGroup> metricGroups = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    private final List<SnmpConfigStateChangeObserver> observers = new LinkedList<>();

    private final Thread watcherThread;

    public SnmpConfigFileParser(
            @Value("${snmp.config.base_dir}") String baseConfigDir,
            @Value("${snmp.config.collection_file}") String collectionConfigFile,
            @Value("${snmp.config.modules_dir}") String modulesDir) {

        this.baseConfigDir = baseConfigDir;
        this.collectionConfigFile = collectionConfigFile;
        this.modulesDir = modulesDir;

        this.collectionConfigPath = Paths.get(this.baseConfigDir);
        this.collectionGroupPath = this.collectionConfigPath.resolve(modulesDir);

        watcherThread = new Thread(this, "config-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();

    }

    /**
     * Get the current state of SNMP config information.
     *
     * @return  Configuration state captured as combination of {@link SnmpSystemDefinition} and {@link SnmpMetricGroup}
     * @throws IOException  If failed to access the config files, like file not found.
     * @throws ConfigFileFormatException    Unable to parse the config files, content ill formatted.
     */
    public synchronized SnmpConfigState getCurrentConfigState() throws IOException, ConfigFileFormatException {
        if (metricGroups.size() == 0) {
            LOG.info("It seems config information has not loaded. load configuration now.");
            loadSnmpConfigs(collectionConfigPath.resolve(collectionConfigFile), collectionGroupPath);
        }

        SnmpConfigState configState = new SnmpConfigState(
                systemDefinitions.values(),
                metricGroups.values()
        );

        return configState;
    }

    /**
     * Watch SNMP config dir to detect changes and update configuration accordingly.
     */
    @Override
    public void run() {
        LOG.info("Starting the config file watcher thread");
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            Map<WatchKey, Path> keyPathMapping = new HashMap<>();

            WatchKey collectionKey = collectionConfigPath.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            keyPathMapping.put(collectionKey, collectionConfigPath);
            WatchKey groupKey = collectionGroupPath.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
            keyPathMapping.put(groupKey, collectionGroupPath);

            while (true) {
                WatchKey key = watcher.take();
                Path signaledPath = keyPathMapping.get(key);
                LOG.info("Key for path {} signalled", signaledPath);

                // too many moving parts, lock the whole object
                synchronized (this) {
                    boolean stateChanged = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == OVERFLOW) {
                            LOG.warn("OVERFLOW events occurred while watch for directory {}", collectionConfigPath);
                            continue;
                        }

                        WatchEvent<Path> ev = cast(event);
                        Path affectedPath = ev.context();
                        LOG.info("Detected {} event on relative path {}", kind, affectedPath);

                        if (signaledPath.equals(collectionConfigPath)
                                && affectedPath.equals(Paths.get(collectionConfigFile))) {
                            if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
                                // if top level config file changes, need to redo parsing.
                                loadSnmpConfigs(collectionConfigPath.resolve(collectionConfigFile),
                                        Paths.get(modulesDir));
                                stateChanged = true;
                                break; // full parsing, no need to check the other events.
                            }
                        }

                        if (signaledPath.equals(collectionGroupPath) && affectedPath.toString().endsWith(".yaml")) {
                            if (kind == ENTRY_CREATE || kind == ENTRY_MODIFY) {
                                // if only a module file changed, we just refresh specific file
                                refreshSnmpCollectionGroupConfig(
                                        collectionGroupPath.resolve(affectedPath), kind == ENTRY_CREATE);
                                stateChanged = true;
                            }

                            if (kind == ENTRY_DELETE) {
                                deleteSnmpCollectionGroupConfig(
                                        collectionGroupPath.resolve(affectedPath));
                                stateChanged = true;
                            }
                        }
                    }

                    if (!key.reset()) {
                        break;
                    }

                    // update other interested parties about the change
                    if (stateChanged) {
                        fireConfigStateChangeNotification();
                        stateChanged = false;
                    }
                }
            }
        } catch (ConfigFileFormatException e) {
            LOG.error("Failed to parse SNMP configuration files.", e);
        } catch (IOException e) {
            LOG.error("Failed to access SNMP related config files", e);
        } catch (InterruptedException e) {
            LOG.info("Config file watch task interrupted, changes won't be picked up any more", e);
        }
    }

    /**
     * Parse SNMP related config files for defined SnmpCollection, SnmpCollectionGroup, and SnmpSystemDefinition.
     *
     * @param snmpCollectionConfig the path for SnmpCollection config file,  by default snmp-collection.yaml
     * @param moduleSubPath sub path under base config path to locate various module configs
     */
    private void loadSnmpConfigs(Path snmpCollectionConfig, Path moduleSubPath)
            throws IOException, ConfigFileFormatException {

        try {
            LOG.info("Parsing SnmpCollection config file {}", snmpCollectionConfig);
            snmpCollection = parseSnmpCollectionConfig(snmpCollectionConfig);

            metricGroups.clear();
            collectionGroupPathMapping.clear();
            systemDefinitions.clear();
            systemDefinitionPathMapping.clear();

            Path moduleFilePath = snmpCollectionConfig.resolveSibling(moduleSubPath);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(moduleFilePath, "*.yaml")) {
                for (Path path : ds) {
                    LOG.info("Parsing SNMP Collection Group Config file {}", path);

                    parseSnmpCollectionGroupConfig(path);
                }
            }
            LOG.info("Completed parsing SnmpCollection config file {}", snmpCollectionConfig);

        } catch (IOException e) {
            LOG.error("Unable to complete parsing SNMP related config files", e);
            throw e;
        }
    }

    /**
     * Refresh a SnmpCollectionGroup when config file changes.
     */
    private void refreshSnmpCollectionGroupConfig(Path collectionGroupConfigFilePath, boolean isNewFile)
            throws IOException {

        if (! isNewFile) {
            clearStaleCollectionGroupInfo(collectionGroupConfigFilePath);
        }
        parseSnmpCollectionGroupConfig(collectionGroupConfigFilePath);

        LOG.info("Refreshed SNMP CollectionGroup file at {}", collectionGroupConfigFilePath);
    }

    /**
     * Parse a SnmpCollectionGroup config file to extract defined SnmpCollectionGroup and SnmpSystemDefinition.
     *
     * @param collectionGroupConfigFilePath Path to a config file containing a collection group definition,
     *                                      it is roughly corresponding to a MIB module
     * */
    private void parseSnmpCollectionGroupConfig(Path collectionGroupConfigFilePath)
            throws IOException {

        try {
            SnmpCollectionGroup collectionGroup = objectMapper.readValue(
                    Files.newBufferedReader(collectionGroupConfigFilePath),
                    SnmpCollectionGroup.class);

            if (snmpCollection.getCollectionGroups().contains(collectionGroup.getName())) {
                // collect all the groups available
                Map<String, SnmpMetricGroup> parsedMetricGroups = collectionGroup.getGroups()
                        .stream()
                        .collect(Collectors.toMap(SnmpMetricGroup::getName, Function.identity()));
                metricGroups.putAll(parsedMetricGroups);

                // we also want to keep a mapping of config file path to the groups we get from it
                collectionGroupPathMapping.put(collectionGroupConfigFilePath, parsedMetricGroups.keySet());

                // collect the system definitions found in this collection group
                Map<String, SnmpSystemDefinition> parsedSystemDefinitions = collectionGroup
                        .getSystems()
                        .stream()
                        .collect(Collectors.toMap(SnmpSystemDefinition::getName, Function.identity()));
                systemDefinitions.putAll(parsedSystemDefinitions);

                // config file path to its contained system definitions.
                systemDefinitionPathMapping.put(collectionGroupConfigFilePath, parsedSystemDefinitions.keySet());

                LOG.info("Completed parsing SnmpCollectionGroup config file {}", collectionGroupConfigFilePath);
            }

        } catch (IOException e) {
            LOG.warn("Failed to refresh SNMP mib module config file", e);
            throw e;
        }
    }

    /**
     * Parse config file to extract defined SnmpCollection.
     *
     * @param collectionConfigFilePath Path pointing to SnmpCollection config file.
     * @return a SnmpCollection
     * @throws ConfigFileFormatException if the content of the file cannot be parsed
     */
    private SnmpCollection parseSnmpCollectionConfig(Path collectionConfigFilePath)
            throws ConfigFileFormatException {
        SnmpCollection snmpCollection = null;
        try {
            SnmpCollection[] snmpCollections = objectMapper.readValue(
                    Files.newBufferedReader(collectionConfigFilePath),
                    SnmpCollection[].class);

            SnmpCollection[] activeCollections = Arrays.stream(snmpCollections)
                    .filter(c -> c.isActive()).toArray(SnmpCollection[]::new);

            if (activeCollections.length != 1) {
                LOG.error("Config file {} must have one and only one SnmpCollection active", collectionConfigFilePath);
                throw new ConfigFileFormatException("Unable to find an active collection in config file "
                        + collectionConfigFilePath);
            }

            snmpCollection = activeCollections[0];

            LOG.info("Completed parsing SnmpCollection config file {} to retrieve active collection {}",
                    collectionConfigFilePath, snmpCollection.getName());

        } catch (IOException e) {
            LOG.warn("Failed to read SNMP collection config file", e);
        }

        return snmpCollection;
    }


    private void clearStaleCollectionGroupInfo(Path collectionGroupConfigFilePath) {
        Set<String> staleCollectionGroups = collectionGroupPathMapping.remove(collectionGroupConfigFilePath);
        Set<String> staleSystemDefinitions = systemDefinitionPathMapping.remove(collectionGroupConfigFilePath);

        for (String groupName : staleCollectionGroups) {
            metricGroups.remove(groupName);
        }
        LOG.debug("Cleared old CollectionGroups from config file {}", collectionGroupConfigFilePath);

        for (String systemDefinitionName : staleSystemDefinitions) {
            systemDefinitions.remove(systemDefinitionName);
        }
        LOG.debug("Cleared old SystemDefinition from config file {}", collectionGroupConfigFilePath);
    }

    private void fireConfigStateChangeNotification() {
        SnmpConfigState configState = new SnmpConfigState(
                systemDefinitions.values(),
                metricGroups.values()
        );

        for (SnmpConfigStateChangeObserver observer : observers) {
            observer.configStateChanged(configState);
        }
    }


    public synchronized void addConfigStateObserver(SnmpConfigStateChangeObserver observer) {
        this.observers.add(observer);
    }


    /**
     * After a config file is deleted, remove its old config contents.
     *
     * @param affectedPath the snmp collection group config file has been removed
     */
    @VisibleForTesting
    void deleteSnmpCollectionGroupConfig(Path affectedPath) {

        clearStaleCollectionGroupInfo(affectedPath);
    }

    @VisibleForTesting
    SnmpCollection getSnmpCollection() {
        return snmpCollection;
    }

    @VisibleForTesting
    Map<Path, Set<String>> getCollectionGroupPathMapping() {
        return collectionGroupPathMapping;
    }

    @VisibleForTesting
    Map<Path, Set<String>> getSystemDefinitionPathMapping() {
        return systemDefinitionPathMapping;
    }

    @VisibleForTesting
    Map<String, SnmpSystemDefinition> getSystemDefinitions() {
        return systemDefinitions;
    }

    @VisibleForTesting
    Map<String, SnmpMetricGroup> getMetricGroups() {
        return metricGroups;
    }

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }
}
