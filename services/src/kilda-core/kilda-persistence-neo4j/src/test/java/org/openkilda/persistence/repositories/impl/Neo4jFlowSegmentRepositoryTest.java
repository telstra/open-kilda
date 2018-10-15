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

import org.openkilda.model.FlowSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Neo4jFlowSegmentRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    static FlowSegmentRepository flowSegmentRepository;
    static SwitchRepository switchRepository;

    @BeforeClass
    public static void setUp() {
        flowSegmentRepository = new Neo4jFlowSegmentRepository(txManager);
        switchRepository = new Neo4jSwitchRepository(txManager);
    }

    @Test
    public void shouldCreateFindAndDeleteSegment() {
        Switch switchA = new Switch();
        switchA.setSwitchId(new SwitchId(1));
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(new SwitchId(2));
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        FlowSegment segment = new FlowSegment();
        segment.setSrcSwitch(switchA);
        segment.setDestSwitch(switchB);
        segment.setFlowId(TEST_FLOW_ID);

        flowSegmentRepository.createOrUpdate(segment);

        Collection<FlowSegment> allSegments = flowSegmentRepository.findAll();
        assertEquals(1, allSegments.size());
        FlowSegment foundSegment = allSegments.iterator().next();

        assertEquals(switchA.getSwitchId(), foundSegment.getSrcSwitchId());
        assertEquals(switchB.getSwitchId(), foundSegment.getDestSwitchId());

        flowSegmentRepository.delete(foundSegment);
        assertEquals(0, flowSegmentRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldCreateAndFindSegmentByFlowId() {
        Switch switchA = new Switch();
        switchA.setSwitchId(new SwitchId(1));
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(new SwitchId(2));
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        FlowSegment segment = new FlowSegment();
        segment.setSrcSwitch(switchA);
        segment.setDestSwitch(switchB);
        segment.setFlowId(TEST_FLOW_ID);

        flowSegmentRepository.createOrUpdate(segment);

        List<FlowSegment> foundSegment =
                StreamSupport.stream(flowSegmentRepository.findByFlowId(TEST_FLOW_ID).spliterator(), false)
                        .collect(Collectors.toList());
        assertThat(foundSegment, Matchers.hasSize(1));
    }
}
