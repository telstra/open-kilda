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

package org.openkilda.wfm.topology.reroute.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Node;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class RerouteServiceTest extends StableAbstractStormTest {
    private static EmbeddedNeo4jDatabase embeddedNeo4jDb;
    private static TransactionManager txManager;
    private static SwitchRepository switchRepository;
    private static FlowRepository flowRepository;
    private static FlowSegmentRepository flowSegmentRepository;
    private static RerouteService rerouteService;

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
        flowRepository = repositoryFactory.createFlowRepository();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();

        rerouteService = new RerouteService(txManager, repositoryFactory);
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
    public void shouldGetAffectedFlowIdsAndSetUpFlowInDownStatus() {
        String switchStart = "02:";
        int index = 1;
        Switch nodeA = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + String.format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + String.format("%02X", index));

        String flowId = "flow";
        createFlow(flowId, nodeA, nodeC, 1, 1, FlowStatus.UP);
        createFlow(flowId, nodeC, nodeA, 1, 1, FlowStatus.UP);
        createFlowSegment(flowId, nodeA, nodeB, 1, 1);
        createFlowSegment(flowId, nodeB, nodeA, 1, 1);
        createFlowSegment(flowId, nodeB, nodeC, 2, 1);
        createFlowSegment(flowId, nodeC, nodeB, 1, 2);

        Node node = new Node();
        node.setSwitchId(nodeB.getSwitchId());
        node.setPortNo(1);

        Set<String> affectedFlowIds = rerouteService.getAffectedFlowIds(node);

        List<Flow> flows = new ArrayList<>();
        flowRepository.findById(flowId).forEach(flows::add);

        assertEquals(2, flows.size());
        assertEquals(FlowStatus.DOWN, flows.get(0).getStatus());

        assertTrue(affectedFlowIds.contains(flowId));
    }

    private Switch createSwitch(String name) {
        Switch sw = new Switch();
        sw.setSwitchId(new SwitchId(name));
        sw.setStatus(SwitchStatus.ACTIVE);

        switchRepository.createOrUpdate(sw);
        return sw;
    }

    private void createFlow(String flowId, Switch src, Switch dst, int srcPort, int dstPort, FlowStatus flowStatus) {
        Flow flow = new Flow();
        flow.setFlowId(flowId);
        flow.setSrcSwitch(src);
        flow.setSrcPort(srcPort);
        flow.setDestSwitch(dst);
        flow.setDestPort(dstPort);
        flow.setStatus(flowStatus);

        flowRepository.createOrUpdate(flow);
    }

    private void createFlowSegment(String flowId, Switch src, Switch dst, int srcPort, int dstPort) {
        FlowSegment fs = new FlowSegment();
        fs.setFlowId(flowId);
        fs.setSrcSwitch(src);
        fs.setSrcPort(srcPort);
        fs.setDestSwitch(dst);
        fs.setDestPort(dstPort);

        flowSegmentRepository.createOrUpdate(fs);
    }

}
