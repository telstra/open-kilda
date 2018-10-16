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
import static org.junit.Assert.assertThat;

import org.openkilda.model.Flow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Neo4jFlowRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    static FlowRepository flowRepository;
    static SwitchRepository switchRepository;

    @BeforeClass
    public static void setUp() {
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory);
    }

    @Test
    public void shouldCreateFindAndDeleteFlow() {
        Switch switchA = new Switch();
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        Flow flow = new Flow();
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchB);

        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        assertEquals(1, allFlows.size());
        Flow foundFlow = allFlows.iterator().next();

        assertEquals(switchA.getSwitchId(), foundFlow.getSrcSwitchId());
        assertEquals(switchB.getSwitchId(), foundFlow.getDestSwitchId());
        assertEquals(2, switchRepository.findAll().size());

        flowRepository.delete(flow);
        assertEquals(0, flowRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldCreateAndFindFlowById() {
        Switch switchA = new Switch();
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        Flow flow = new Flow();
        flow.setFlowId(TEST_FLOW_ID);
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchB);

        flowRepository.createOrUpdate(flow);

        List<Flow> foundFlow = StreamSupport.stream(flowRepository.findById(TEST_FLOW_ID).spliterator(), false)
                .collect(Collectors.toList());
        assertThat(foundFlow, Matchers.hasSize(1));
    }
}
