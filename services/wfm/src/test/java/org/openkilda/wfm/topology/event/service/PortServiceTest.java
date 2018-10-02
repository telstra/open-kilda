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

import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.LinkProps;
import org.openkilda.model.Port;
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

public class PortServiceTest extends StableAbstractStormTest {
    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static TransactionManager txManager;
    private static LinkPropsRepository linkPropsRepository;
    private static IslRepository islRepository;
    private static PortService portService;
    private static int islCostWhenPortDown;

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

        islCostWhenPortDown = 10000;
        portService = new PortService(txManager, repositoryFactory, islCostWhenPortDown);
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
    public void shouldSetUpIslInInactiveStatusAndSetUpNewCostWhenPortInDownStatus() {
        int cost = 10;
        createLinkProps(cost);
        createIsl(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT, TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT, cost);
        createIsl(TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT, TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT, cost);

        Port port = new Port();
        port.setSwitchId(TEST_SWITCH_B_ID);
        port.setPortNo(TEST_SWITCH_A_PORT);

        portService.processWhenPortIsDown(port);

        Isl isl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT);

        assertEquals(IslStatus.INACTIVE, isl.getStatus());
        assertEquals(islCostWhenPortDown + cost, isl.getCost());

        Isl reverseIsl = islRepository.findByEndpoints(TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT,
                TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT);

        assertEquals(IslStatus.INACTIVE, reverseIsl.getStatus());
        assertEquals(islCostWhenPortDown + cost, reverseIsl.getCost());

        LinkProps linkProps = linkPropsRepository.findByEndpoints(TEST_SWITCH_A_ID, TEST_SWITCH_A_PORT,
                TEST_SWITCH_B_ID, TEST_SWITCH_B_PORT);

        assertEquals((long) (islCostWhenPortDown + cost), linkProps.getProperties().get("cost"));
    }

    private void createIsl(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort, int cost) {
        Isl isl = new Isl();
        isl.setSrcSwitch(createSwitch(srcSwitch));
        isl.setSrcPort(srcPort);
        isl.setDestSwitch(createSwitch(dstSwitch));
        isl.setDestPort(dstPort);
        isl.setCost(cost);
        isl.setActualStatus(IslStatus.ACTIVE);
        isl.setStatus(IslStatus.ACTIVE);

        islRepository.createOrUpdate(isl);
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
