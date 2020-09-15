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

import org.openkilda.snmp.collector.collection.data.SnmpCollectionGroup;
import org.openkilda.snmp.collector.collection.data.SnmpMetricGroup;
import org.openkilda.snmp.collector.fixtures.SnmpCollectorTestFixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;

public class SnmpConfigManagerTest {

    private static Logger LOG = LoggerFactory.getLogger(SnmpConfigManagerTest.class);

    private SnmpConfigManager configManager;
    private ObjectMapper mapper = new ObjectMapper(new YAMLFactory());


    @Before
    public void init() throws Exception {
        URL url = getClass().getClassLoader().getResource(SnmpCollectorTestFixtures.testCollectionFile);

        Path collectionFilePath = new File(url.getFile()).toPath();
        String collectionFileName = collectionFilePath.getFileName().toString();
        String baseConfigPath = collectionFilePath.getParent().toString();

        LOG.info("Using base dir: {}, collection file name: {} and module path: {} for test",
                baseConfigPath, collectionFileName, baseConfigPath);

        SnmpConfigFileParser parser = new SnmpConfigFileParser(
                baseConfigPath,
                collectionFileName,
                SnmpCollectorTestFixtures.testModuleSubDir
        );

        configManager = new SnmpConfigManager(parser);
    }


    @Test
    public void testGetMatchedMetricGroups() throws Exception {

        Collection<SnmpMetricGroup> matchedGroups = configManager.getMatchedMetricGroups(
                SnmpCollectorTestFixtures.testSysObjectIdMask);

        assertEquals(1, matchedGroups.size());

        SnmpCollectionGroup expectedCollectionGroup = mapper.readValue(
                getClass().getClassLoader().getResourceAsStream(SnmpCollectorTestFixtures.testCollectionGroupFile),
                SnmpCollectionGroup.class
        );

        assertEquals(expectedCollectionGroup.getGroups().get(0), matchedGroups.toArray()[0]);

    }


}
