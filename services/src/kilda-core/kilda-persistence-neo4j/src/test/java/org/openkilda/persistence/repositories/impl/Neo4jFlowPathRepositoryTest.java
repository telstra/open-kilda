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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class Neo4jFlowPathRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);

    static FlowPathRepository flowPathRepository;
    static SwitchRepository switchRepository;

    private Switch switchA;
    private Switch switchB;

    @BeforeClass
    public static void setUp() {
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
    public void shouldCreateFlowPath() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> allPaths = flowPathRepository.findAll();
        FlowPath foundPath = allPaths.iterator().next();

        assertEquals(switchA.getSwitchId(), foundPath.getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundPath.getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldDeleteFlowPath() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        flowPathRepository.delete(flowPath);

        assertEquals(0, flowPathRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowPath() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> allPaths = flowPathRepository.findAll();
        FlowPath foundPath = allPaths.iterator().next();
        flowPathRepository.delete(foundPath);

        assertEquals(0, flowPathRepository.findAll().size());
    }

    @Test
    public void shouldFindFindPathById() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findById(flowPath.getPathId());
        assertTrue(foundPath.isPresent());
    }

    @Test
    public void shouldFindPathByFlowIdAndCookie() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findByFlowIdAndCookie(TEST_FLOW_ID, flowPath.getCookie());
        assertTrue(foundPath.isPresent());
    }

    @Test
    public void shouldFindPathByFlowId() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findByFlowId(TEST_FLOW_ID);
        assertThat(foundPaths, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindPathBySrc() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySrcSwitch(switchA.getSwitchId());
        assertThat(foundPaths, Matchers.hasSize(1));
    }

    @Test
    public void shouldNotFindPathByWrongSrc() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySrcSwitch(switchB.getSwitchId());
        assertThat(foundPaths, Matchers.empty());
    }

    @Test
    public void shouldFindPathByEndpointSwitch() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findByEndpointSwitch(switchB.getSwitchId());
        assertThat(foundPaths, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindPathBySegmentSwitch() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPathWithIntermediate(TEST_FLOW_ID, switchA, switchC, 100, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentSwitch(switchC.getSwitchId());
        assertThat(foundPaths, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindPathBySegmentDestSwitch() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPathWithIntermediate(TEST_FLOW_ID, switchA, switchC, 100, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentDestSwitch(switchC.getSwitchId());
        assertThat(foundPaths, Matchers.hasSize(1));
    }

    @Test
    public void shouldNotFindPathByWrongSegmentDestSwitch() {
        Switch switchC = buildTestSwitch(TEST_SWITCH_C_ID.getId());
        switchRepository.createOrUpdate(switchC);

        FlowPath flowPath = buildTestFlowPathWithIntermediate(TEST_FLOW_ID, switchA, switchC, 100, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentDestSwitch(switchA.getSwitchId());
        assertThat(foundPaths, Matchers.empty());
    }

    private FlowPath buildTestFlowPath(String flowId, Switch srcSwitch, Switch destSwitch) {
        return FlowPath.builder()
                .pathId(new PathId(flowId + "_path"))
                .flowId(flowId)
                .cookie(new Cookie(1))
                .meterId(new MeterId(1))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
    }

    private FlowPath buildTestFlowPathWithIntermediate(String flowId, Switch srcSwitch,
                                                       Switch intSwitch, int intPort, Switch destSwitch) {
        PathSegment segment = PathSegment.builder()
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .destSwitch(intSwitch)
                .destPort(intPort)
                .pathId(new PathId(flowId + "_forward_path"))
                .build();

        return FlowPath.builder()
                .pathId(new PathId(flowId + "_forward_path"))
                .flowId(flowId)
                .cookie(new Cookie(Cookie.FORWARD_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(1))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.singletonList(segment))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
    }
}
