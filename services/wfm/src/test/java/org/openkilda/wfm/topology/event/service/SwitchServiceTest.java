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

package org.openkilda.wfm.topology.event.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSessionFactory;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.EmbeddedNeo4jDatabase;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.StableAbstractStormTest;
import org.openkilda.wfm.config.provider.ConfigurationProvider;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Properties;

public class SwitchServiceTest extends StableAbstractStormTest {
    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static TransactionManager txManager;
    private static SwitchRepository switchRepository;
    private static SwitchService switchService;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpOnce() throws Exception {
        StableAbstractStormTest.startCompleteTopology();

        embeddedNeo4jDb = new EmbeddedNeo4jDatabase(fsData.getRoot());

        LaunchEnvironment launchEnvironment = makeLaunchEnvironment();
        Properties configOverlay = new Properties();
        configOverlay.setProperty("neo4j.uri", embeddedNeo4jDb.getConnectionUri());

        launchEnvironment.setupOverlay(configOverlay);

        ConfigurationProvider configurationProvider = launchEnvironment.getConfigurationProvider();
        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);

        txManager = persistenceManager.getTransactionManager();

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchService = new SwitchService(txManager, repositoryFactory);
    }

    @AfterClass
    public static void tearDown() {
        embeddedNeo4jDb.stop();
    }

    @After
    public void cleanUp() {
        ((Neo4jSessionFactory) txManager).getSession().purgeDatabase();
    }

    @Test
    public void shouldCreateAndThenUpdateSwitch() {
        SwitchId switchId = new SwitchId("ff:01");
        String description = "test";
        Switch sw = createSwitch(switchId, description, SwitchStatus.ACTIVE);
        switchService.createOrUpdateSwitch(sw);

        Switch foundSwitch = switchRepository.findBySwitchId(switchId);

        assertNotNull(foundSwitch);
        assertEquals(switchId, foundSwitch.getSwitchId());
        assertEquals(description, foundSwitch.getDescription());

        description = "description";
        sw = createSwitch(switchId, description, SwitchStatus.ACTIVE);
        switchService.createOrUpdateSwitch(sw);

        foundSwitch = switchRepository.findBySwitchId(switchId);
        assertEquals(description, foundSwitch.getDescription());
    }

    @Test
    public void shouldDeactivateAndActivateSwitch() {
        SwitchId switchId = new SwitchId("ff:01");
        String description = "test";
        Switch sw = createSwitch(switchId, description, SwitchStatus.ACTIVE);
        switchService.createOrUpdateSwitch(sw);

        switchService.deactivateSwitch(sw);
        Switch foundSwitch = switchRepository.findBySwitchId(switchId);

        assertEquals(SwitchStatus.INACTIVE, foundSwitch.getStatus());

        switchService.activateSwitch(sw);
        foundSwitch = switchRepository.findBySwitchId(switchId);

        assertEquals(SwitchStatus.ACTIVE, foundSwitch.getStatus());
    }

    private Switch createSwitch(SwitchId switchId, String description, SwitchStatus switchStatus) {
        Switch sw = new Switch();
        sw.setSwitchId(switchId);
        sw.setAddress("address");
        sw.setHostname("hostname");
        sw.setDescription(description);
        sw.setStatus(switchStatus);
        return sw;
    }
}
