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
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

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
    public void shouldFindPathByFlowIdAndCookie() {
        FlowPath flowPath = buildTestFlowPath(TEST_FLOW_ID, switchA, switchB);
        flowPathRepository.createOrUpdate(flowPath);

        Optional<FlowPath> foundPath = flowPathRepository.findByFlowIdAndCookie(TEST_FLOW_ID, flowPath.getCookie());
        assertTrue(foundPath.isPresent());
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
}
