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
import static org.junit.Assert.assertFalse;
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
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class Neo4jSwitchRepositoryTest extends Neo4jBasedTest {
    private static final SwitchId TEST_SWITCH_ID_A = new SwitchId(1);
    private static final SwitchId TEST_SWITCH_ID_B = new SwitchId(2);
    private static final SwitchId TEST_SWITCH_ID_C = new SwitchId(3);
    private static final String TEST_FLOW_ID_A = "test_flow_id_a";
    private static final String TEST_FLOW_ID_B = "test_flow_id_b";

    private static SwitchRepository switchRepository;
    private static FlowRepository flowRepository;

    @BeforeClass
    public static void setUp() {
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateSwitch() {
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID_A)
                .description("Some description").build();

        switchRepository.createOrUpdate(origSwitch);

        assertEquals(1, switchRepository.findAll().size());
    }

    @Test
    public void shouldFindSwitchById() {
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID_A)
                .description("Some description").build();

        switchRepository.createOrUpdate(origSwitch);

        Switch foundSwitch = switchRepository.findById(TEST_SWITCH_ID_A).get();
        assertEquals(origSwitch.getDescription(), foundSwitch.getDescription());
    }

    @Test
    public void shouldFindSwitchesByFlowId() {
        createTwoFlows();

        Collection<SwitchId> switches = switchRepository.findSwitchesInFlowPathByFlowId(TEST_FLOW_ID_A).stream()
                .map(Switch::getSwitchId)
                .collect(Collectors.toList());

        assertEquals(2, switches.size());
        assertTrue(switches.contains(TEST_SWITCH_ID_A));
        assertTrue(switches.contains(TEST_SWITCH_ID_B));
        assertFalse(switches.contains(TEST_SWITCH_ID_C));
    }

    @Test
    public void shouldFindSwitchOfOneSwitchFlowByFlowId() {
        createOneSwitchFlow();

        Collection<SwitchId> switches = switchRepository.findSwitchesInFlowPathByFlowId(TEST_FLOW_ID_A).stream()
                .map(Switch::getSwitchId)
                .collect(Collectors.toList());

        assertEquals(1, switches.size());
        assertTrue(switches.contains(TEST_SWITCH_ID_A));
    }

    @Test
    public void shouldDeleteSwitch() {
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID_A)
                .description("Some description").build();

        switchRepository.createOrUpdate(origSwitch);
        switchRepository.delete(origSwitch);

        assertEquals(0, switchRepository.findAll().size());
    }

    private void createOneSwitchFlow() {
        Switch switchA = Switch.builder().switchId(TEST_SWITCH_ID_A).build();
        switchRepository.createOrUpdate(switchA);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_A)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchA)
                .destPort(2)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(Cookie.FORWARD_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardFlowPath);

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_reverse_path"))
                .flow(flow)
                .cookie(new Cookie(Cookie.REVERSE_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(2))
                .srcSwitch(switchA)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reverseFlowPath);

        flowRepository.createOrUpdate(flow);
    }

    private void createTwoFlows() {
        Switch switchA = Switch.builder().switchId(TEST_SWITCH_ID_A).build();
        switchRepository.createOrUpdate(switchA);
        Switch switchB = Switch.builder().switchId(TEST_SWITCH_ID_B).build();
        switchRepository.createOrUpdate(switchB);
        Switch switchC = Switch.builder().switchId(TEST_SWITCH_ID_C).build();
        switchRepository.createOrUpdate(switchC);

        //create flow TEST_FLOW_ID_A
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_A)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(4)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(Cookie.FORWARD_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardFlowPath);

        PathSegment forwardSegment = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(2)
                .destSwitch(switchB)
                .destPort(3)
                .path(forwardFlowPath)
                .build();
        forwardFlowPath.setSegments(Collections.singletonList(forwardSegment));

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_reverse_path"))
                .flow(flow)
                .cookie(new Cookie(Cookie.REVERSE_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(2))
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reverseFlowPath);

        PathSegment reverseSegment = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(3)
                .destSwitch(switchA)
                .destPort(2)
                .path(reverseFlowPath)
                .build();
        reverseFlowPath.setSegments(Collections.singletonList(reverseSegment));

        flowRepository.createOrUpdate(flow);

        //create flow TEST_FLOW_ID_B
        flow = Flow.builder()
                .flowId(TEST_FLOW_ID_B)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchC)
                .destPort(7)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(Cookie.FORWARD_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(3))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardFlowPath);

        PathSegment forwardSegmentA = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(2)
                .destSwitch(switchB)
                .destPort(3)
                .path(forwardFlowPath)
                .build();

        PathSegment forwardSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(5)
                .destSwitch(switchC)
                .destPort(6)
                .path(forwardFlowPath)
                .build();
        forwardFlowPath.setSegments(Lists.newArrayList(forwardSegmentA, forwardSegmentB));

        reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_reverse_path"))
                .flow(flow)
                .cookie(new Cookie(Cookie.REVERSE_FLOW_COOKIE_MASK | 1L))
                .meterId(new MeterId(4))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reverseFlowPath);

        PathSegment reverseSegmentA = PathSegment.builder()
                .srcSwitch(switchC)
                .srcPort(6)
                .destSwitch(switchB)
                .destPort(5)
                .path(reverseFlowPath)
                .build();

        PathSegment reverseSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(3)
                .destSwitch(switchA)
                .destPort(2)
                .path(reverseFlowPath)
                .build();
        reverseFlowPath.setSegments(Lists.newArrayList(reverseSegmentA, reverseSegmentB));

        flowRepository.createOrUpdate(flow);
    }
}
