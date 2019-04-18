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

import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
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
    static FlowPathRepository flowPathRepository;
    static SwitchRepository switchRepository;

    private Switch switchA;
    private Switch switchB;

    @BeforeClass
    public static void setUp() {
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory, txManager);
        flowPathRepository = new Neo4jFlowPathRepository(neo4jSessionFactory, txManager);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createSwitches() {
        switchA = buildTestSwitch(TEST_SWITCH_A_ID.getId());
        switchRepository.createOrUpdate(switchA);

        switchB = buildTestSwitch(TEST_SWITCH_B_ID.getId());
        switchRepository.createOrUpdate(switchB);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldCreateFlow() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        Flow foundFlow = allFlows.iterator().next();

        assertEquals(switchA.getSwitchId(), foundFlow.getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundFlow.getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldDeleteFlow() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        flowRepository.delete(flow);

        assertEquals(0, flowRepository.findAll().size());
    }

    @Test
    public void shouldNotDeleteSwitchOnFlowDelete() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        flowRepository.delete(flow);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldUpdateFlow() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flow.setBandwidth(10000);
        flowRepository.createOrUpdate(flow);

        flow.setBandwidth(100);
        flow.setDescription("test_description_updated");
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
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> allFlows = flowRepository.findAll();
        Flow foundFlow = allFlows.iterator().next();

        flowRepository.delete(foundFlow);

        assertEquals(0, flowRepository.findAll().size());
    }

    @Test
    public void shouldCheckForExistence() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        assertTrue(flowRepository.exists(TEST_FLOW_ID));
    }

    @Test
    public void shouldFindFlowById() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        Optional<Flow> foundFlow = flowRepository.findById(TEST_FLOW_ID);
        assertTrue(foundFlow.isPresent());
    }

    @Test
    public void shouldFind2SegmentFlowById() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        Flow flow = buildTestFlowWithIntermediate(TEST_FLOW_ID, switchA, switchC, 100, switchB);
        flowRepository.createOrUpdate(flow);

        Optional<Flow> foundFlow = flowRepository.findById(TEST_FLOW_ID);
        assertTrue(foundFlow.isPresent());
    }

    @Test
    public void shouldFindFlowByGroupId() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flow.setGroupId(TEST_GROUP_ID);
        flowRepository.createOrUpdate(flow);

        List<Flow> foundFlow = Lists.newArrayList(flowRepository.findByGroupId(TEST_GROUP_ID));
        assertThat(foundFlow, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindFlowByEndpoint() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> foundFlows = flowRepository.findByEndpoint(TEST_SWITCH_A_ID, 1);
        Set<String> foundFlowIds = foundFlows.stream().map(foundFlow -> flow.getFlowId()).collect(Collectors.toSet());
        assertThat(foundFlowIds, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindFlowBySwitchEndpoint() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> foundFlows = flowRepository.findByEndpointSwitch(TEST_SWITCH_A_ID);
        Set<String> foundFlowIds = foundFlows.stream().map(foundFlow -> flow.getFlowId()).collect(Collectors.toSet());
        assertThat(foundFlowIds, Matchers.hasSize(1));
    }

    @Test
    public void shouldActiveFlowsWithPortInPath() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        Flow flow = buildTestFlowWithIntermediate(TEST_FLOW_ID, switchA, switchC, 100, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> foundFlows = flowRepository.findActiveFlowsWithPortInPath(TEST_SWITCH_C_ID, 100);
        assertThat(foundFlows, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindActiveFlowsByEndpoint() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> foundFlows = flowRepository.findActiveFlowsWithPortInPath(TEST_SWITCH_A_ID, 1);
        assertThat(foundFlows, Matchers.hasSize(1));
    }

    @Test
    public void shouldNotFindInactiveFlowsByEndpoint() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flow.setStatus(FlowStatus.DOWN);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> foundFlows = flowRepository.findActiveFlowsWithPortInPath(TEST_SWITCH_A_ID, 1);
        assertThat(foundFlows, Matchers.empty());
    }

    @Test
    public void shouldFindDownFlowIdsByEndpoint() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flow.setStatus(FlowStatus.DOWN);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> foundFlows = flowRepository.findDownFlows();
        assertThat(foundFlows, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindFlowForIsl() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<Flow> foundFlow = flowRepository.findWithPathSegment(switchA.getSwitchId(), 1,
                switchB.getSwitchId(), 2);
        assertThat(foundFlow, Matchers.hasSize(1));
        assertThat(foundFlow.iterator().next().getForwardPathId(), Matchers.equalTo(flow.getForwardPathId()));
        assertThat(foundFlow.iterator().next().getReversePathId(), Matchers.equalTo(flow.getReversePathId()));
    }

    @Test
    public void shouldFindActiveFlowsOverSegments() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        Flow flow = buildTestFlowWithIntermediate(TEST_FLOW_ID, switchA, switchC, 100, switchB);
        flowRepository.createOrUpdate(flow);

        Collection<String> foundFlows = flowRepository.findFlowIdsWithSwitchInPath(TEST_SWITCH_C_ID);
        assertThat(foundFlows, Matchers.hasSize(1));
    }

    @Test
    public void shouldCreateFlowGroupIdForFlow() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flow.setGroupId(TEST_GROUP_ID);
        flowRepository.createOrUpdate(flow);

        Optional<String> groupOptional = flowRepository.getOrCreateFlowGroupId(TEST_FLOW_ID);

        assertTrue(groupOptional.isPresent());
        assertNotNull(groupOptional.get());
        assertEquals(groupOptional.get(),
                flowRepository.findById(TEST_FLOW_ID).get().getGroupId());
    }

    @Test
    public void shouldGetFlowGroupIdForFlow() {
        Flow flow = buildTestFlow(TEST_FLOW_ID, switchA, switchB);
        flow.setGroupId(TEST_GROUP_ID);
        flowRepository.createOrUpdate(flow);

        Optional<String> groupOptional = flowRepository.getOrCreateFlowGroupId(TEST_FLOW_ID);

        assertTrue(groupOptional.isPresent());
        assertEquals(TEST_GROUP_ID, groupOptional.get());
    }

    private Flow buildTestFlow(String flowId, Switch srcSwitch, Switch destSwitch) {
        PathSegment forwardSegment = PathSegment.builder()
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .destSwitch(destSwitch)
                .destPort(2)
                .pathId(new PathId(flowId + "_forward_path"))
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_forward_path"))
                .flowId(flowId)
                .cookie(new Cookie(Cookie.FORWARD_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(1))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.singletonList(forwardSegment))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        PathSegment reverseSegment = PathSegment.builder()
                .srcSwitch(destSwitch)
                .srcPort(2)
                .destSwitch(srcSwitch)
                .destPort(1)
                .pathId(new PathId(flowId + "_reverse_path"))
                .build();

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_reverse_path"))
                .flowId(flowId)
                .cookie(new Cookie(Cookie.REVERSE_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(2))
                .srcSwitch(destSwitch)
                .destSwitch(srcSwitch)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.singletonList(reverseSegment))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        return Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .destSwitch(destSwitch)
                .destPort(2)
                .forwardPath(forwardFlowPath)
                .reversePath(reverseFlowPath)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
    }

    private Flow buildTestFlowWithIntermediate(String flowId, Switch srcSwitch,
                                               Switch intSwitch, int intPort, Switch destSwitch) {
        PathSegment forwardSegment = PathSegment.builder()
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .destSwitch(intSwitch)
                .destPort(intPort)
                .pathId(new PathId(flowId + "_forward_path"))
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_forward_path"))
                .flowId(flowId)
                .cookie(new Cookie(Cookie.FORWARD_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(1))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.singletonList(forwardSegment))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        PathSegment reverseSegment = PathSegment.builder()
                .srcSwitch(intSwitch)
                .srcPort(100)
                .destSwitch(srcSwitch)
                .destPort(1)
                .pathId(new PathId(flowId + "_reverse_path"))
                .build();

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_reverse_path"))
                .flowId(flowId)
                .cookie(new Cookie(Cookie.REVERSE_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(2))
                .srcSwitch(destSwitch)
                .destSwitch(srcSwitch)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.singletonList(reverseSegment))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        return Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .destSwitch(destSwitch)
                .destPort(2)
                .forwardPath(forwardFlowPath)
                .reversePath(reverseFlowPath)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
    }
}
