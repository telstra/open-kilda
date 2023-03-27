/* Copyright 2022 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class FermaYFlowRepositoryTest extends InMemoryGraphBasedTest {
    static final String Y_FLOW_ID_1 = "y_flow_1";
    static final String FLOW_ID_1 = "test_flow_1";
    static final String FLOW_ID_2 = "test_flow_2";
    static final String FLOW_ID_3 = "test_flow_3";
    public static final String SUB_FLOW_1_DESCRIPTION_UPDATED = "SubFlow1 description updated";
    public static final String SUB_FLOW_2_DESCRIPTION_UPDATED = "SubFlow2 description updated";
    public static final String Y_FLOW_DESCRIPTION = "Y-flow description";

    FlowRepository flowRepository;
    YFlowRepository yFlowRepository;
    FlowPathRepository flowPathRepository;
    SwitchRepository switchRepository;

    Switch switch1;
    Switch switch2;
    Switch switch3;

    @Before
    public void setUp() {
        flowRepository = repositoryFactory.createFlowRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        switch1 = createTestSwitch(SWITCH_ID_1.getId());
        switch2 = createTestSwitch(SWITCH_ID_2.getId());
        switch3 = createTestSwitch(SWITCH_ID_3.getId());
    }

    @Test
    public void shouldCreateFlow() {
        createYFlow(Y_FLOW_ID_1, FLOW_ID_1, FLOW_ID_2);
        createTestFlow(FLOW_ID_3, switch1, PORT_3, VLAN_2, switch2, PORT_2, VLAN_1);

        assertTrue(yFlowRepository.findYFlowId(FLOW_ID_1).isPresent());
        assertEquals(Y_FLOW_ID_1, yFlowRepository.findYFlowId(FLOW_ID_1).get());
        assertTrue(yFlowRepository.findYFlowId(FLOW_ID_2).isPresent());
        assertEquals(Y_FLOW_ID_1, yFlowRepository.findYFlowId(FLOW_ID_2).get());
        assertFalse(yFlowRepository.findYFlowId(FLOW_ID_3).isPresent());
    }

    @Test
    public void editYSubFlowDescriptionViaActualFlowDescription() {
        YFlow flow = createYFlow(Y_FLOW_ID_1, FLOW_ID_1, FLOW_ID_2);

        assertTrue(flowRepository.findById(FLOW_ID_1).isPresent());
        Flow subflow1 = flowRepository.findById(FLOW_ID_1).get();
        subflow1.setDescription(SUB_FLOW_1_DESCRIPTION_UPDATED);

        assertTrue(flowRepository.findById(FLOW_ID_2).isPresent());
        Flow subflow2 = flowRepository.findById(FLOW_ID_2).get();
        subflow2.setDescription(SUB_FLOW_2_DESCRIPTION_UPDATED);

        assertTrue(yFlowRepository.findById(Y_FLOW_ID_1).isPresent());
        YFlow updatedYfFlow = yFlowRepository.findById(Y_FLOW_ID_1).get();
        Set<String> actualEditedDescriptions = updatedYfFlow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .map(Flow::getDescription)
                .collect(Collectors.toSet());

        Set<String> expectedDescriptions = newHashSet(SUB_FLOW_1_DESCRIPTION_UPDATED, SUB_FLOW_2_DESCRIPTION_UPDATED);
        assertEquals(expectedDescriptions, actualEditedDescriptions);
    }

    private YFlow createYFlow(String yFlowId, String flowId1, String flowId2) {
        YFlow yFlow = YFlow.builder()
                .yFlowId(yFlowId)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .sharedEndpoint(new SharedEndpoint(SWITCH_ID_1, PORT_1))
                .description(Y_FLOW_DESCRIPTION)
                .build();
        Flow flow1 = createTestFlow(flowId1, switch1, PORT_1, VLAN_1, switch2, PORT_3, VLAN_3);
        Flow flow2 = createTestFlow(flowId2, switch1, PORT_1, VLAN_2, switch3, PORT_4, VLAN_3);

        YSubFlow subFlow1 = YSubFlow.builder()
                .yFlow(yFlow)
                .flow(flow1)
                .endpointSwitchId(SWITCH_ID_2)
                .endpointPort(PORT_3)
                .endpointVlan(VLAN_3)
                .sharedEndpointVlan(VLAN_1)
                .build();

        YSubFlow subFlow2 = YSubFlow.builder()
                .yFlow(yFlow)
                .flow(flow2)
                .endpointSwitchId(SWITCH_ID_3)
                .endpointPort(PORT_4)
                .endpointVlan(VLAN_3)
                .sharedEndpointVlan(VLAN_2)
                .build();

        yFlow.addSubFlow(subFlow1);
        yFlow.addSubFlow(subFlow2);
        yFlowRepository.add(yFlow);
        return yFlow;
    }

    private Flow createTestFlow(
            String flowId, Switch srcSwitch, int srcPort, int srcVlan, Switch destSwitch, int destPort, int destVlan) {
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .destVlan(destVlan)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .build();
        flowRepository.add(flow);

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_forward_path"))
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 1L))
                .meterId(new MeterId(1))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .build();

        PathSegment forwardSegment = PathSegment.builder()
                .pathId(forwardFlowPath.getPathId())
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .build();
        forwardFlowPath.setSegments(Collections.singletonList(forwardSegment));

        flowPathRepository.add(forwardFlowPath);
        flow.setForwardPath(forwardFlowPath);

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_reverse_path"))
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 1L))
                .meterId(new MeterId(2))
                .srcSwitch(destSwitch)
                .destSwitch(srcSwitch)
                .status(FlowPathStatus.ACTIVE)
                .build();

        PathSegment reverseSegment = PathSegment.builder()
                .pathId(reverseFlowPath.getPathId())
                .srcSwitch(destSwitch)
                .srcPort(destPort)
                .destSwitch(srcSwitch)
                .destPort(srcPort)
                .build();
        reverseFlowPath.setSegments(Collections.singletonList(reverseSegment));

        flowPathRepository.add(reverseFlowPath);
        flow.setReversePath(reverseFlowPath);

        return flow;
    }
}
