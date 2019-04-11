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
import org.openkilda.model.FlowSegment;
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
import java.util.stream.Collectors;

public class Neo4jFlowSegmentRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final String TEST_FLOW_ID2 = "test_flow_2";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);
    static final int TEST_PORT_A = 1;
    static final int TEST_PORT_B = 2;
    static final int TEST_PORT_C = 3;
    static FlowSegmentRepository flowSegmentRepository;
    static FlowRepository flowRepository;
    static SwitchRepository switchRepository;

    private Switch switchA;
    private Switch switchB;
    private Switch switchC;

    @BeforeClass
    public static void setUp() {
        flowSegmentRepository = new Neo4jFlowSegmentRepository(neo4jSessionFactory, txManager);
        flowRepository = new Neo4jFlowRepository(neo4jSessionFactory, txManager);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createSwitches() {
        switchA = Switch.builder().switchId(TEST_SWITCH_A_ID).build();
        switchRepository.createOrUpdate(switchA);

        switchB = Switch.builder().switchId(TEST_SWITCH_B_ID).build();
        switchRepository.createOrUpdate(switchB);

        switchC = Switch.builder().switchId(TEST_SWITCH_C_ID).build();
        switchRepository.createOrUpdate(switchC);
    }

    @Test
    public void shouldCreateFlowSegment() {
        FlowSegment segment = FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flowId(TEST_FLOW_ID)
                .build();
        flowSegmentRepository.createOrUpdate(segment);

        Collection<FlowSegment> allSegments = flowSegmentRepository.findAll();
        FlowSegment foundSegment = allSegments.iterator().next();

        assertEquals(switchA.getSwitchId(), foundSegment.getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundSegment.getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldCreateSwitchAlongWithFlowSegment() {
        FlowSegment segment = FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flowId(TEST_FLOW_ID)
                .build();
        flowSegmentRepository.createOrUpdate(segment);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFlowSegment() {
        FlowSegment segment = FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flowId(TEST_FLOW_ID)
                .build();
        flowSegmentRepository.createOrUpdate(segment);

        flowSegmentRepository.delete(segment);

        assertEquals(0, flowSegmentRepository.findAll().size());
    }

    @Test
    public void shouldNotDeleteSwitchOnFlowSegmentDelete() {
        FlowSegment segment = FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flowId(TEST_FLOW_ID)
                .build();
        flowSegmentRepository.createOrUpdate(segment);

        flowSegmentRepository.delete(segment);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowSegment() {
        FlowSegment segment = FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flowId(TEST_FLOW_ID)
                .build();
        flowSegmentRepository.createOrUpdate(segment);

        Collection<FlowSegment> allSegments = flowSegmentRepository.findAll();
        FlowSegment foundSegment = allSegments.iterator().next();
        flowSegmentRepository.delete(foundSegment);

        assertEquals(0, flowSegmentRepository.findAll().size());
    }

    @Test
    public void shouldFindSegmentByFlowIdAndCookie() {
        FlowSegment segment = FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .cookie(1)
                .build();
        segment.setFlowId(TEST_FLOW_ID);

        flowSegmentRepository.createOrUpdate(segment);

        List<FlowSegment> foundSegment = Lists.newArrayList(
                flowSegmentRepository.findByFlowIdAndCookie(TEST_FLOW_ID, 1));
        assertThat(foundSegment, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindFlowSegmentsByFlowGroupId() {
        String flowGroup = "flow_group";

        flowSegmentRepository.createOrUpdate(FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flowId(TEST_FLOW_ID)
                .build());
        flowSegmentRepository.createOrUpdate(FlowSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .flowId(TEST_FLOW_ID2)
                .build());

        flowRepository.createOrUpdate(Flow.builder()
                .flowId(TEST_FLOW_ID)
                .groupId(flowGroup)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build());
        flowRepository.createOrUpdate(Flow.builder()
                .flowId(TEST_FLOW_ID2)
                .groupId(flowGroup)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build());

        List<FlowSegment> segments = Lists.newArrayList(flowSegmentRepository.findByFlowGroupId(flowGroup));
        assertThat(segments, Matchers.hasSize(2));
        assertThat(
                segments.stream().map(FlowSegment::getFlowId).collect(Collectors.toSet()),
                Matchers.contains(TEST_FLOW_ID, TEST_FLOW_ID2));
    }

    @Test
    public void shouldFindBothFlowSegmentsForEndpoint() {
        FlowSegment forwardSegment = FlowSegment.builder()
                .srcSwitch(switchA)
                .srcPort(TEST_PORT_A)
                .destSwitch(switchB)
                .destPort(TEST_PORT_B)
                .cookie(1)
                .build();
        forwardSegment.setFlowId(TEST_FLOW_ID);


        flowSegmentRepository.createOrUpdate(forwardSegment);

        FlowSegment reverseSegment = FlowSegment.builder()
                .srcSwitch(switchB)
                .srcPort(TEST_PORT_B)
                .destSwitch(switchA)
                .destPort(TEST_PORT_A)
                .cookie(1)
                .build();
        reverseSegment.setFlowId(TEST_FLOW_ID);


        flowSegmentRepository.createOrUpdate(reverseSegment);


        List<FlowSegment> foundSegment = Lists.newArrayList(
                flowSegmentRepository.findFlowSegmentsByEndpoint(TEST_FLOW_ID, TEST_SWITCH_A_ID, TEST_PORT_A));
        assertThat(foundSegment, Matchers.hasSize(2));
    }

    @Test
    public void shouldFindOnlyFailedSegments() {
        FlowSegment forwardSegment1 = FlowSegment.builder()
                .srcSwitch(switchA)
                .srcPort(TEST_PORT_A)
                .destSwitch(switchB)
                .destPort(TEST_PORT_B)
                .failed(true)
                .cookie(1)
                .build();
        forwardSegment1.setFlowId(TEST_FLOW_ID);
        flowSegmentRepository.createOrUpdate(forwardSegment1);

        FlowSegment reverseSegment1 = FlowSegment.builder()
                .srcSwitch(switchB)
                .srcPort(TEST_PORT_B)
                .destSwitch(switchA)
                .destPort(TEST_PORT_A)
                .failed(true)
                .cookie(1)
                .build();
        reverseSegment1.setFlowId(TEST_FLOW_ID);
        flowSegmentRepository.createOrUpdate(reverseSegment1);

        FlowSegment forwardSegment2 = FlowSegment.builder()
                .srcSwitch(switchB)
                .srcPort(TEST_PORT_B)
                .destSwitch(switchC)
                .destPort(TEST_PORT_C)
                .failed(false)
                .cookie(1)
                .build();
        forwardSegment2.setFlowId(TEST_FLOW_ID);
        flowSegmentRepository.createOrUpdate(forwardSegment2);

        FlowSegment reverseSegment2 = FlowSegment.builder()
                .srcSwitch(switchC)
                .srcPort(TEST_PORT_C)
                .destSwitch(switchB)
                .destPort(TEST_PORT_B)
                .failed(false)
                .cookie(1)
                .build();
        reverseSegment2.setFlowId(TEST_FLOW_ID);
        flowSegmentRepository.createOrUpdate(reverseSegment2);

        Collection<FlowSegment> failedSegments = flowSegmentRepository.findFailedSegmentsForFlow(TEST_FLOW_ID);
        assertThat(failedSegments, Matchers.hasSize(2));
    }
}
