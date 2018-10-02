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

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.LinkPropsRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class IslServiceTest extends StableAbstractStormTest {
    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static TransactionManager txManager;
    private static LinkPropsRepository linkPropsRepository;
    private static IslRepository islRepository;
    private static IslService islService;

    private static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    private static final int TEST_SWITCH_A_PORT = 1;
    private static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    private static final int TEST_SWITCH_B_PORT = 1;

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
        linkPropsRepository = repositoryFactory.createLinkPropsRepository();
        islRepository = repositoryFactory.createIslRepository();

        islService = new IslService(txManager, repositoryFactory);
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
    public void shouldCreateAndThenUpdateIsl() {
        int cost = 10;
        createLinkProps(cost);

        Isl isl = createIsl();

        islService.createOrUpdateIsl(isl);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT);

        assertNotNull(foundIsl);
        assertEquals(IslStatus.INACTIVE, foundIsl.getStatus());
        assertEquals(IslStatus.ACTIVE, foundIsl.getActualStatus());
        assertEquals(cost, foundIsl.getCost());

        Isl foundReverseIsl = islRepository.findByEndpoints(TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT,
                TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT);

        assertNotNull(foundReverseIsl);
        assertEquals(IslStatus.INACTIVE, foundReverseIsl.getStatus());
        assertEquals(IslStatus.INACTIVE, foundReverseIsl.getActualStatus());

        islService.createOrUpdateIsl(foundReverseIsl);

        foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT);

        assertEquals(IslStatus.ACTIVE, foundIsl.getStatus());

    }

    @Test
    public void shouldPutIslInMovedStatus() {
        Isl isl = createIsl();

        islService.createOrUpdateIsl(isl);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT);

        assertNotNull(foundIsl);

        islService.islDiscoveryFailed(isl);

        foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT);

        assertEquals(IslStatus.MOVED, foundIsl.getStatus());
    }

    private Isl createIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(createSwitch(TEST_SWITCH_A_ID));
        isl.setSrcPort(TEST_SWITCH_A_PORT);
        isl.setDestSwitch(createSwitch(TEST_SWITCH_B_ID));
        isl.setDestPort(TEST_SWITCH_B_PORT);

        return isl;
    }

    private Switch createSwitch(SwitchId switchId) {
        Switch sw = new Switch();
        sw.setSwitchId(switchId);
        sw.setStatus(SwitchStatus.ACTIVE);
        return sw;
    }

    private void createLinkProps(int cost) {
        LinkProps linkProps = new LinkProps();
        linkProps.setSrcSwitchId(TEST_SWITCH_A_ID);
        linkProps.setSrcPort(TEST_SWITCH_A_PORT);
        linkProps.setDstSwitchId(TEST_SWITCH_B_ID);
        linkProps.setDstPort(TEST_SWITCH_B_PORT);

        Map<String, Object> properties = new HashMap<>();
        properties.put("cost", (long) cost);
        linkProps.setProperties(properties);

        linkPropsRepository.createOrUpdate(linkProps);
    }

}
