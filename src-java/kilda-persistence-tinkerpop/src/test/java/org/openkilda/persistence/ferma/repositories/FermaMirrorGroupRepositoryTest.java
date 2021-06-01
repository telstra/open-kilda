/* Copyright 2020 Telstra Open Source
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
import static org.junit.Assert.assertTrue;

import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.MirrorGroupRepository;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collection;

public class FermaMirrorGroupRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final String TEST_PATH_ID = "test_path";
    static final GroupId MIN_GROUP_ID = new GroupId(5);
    static final GroupId MAX_GROUP_ID = new GroupId(25);

    MirrorGroupRepository mirrorGroupRepository;

    Switch theSwitch;

    @Before
    public void setUp() {
        mirrorGroupRepository = repositoryFactory.createMirrorGroupRepository();

        theSwitch = createTestSwitch(1);
    }

    @Test
    public void shouldCreateMirrorGroup() {
        createMirrorGroup();

        Collection<MirrorGroup> allMirrorGroups = mirrorGroupRepository.findAll();
        MirrorGroup foundMirrorGroup = allMirrorGroups.iterator().next();

        assertEquals(theSwitch.getSwitchId(), foundMirrorGroup.getSwitchId());
        assertEquals(TEST_FLOW_ID, foundMirrorGroup.getFlowId());
    }

    @Ignore("InMemoryGraph doesn't enforce constraint")
    @Test(expected = PersistenceException.class)
    public void shouldNotGetMoreThanOneMirrorGroupsForPath() {
        createMirrorGroup(1, new PathId(TEST_PATH_ID));
        createMirrorGroup(2, new PathId(TEST_PATH_ID));
        mirrorGroupRepository.findByPathId(new PathId(TEST_PATH_ID));
    }

    @Test
    public void shouldGetZeroMirrorGroupsForPath() {
        Collection<MirrorGroup> meters = mirrorGroupRepository.findByPathId(new PathId(TEST_PATH_ID));
        assertTrue(meters.isEmpty());
    }

    @Test
    public void shouldDeleteMirrorGroup() {
        MirrorGroup mirrorGroup = createMirrorGroup();

        transactionManager.doInTransaction(() ->
                mirrorGroupRepository.remove(mirrorGroup));

        assertEquals(0, mirrorGroupRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundMirrorGroup() {
        createMirrorGroup();

        transactionManager.doInTransaction(() -> {
            Collection<MirrorGroup> allMirrorGroups = mirrorGroupRepository.findAll();
            MirrorGroup foundMirrorGroup = allMirrorGroups.iterator().next();
            mirrorGroupRepository.remove(foundMirrorGroup);
        });

        assertEquals(0, mirrorGroupRepository.findAll().size());
    }

    @Test
    public void shouldSelectNextInOrderResourceWhenFindUnassignedGroupId() {
        long first = findUnassignedMirrorGroupAndCreate("flow_1");
        assertEquals(5, first);

        long second = findUnassignedMirrorGroupAndCreate("flow_2");
        assertEquals(6, second);

        long third = findUnassignedMirrorGroupAndCreate("flow_3");
        assertEquals(7, third);

        transactionManager.doInTransaction(() -> {
            mirrorGroupRepository.findAll().stream()
                    .filter(mirrorGroup -> mirrorGroup.getGroupId().getValue() == second)
                    .forEach(mirrorGroupRepository::remove);
        });
        long fourth = findUnassignedMirrorGroupAndCreate("flow_4");
        assertEquals(6, fourth);

        long fifth = findUnassignedMirrorGroupAndCreate("flow_5");
        assertEquals(8, fifth);
    }

    @Test
    public void shouldAssignTwoGroupIdsForOneFlow() {
        String flowId = "flow_1";
        long flowCookie = findUnassignedMirrorGroupAndCreate(flowId, TEST_PATH_ID + "_1");
        assertEquals(5, flowCookie);

        long secondCookie = findUnassignedMirrorGroupAndCreate(flowId, TEST_PATH_ID + "_2");
        assertEquals(6, secondCookie);
    }

    private long findUnassignedMirrorGroupAndCreate(String flowId) {
        return findUnassignedMirrorGroupAndCreate(flowId, TEST_PATH_ID);
    }

    private long findUnassignedMirrorGroupAndCreate(String flowId, String pathId) {
        GroupId availableGroupId = mirrorGroupRepository
                .findFirstUnassignedGroupId(theSwitch.getSwitchId(), MIN_GROUP_ID, MAX_GROUP_ID).get();
        MirrorGroup mirrorGroup = MirrorGroup.builder()
                .switchId(theSwitch.getSwitchId())
                .groupId(availableGroupId)
                .pathId(new PathId(flowId + "_" + pathId))
                .flowId(flowId)
                .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                .mirrorDirection(MirrorDirection.INGRESS)
                .build();
        mirrorGroupRepository.add(mirrorGroup);
        return availableGroupId.getValue();
    }

    private MirrorGroup createMirrorGroup(int groupId, PathId pathId) {
        MirrorGroup mirrorGroup = MirrorGroup.builder()
                .switchId(theSwitch.getSwitchId())
                .groupId(new GroupId(groupId))
                .pathId(pathId)
                .flowId(TEST_FLOW_ID)
                .mirrorDirection(MirrorDirection.INGRESS)
                .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                .build();
        mirrorGroupRepository.add(mirrorGroup);
        return mirrorGroup;
    }

    private MirrorGroup createMirrorGroup() {
        return createMirrorGroup(1, new PathId(TEST_PATH_ID));
    }
}
