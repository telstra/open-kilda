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
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.ogm.testutil.TestServer;

import java.util.Collection;

public class FlowRepositoryImplTest {
    static final String TEST_SWITCH_A_NAME = "SwitchA";
    static final String TEST_SWITCH_B_NAME = "SwitchB";

    static TestServer testServer;
    static FlowRepository flowRepository;
    static SwitchRepository switchRepository;

    @BeforeClass
    public static void setUp() {
        testServer = new TestServer(true, true, 5, 7687);
        flowRepository = new FlowRepositoryImpl();
        switchRepository = new SwitchRepositoryImpl();
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @Test
    public void shouldCreateAndFindFlow() {
        Switch aSwitch = new Switch();
        aSwitch.setSwitchId(new SwitchId(1));
        aSwitch.setName(TEST_SWITCH_A_NAME);
        aSwitch.setDescription("Some description");

        Switch bSwitch = new Switch();
        bSwitch.setSwitchId(new SwitchId(2));
        bSwitch.setName(TEST_SWITCH_B_NAME);

        Flow flow = new Flow();
        flow.setSrcSwitch(aSwitch);
        flow.setDestSwitch(bSwitch);

        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        assertEquals(1, allFlows.size());
        Flow foundFlow = allFlows.iterator().next();

        assertEquals(aSwitch.getSwitchId(), foundFlow.getSrcSwitchId());
        assertEquals(2, switchRepository.findAll().size());

        Switch foundSwitch = switchRepository.findByName(TEST_SWITCH_A_NAME);
        assertEquals(aSwitch.getSwitchId(), foundSwitch.getSwitchId());
        assertEquals(aSwitch.getDescription(), foundSwitch.getDescription());

        flowRepository.delete(flow);
        assertEquals(0, flowRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());
    }
}