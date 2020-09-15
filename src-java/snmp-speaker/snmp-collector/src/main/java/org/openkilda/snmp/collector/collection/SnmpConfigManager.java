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

import org.openkilda.snmp.collector.collection.data.SnmpMetricGroup;
import org.openkilda.snmp.collector.collection.data.SnmpSystemDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Class {@link SnmpConfigManager} borrows the help from {@link SnmpConfigFileParser} to get up-to-date config
 * information regarding what kinds of metrics are interested for different systems,  and it will  answer the query
 * that for a specific system, what are the metrics to collect.
 */
@Component
public class SnmpConfigManager implements SnmpConfigStateChangeObserver {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpConfigManager.class);

    private Collection<SnmpSystemDefinition> systemDefinitions;
    private Collection<SnmpMetricGroup> metricGroups;
    private final ReentrantLock configStateLock = new ReentrantLock();
    private final Map<String, Collection<SnmpMetricGroup>> sysObjectIdToMetricGroupCache = new HashMap<>();

    private final SnmpConfigFileParser configFileParser;

    @Autowired
    public SnmpConfigManager(SnmpConfigFileParser parser) throws IOException, ConfigFileFormatException {

        this.configFileParser = parser;
        this.configFileParser.addConfigStateObserver(this);

        SnmpConfigState configState = this.configFileParser.getCurrentConfigState();
        systemDefinitions = configState.getSystemDefinitions();
        metricGroups = configState.getMetricGroups();
    }

    /**
     * Given an sysObjectId, return a list of MetricGroups applicable to it.
     *
     * @param sysObjectID A SNMP sysObjectID
     * @return A collection of SnmpMetricGroup that can collect
     */
    public Collection<SnmpMetricGroup> getMatchedMetricGroups(String sysObjectID) {

        configStateLock.lock();

        Collection<SnmpMetricGroup> matchedMetricGroups = sysObjectIdToMetricGroupCache.get(sysObjectID);
        if (matchedMetricGroups != null) {
            return matchedMetricGroups;
        }

        try {
            Collection<SnmpSystemDefinition> matchedSystemDefinitions = systemDefinitions
                    .stream()
                    .filter(x -> sysObjectID.startsWith(x.getSysObjectIdMask()))
                    .collect(Collectors.toCollection(ArrayList<SnmpSystemDefinition>::new));

            // from matched SystemDefinitions to all the MetricGroups names
            Collection<String> matchedMetricGroupNames = matchedSystemDefinitions
                    .stream()
                    .flatMap(x -> x.getCollections().stream())
                    .collect(Collectors.toCollection(HashSet<String>::new));

            // From all the MetricGroups understood by the system, filter out those are in the matchedMetricGroupNames
            matchedMetricGroups = metricGroups
                    .stream()
                    .filter(x -> matchedMetricGroupNames.contains(x.getName()))
                    .collect(Collectors.toCollection(ArrayList<SnmpMetricGroup>::new));

            LOG.debug("System with sysObjectId {} will collect following groups {}",
                    sysObjectID, matchedMetricGroupNames);

            return matchedMetricGroups;
        } finally {
            configStateLock.unlock();
        }
    }

    @Override
    public void configStateChanged(SnmpConfigState configState) {
        configStateLock.lock();
        try {
            // state change need to clear the cache, too.
            sysObjectIdToMetricGroupCache.clear();

            this.systemDefinitions = configState.getSystemDefinitions();
            this.metricGroups = configState.getMetricGroups();
        } finally {
            configStateLock.unlock();
        }
    }
}
