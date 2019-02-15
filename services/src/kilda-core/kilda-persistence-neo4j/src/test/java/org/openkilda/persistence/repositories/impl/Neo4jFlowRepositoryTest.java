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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Neo4jFlowRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final String TEST_GROUP_ID = "test_group";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);

    static FlowRepository flowRepository;
    static FlowSegmentRepository flowSegmentRepository;
    static SwitchRepository switchRepository;

    private Switch switchA;
    private Switch switchB;

    @BeforeClass
    public static void setUp() {
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory, txManager);
        flowSegmentRepository = new Neo4jFlowSegmentRepository(neo4jSessionFactory, txManager);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createSwitches() {
        switchA = Switch.builder().switchId(TEST_SWITCH_A_ID).build();
        switchRepository.createOrUpdate(switchA);

        switchB = Switch.builder().switchId(TEST_SWITCH_B_ID).build();
        switchRepository.createOrUpdate(switchB);
    }

    @Test
    public void shouldCreateFlow() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        Flow foundFlow = allFlows.iterator().next();

        assertEquals(switchA.getSwitchId(), foundFlow.getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundFlow.getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldCreateSwitchAlongWithFlow() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFlow() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);
        flowRepository.delete(flow);

        assertEquals(0, flowRepository.findAll().size());
    }

    @Test
    public void shouldNotDeleteSwitchOnFlowDelete() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);
        flowRepository.delete(flow);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void testUpdateFlowRelationship() {
        switchA.setDescription("Some description");
        switchRepository.createOrUpdate(switchA);

        Flow flow = new Flow();
        flow.setSrcSwitch(switchA);
        flow.setSrcPort(1);
        flow.setSrcVlan(1);
        flow.setDestSwitch(switchB);
        flow.setDestPort(1);
        flow.setDestVlan(1);
        flow.setFlowId("12");
        flow.setBandwidth(10000);
        flow.setCookie(10222);
        flow.setIgnoreBandwidth(false);
        flowRepository.createOrUpdate(flow);
        Collection<Switch> switches = switchRepository.findAll();
        assertEquals(2, switches.size());
    }

    @Test
    public void shouldUpdateFlow() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .bandwidth(10000)
                .cookie(10222)
                .ignoreBandwidth(false)
                .build();
        flowRepository.createOrUpdate(flow);

        flow.setBandwidth(100);
        flow.setDescription("test_description");
        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        assertThat(allFlows, Matchers.hasSize(1));

        Flow foundFlow = allFlows.iterator().next();
        assertEquals(flow.getSrcSwitch(), foundFlow.getSrcSwitch());
        assertEquals(flow.getDestSwitch(), foundFlow.getDestSwitch());
        assertEquals(flow.getBandwidth(), foundFlow.getBandwidth());
        assertEquals(flow.getDescription(), foundFlow.getDescription());
    }

    @Test
    public void shouldDeleteFoundFlow() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        Flow foundFlow = allFlows.iterator().next();

        flowRepository.delete(foundFlow);

        assertEquals(0, flowRepository.findAll().size());
    }

    @Test
    public void shouldCheckForExistence() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);

        assertTrue(flowRepository.exists(TEST_FLOW_ID));
    }

    @Test
    public void shouldFindFlowById() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);

        List<Flow> foundFlow = Lists.newArrayList(flowRepository.findById(TEST_FLOW_ID));
        assertThat(foundFlow, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindFlowByGroupId() {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .groupId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();

        flowRepository.createOrUpdate(flow);

        List<Flow> foundFlow = Lists.newArrayList(flowRepository.findByGroupId(TEST_FLOW_ID));
        assertThat(foundFlow, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindAllFlowPairs() {
        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .cookie(0x4000000000000001L)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .cookie(0x8000000000000001L)
                .build();

        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        Collection<FlowPair> foundFlowPairs = flowRepository.findAllFlowPairs();
        assertThat(foundFlowPairs, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindFlowPairById() {
        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .cookie(0x4000000000000001L)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .cookie(0x8000000000000001L)
                .build();

        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        FlowPair foundFlowPair = flowRepository.findFlowPairById(TEST_FLOW_ID).get();
        assertThat(foundFlowPair.getForward(), Matchers.equalTo(forwardFlow));
        assertThat(foundFlowPair.getReverse(), Matchers.equalTo(reverseFlow));
    }

    @Test
    public void shouldFindFlowIdsByEndpoint() {
        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .srcPort(2)
                .destSwitch(switchA)
                .destPort(1)
                .build();

        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        Collection<Flow> foundFlows = flowRepository.findFlowIdsByEndpoint(TEST_SWITCH_A_ID, 1);
        Set<String> foundFlowIds = foundFlows.stream().map(flow -> flow.getFlowId()).collect(Collectors.toSet());
        assertThat(foundFlowIds, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindActiveFlowIdsOverSegments() {
        Switch switchC = Switch.builder().switchId(TEST_SWITCH_C_ID).build();
        switchRepository.createOrUpdate(switchC);

        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .status(FlowStatus.UP)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .srcPort(2)
                .srcVlan(11)
                .destSwitch(switchA)
                .destPort(1)
                .destVlan(10)
                .status(FlowStatus.UP)
                .build();

        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        FlowSegment segment = FlowSegment.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .srcPort(1)
                .destSwitch(switchC)
                .destPort(100)
                .build();
        flowSegmentRepository.createOrUpdate(segment);

        Collection<String> foundFlowIds = flowRepository.findActiveFlowIdsWithPortInPath(TEST_SWITCH_C_ID, 100);
        assertThat(foundFlowIds, Matchers.hasSize(1));
    }


    @Test
    public void shouldFindActiveFlowIdsByEndpoint() {
        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .status(FlowStatus.UP)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .srcPort(2)
                .srcVlan(11)
                .destSwitch(switchA)
                .destPort(1)
                .destVlan(10)
                .status(FlowStatus.UP)
                .build();

        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        Collection<String> foundFlowIds = flowRepository.findActiveFlowIdsWithPortInPath(TEST_SWITCH_A_ID, 1);
        assertThat(foundFlowIds, Matchers.hasSize(1));
    }

    @Test
    public void shouldNotFindInactiveFlowIdsByEndpoint() {
        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .status(FlowStatus.DOWN)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .srcPort(2)
                .srcVlan(11)
                .destSwitch(switchA)
                .destPort(1)
                .destVlan(10)
                .status(FlowStatus.DOWN)
                .build();

        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        Collection<String> foundFlowIds = flowRepository.findActiveFlowIdsWithPortInPath(TEST_SWITCH_A_ID, 1);
        assertThat(foundFlowIds, Matchers.empty());
    }

    @Test
    public void shouldFindDownFlowIdsByEndpoint() {
        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .srcVlan(10)
                .destSwitch(switchB)
                .destPort(2)
                .destVlan(11)
                .status(FlowStatus.DOWN)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .srcPort(2)
                .srcVlan(11)
                .destSwitch(switchA)
                .destPort(1)
                .destVlan(10)
                .status(FlowStatus.DOWN)
                .build();

        flowRepository.createOrUpdate(forwardFlow);
        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        Collection<String> foundFlowIds = flowRepository.findDownFlowIds();
        assertThat(foundFlowIds, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindFlowPairForIsl() {
        Flow forwardFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 1L)
                .build();

        Flow reverseFlow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 1L)
                .build();

        flowRepository.createOrUpdate(FlowPair.builder().forward(forwardFlow).reverse(reverseFlow).build());

        FlowSegment segment = FlowSegment.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(100)
                .build();
        flowSegmentRepository.createOrUpdate(segment);

        Collection<FlowPair> foundFlowPair = flowRepository.findAllFlowPairsWithSegment(switchA.getSwitchId(), 1,
                switchB.getSwitchId(), 100);
        assertThat(foundFlowPair, Matchers.hasSize(1));
        assertThat(foundFlowPair.iterator().next().getForward(), Matchers.equalTo(forwardFlow));
        assertThat(foundFlowPair.iterator().next().getReverse(), Matchers.equalTo(reverseFlow));
    }

    @Test
    public void shouldCreateFlowGroupIdForFlow() {
        flowRepository.createOrUpdate(Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 1L)
                .build());
        flowRepository.createOrUpdate(Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchB)
                .srcPort(2)
                .destSwitch(switchA)
                .destPort(1)
                .cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 1L)
                .build());

        Optional<String> groupOptional = flowRepository.getOrCreateFlowGroupId(TEST_FLOW_ID);

        assertTrue(groupOptional.isPresent());
        assertNotNull(groupOptional.get());
        assertEquals(groupOptional.get(),
                flowRepository.findFlowPairById(TEST_FLOW_ID).get().getForward().getGroupId());
    }

    @Test
    public void shouldGetFlowGroupIdForFlow() {
        flowRepository.createOrUpdate(Flow.builder()
                .flowId(TEST_FLOW_ID)
                .groupId(TEST_GROUP_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .cookie(Flow.FORWARD_FLOW_COOKIE_MASK | 1L)
                .build());
        flowRepository.createOrUpdate(Flow.builder()
                .flowId(TEST_FLOW_ID)
                .groupId(TEST_GROUP_ID)
                .srcSwitch(switchB)
                .srcPort(2)
                .destSwitch(switchA)
                .destPort(1)
                .cookie(Flow.REVERSE_FLOW_COOKIE_MASK | 1L)
                .build());

        Optional<String> groupOptional = flowRepository.getOrCreateFlowGroupId(TEST_FLOW_ID);

        assertTrue(groupOptional.isPresent());
        assertEquals(TEST_GROUP_ID, groupOptional.get());
    }
}
