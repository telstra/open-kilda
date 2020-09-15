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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.snmp.collector.collection.data.SnmpCollection;
import org.openkilda.snmp.collector.collection.data.SnmpCollectionGroup;
import org.openkilda.snmp.collector.collection.data.SnmpMetricGroup;
import org.openkilda.snmp.collector.collection.data.SnmpSystemDefinition;
import org.openkilda.snmp.collector.fixtures.SnmpCollectorTestFixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@RunWith(SpringRunner.class)
public class SnmpConfigFileParserTest {

    private static Logger LOG = LoggerFactory.getLogger(SnmpConfigFileParserTest.class);

    SnmpConfigFileParser parser;
    Path collectionConfigPath;
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Before
    public void init() throws Exception {
        URL collectionConfig = getClass().getClassLoader().getResource("sample_snmp_collection.yaml");

        collectionConfigPath = new File(collectionConfig.getFile()).toPath();
        String baseConfigDir = collectionConfigPath.getParent().toString();
        String collectionConfigFile = SnmpCollectorTestFixtures.testCollectionFile;
        String modulesDir = SnmpCollectorTestFixtures.testModuleSubDir;

        LOG.info("Using\nbase dir {}\ncollection config file: {}\nmodule dir: {} for test",
                baseConfigDir, collectionConfigFile, modulesDir);
        parser = new SnmpConfigFileParser(baseConfigDir, collectionConfigFile, modulesDir);

    }

    @Test
    public void testConfigFileParsing() throws Exception {

        // SnmpCollection related structure
        SnmpConfigState configState = parser.getCurrentConfigState();

        SnmpCollection snmpCollection = parser.getSnmpCollection();
        assertEquals("kilda", snmpCollection.getName());
        assertTrue(snmpCollection.isActive());
        assertEquals(Arrays.asList("mib2", "noviflow"), snmpCollection.getCollectionGroups());

        // System definition related
        SnmpSystemDefinition expected = SnmpCollectorTestFixtures.getSystemDefinitionFixture();
        Map<String, SnmpSystemDefinition> systemDefinitionMap = parser.getSystemDefinitions();
        assertTrue(systemDefinitionMap.containsKey("systemdef1"));
        assertEquals(expected, systemDefinitionMap.get("systemdef1"));

        // Metric group related
        SnmpMetricGroup metricGroup = SnmpCollectorTestFixtures.getMetricGroupFixture();
        Map<String, SnmpMetricGroup> metricGroupMap = parser.getMetricGroups();
        assertTrue(metricGroupMap.containsKey("ifTable"));
        assertEquals(metricGroup, metricGroupMap.get("ifTable"));

        File sampleCollectionGroupConfigFile = new File(getClass()
                .getClassLoader().getResource("modules/sample_snmp_collection_group.yaml").getFile());
        Path sampleCollectionGroupConfigPath = sampleCollectionGroupConfigFile.toPath().toAbsolutePath();
        // auxiliary structure
        Map<Path, Set<String>> collectionGroupPathMapping = parser.getCollectionGroupPathMapping();
        Map<Path, Set<String>> systemDefinitionPathMapping = parser.getSystemDefinitionPathMapping();

        assertTrue(collectionGroupPathMapping.containsKey(sampleCollectionGroupConfigPath));
        assertTrue(collectionGroupPathMapping.get(sampleCollectionGroupConfigPath).size() == 1
                && collectionGroupPathMapping.get(sampleCollectionGroupConfigPath).contains("ifTable"));

        assertTrue(systemDefinitionPathMapping.containsKey(sampleCollectionGroupConfigPath));
        assertTrue(systemDefinitionPathMapping.get(sampleCollectionGroupConfigPath).size() == 1
                && systemDefinitionPathMapping.get(sampleCollectionGroupConfigPath).contains("systemdef1"));

        // removing a collection group file
        parser.deleteSnmpCollectionGroupConfig(sampleCollectionGroupConfigPath);
        assertFalse(collectionGroupPathMapping.containsKey(sampleCollectionGroupConfigPath));

        assertFalse(systemDefinitionPathMapping.containsKey(sampleCollectionGroupConfigPath));
    }


    @Test
    public void testGetCurrentConfigState() throws Exception {
        SnmpConfigState configState = parser.getCurrentConfigState();

        SnmpCollectionGroup expectedCollectionGroup = mapper.readValue(
                getClass().getClassLoader().getResourceAsStream("modules/sample_snmp_collection_group.yaml"),
                SnmpCollectionGroup.class
        );

        assertTrue(configState.getSystemDefinitions().size() == 1);
        assertEquals(expectedCollectionGroup.getSystems().get(0), configState.getSystemDefinitions().toArray()[0]);
        assertEquals(expectedCollectionGroup.getGroups().get(0), configState.getMetricGroups().toArray()[0]);
    }

}
