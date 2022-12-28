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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.persistence.ferma.repositories.FermaFlowMirrorPathRepositoryTest.buildMirrorPath;

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
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FermaFlowMirrorRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final PathId TEST_PATH_ID = new PathId("test_path_id");
    static final String TEST_FLOW_MIRROR_ID_1 = "flow_mirror_1";
    static final String TEST_FLOW_MIRROR_ID_2 = "flow_mirror_2";
    static final String TEST_FLOW_MIRROR_ID_3 = "flow_mirror_3";
    static final PathId TEST_MIRROR_PATH_ID_1 = new PathId("mirror_path_1");
    static final PathId TEST_MIRROR_PATH_ID_2 = new PathId("mirror_path_2");
    static final PathId TEST_MIRROR_PATH_ID_3 = new PathId("mirror_path_3");
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);
    static final GroupId TEST_GROUP_ID = new GroupId(10);
    static final FlowSegmentCookie FORWARD_COOKIE = FlowSegmentCookie.builder()
            .flowEffectiveId(1).direction(FlowPathDirection.FORWARD).mirror(true).build();

    FlowMirrorPathRepository flowMirrorPathRepository;
    FlowMirrorRepository flowMirrorRepository;
    FlowMirrorPointsRepository flowMirrorPointsRepository;
    MirrorGroupRepository mirrorGroupRepository;
    SwitchRepository switchRepository;

    Switch switchA;
    Switch switchB;
    Switch switchC;
    FlowMirrorPoints flowMirrorPoints;
    MirrorGroup mirrorGroup;

    @Before
    public void setUp() {
        flowMirrorPointsRepository = repositoryFactory.createFlowMirrorPointsRepository();
        flowMirrorRepository = repositoryFactory.createFlowMirrorRepository();
        flowMirrorPathRepository = repositoryFactory.createFlowMirrorPathRepository();
        mirrorGroupRepository = repositoryFactory.createMirrorGroupRepository();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchA = createTestSwitch(TEST_SWITCH_A_ID.getId());
        switchB = createTestSwitch(TEST_SWITCH_B_ID.getId());
        switchC = createTestSwitch(TEST_SWITCH_C_ID.getId());

        assertEquals(3, switchRepository.findAll().size());

        mirrorGroup = MirrorGroup.builder()
                .groupId(TEST_GROUP_ID)
                .flowId(TEST_FLOW_ID)
                .pathId(TEST_PATH_ID)
                .switchId(switchA.getSwitchId())
                .mirrorDirection(MirrorDirection.INGRESS)
                .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                .build();
        mirrorGroupRepository.add(mirrorGroup);

        flowMirrorPoints = FlowMirrorPoints.builder()
                .mirrorGroup(mirrorGroup)
                .mirrorSwitch(switchA)
                .build();
        flowMirrorPointsRepository.add(flowMirrorPoints);
    }

    @Test
    public void shouldCreateFlowMirror() {
        createTestFlowMirrors();

        Collection<FlowMirror> allMirrors = flowMirrorRepository.findAll();
        assertEquals(2, allMirrors.size());

        FlowMirror foundMirror = flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1).get();
        assertEquals(switchA.getSwitchId(), foundMirror.getMirrorSwitchId());
        assertEquals(switchB.getSwitchId(), foundMirror.getEgressSwitchId());

        FlowMirrorPoints flowMirrorPoints = flowMirrorPointsRepository.findByGroupId(TEST_GROUP_ID).get();
        assertEquals(2, flowMirrorPoints.getFlowMirrors().size());
    }

    @Test
    public void shouldExistFlowMirror() {
        createTestFlowMirrors();
        assertTrue(flowMirrorRepository.exists(TEST_FLOW_MIRROR_ID_1));
        assertFalse(flowMirrorRepository.exists(TEST_FLOW_MIRROR_ID_3));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfTransactionIsOpenDuringRemove() {
        createTestFlowMirrors();
        transactionManager.doInTransaction(() -> flowMirrorRepository.remove(TEST_FLOW_MIRROR_ID_1));
    }

    @Test
    public void shouldFindByDestEndpoint() {
        createTestFlowMirrors();

        FlowMirror foundMirror = flowMirrorRepository.findByEgressEndpoint(switchC.getSwitchId(), 3, 4, 5).get();
        assertEquals(switchA.getSwitchId(), foundMirror.getMirrorSwitchId());
        assertEquals(switchC.getSwitchId(), foundMirror.getEgressSwitchId());
    }

    @Test
    public void shouldFindByEgressSwitchIdAndPort() {
        FlowMirror mirrorA = createFlowMirror(TEST_FLOW_MIRROR_ID_1, switchA, switchB, 2, 3, 4);
        FlowMirror mirrorB = createFlowMirror(TEST_FLOW_MIRROR_ID_2, switchA, switchC, 3, 4, 5);
        FlowMirror mirrorC = createFlowMirror(TEST_FLOW_MIRROR_ID_3, switchB, switchC, 3, 5, 6);
        flowMirrorPoints.addFlowMirrors(mirrorA, mirrorB, mirrorC);

        Map<String, FlowMirror> foundMirrors =
                flowMirrorRepository.findByEgressSwitchIdAndPort(switchC.getSwitchId(), 3)
                        .stream().collect(Collectors.toMap(FlowMirror::getFlowMirrorId, Function.identity()));
        assertEquals(2, foundMirrors.size());
        assertEquals(switchA.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_2).getMirrorSwitchId());
        assertEquals(switchC.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_2).getEgressSwitchId());
        assertEquals(switchB.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_3).getMirrorSwitchId());
        assertEquals(switchC.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_3).getEgressSwitchId());
    }

    @Test
    public void shouldDeleteFlowMirror() {
        FlowMirror mirror = createFlowMirror(TEST_FLOW_MIRROR_ID_1, switchA, switchB, 2, 3, 4);
        transactionManager.doInTransaction(() -> flowMirrorRepository.remove(mirror));
        assertEquals(0, flowMirrorRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowMirror() {
        createTestFlowMirrors();
        transactionManager.doInTransaction(() -> flowMirrorRepository.findAll().forEach(flowMirrorRepository::remove));
        assertEquals(0, flowMirrorRepository.findAll().size());
    }

    @Test
    public void shouldUpdateFlowMirrorStatus() {
        createTestFlowMirrors();
        assertEquals(FlowPathStatus.ACTIVE, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1).get().getStatus());
        assertEquals(FlowPathStatus.ACTIVE, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_2).get().getStatus());

        flowMirrorRepository.updateStatus(TEST_FLOW_MIRROR_ID_2, FlowPathStatus.DEGRADED);
        assertEquals(FlowPathStatus.ACTIVE, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1).get().getStatus());
        assertEquals(FlowPathStatus.DEGRADED, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_2).get().getStatus());
    }

    @Test
    public void shouldRemoveFlowMirror() {
        createTestFlowMirrors();
        assertEquals(2, flowMirrorRepository.findAll().size());

        flowMirrorRepository.remove(TEST_FLOW_MIRROR_ID_1);
        Collection<FlowMirror> allFlowMirrors = flowMirrorRepository.findAll();
        assertEquals(1, allFlowMirrors.size());
        assertEquals(TEST_FLOW_MIRROR_ID_2, allFlowMirrors.iterator().next().getFlowMirrorId());
    }

    @Test
    public void shouldAddFlowMirrorPaths() {
        createTestFlowMirrors();
        FlowMirrorPath path1 = buildMirrorPath(TEST_MIRROR_PATH_ID_1, FORWARD_COOKIE, switchA, switchB);
        FlowMirrorPath path2 = buildMirrorPath(TEST_MIRROR_PATH_ID_2, FORWARD_COOKIE, switchB, switchA);
        flowMirrorPathRepository.add(path1);
        flowMirrorPathRepository.add(path2);

        Optional<FlowMirror> mirror = flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1);
        assertTrue(mirror.isPresent());

        mirror.get().addMirrorPaths(path1, path2);

        Optional<FlowMirror> mirror2 = flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1);
        assertTrue(mirror2.isPresent());
        assertEquals(2, mirror2.get().getMirrorPaths().size());

        assertEquals(Sets.newHashSet(TEST_MIRROR_PATH_ID_1, TEST_MIRROR_PATH_ID_2), mirror2.get().getMirrorPaths()
                .stream().map(FlowMirrorPath::getMirrorPathId).collect(Collectors.toSet()));
    }

    @Test
    public void shouldSetForwardFlowMirrorPath() {
        createTestFlowMirrors();
        FlowMirrorPath path1 = buildMirrorPath(TEST_MIRROR_PATH_ID_1, FORWARD_COOKIE, switchA, switchB);
        flowMirrorPathRepository.add(path1);

        Optional<FlowMirror> mirror = flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1);
        assertTrue(mirror.isPresent());

        mirror.get().setForwardPath(path1);

        Optional<FlowMirror> mirror2 = flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1);
        assertTrue(mirror2.isPresent());
        assertEquals(1, mirror2.get().getMirrorPaths().size());
        assertEquals(TEST_MIRROR_PATH_ID_1, mirror2.get().getMirrorPaths().iterator().next().getMirrorPathId());
        assertEquals(TEST_MIRROR_PATH_ID_1, mirror2.get().getForwardPath().getMirrorPathId());
        assertEquals(TEST_MIRROR_PATH_ID_1, mirror2.get().getForwardPathId());
    }

    private void createTestFlowMirrors() {
        FlowMirror mirrorA = createFlowMirror(TEST_FLOW_MIRROR_ID_1, switchA, switchB, 2, 3, 4);
        FlowMirror mirrorB = createFlowMirror(TEST_FLOW_MIRROR_ID_2, switchA, switchC, 3, 4, 5);
        flowMirrorPoints.addFlowMirrors(mirrorA, mirrorB);
    }

    private FlowMirror createFlowMirror(
            String mirrorPathId, Switch mirrorSwitch, Switch egressSwitch, int dstPort, int dstOuterVlan,
            int dstInnerVlan) {
        FlowMirror flowMirror = FlowMirror.builder()
                .flowMirrorId(mirrorPathId)
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(egressSwitch)
                .egressPort(dstPort)
                .egressOuterVlan(dstOuterVlan)
                .egressInnerVlan(dstInnerVlan)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flowMirrorRepository.add(flowMirror);
        return flowMirror;
    }
}
