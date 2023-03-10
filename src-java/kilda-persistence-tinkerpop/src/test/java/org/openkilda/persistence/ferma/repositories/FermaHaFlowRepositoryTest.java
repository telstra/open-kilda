/* Copyright 2023 Telstra Open Source
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

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.function.Function.identity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.persistence.ferma.repositories.FermaHaFlowPathRepositoryTest.buildSegments;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlowPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.HaSubFlowEdge;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FermaHaFlowRepositoryTest extends InMemoryGraphBasedTest {
    private static final FlowSegmentCookie COOKIE_1 = FlowSegmentCookie.builder().flowEffectiveId(1)
            .direction(FlowPathDirection.FORWARD).build();
    private static final FlowSegmentCookie COOKIE_2 = FlowSegmentCookie.builder().flowEffectiveId(2)
            .direction(FlowPathDirection.REVERSE).build();
    private static final int BANDWIDTH_1 = 1000;
    private static final int BANDWIDTH_2 = 2000;
    private static final long LATENCY_1 = 1;
    private static final long LATENCY_2 = 2;
    private static final long LATENCY_3 = 3;
    private static final long LATENCY_4 = 4;
    private static final int PRIORITY_1 = 5;
    private static final int PRIORITY_2 = 6;
    private static final String GROUP_1 = "group_1";
    private static final String GROUP_2 = "group_2";
    private static final String DESCRIPTION_1 = "description_1";
    private static final String DESCRIPTION_2 = "description_2";

    private HaSubFlowRepository haSubFlowRepository;
    private HaFlowPathRepository haFlowPathRepository;
    private HaFlowRepository haFlowRepository;
    private SwitchRepository switchRepository;
    private PathSegmentRepository pathSegmentRepository;

    private Switch switch1;
    private Switch switch2;
    private Switch switch3;
    private HaFlow haFlow1;
    private HaFlow haFlow2;
    private HaFlowPath path1;
    private HaFlowPath path2;
    private HaSubFlow subFlow1;
    private HaSubFlow subFlow2;

    @Before
    public void setUp() {
        haFlowRepository = repositoryFactory.createHaFlowRepository();
        haFlowPathRepository = repositoryFactory.createHaFlowPathRepository();
        haSubFlowRepository = repositoryFactory.createHaSubFlowRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        pathSegmentRepository = repositoryFactory.createPathSegmentRepository();

        switch1 = createTestSwitch(SWITCH_ID_1.getId());
        switch2 = createTestSwitch(SWITCH_ID_2.getId());
        switch3 = createTestSwitch(SWITCH_ID_3.getId());
        assertEquals(3, switchRepository.findAll().size());

        haFlow1 = buildHaFlow(
                HA_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1, DESCRIPTION_1, PathComputationStrategy.COST,
                FlowStatus.UP, true, true, true, true, true);

        haFlow2 = buildHaFlow(
                HA_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, LATENCY_3, LATENCY_4, BANDWIDTH_2,
                FlowEncapsulationType.VXLAN, PRIORITY_2, DESCRIPTION_2, PathComputationStrategy.LATENCY,
                FlowStatus.IN_PROGRESS, false, false, false, false, false);

        path1 = buildHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2, switch1,
                SWITCH_ID_2, GROUP_ID_1);
        path2 = buildHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4, switch1,
                SWITCH_ID_4, GROUP_ID_2);
        subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, DESCRIPTION_1);
        subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_3);
    }

    @Test
    public void createHaFlowTest() {
        createHaFlowWithSubFlows(haFlow1);
        createHaFlow(haFlow2);

        haFlowPathRepository.add(path1);
        haFlowPathRepository.add(path2);
        haFlow1.addPaths(path1, path2);

        Map<String, HaFlow> flowMap = haFlowRepository.findAll().stream()
                .collect(Collectors.toMap(HaFlow::getHaFlowId, identity()));
        assertEquals(2, flowMap.size());

        // flow 1
        assertHaFlow(HA_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1, DESCRIPTION_1, PathComputationStrategy.COST,
                FlowStatus.UP, true, true, true, true, true, flowMap.get(HA_FLOW_ID_1));
        assertSubFlows(flowMap.get(HA_FLOW_ID_1).getSubFlows(), subFlow1, subFlow2);
        assertPathsFlows(flowMap.get(HA_FLOW_ID_1).getPaths(), path1, path2);

        // flow 2
        assertHaFlow(HA_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, LATENCY_3, LATENCY_4, BANDWIDTH_2,
                FlowEncapsulationType.VXLAN, PRIORITY_2, DESCRIPTION_2, PathComputationStrategy.LATENCY,
                FlowStatus.IN_PROGRESS, false, false, false, false, false, flowMap.get(HA_FLOW_ID_2));
        assertEquals(0, flowMap.get(HA_FLOW_ID_2).getSubFlows().size());
        assertEquals(0, flowMap.get(HA_FLOW_ID_2).getPaths().size());
    }

    @Test
    public void existHaFlowTest() {
        createHaFlow(haFlow1);
        assertTrue(haFlowRepository.exists(haFlow1.getHaFlowId()));
        assertFalse(haFlowRepository.exists(haFlow2.getHaFlowId()));
    }

    @Test
    public void findByIdHaFlowTest() {
        createHaFlowWithSubFlows(haFlow1);
        createHaFlow(haFlow2);

        haFlowPathRepository.add(path1);
        haFlowPathRepository.add(path2);
        haFlow1.addPaths(path1, path2);

        // flow 1
        Optional<HaFlow> foundFlow1 = haFlowRepository.findById(HA_FLOW_ID_1);
        assertTrue(foundFlow1.isPresent());
        assertHaFlow(HA_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1, DESCRIPTION_1, PathComputationStrategy.COST,
                FlowStatus.UP, true, true, true, true, true, foundFlow1.get());
        assertSubFlows(foundFlow1.get().getSubFlows(), subFlow1, subFlow2);
        assertPathsFlows(foundFlow1.get().getPaths(), path1, path2);

        // flow 2
        Optional<HaFlow> foundFlow2 = haFlowRepository.findById(HA_FLOW_ID_2);
        assertTrue(foundFlow2.isPresent());
        assertHaFlow(HA_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, LATENCY_3, LATENCY_4, BANDWIDTH_2,
                FlowEncapsulationType.VXLAN, PRIORITY_2, DESCRIPTION_2, PathComputationStrategy.LATENCY,
                FlowStatus.IN_PROGRESS, false, false, false, false, false, foundFlow2.get());
        assertEquals(0, foundFlow2.get().getSubFlows().size());
        assertEquals(0, foundFlow2.get().getPaths().size());
    }

    @Test
    public void findByIdNoExistentFlowTest() {
        assertFalse(haFlowRepository.findById(HA_FLOW_ID_1).isPresent());

        createHaFlow(haFlow1);
        assertTrue(haFlowRepository.findById(HA_FLOW_ID_1).isPresent());

        haFlowRepository.remove(HA_FLOW_ID_1);
        assertFalse(haFlowRepository.findById(HA_FLOW_ID_1).isPresent());
    }

    @Test
    public void haFlowCascadeRemoveTest() {
        createHaFlowWithSubFlows(haFlow1);
        createHaFlow(haFlow2);

        haFlowPathRepository.add(path1);
        path1.setSegments(buildSegments(path1, switch1, switch2, switch3));
        path1.setHaSubFlowEdges(buildSubFlowEdges(subFlow1, subFlow2));

        haFlowPathRepository.add(path2);
        path2.setSegments(buildSegments(path2, switch3, switch2, switch1));
        path2.setHaSubFlowEdges(buildSubFlowEdges(subFlow2, subFlow1));
        haFlow1.addPaths(path1, path2);

        assertEquals(2, haFlowRepository.findAll().size());
        assertEquals(2, haFlowPathRepository.findAll().size());
        assertEquals(2, haSubFlowRepository.findAll().size());
        assertEquals(2, pathSegmentRepository.findByPathId(path1.getHaPathId()).size());
        assertEquals(2, pathSegmentRepository.findByPathId(path2.getHaPathId()).size());

        Optional<HaFlow> removedFlow = haFlowRepository.remove(haFlow1.getHaFlowId());
        assertTrue(removedFlow.isPresent());
        assertHaFlow(HA_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, LATENCY_1, LATENCY_2, BANDWIDTH_1,
                FlowEncapsulationType.TRANSIT_VLAN, PRIORITY_1, DESCRIPTION_1, PathComputationStrategy.COST,
                FlowStatus.UP, true, true, true, true, true, removedFlow.get());

        assertTrue(haFlowRepository.findById(haFlow2.getHaFlowId()).isPresent());
        assertEquals(1, haFlowRepository.findAll().size());
        assertEquals(0, haFlowPathRepository.findAll().size());
        assertEquals(0, haSubFlowRepository.findAll().size());
        assertEquals(0, pathSegmentRepository.findByPathId(path1.getHaPathId()).size());
        assertEquals(0, pathSegmentRepository.findByPathId(path2.getHaPathId()).size());
    }

    @Test
    public void haFlowSetMainPathsTest() {
        createHaFlowWithSubFlows(haFlow1);
        haFlowPathRepository.add(path1);
        haFlowPathRepository.add(path2);
        path1.setHaSubFlowEdges(buildSubFlowEdges(subFlow1, subFlow2));
        path2.setHaSubFlowEdges(buildSubFlowEdges(subFlow1, subFlow2));
        haFlow1.setForwardPath(path1);
        haFlow1.setReversePath(path2);

        Optional<HaFlow> foundFlow = haFlowRepository.findById(HA_FLOW_ID_1);
        assertTrue(foundFlow.isPresent());
        assertPathsFlows(foundFlow.get().getPaths(), path1, path2);
        assertEquals(path1.getHaPathId(), foundFlow.get().getForwardPathId());
        assertPathsFlows(newArrayList(foundFlow.get().getForwardPath()), path1);
        assertEquals(path2.getHaPathId(), foundFlow.get().getReversePathId());
        assertPathsFlows(newArrayList(foundFlow.get().getReversePath()), path2);
    }

    @Test
    public void haFlowSetProtectedForwardPathsTest() {
        createHaFlowWithSubFlows(haFlow1);
        haFlowPathRepository.add(path1);
        haFlowPathRepository.add(path2);
        path1.setHaSubFlowEdges(buildSubFlowEdges(subFlow1, subFlow2));
        path2.setHaSubFlowEdges(buildSubFlowEdges(subFlow1, subFlow2));
        haFlow1.setProtectedForwardPath(path1);
        haFlow1.setProtectedReversePath(path2);

        Optional<HaFlow> foundFlow = haFlowRepository.findById(HA_FLOW_ID_1);
        assertTrue(foundFlow.isPresent());
        assertPathsFlows(foundFlow.get().getPaths(), path1, path2);
        assertEquals(path1.getHaPathId(), foundFlow.get().getProtectedForwardPathId());
        assertPathsFlows(newArrayList(foundFlow.get().getProtectedForwardPath()), path1);
        assertEquals(path2.getHaPathId(), foundFlow.get().getProtectedReversePathId());
        assertPathsFlows(newArrayList(foundFlow.get().getProtectedReversePath()), path2);
    }

    private void createHaFlowWithSubFlows(HaFlow haFlow) {
        createHaFlow(haFlow);
        haSubFlowRepository.add(subFlow1);
        haSubFlowRepository.add(subFlow2);
        haFlow.setSubFlows(newHashSet(subFlow1, subFlow2));
    }

    private void assertPathsFlows(Collection<HaFlowPath> actualPaths, HaFlowPath... expectedPaths) {
        assertEquals(expectedPaths.length, actualPaths.size());
        assertEquals(newHashSet(expectedPaths), newHashSet(actualPaths));
    }

    private void assertSubFlows(Set<HaSubFlow> actualSubFlows, HaSubFlow... expectedSubFlows) {
        assertEquals(expectedSubFlows.length, actualSubFlows.size());
        assertEquals(newHashSet(expectedSubFlows), newHashSet(actualSubFlows));
    }

    private void assertHaFlow(
            String flowId, SwitchId switchId, int port, int vlan, int innerVlan, long latency, long latencyTier2,
            long bandwidth, FlowEncapsulationType encapsulationType, int priority, String description,
            PathComputationStrategy strategy, FlowStatus status, boolean protectedPath, boolean pinned, boolean pings,
            boolean ignoreBandwidth, boolean strictBandwidth, HaFlow actualHaFlow) {
        assertEquals(flowId, actualHaFlow.getHaFlowId());
        assertEquals(switchId, actualHaFlow.getSharedEndpoint().getSwitchId());
        assertEquals(port, actualHaFlow.getSharedEndpoint().getPortNumber().intValue());
        assertEquals(vlan, actualHaFlow.getSharedEndpoint().getOuterVlanId());
        assertEquals(innerVlan, actualHaFlow.getSharedEndpoint().getInnerVlanId());
        assertEquals(latency, actualHaFlow.getMaxLatency().longValue());
        assertEquals(latencyTier2, actualHaFlow.getMaxLatencyTier2().longValue());
        assertEquals(bandwidth, actualHaFlow.getMaximumBandwidth());
        assertEquals(encapsulationType, actualHaFlow.getEncapsulationType());
        assertEquals(priority, actualHaFlow.getPriority().intValue());
        assertEquals(description, actualHaFlow.getDescription());
        assertEquals(strategy, actualHaFlow.getPathComputationStrategy());
        assertEquals(status, actualHaFlow.getStatus());
        assertEquals(protectedPath, actualHaFlow.isAllocateProtectedPath());
        assertEquals(pinned, actualHaFlow.isPinned());
        assertEquals(pings, actualHaFlow.isPeriodicPings());
        assertEquals(ignoreBandwidth, actualHaFlow.isIgnoreBandwidth());
        assertEquals(strictBandwidth, actualHaFlow.isStrictBandwidth());
    }

    private void createHaFlow(HaFlow haFlow) {
        haFlowRepository.add(haFlow);
    }

    private static Set<HaSubFlowEdge> buildSubFlowEdges(HaSubFlow subFlow1, HaSubFlow subFlow2) {
        return FermaModelUtils.buildHaSubFlowEdges(HA_FLOW_ID_1, subFlow1, subFlow2, METER_ID_3, METER_ID_4);
    }
}
