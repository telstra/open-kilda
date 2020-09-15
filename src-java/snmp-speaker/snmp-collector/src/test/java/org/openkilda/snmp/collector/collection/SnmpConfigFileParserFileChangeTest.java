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

import org.openkilda.snmp.collector.collection.data.SnmpSystemDefinition;
import org.openkilda.snmp.collector.fixtures.SnmpCollectorTestFixtures;

import com.google.common.io.Files;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class SnmpConfigFileParserFileChangeTest {

    private static Logger LOG = LoggerFactory.getLogger(SnmpConfigFileParserFileChangeTest.class);

    private SnmpConfigFileParser parser;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    SnmpConfigStateChangeObserver observer;

    @Before
    public void init() throws Exception {

        // prepare the test environment: create modules folder and add the collection config file
        Path baseConfigPath = tempFolder.getRoot().toPath();
        File modulesFolder = tempFolder.newFolder(SnmpCollectorTestFixtures.testModuleSubDir);

        URL url = getClass().getClassLoader().getResource(SnmpCollectorTestFixtures.testCollectionFile);
        File sampleCollectionFile = new File(url.getFile());
        File targetFile = baseConfigPath.resolve(SnmpCollectorTestFixtures.testCollectionFile).toFile();
        Files.copy(sampleCollectionFile, targetFile);


        LOG.info("Using base dir: {}, collection file name: {} and module path: {} for test",
                baseConfigPath.toString(), SnmpCollectorTestFixtures.testCollectionFile, modulesFolder);

        parser = new SnmpConfigFileParser(
                baseConfigPath.toString(),
                SnmpCollectorTestFixtures.testCollectionFile,
                SnmpCollectorTestFixtures.testModuleSubDir
        );

        // mock setup.
        MockitoAnnotations.initMocks(this);
        parser.addConfigStateObserver(observer);
    }

    @Test
    public void whenNewGroupFileAdded_Observer_notified() throws Exception {

        // just trigger initial parsing
        parser.getCurrentConfigState();

        URL url = getClass().getClassLoader().getResource(SnmpCollectorTestFixtures.testCollectionGroupFile);
        File sampleCollectionGroupFile = new File(url.getFile());

        File targetFile = tempFolder.getRoot().toPath().resolve(
                SnmpCollectorTestFixtures.testCollectionGroupFile)
                .toFile();

        LOG.info("Copying {} to {}", sampleCollectionGroupFile.toString(), targetFile.toString());
        Files.copy(sampleCollectionGroupFile, targetFile);

        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            LOG.info("Ignoring interrupted exception in test");
        }

        ArgumentCaptor<SnmpConfigState> captor = ArgumentCaptor.forClass(SnmpConfigState.class);
        Mockito.verify(observer).configStateChanged(captor.capture());

        SnmpConfigState configState = captor.getValue();
        assertEquals(SnmpCollectorTestFixtures.getSystemDefinitionFixture(),
                configState.getSystemDefinitions().toArray(new SnmpSystemDefinition[0])[0]);
        assertEquals(SnmpCollectorTestFixtures.getMetricGroupFixture(),
                configState.getMetricGroups().toArray()[0]);
    }
}
