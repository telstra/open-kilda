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

package org.openkilda.persistence.repositories.impl;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TestConfigurationProvider;
import org.openkilda.persistence.neo4j.Neo4jConfig;
import org.openkilda.persistence.neo4j.Neo4jTransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.ogm.testutil.TestServer;

import java.io.IOException;
import java.util.Collection;

public class FlowRepositoryImplTest {
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    static TestServer testServer;
    static FlowRepository flowRepository;
    static SwitchRepository switchRepository;

    @BeforeClass
    public static void setUp() throws IOException {
        testServer = new TestServer(true, true, 5, 7687);

        Neo4jConfig neo4jConfig = new TestConfigurationProvider().getConfiguration(Neo4jConfig.class);
        Neo4jTransactionManager txManager = new Neo4jTransactionManager(neo4jConfig);

        flowRepository = new FlowRepositoryImpl(txManager);
        switchRepository = new SwitchRepositoryImpl(txManager);
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @Test
    public void shouldCreateAndFindFlow() {
        Switch switchA = new Switch();
        switchA.setSwitchId(new SwitchId(1));
        switchA.setSwitchId(TEST_SWITCH_A_ID);
        switchA.setDescription("Some description");

        Switch switchB = new Switch();
        switchB.setSwitchId(new SwitchId(2));
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        Flow flow = new Flow();
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchB);

        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        assertEquals(1, allFlows.size());
        Flow foundFlow = allFlows.iterator().next();

        assertEquals(switchA.getSwitchId(), foundFlow.getSrcSwitchId());
        assertEquals(2, switchRepository.findAll().size());

        Switch foundSwitch = switchRepository.findBySwitchId(TEST_SWITCH_A_ID);
        assertEquals(switchA.getSwitchId(), foundSwitch.getSwitchId());
        assertEquals(switchA.getDescription(), foundSwitch.getDescription());

        flowRepository.delete(flow);
        assertEquals(0, flowRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());
    }
}
