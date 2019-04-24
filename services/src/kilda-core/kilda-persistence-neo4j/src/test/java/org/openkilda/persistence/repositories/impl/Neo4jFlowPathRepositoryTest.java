/* Copyright 2019 Telstra Open Source
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

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
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

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Neo4jFlowPathRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);

    static FlowRepository flowRepository;
    static FlowPathRepository flowPathRepository;
    static SwitchRepository switchRepository;

    private Switch switchA;
    private Switch switchB;
    private Flow flow;

    @BeforeClass
    public static void setUp() {
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory, txManager);
        flowPathRepository = new Neo4jFlowPathRepository(neo4jSessionFactory, txManager);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createSwitchesAndFlow() {
        switchA = buildTestSwitch(TEST_SWITCH_A_ID.getId());
        switchRepository.createOrUpdate(switchA);

        switchB = buildTestSwitch(TEST_SWITCH_B_ID.getId());
        switchRepository.createOrUpdate(switchB);

        assertEquals(2, switchRepository.findAll().size());

        flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flowRepository.createOrUpdate(flow);
    }

    @Test
    public void shouldCreateFlowPaths() {
        Flow flowWithPaths = buildTestFlowPathPair();
        flowPathRepository.createOrUpdate(flowWithPaths.getForwardPath());
        flowPathRepository.createOrUpdate(flowWithPaths.getReversePath());

        Collection<FlowPath> allPaths = flowPathRepository.findAll();
        assertThat(allPaths, hasSize(2));

        FlowPath foundForwardPath = flowPathRepository.findById(flowWithPaths.getForwardPathId()).get();
        assertEquals(switchA.getSwitchId(), foundForwardPath.getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundForwardPath.getDestSwitch().getSwitchId());

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        assertEquals(foundFlow.getFlowId(), foundForwardPath.getFlow().getFlowId());
    }

    @Test
    public void shouldCreateFlowWithPaths() {
        Flow flowWithPaths = buildTestFlowPathPair();
        flowRepository.createOrUpdate(flowWithPaths);

        Collection<FlowPath> allPaths = flowPathRepository.findAll();
        assertThat(allPaths, hasSize(2));

        FlowPath foundForwardPath = flowPathRepository.findById(flowWithPaths.getForwardPathId()).get();
        assertEquals(switchA.getSwitchId(), foundForwardPath.getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundForwardPath.getDestSwitch().getSwitchId());

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        assertEquals(foundFlow.getFlowId(), foundForwardPath.getFlow().getFlowId());
    }

    @Test
    public void shouldFlowPathUpdateKeepRelations() {
        Flow flowWithPaths = buildTestFlowPathPair();
        flowPathRepository.createOrUpdate(flowWithPaths.getForwardPath());
        flowPathRepository.createOrUpdate(flowWithPaths.getReversePath());

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        FlowPath foundPath = flowPathRepository.findById(flowWithPaths.getForwardPathId()).get();
        foundPath.setStatus(FlowPathStatus.INACTIVE);
        flowPathRepository.createOrUpdate(foundPath);

        foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));
    }

    @Test
    public void shouldFlowPathUpdateKeepFlowRelations() {
        Flow flowWithPaths = buildTestFlowPathPair();
        flowRepository.createOrUpdate(flowWithPaths);

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        FlowPath flowPath = foundFlow.getPaths().stream()
                .filter(path -> path.getPathId().equals(flowWithPaths.getReversePathId()))
                .findAny().get();
        flowPath.setStatus(FlowPathStatus.INACTIVE);
        flowPathRepository.createOrUpdate(flowPath);

        foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));
    }

    @Test
    public void shouldDeleteFlowPath() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        flowPathRepository.delete(flowPath);

        assertEquals(0, flowPathRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowPath() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> allPaths = flowPathRepository.findAll();
        FlowPath foundPath = allPaths.iterator().next();
        flowPathRepository.delete(foundPath);

        assertEquals(0, flowPathRepository.findAll().size());
    }

    @Test
    public void shouldFindFindPathById() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findById(flowPath.getPathId());
        assertTrue(foundPath.isPresent());
    }

    @Test
    public void shouldFindPathByFlowIdAndCookie() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findByFlowIdAndCookie(TEST_FLOW_ID, flowPath.getCookie());
        assertTrue(foundPath.isPresent());
    }

    @Test
    public void shouldFindPathByFlowId() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findByFlowId(TEST_FLOW_ID);
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldFindPathBySrc() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySrcSwitch(switchA.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldNotFindPathByWrongSrc() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySrcSwitch(switchB.getSwitchId());
        assertThat(foundPaths, Matchers.empty());
    }

    @Test
    public void shouldFindPathByEndpointSwitch() {
        FlowPath flowPath = buildTestFlowPath();
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findByEndpointSwitch(switchB.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldFindPathBySegmentSwitch() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPathWithIntermediate(switchC, 100);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentSwitch(switchC.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldFindPathBySegmentDestSwitch() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPathWithIntermediate(switchC, 100);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentDestSwitch(switchC.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldNotFindPathByWrongSegmentDestSwitch() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPathWithIntermediate(switchC, 100);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentDestSwitch(switchA.getSwitchId());
        assertThat(foundPaths, Matchers.empty());
    }

    @Test
    public void shouldKeepSegmentsOrdered() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPath();

        List<PathSegment> segments = asList(PathSegment.builder()
                        .srcSwitch(switchA)
                        .destSwitch(switchC)
                        .path(flowPath)
                        .build(),
                PathSegment.builder()
                        .srcSwitch(switchC)
                        .destSwitch(switchB)
                        .path(flowPath)
                        .build());
        flowPath.setSegments(segments);

        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findById(flowPath.getPathId());
        assertEquals(foundPath.get().getSegments().get(0).getDestSwitch(), switchC);
    }

    @Test
    public void shouldIgnorePreviousSegmentsOrder() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPath();

        List<PathSegment> segments = asList(PathSegment.builder()
                        .srcSwitch(switchC)
                        .destSwitch(switchB)
                        .path(flowPath)
                        .build(),
                PathSegment.builder()
                        .srcSwitch(switchA)
                        .destSwitch(switchC)
                        .path(flowPath)
                        .build());
        segments.get(0).setSeqId(2);
        segments.get(1).setSeqId(0);
        flowPath.setSegments(segments);

        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findById(flowPath.getPathId());
        assertEquals(foundPath.get().getSegments().get(0).getDestSwitch(), switchB);
    }

    private FlowPath buildTestFlowPath() {
        FlowPath flowPath = FlowPath.builder()
                .pathId(new PathId(flow.getFlowId() + "_path"))
                .flow(flow)
                .cookie(new Cookie(1))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        flow.setForwardPath(flowPath);

        return flowPath;
    }

    private Flow buildTestFlowPathPair() {
        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(flow.getFlowId() + "_forward"))
                .flow(flow)
                .cookie(new Cookie(1))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(flow.getFlowId() + "_reverse"))
                .flow(flow)
                .cookie(new Cookie(2))
                .meterId(new MeterId(2))
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reversePath);

        return flow;
    }

    private FlowPath buildTestFlowPathWithIntermediate(Switch intSwitch, int intPort) {
        FlowPath flowPath = FlowPath.builder()
                .pathId(new PathId(flow.getFlowId() + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(Cookie.FORWARD_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        flow.setForwardPath(flowPath);

        PathSegment segment1 = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(intSwitch)
                .destPort(intPort)
                .path(flowPath)
                .build();
        PathSegment segment2 = PathSegment.builder()
                .srcSwitch(intSwitch)
                .srcPort(intPort + 100)
                .destSwitch(switchB)
                .destPort(2)
                .path(flowPath)
                .build();
        flowPath.setSegments(asList(segment1, segment2));

        return flowPath;
    }
}
