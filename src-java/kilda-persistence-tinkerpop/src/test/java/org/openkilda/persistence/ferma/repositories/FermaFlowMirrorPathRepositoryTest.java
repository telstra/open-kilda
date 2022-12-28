/* Copyright 2021 Telstra Open Source
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
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FermaFlowMirrorPathRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final String TEST_FLOW_MIRROR_ID = "test_mirror_id";
    static final PathId TEST_FLOW_PATH_ID = new PathId("test_flow_path_id");
    static final PathId TEST_FORWARD_PATH_ID = new PathId("test_forward_path_id");
    static final PathId TEST_REVERSE_PATH_ID = new PathId("test_reverse_path_id");
    static final PathId TEST_MIRROR_PATH_ID_1 = new PathId("mirror_path_1");
    static final PathId TEST_MIRROR_PATH_ID_2 = new PathId("mirror_path_2");
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);
    static final SwitchId TEST_SWITCH_D_ID = new SwitchId(4);
    static final GroupId TEST_GROUP_ID = new GroupId(10);
    static final Integer TEST_OUTER_VLAN = 4;
    static final Integer TEST_INNER_VLAN = 5;
    static final Integer PORT_1 = 6;
    static final Integer PORT_2 = 7;
    static final Integer PORT_3 = 8;
    static final Integer PORT_4 = 9;
    static final FlowSegmentCookie FORWARD_COOKIE = FlowSegmentCookie.builder()
            .flowEffectiveId(1).direction(FlowPathDirection.FORWARD).mirror(true).build();
    static final FlowSegmentCookie REVERSE_COOKIE = FlowSegmentCookie.builder()
            .flowEffectiveId(1).direction(FlowPathDirection.REVERSE).mirror(true).build();

    FlowMirrorPathRepository flowMirrorPathRepository;
    FlowMirrorRepository flowMirrorRepository;
    FlowMirrorPointsRepository flowMirrorPointsRepository;
    MirrorGroupRepository mirrorGroupRepository;
    SwitchRepository switchRepository;

    Switch switchA;
    Switch switchB;
    Switch switchC;
    Switch switchD;
    FlowMirror flowMirror;
    FlowMirrorPoints flowMirrorPoints;
    MirrorGroup mirrorGroup;

    @Before
    public void setUp() {
        flowMirrorRepository = repositoryFactory.createFlowMirrorRepository();
        flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();
        flowMirrorPointsRepository = repositoryFactory.createFlowMirrorPointsRepository();
        mirrorGroupRepository = repositoryFactory.createMirrorGroupRepository();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchA = createTestSwitch(TEST_SWITCH_A_ID.getId());
        switchB = createTestSwitch(TEST_SWITCH_B_ID.getId());
        switchC = createTestSwitch(TEST_SWITCH_C_ID.getId());
        switchD = createTestSwitch(TEST_SWITCH_D_ID.getId());

        assertEquals(4, switchRepository.findAll().size());

        mirrorGroup = MirrorGroup.builder()
                .groupId(TEST_GROUP_ID)
                .flowId(TEST_FLOW_ID)
                .pathId(TEST_FLOW_PATH_ID)
                .switchId(switchA.getSwitchId())
                .mirrorDirection(MirrorDirection.INGRESS)
                .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                .build();
        mirrorGroupRepository.add(mirrorGroup);

        flowMirror = FlowMirror.builder()
                .flowMirrorId(TEST_FLOW_MIRROR_ID)
                .forwardPathId(TEST_FORWARD_PATH_ID)
                .reversePathId(TEST_REVERSE_PATH_ID)
                .mirrorSwitch(switchA)
                .egressSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .egressOuterVlan(TEST_OUTER_VLAN)
                .egressInnerVlan(TEST_INNER_VLAN)
                .build();
        flowMirrorRepository.add(flowMirror);

        flowMirrorPoints = FlowMirrorPoints.builder()
                .mirrorSwitch(switchA)
                .mirrorGroup(mirrorGroup)
                .build();
        flowMirrorPointsRepository.add(flowMirrorPoints);
        flowMirrorPoints.addFlowMirrors(flowMirror);
    }

    @Test
    public void shouldCreateFlowMirrorPaths() {
        createTestFlowPaths();

        Collection<FlowMirrorPath> allPaths = flowMirrorPathRepository.findAll();
        assertThat(allPaths, hasSize(2));

        FlowMirrorPath foundPath = flowMirrorPathRepository.findById(TEST_MIRROR_PATH_ID_1).get();
        assertEquals(switchA.getSwitchId(), foundPath.getMirrorSwitchId());
        assertEquals(switchB.getSwitchId(), foundPath.getEgressSwitchId());

        FlowMirror flowMirror = flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID).get();
        assertThat(flowMirror.getMirrorPaths(), hasSize(2));
    }

    @Test
    public void shouldFindFlowMirrorPathsByIds() {
        createTestFlowPaths();

        Map<PathId, FlowMirrorPath> pathMap = flowMirrorPathRepository.findByIds(
                newHashSet(TEST_MIRROR_PATH_ID_1, TEST_MIRROR_PATH_ID_2));
        assertEquals(2, pathMap.size());
        assertEquals(TEST_MIRROR_PATH_ID_1, pathMap.get(TEST_MIRROR_PATH_ID_1).getMirrorPathId());
        assertEquals(TEST_MIRROR_PATH_ID_2, pathMap.get(TEST_MIRROR_PATH_ID_2).getMirrorPathId());

        Map<PathId, FlowMirrorPath> singlePathMap = flowMirrorPathRepository.findByIds(
                newHashSet(TEST_MIRROR_PATH_ID_2));
        assertEquals(1, singlePathMap.size());
        assertEquals(TEST_MIRROR_PATH_ID_2, singlePathMap.get(TEST_MIRROR_PATH_ID_2).getMirrorPathId());
    }

    @Test
    public void shouldFindFlowMirrorByIds() {
        createTestFlowPaths();

        Map<PathId, FlowMirror> mirrorMap = flowMirrorPathRepository.findFlowsMirrorsByPathIds(
                newHashSet(TEST_MIRROR_PATH_ID_1, TEST_MIRROR_PATH_ID_2));
        assertEquals(2, mirrorMap.size());
        assertEquals(flowMirror, mirrorMap.get(TEST_MIRROR_PATH_ID_1));
        assertEquals(flowMirror, mirrorMap.get(TEST_MIRROR_PATH_ID_2));

        Map<PathId, FlowMirror> singleMirrorMap = flowMirrorPathRepository.findFlowsMirrorsByPathIds(
                newHashSet(TEST_MIRROR_PATH_ID_2));
        assertEquals(1, singleMirrorMap.size());
        assertEquals(flowMirror, singleMirrorMap.get(TEST_MIRROR_PATH_ID_2));
    }

    @Test
    public void shouldFindFlowMirrorPointsByIds() {
        createTestFlowPaths();

        Map<PathId, FlowMirrorPoints> mirrorMap = flowMirrorPathRepository.findFlowsMirrorPointsByPathIds(
                newHashSet(TEST_MIRROR_PATH_ID_1, TEST_MIRROR_PATH_ID_2));
        assertEquals(2, mirrorMap.size());
        assertEquals(flowMirrorPoints, mirrorMap.get(TEST_MIRROR_PATH_ID_1));
        assertEquals(flowMirrorPoints, mirrorMap.get(TEST_MIRROR_PATH_ID_2));

        Map<PathId, FlowMirrorPoints> singleMirrorMap = flowMirrorPathRepository.findFlowsMirrorPointsByPathIds(
                newHashSet(TEST_MIRROR_PATH_ID_2));
        assertEquals(1, singleMirrorMap.size());
        assertEquals(flowMirrorPoints, singleMirrorMap.get(TEST_MIRROR_PATH_ID_2));
    }

    @Test
    public void shouldFindFlowMirrorPointsByEndpointSwitch() {
        createTestFlowPaths();

        List<FlowMirrorPath> firstResponse = new ArrayList<>(
                flowMirrorPathRepository.findByEndpointSwitch(TEST_SWITCH_A_ID));
        assertEquals(2, firstResponse.size());
        assertEquals(TEST_MIRROR_PATH_ID_1, firstResponse.get(0).getMirrorPathId());
        assertEquals(TEST_MIRROR_PATH_ID_2, firstResponse.get(1).getMirrorPathId());

        List<FlowMirrorPath> secondResponse = new ArrayList<>(
                flowMirrorPathRepository.findByEndpointSwitch(TEST_SWITCH_B_ID));
        assertEquals(1, secondResponse.size());
        assertEquals(TEST_MIRROR_PATH_ID_1, secondResponse.get(0).getMirrorPathId());
    }

    @Test
    public void shouldFindFlowMirrorPointsBySegmentSwitch() {
        createTestFlowPaths();
        List<FlowMirrorPath> paths = new ArrayList<>(
                flowMirrorPathRepository.findBySegmentSwitch(TEST_SWITCH_D_ID));
        assertEquals(1, paths.size());
        assertEquals(TEST_MIRROR_PATH_ID_2, paths.get(0).getMirrorPathId());
    }

    @Test
    public void shouldDeleteFlowMirrorPath() {
        FlowMirrorPath path = createFlowPath(TEST_MIRROR_PATH_ID_1, FORWARD_COOKIE, switchA, switchB);

        transactionManager.doInTransaction(() ->
                flowMirrorPathRepository.remove(path));

        assertEquals(0, flowMirrorPathRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowMirrorPath() {
        createTestFlowPaths();

        transactionManager.doInTransaction(() -> {
            flowMirrorPathRepository.findAll().forEach(flowMirrorPathRepository::remove);
        });

        assertEquals(0, flowMirrorPathRepository.findAll().size());
    }

    private void createTestFlowPaths() {
        FlowMirrorPath pathA = createFlowPath(TEST_MIRROR_PATH_ID_1, FORWARD_COOKIE, switchA, switchB);
        FlowMirrorPath pathB = createFlowPath(TEST_MIRROR_PATH_ID_2, REVERSE_COOKIE, switchA, switchC);
        pathB.setSegments(Lists.newArrayList(
                PathSegment.builder()
                        .pathId(TEST_MIRROR_PATH_ID_2)
                        .srcSwitch(switchA)
                        .srcPort(PORT_1)
                        .destSwitch(switchD)
                        .destPort(PORT_2)
                        .build(),
                PathSegment.builder()
                        .pathId(TEST_MIRROR_PATH_ID_2)
                        .srcSwitch(switchD)
                        .srcPort(PORT_3)
                        .destSwitch(switchC)
                        .destPort(PORT_4)
                        .build()));
        flowMirror.addMirrorPaths(pathA, pathB);
    }

    private FlowMirrorPath createFlowPath(PathId pathId, FlowSegmentCookie cookie, Switch srcSwitch, Switch dstSwitch) {

        FlowMirrorPath flowMirrorPath = buildMirrorPath(pathId, cookie, srcSwitch, dstSwitch);
        flowMirrorPathRepository.add(flowMirrorPath);
        return flowMirrorPath;
    }

    static FlowMirrorPath buildMirrorPath(PathId pathId, FlowSegmentCookie cookie, Switch srcSwitch, Switch dstSwitch) {
        return FlowMirrorPath.builder()
                .mirrorPathId(pathId)
                .cookie(cookie)
                .mirrorSwitch(srcSwitch)
                .egressSwitch(dstSwitch)
                .status(FlowPathStatus.ACTIVE)
                .build();
    }
}
