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
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildSegments;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

public class FermaHaFlowPathRepositoryTest extends InMemoryGraphBasedTest {
    private static final FlowSegmentCookie COOKIE_1 = FlowSegmentCookie.builder().flowEffectiveId(1)
            .direction(FlowPathDirection.FORWARD).build();
    private static final FlowSegmentCookie COOKIE_2 = FlowSegmentCookie.builder().flowEffectiveId(2)
            .direction(FlowPathDirection.REVERSE).build();
    private static final int BANDWIDTH_1 = 1000;
    private static final int BANDWIDTH_2 = 2000;

    private HaSubFlowRepository haSubFlowRepository;
    private HaFlowPathRepository haFlowPathRepository;
    private HaFlowRepository haFlowRepository;
    private SwitchRepository switchRepository;
    private PathSegmentRepository pathSegmentRepository;
    private FlowPathRepository flowPathRepository;

    private Switch switch1;
    private Switch switch2;
    private Switch switch3;
    private Switch switch4;
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
        flowPathRepository = repositoryFactory.createFlowPathRepository();

        switch1 = createTestSwitch(SWITCH_ID_1.getId());
        switch2 = createTestSwitch(SWITCH_ID_2.getId());
        switch3 = createTestSwitch(SWITCH_ID_3.getId());
        switch4 = createTestSwitch(SWITCH_ID_4.getId());
        assertEquals(4, switchRepository.findAll().size());

        haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID_1)
                .sharedSwitch(switch3)
                .sharedPort(PORT_1)
                .sharedOuterVlan(0)
                .sharedInnerVlan(0)
                .build();
        haFlowRepository.add(haFlow);
        subFlow1 = buildHaSubFlow(SUB_FLOW_ID_1, switch1, PORT_1, VLAN_1, INNER_VLAN_1, DESCRIPTION_1);
        subFlow2 = buildHaSubFlow(SUB_FLOW_ID_2, switch2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haSubFlowRepository.add(subFlow1);
        haSubFlowRepository.add(subFlow2);
        haFlow.setSubFlows(Sets.newHashSet(subFlow1, subFlow2));

        assertEquals(2, haFlow.getSubFlows().size());

    }

    @Test
    public void createFlowEmptySubPathsAndSubFlowsTest() {
        createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        HaFlowPath path2 = createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);
        haFlow.addPaths(path2);

        Map<PathId, HaFlowPath> pathMap = haFlowPathsToMap(haFlowPathRepository.findAll());

        assertEquals(2, pathMap.size());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, null, pathMap.get(PATH_ID_1));
        assertHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2, haFlow, pathMap.get(PATH_ID_2));
    }

    @Test
    public void createFlowWithSubPathsTest() {
        HaFlowPath path1 = createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        List<FlowPath> expectedSubPaths = createSubPaths(path1);
        path1.setSubPaths(expectedSubPaths);

        Collection<HaFlowPath> paths = haFlowPathRepository.findAll();
        assertEquals(1, paths.size());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, null, paths.iterator().next());
        assertEquals(expectedSubPaths, paths.iterator().next().getSubPaths());
    }

    @Test
    public void createFlowWithSubFlowsTest() {
        HaFlowPath path1 = createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch3, SWITCH_ID_2, GROUP_ID_1);
        path1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));
        haFlow.setForwardPath(path1);

        Collection<HaFlowPath> paths = haFlowPathRepository.findAll();
        assertEquals(1, paths.size());
        assertEquals(Lists.newArrayList(subFlow1, subFlow2), paths.iterator().next().getHaSubFlows());
    }

    @Test
    public void findByIdFlowWithSubFlowsAndSubPathsTest() {
        HaFlowPath path1 = createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);
        haFlow.addPaths(path1);
        List<FlowPath> expectedSubPaths = createSubPaths(path1);
        path1.setSubPaths(expectedSubPaths);
        path1.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));

        assertEquals(2, haFlowPathRepository.findAll().size());

        Optional<HaFlowPath> foundPath1 = haFlowPathRepository.findById(PATH_ID_1);
        assertTrue(foundPath1.isPresent());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, haFlow, foundPath1.get());
        assertEquals(expectedSubPaths, foundPath1.get().getSubPaths());
        assertEquals(Lists.newArrayList(subFlow1, subFlow2), foundPath1.get().getHaSubFlows());
    }

    @Test
    public void findByIdFlowWithoutSubFlowsAndSubPathsTest() {
        createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);

        assertEquals(2, haFlowPathRepository.findAll().size());

        Optional<HaFlowPath> foundPath2 = haFlowPathRepository.findById(PATH_ID_2);
        assertTrue(foundPath2.isPresent());
        assertHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2, null, foundPath2.get());
        assertEquals(0, foundPath2.get().getSubPaths().size());
        assertEquals(0, foundPath2.get().getHaSubFlows().size());
    }

    @Test
    public void findByIdNonExistentFlowTest() {
        assertFalse(haFlowPathRepository.findById(PATH_ID_3).isPresent());
    }

    @Test
    public void findByHaFlowIdTest() {
        createHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        HaFlowPath path2 = createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);
        haFlow.addPaths(path2);

        assertEquals(2, haFlowPathRepository.findAll().size());

        Collection<HaFlowPath> paths = haFlowPathRepository.findByHaFlowId(HA_FLOW_ID_1);
        assertEquals(1, paths.size());

        assertHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2, haFlow, paths.iterator().next());
    }

    @Test
    public void removeHaFlowPathWithSubPathsAndSubFlowsTest() {
        createHaFlowPathWithSubPathsAndSubFlows(
                PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2, switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);

        assertEquals(2, haFlowPathRepository.findAll().size());
        assertEquals(2, flowPathRepository.findAll().size());
        assertEquals(2, pathSegmentRepository.findByPathId(SUB_PATH_ID_1).size());
        assertEquals(2, pathSegmentRepository.findByPathId(SUB_PATH_ID_2).size());

        Optional<HaFlowPath> path = haFlowPathRepository.remove(PATH_ID_1);
        assertTrue(path.isPresent());
        assertHaFlowPath(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1, haFlow, path.get());
        assertEquals(1, haFlowPathRepository.findAll().size());
        assertEquals(PATH_ID_2, haFlowPathRepository.findAll().iterator().next().getHaPathId());
        assertEquals(0, flowPathRepository.findAll().size());
        assertEquals(0, pathSegmentRepository.findByPathId(PATH_ID_1).size());
    }

    @Test
    public void removeHaFlowPathWithOutSubPathsTest() {
        createHaFlowPathWithSubPathsAndSubFlows(PATH_ID_1, BANDWIDTH_1, COOKIE_1, METER_ID_1, METER_ID_2,
                switch1, SWITCH_ID_2, GROUP_ID_1);
        createHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2);

        assertEquals(2, haFlowPathRepository.findAll().size());
        assertEquals(2, flowPathRepository.findAll().size());
        assertEquals(2, pathSegmentRepository.findByPathId(SUB_PATH_ID_1).size());
        assertEquals(2, pathSegmentRepository.findByPathId(SUB_PATH_ID_2).size());

        Optional<HaFlowPath> path = haFlowPathRepository.remove(PATH_ID_2);
        assertTrue(path.isPresent());
        assertHaFlowPath(PATH_ID_2, BANDWIDTH_2, COOKIE_2, METER_ID_3, METER_ID_4,
                switch3, SWITCH_ID_4, GROUP_ID_2, null, path.get());
        assertEquals(1, haFlowPathRepository.findAll().size());
        assertEquals(PATH_ID_1, haFlowPathRepository.findAll().iterator().next().getHaPathId());
        assertEquals(2, flowPathRepository.findAll().size());
        assertEquals(2, pathSegmentRepository.findByPathId(SUB_PATH_ID_1).size());
        assertEquals(2, pathSegmentRepository.findByPathId(SUB_PATH_ID_2).size());
    }

    private void assertHaFlowPath(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId, HaFlow haFlow,
            HaFlowPath actualPath) {
        assertEquals(pathId, actualPath.getHaPathId());
        assertEquals(bandwidth, actualPath.getBandwidth());
        assertEquals(cookie, actualPath.getCookie());
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

    private FlowPath createPathWithSegments(
            PathId pathId, HaFlowPath haFlowPath, Switch switch1, Switch switch2, Switch switch3) {
        FlowPath path = buildPath(pathId, haFlowPath, switch1, switch3);
        flowPathRepository.add(path);
        path.setSegments(buildSegments(path.getPathId(), switch1, switch2, switch3));
        return path;
    }

    private List<FlowPath> createSubPaths(HaFlowPath haPath) {
        return Lists.newArrayList(
                createPathWithSegments(SUB_PATH_ID_1, haPath, switch1, switch2, switch3),
                createPathWithSegments(SUB_PATH_ID_2, haPath, switch1, switch2, switch4));
    }

    private HaFlowPath createHaFlowPathWithSubPathsAndSubFlows(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId) {
        HaFlowPath path = buildHaFlowPath(
                pathId, bandwidth, cookie, sharedMeterId, yPointMeterId, sharedSwitch,
                yPointSwitchId, yPointGroupId);
        haFlowPathRepository.add(path);

        haFlow.addPaths(path);
        path.setSubPaths(createSubPaths(path));
        path.setHaSubFlows(Lists.newArrayList(subFlow1, subFlow2));
        assertEquals(2, path.getHaFlow().getSubFlows().size());
        return path;
    }

    private HaFlowPath createHaFlowPath(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId) {
        HaFlowPath path = buildHaFlowPath(
                pathId, bandwidth, cookie, sharedMeterId, yPointMeterId, sharedSwitch,
                yPointSwitchId, yPointGroupId);
        haFlowPathRepository.add(path);
        return path;
    }

    private static Map<PathId, HaFlowPath> haFlowPathsToMap(Collection<HaFlowPath> paths) {
        return paths.stream().collect(Collectors.toMap(HaFlowPath::getHaPathId, Function.identity()));
    }
}
