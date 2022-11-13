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

package org.openkilda.messaging.info.switches.v2;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2.BucketEntry;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class GroupsValidationEntryV2Test {

    @Test
    public void splitAndUniteEmptyGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(true)
                .missing(new ArrayList<>())
                .misconfigured(new ArrayList<>())
                .excess(new ArrayList<>())
                .proper(new ArrayList<>())
                .build();
        List<GroupsValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNullGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(true)
                .missing(null)
                .misconfigured(null)
                .excess(null)
                .proper(null)
                .build();
        List<GroupsValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteOneGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildGroupsInfo(1, 1))
                .excess(buildGroupsInfo(2, 1))
                .proper(buildGroupsInfo(3, 1))
                .misconfigured(buildMisconfiguredGroupsInfo(4, 1))
                .build();
        List<GroupsValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteFourGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildGroupsInfo(1, 1))
                .excess(buildGroupsInfo(2, 1))
                .proper(buildGroupsInfo(3, 1))
                .misconfigured(buildMisconfiguredGroupsInfo(4, 1))
                .build();
        List<GroupsValidationEntryV2> list = entry.split(1, 1);
        assertEquals(4, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteTwoEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildGroupsInfo(1, 2))
                .excess(null)
                .proper(buildGroupsInfo(3, 2))
                .misconfigured(new ArrayList<>())
                .build();
        List<GroupsValidationEntryV2> list = entry.split(2, 2);
        assertEquals(3, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNotDividedGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildGroupsInfo(1, 1))
                .excess(buildGroupsInfo(2, 2))
                .proper(buildGroupsInfo(3, 3))
                .misconfigured(buildMisconfiguredGroupsInfo(4, 4))
                .build();
        List<GroupsValidationEntryV2> list = entry.split(2, 3);
        assertEquals(4, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteHugeChunkGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildGroupsInfo(1, 1))
                .excess(buildGroupsInfo(2, 2))
                .proper(buildGroupsInfo(3, 3))
                .misconfigured(buildMisconfiguredGroupsInfo(4, 4))
                .build();
        List<GroupsValidationEntryV2> list = entry.split(100, 200);
        assertEquals(1, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildGroupsInfo(1, 500))
                .excess(buildGroupsInfo(1000, 600))
                .proper(buildGroupsInfo(2000, 700))
                .misconfigured(buildMisconfiguredGroupsInfo(3000, 800))
                .build();
        List<GroupsValidationEntryV2> list = entry.split(100, 200);
        assertEquals(14, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesByOneGroupEntryTest() {
        GroupsValidationEntryV2 entry = GroupsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildGroupsInfo(1, 500))
                .excess(buildGroupsInfo(1000, 600))
                .proper(buildGroupsInfo(2000, 700))
                .misconfigured(buildMisconfiguredGroupsInfo(3000, 800))
                .build();
        List<GroupsValidationEntryV2> list = entry.split(1, 1);
        assertEquals(2600, list.size());
        GroupsValidationEntryV2 united = GroupsValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    static List<GroupInfoEntryV2> buildGroupsInfo(int idBase, int count) {
        List<GroupInfoEntryV2> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildGroupInfo(idBase + i));
        }
        return result;
    }

    static List<MisconfiguredInfo<GroupInfoEntryV2>> buildMisconfiguredGroupsInfo(int idBase, int count) {
        List<MisconfiguredInfo<GroupInfoEntryV2>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildMisconfiguredGroupInfo(idBase + i));
        }
        return result;
    }

    private static GroupInfoEntryV2 buildGroupInfo(int id) {
        return GroupInfoEntryV2.builder()
                .groupId(id)
                .buckets(Lists.newArrayList(buildBucket(id + 1), buildBucket(id + 5)))
                .flowPathId("path_" + id)
                .flowId("flow_" + id)
                .build();
    }

    private static BucketEntry buildBucket(int baseId) {
        return BucketEntry.builder()
                .port(baseId)
                .vlan(baseId + 1)
                .vni(baseId + 2)
                .build();
    }

    private static MisconfiguredInfo<GroupInfoEntryV2> buildMisconfiguredGroupInfo(int id) {
        return MisconfiguredInfo.<GroupInfoEntryV2>builder()
                .expected(buildGroupInfo(id))
                .discrepancies(GroupInfoEntryV2.builder()
                        .flowId("fl_" + (id + 5))
                        .flowPathId("path_" + (id + 7))
                        .build())
                .build();
    }
}
