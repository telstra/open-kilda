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

import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class FermaFlowMirrorPointsRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final PathId TEST_PATH_ID = new PathId("test_path_id");
    static final PathId TEST_MIRROR_PATH_ID_1 = new PathId("mirror_path_1");
    static final PathId TEST_MIRROR_PATH_ID_2 = new PathId("mirror_path_2");
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final GroupId TEST_GROUP_ID = new GroupId(10);

    FlowPathRepository flowPathRepository;
    FlowMirrorPointsRepository flowMirrorPointsRepository;
    MirrorGroupRepository mirrorGroupRepository;
    SwitchRepository switchRepository;

    Switch switchA;
    Switch switchB;
    MirrorGroup mirrorGroup;

    @Before
    public void setUp() {
        flowMirrorPointsRepository = repositoryFactory.createFlowMirrorPointsRepository();
        mirrorGroupRepository = repositoryFactory.createMirrorGroupRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();

        switchA = createTestSwitch(TEST_SWITCH_A_ID.getId());
        switchB = createTestSwitch(TEST_SWITCH_B_ID.getId());

        assertEquals(2, switchRepository.findAll().size());

        mirrorGroup = MirrorGroup.builder()
                .groupId(TEST_GROUP_ID)
                .flowId(TEST_FLOW_ID)
                .pathId(TEST_PATH_ID)
                .switchId(switchA.getSwitchId())
                .mirrorDirection(MirrorDirection.INGRESS)
                .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                .build();
        mirrorGroupRepository.add(mirrorGroup);
    }

    @Test
    public void shouldCreateFlowMirrorPaths() {
        createTestFlowMirrorPoints();

        Collection<FlowMirrorPoints> allPaths = flowMirrorPointsRepository.findAll();
        assertThat(allPaths, hasSize(1));

        FlowMirrorPoints flowMirrorPoints = flowMirrorPointsRepository.findByGroupId(TEST_GROUP_ID).get();
        assertEquals(switchA.getSwitchId(), flowMirrorPoints.getMirrorSwitch().getSwitchId());
    }

    @Test
    public void shouldFindByDestEndpoint() {
        createFlowPathWithFlowMirrorPoints();

        FlowMirrorPoints flowMirrorPoints
                = flowMirrorPointsRepository.findByPathIdAndSwitchId(TEST_PATH_ID, switchA.getSwitchId()).get();
        assertEquals(switchA.getSwitchId(), flowMirrorPoints.getMirrorSwitch().getSwitchId());
    }

    @Test
    public void shouldDeleteFlowPath() {
        FlowMirrorPoints flowMirrorPoints = createTestFlowMirrorPoints();

        transactionManager.doInTransaction(() ->
                flowMirrorPointsRepository.remove(flowMirrorPoints));

        assertEquals(0, flowMirrorPointsRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowPath() {
        createTestFlowMirrorPoints();

        transactionManager.doInTransaction(() -> {
            flowMirrorPointsRepository.findAll().forEach(flowMirrorPointsRepository::remove);
        });

        assertEquals(0, flowMirrorPointsRepository.findAll().size());
    }

    private void createFlowPathWithFlowMirrorPoints() {
        FlowPath flowPath = FlowPath.builder()
                .pathId(TEST_PATH_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .build();
        flowPath.addFlowMirrorPoints(createTestFlowMirrorPoints());
        flowPathRepository.add(flowPath);
    }

    private FlowMirrorPoints createTestFlowMirrorPoints() {
        FlowMirrorPoints flowMirrorPoints = FlowMirrorPoints.builder()
                .mirrorGroup(mirrorGroup)
                .mirrorSwitch(switchA)
                .build();
        flowMirrorPointsRepository.add(flowMirrorPoints);
        return flowMirrorPoints;
    }
}
