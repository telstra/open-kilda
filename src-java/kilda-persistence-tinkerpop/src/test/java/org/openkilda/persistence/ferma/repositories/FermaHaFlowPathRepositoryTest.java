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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlowPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlow.HaSharedEndpoint;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.HaSubFlowEdge;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.persistence.repositories.PathSegmentRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FermaHaFlowPathRepositoryTest extends InMemoryGraphBasedTest {
    private static final FlowSegmentCookie COOKIE_1 = FlowSegmentCookie.builder().flowEffectiveId(1).build();
    private static final FlowSegmentCookie COOKIE_2 = FlowSegmentCookie.builder().flowEffectiveId(2).build();
    private static final int BANDWIDTH_1 = 1000;
    private static final int BANDWIDTH_2 = 2000;
    private static final String GROUP_1 = "group_1";
    private static final String GROUP_2 = "group_2";

    private HaSubFlowRepository haSubFlowRepository;
    private HaFlowPathRepository haFlowPathRepository;
    private HaFlowRepository haFlowRepository;
    private SwitchRepository switchRepository;
    private PathSegmentRepository pathSegmentRepository;

    private Switch switch1;
    private Switch switch2;
    private Switch switch3;
    private HaFlow haFlow;
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

        haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID_1)
                .sharedEndpoint(new HaSharedEndpoint(SWITCH_ID_3, 0, 0, 0))
                .build();
        haFlowRepository.add(haFlow);
        subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, DESCRIPTION_1);
        subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haSubFlowRepository.add(subFlow1);
        haSubFlowRepository.add(subFlow2);
        haFlow.setSubFlows(Sets.newHashSet(subFlow1, subFlow2));

        assertEquals(2, haFlow.getSubFlows().size());

    }

    @Test
    public void createFlowEmptySegmentsAndEdgesTest() {
        createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        HaFlowPath path2 = createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);
        haFlow.addPaths(path2);

        Map<PathId, HaFlowPath> pathMap = haFlowPathsToMap(haFlowPathRepository.findAll());

        assertEquals(2, pathMap.size());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, null, pathMap.get(PATH_ID_1));
        assertHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2, haFlow, pathMap.get(PATH_ID_2));
    }

    @Test
    public void createFlowWithSegmentsTest() {
        HaFlowPath path1 = createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        List<PathSegment> expectedSegments = buildSegments(path1);
        path1.setSegments(expectedSegments);

        Collection<HaFlowPath> paths = haFlowPathRepository.findAll();
        assertEquals(1, paths.size());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, null, paths.iterator().next());
        assertEquals(expectedSegments, paths.iterator().next().getSegments());
    }

    @Test
    public void createFlowWithEdgesTest() {
        HaFlowPath path1 = createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        haFlow.addPaths(path1);
        path1.setHaSubFlowEdges(buildSubFlowEdges());

        Collection<HaFlowPath> paths = haFlowPathRepository.findAll();
        assertEquals(1, paths.size());
        Map<String, HaSubFlowEdge> edgeMap = haSubFlowEdgesToMap(paths.iterator().next().getHaSubFlowEdges());

        assertEquals(2, edgeMap.size());
        assertHaSubFlowEdge(HA_FLOW_ID_1, path1, subFlow1, METER_ID_3, edgeMap.get(SUB_FLOW_ID_1));
        assertHaSubFlowEdge(HA_FLOW_ID_1, path1, subFlow2, METER_ID_4, edgeMap.get(SUB_FLOW_ID_2));
    }

    @Test
    public void findByIdFlowWithEdgesAndSegmentsTest() {
        HaFlowPath path1 = createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);
        haFlow.addPaths(path1);
        List<PathSegment> expectedSegments = buildSegments(path1);
        path1.setSegments(expectedSegments);
        path1.setHaSubFlowEdges(buildSubFlowEdges());

        assertEquals(2, haFlowPathRepository.findAll().size());

        Optional<HaFlowPath> foundPath1 = haFlowPathRepository.findById(PATH_ID_1);
        assertTrue(foundPath1.isPresent());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, haFlow, foundPath1.get());
        assertEquals(expectedSegments, foundPath1.get().getSegments());

        Map<String, HaSubFlowEdge> edgeMap = haSubFlowEdgesToMap(foundPath1.get().getHaSubFlowEdges());
        assertEquals(2, edgeMap.size());
        assertHaSubFlowEdge(HA_FLOW_ID_1, path1, subFlow1, METER_ID_3, edgeMap.get(SUB_FLOW_ID_1));
        assertHaSubFlowEdge(HA_FLOW_ID_1, path1, subFlow2, METER_ID_4, edgeMap.get(SUB_FLOW_ID_2));
    }

    @Test
    public void findByIdFlowWithoutEdgesAndSegmentsTest() {
        createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);

        assertEquals(2, haFlowPathRepository.findAll().size());

        Optional<HaFlowPath> foundPath2 = haFlowPathRepository.findById(PATH_ID_2);
        assertTrue(foundPath2.isPresent());
        assertHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2, null, foundPath2.get());
        assertEquals(0, foundPath2.get().getSegments().size());
        assertEquals(0, foundPath2.get().getHaSubFlowEdges().size());
    }

    @Test
    public void findByIdNonExistentFlowTest() {
        assertFalse(haFlowPathRepository.findById(PATH_ID_3).isPresent());
    }

    @Test
    public void findByHaFlowIdTest() {
        createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        HaFlowPath path2 = createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);
        haFlow.addPaths(path2);

        assertEquals(2, haFlowPathRepository.findAll().size());

        Collection<HaFlowPath> paths = haFlowPathRepository.findByHaFlowId(HA_FLOW_ID_1);
        assertEquals(1, paths.size());

        assertHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2, haFlow, paths.iterator().next());
    }

    @Test
    public void removeHaFlowPathWithSegmentsAndEdgesTest() {
        createHaFlowFlowWithSegmentsAndEdges(
                PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2, switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);

        assertEquals(2, haFlowPathRepository.findAll().size());
        assertEquals(2, pathSegmentRepository.findByPathId(PATH_ID_1).size());

        Optional<HaFlowPath> path = haFlowPathRepository.remove(PATH_ID_1);
        assertTrue(path.isPresent());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, haFlow, path.get());
        assertEquals(1, haFlowPathRepository.findAll().size());
        assertEquals(PATH_ID_2, haFlowPathRepository.findAll().iterator().next().getHaPathId());
        assertEquals(0, pathSegmentRepository.findByPathId(PATH_ID_1).size());
    }

    @Test
    public void removeHaFlowPathWithOutSegmentsTest() {
        HaFlowPath path1 = createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, GROUP_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);
        haFlow.addPaths(path1);
        path1.setSegments(buildSegments(path1));
        path1.setHaSubFlowEdges(buildSubFlowEdges());

        assertEquals(2, haFlowPathRepository.findAll().size());
        assertEquals(2, pathSegmentRepository.findByPathId(PATH_ID_1).size());

        Optional<HaFlowPath> path = haFlowPathRepository.remove(PATH_ID_1);
        assertTrue(path.isPresent());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, GROUP_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, haFlow, path.get());
        assertEquals(1, haFlowPathRepository.findAll().size());
        assertEquals(PATH_ID_2, haFlowPathRepository.findAll().iterator().next().getHaPathId());
        assertEquals(0, pathSegmentRepository.findByPathId(PATH_ID_1).size());
    }

    private void assertHaFlowPath(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, String sharedGroupId, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId, HaFlow haFlow,
            HaFlowPath actualPath) {
        assertEquals(pathId, actualPath.getHaPathId());
        assertEquals(bandwidth, actualPath.getBandwidth());
        assertEquals(cookie, actualPath.getCookie());
        assertEquals(sharedGroupId, actualPath.getSharedBandwidthGroupId());
        assertEquals(sharedMeterId, actualPath.getSharedPointMeterId());
        assertEquals(yPointMeterId, actualPath.getYPointMeterId());
        assertEquals(sharedSwitch, actualPath.getSharedSwitch());
        assertEquals(sharedSwitch.getSwitchId(), actualPath.getSharedSwitchId());
        assertEquals(yPointSwitchId, actualPath.getYPointSwitchId());
        assertEquals(yPointGroupId, actualPath.getYPointGroupId());
        assertEquals(FlowPathStatus.ACTIVE, actualPath.getStatus());
        assertEquals(haFlow, actualPath.getHaFlow());
        assertEquals(haFlow == null ? null : haFlow.getHaFlowId(), actualPath.getHaFlowId());
    }

    private void assertHaSubFlowEdge(
            String haFlowId, HaFlowPath path, HaSubFlow subFlow, MeterId meterId, HaSubFlowEdge actualEdge) {
        assertEquals(haFlowId, actualEdge.getHaFlowId());
        assertEquals(path, actualEdge.getHaFlowPath());
        assertEquals(path.getHaPathId(), actualEdge.getHaFlowPathId());
        assertEquals(subFlow, actualEdge.getHaSubFlow());
        assertEquals(subFlow.getHaSubFlowId(), actualEdge.getHaSubFlowId());
        assertEquals(subFlow.getEndpointSwitchId(), actualEdge.getSubFlowEndpointSwitchId());
        assertEquals(meterId, actualEdge.getMeterId());
    }

    static List<PathSegment> buildSegments(HaFlowPath path, Switch switch1, Switch switch2, Switch switch3) {
        PathSegment segment1 = PathSegment.builder()
                .pathId(path.getHaPathId()).srcSwitch(switch1).destSwitch(switch3).build();
        PathSegment segment2 = PathSegment.builder()
                .pathId(path.getHaPathId()).srcSwitch(switch3).destSwitch(switch2).build();
        return Lists.newArrayList(segment1, segment2);
    }

    private List<PathSegment> buildSegments(HaFlowPath path) {
        return buildSegments(path, switch1, switch2, switch3);
    }

    private Set<HaSubFlowEdge> buildSubFlowEdges() {
        return FermaModelUtils.buildHaSubFlowEdges(HA_FLOW_ID_1, subFlow1, subFlow2, METER_ID_3, METER_ID_4);
    }

    private HaFlowPath createHaFlowFlowWithSegmentsAndEdges(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, String sharedGroupId, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId) {
        HaFlowPath path = buildHaFlowPath(
                pathId, bandwidth, cookie, sharedGroupId, sharedMeterId, yPointMeterId, sharedSwitch,
                yPointSwitchId, yPointGroupId);
        haFlowPathRepository.add(path);

        haFlow.addPaths(path);
        path.setSegments(buildSegments(path));
        path.setHaSubFlowEdges(buildSubFlowEdges());
        assertEquals(2, path.getHaFlow().getSubFlows().size());
        return path;
    }

    private HaFlowPath createHaFlowPath(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, String sharedGroupId, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId) {
        HaFlowPath path = buildHaFlowPath(
                pathId, bandwidth, cookie, sharedGroupId, sharedMeterId, yPointMeterId, sharedSwitch,
                yPointSwitchId, yPointGroupId);
        haFlowPathRepository.add(path);
        return path;
    }

    private static Map<PathId, HaFlowPath> haFlowPathsToMap(Collection<HaFlowPath> paths) {
        return paths.stream().collect(Collectors.toMap(HaFlowPath::getHaPathId, Function.identity()));
    }

    private static Map<String, HaSubFlowEdge> haSubFlowEdgesToMap(Collection<HaSubFlowEdge> edges) {
        return edges.stream().collect(Collectors.toMap(HaSubFlowEdge::getHaSubFlowId, Function.identity()));
    }
}
