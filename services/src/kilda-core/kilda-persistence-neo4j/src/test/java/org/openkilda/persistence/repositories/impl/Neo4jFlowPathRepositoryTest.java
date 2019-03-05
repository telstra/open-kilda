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

import static org.hamcrest.Matchers.containsInAnyOrder;
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
import java.util.Optional;
import java.util.UUID;

public class Neo4jFlowPathRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    static FlowPathRepository flowPathRepository;
    static FlowRepository flowRepository;
    static SwitchRepository switchRepository;

    private Switch switchA;
    private Switch switchB;

    @BeforeClass
    public static void setUp() {
        flowPathRepository = new Neo4jFlowPathRepository(neo4jSessionFactory, txManager);
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory, txManager);
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
    public void shouldFindPathByFlowIdAndCookie() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findByFlowIdAndCookie(TEST_FLOW_ID, flowPath.getCookie());
        assertTrue(foundPath.isPresent());
    }

    @Test
    public void shouldFindByEndpointSwitch() {
        FlowPath primaryForward = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        FlowPath primaryReverse = buildTestFlowPath(TEST_FLOW_ID, switchB, switchA);

        flowPathRepository.createOrUpdate(primaryForward);
        flowPathRepository.createOrUpdate(primaryReverse);
        flowRepository.createOrUpdate(buildFlow(TEST_FLOW_ID, switchA, switchB, primaryForward, primaryReverse, null));

        Collection<FlowPath> paths = flowPathRepository.findByEndpointSwitch(switchA.getSwitchId());
        assertThat(paths, containsInAnyOrder(primaryForward, primaryReverse));
    }

    @Test
    public void shouldNotFindProtectedIngressByEndpointSwitch() {
        FlowPath primaryForward = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        FlowPath primaryReverse = buildTestFlowPath(TEST_FLOW_ID, switchB, switchA);
        FlowPath protect = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);

        flowPathRepository.createOrUpdate(primaryForward);
        flowPathRepository.createOrUpdate(primaryReverse);
        flowPathRepository.createOrUpdate(protect);
        flowRepository.createOrUpdate(
                buildFlow(TEST_FLOW_ID, switchA, switchB, primaryForward, primaryReverse, protect));

        Collection<FlowPath> paths = flowPathRepository.findByEndpointSwitch(switchA.getSwitchId());
        assertThat(paths, containsInAnyOrder(primaryForward, primaryReverse));
    }

    @Test
    public void shouldFindBySrcSwitch() {
        FlowPath primaryForward = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        FlowPath primaryReverse = buildTestFlowPath(TEST_FLOW_ID, switchB, switchA);

        flowPathRepository.createOrUpdate(primaryForward);
        flowPathRepository.createOrUpdate(primaryReverse);
        flowRepository.createOrUpdate(buildFlow(TEST_FLOW_ID, switchA, switchB, primaryForward, primaryReverse, null));

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchA.getSwitchId());
        assertThat(paths, containsInAnyOrder(primaryForward));
    }

    @Test
    public void shouldFindFlowPathsForIsl() {
        FlowPath primaryForward = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        FlowPath primaryReverse = buildTestFlowPath(TEST_FLOW_ID, switchB, switchA);

        flowPathRepository.createOrUpdate(primaryForward);
        flowPathRepository.createOrUpdate(primaryReverse);
        flowRepository.createOrUpdate(
                buildFlow(TEST_FLOW_ID, switchA, switchB, primaryForward, primaryReverse, null));

        Collection<FlowPath> paths = flowPathRepository.findWithPathSegment(switchA.getSwitchId(), 1,
                switchB.getSwitchId(), 1);
        assertThat(paths, Matchers.hasSize(1));
        assertThat(paths, containsInAnyOrder(primaryForward));
    }

    @Test
    public void shouldFindActiveAffectedPaths() {
        FlowPath path1 = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        FlowPath path2 = buildTestFlowPath(TEST_FLOW_ID, switchB, switchA);
        path2.setStatus(FlowPathStatus.IN_PROGRESS);

        flowPathRepository.createOrUpdate(path1);
        flowPathRepository.createOrUpdate(path2);
        flowRepository.createOrUpdate(buildFlow(TEST_FLOW_ID, switchA, switchB, path1, path2, null));

        Collection<FlowPath> paths = flowPathRepository.findActiveAffectedPaths(
                switchA.getSwitchId(), 1);
        assertThat(paths, containsInAnyOrder(path1));
    }

    private FlowPath buildTestFlowPath(String flowId, Switch srcSwitch, Switch destSwitch) {
        PathId pathId = new PathId(UUID.randomUUID().toString());
        return FlowPath.builder()
                .pathId(pathId)
                .flowId(flowId)
                .cookie(new Cookie(1))
                .meterId(new MeterId(1))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.singletonList(PathSegment.builder()
                        .srcSwitch(srcSwitch)
                        .srcPort(1)
                        .destSwitch(destSwitch)
                        .destPort(1)
                        .pathId(pathId)
                        .build()))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
    }

    private Flow buildFlow(String flowId, Switch srcSwitch, Switch destSwitch, FlowPath primaryForward,
                           FlowPath primaryReverse, FlowPath protect) {
        return Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(1)
                .destSwitch(destSwitch)
                .destPort(1)
                .forwardPath(primaryForward)
                .reversePath(primaryReverse)
                .protectedForwardPath(protect)
                .allocateProtectedPath(protect != null)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
    }
}
