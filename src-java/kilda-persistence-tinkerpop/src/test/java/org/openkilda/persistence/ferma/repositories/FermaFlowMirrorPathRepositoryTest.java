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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
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
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class FermaFlowMirrorPathRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final PathId TEST_PATH_ID = new PathId("test_path_id");
    static final PathId TEST_MIRROR_PATH_ID_1 = new PathId("mirror_path_1");
    static final PathId TEST_MIRROR_PATH_ID_2 = new PathId("mirror_path_2");
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);
    static final GroupId TEST_GROUP_ID = new GroupId(10);

    FlowMirrorPathRepository flowMirrorPathRepository;
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
    public void shouldCreateFlowMirrorPaths() {
        createTestFlowPaths();

        Collection<FlowMirrorPath> allPaths = flowMirrorPathRepository.findAll();
        assertThat(allPaths, hasSize(2));

        FlowMirrorPath foundPath = flowMirrorPathRepository.findById(TEST_MIRROR_PATH_ID_1).get();
        assertEquals(switchA.getSwitchId(), foundPath.getMirrorSwitchId());
        assertEquals(switchB.getSwitchId(), foundPath.getEgressSwitchId());

        FlowMirrorPoints flowMirrorPoints = flowMirrorPointsRepository.findByGroupId(TEST_GROUP_ID).get();
        assertThat(flowMirrorPoints.getMirrorPaths(), hasSize(2));
    }

    @Test
    public void shouldFindByDestEndpoint() {
        createTestFlowPaths();

        FlowMirrorPath foundPath = flowMirrorPathRepository.findByEgressEndpoint(switchC.getSwitchId(), 3, 4, 5).get();
        assertEquals(switchA.getSwitchId(), foundPath.getMirrorSwitchId());
        assertEquals(switchC.getSwitchId(), foundPath.getEgressSwitchId());
    }

    @Test
    public void shouldDeleteFlowMirrorPath() {
        FlowMirrorPath path = createFlowPath(TEST_MIRROR_PATH_ID_1, 1, switchA, switchB, 2, 3, 4);

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
        FlowMirrorPath pathA = createFlowPath(TEST_MIRROR_PATH_ID_1, 1, switchA, switchB, 2, 3, 4);
        FlowMirrorPath pathB = createFlowPath(TEST_MIRROR_PATH_ID_2, 2, switchA, switchC, 3, 4, 5);
        flowMirrorPoints.addPaths(pathA, pathB);
    }

    private FlowMirrorPath createFlowPath(PathId pathId, long cookie, Switch srcSwitch,
                                          Switch dstSwitch, int dstPort, int dstOuterVlan, int dstInnerVlan) {

        FlowMirrorPath flowMirrorPath = FlowMirrorPath.builder()
                .pathId(pathId)
                .cookie(new FlowSegmentCookie(cookie).toBuilder().mirror(true).build())
                .mirrorSwitch(srcSwitch)
                .egressSwitch(dstSwitch)
                .egressPort(dstPort)
                .egressOuterVlan(dstOuterVlan)
                .egressInnerVlan(dstInnerVlan)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flowMirrorPathRepository.add(flowMirrorPath);
        return flowMirrorPath;
    }
}
