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

import org.junit.Test;

import java.util.List;

public class SwitchValidationResponseV2Test {

    @Test
    public void splitAndUniteEmptyValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(buildGroupEntry(0, 0, 0, 0, 0, true))
                .rules(buildRuleEntry(0, 0, 0, 0, 0, true))
                .meters(buildMeterEntry(0, 0, 0, 0, 0, true))
                .logicalPorts(buildLogicalEntry(0, 0, 0, 0, 0, true))
                .build();

        List<SwitchValidationResponseV2> list = response.split(1);
        assertEquals(1, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    @Test
    public void splitAndUniteNullValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(false)
                .groups(null)
                .rules(null)
                .meters(null)
                .logicalPorts(null)
                .build();

        List<SwitchValidationResponseV2> list = response.split(1);
        assertEquals(1, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    @Test
    public void splitAndUniteOneChunkValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(buildGroupEntry(1, 2, 3, 4, 5, false))
                .rules(buildRuleEntry(6, 7, 8, 9, 10, true))
                .meters(buildMeterEntry(11, 12, 13, 14, 15, false))
                .logicalPorts(buildLogicalEntry(16, 17, 18, 19, 20, true))
                .build();

        List<SwitchValidationResponseV2> list = response.split(1000);
        assertEquals(1, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    @Test
    public void splitAndUniteOneChunkFitValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(buildGroupEntry(1, 2, 2, 2, 2, true))
                .rules(buildRuleEntry(6, 2, 2, 2, 2, false))
                .meters(buildMeterEntry(11, 2, 2, 2, 2, true))
                .logicalPorts(buildLogicalEntry(16, 2, 2, 2, 2, false))
                .build();

        List<SwitchValidationResponseV2> list = response.split(32);
        assertEquals(1, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    @Test
    public void splitAndUniteChunkSizeOneValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(buildGroupEntry(1, 2, 3, 4, 5, true))
                .rules(buildRuleEntry(6, 7, 8, 9, 10, false))
                .meters(buildMeterEntry(11, 12, 13, 14, 15, true))
                .logicalPorts(buildLogicalEntry(16, 17, 18, 19, 20, false))
                .build();

        List<SwitchValidationResponseV2> list = response.split(1);
        assertEquals(176, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    @Test
    public void splitAndUniteChunkMediumChunkValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(buildGroupEntry(1, 2, 3, 4, 5, true))
                .rules(buildRuleEntry(6, 7, 8, 9, 10, false))
                .meters(buildMeterEntry(11, 12, 13, 14, 15, true))
                .logicalPorts(buildLogicalEntry(16, 17, 18, 19, 20, false))
                .build();

        List<SwitchValidationResponseV2> list = response.split(3);
        assertEquals(59, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    @Test
    public void splitAndUniteBigChunksValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(buildGroupEntry(1, 102, 103, 104, 105, true))
                .rules(buildRuleEntry(6, 107, 108, 109, 110, false))
                .meters(buildMeterEntry(11, 112, 113, 114, 115, true))
                .logicalPorts(buildLogicalEntry(16, 117, 118, 119, 120, false))
                .build();

        List<SwitchValidationResponseV2> list = response.split(500);
        assertEquals(4, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    @Test
    public void splitAndUniteManySingleChunksValidationTest() {
        SwitchValidationResponseV2 response = SwitchValidationResponseV2.builder()
                .asExpected(true)
                .groups(buildGroupEntry(1, 102, 103, 104, 105, true))
                .rules(buildRuleEntry(6, 107, 108, 109, 110, false))
                .meters(buildMeterEntry(11, 112, 113, 114, 115, true))
                .logicalPorts(buildLogicalEntry(16, 117, 118, 119, 120, false))
                .build();

        List<SwitchValidationResponseV2> list = response.split(1);
        assertEquals(1776, list.size());
        SwitchValidationResponseV2 united = SwitchValidationResponseV2.unite(list);
        assertEquals(response, united);
    }

    private MetersValidationEntryV2 buildMeterEntry(
            int baseId, int excess, int missing, int proper, int misconfigured, boolean asExpected) {
        return MetersValidationEntryV2.builder()
                .asExpected(asExpected)
                .excess(MetersValidationEntryV2Test.buildMetersInfo(baseId, excess))
                .missing(MetersValidationEntryV2Test.buildMetersInfo(baseId + 1000, missing))
                .proper(MetersValidationEntryV2Test.buildMetersInfo(baseId + 2000, proper))
                .misconfigured(MetersValidationEntryV2Test.buildMisconfiguredMetersInfo(baseId + 3000, misconfigured))
                .build();
    }

    private RulesValidationEntryV2 buildRuleEntry(
            int baseId, int excess, int missing, int proper, int misconfigured, boolean asExpected) {
        return RulesValidationEntryV2.builder()
                .asExpected(asExpected)
                .excess(RulesValidationEntryV2Test.buildRulesInfo(baseId, excess))
                .missing(RulesValidationEntryV2Test.buildRulesInfo(baseId + 1000, missing))
                .proper(RulesValidationEntryV2Test.buildRulesInfo(baseId + 2000, proper))
                .misconfigured(RulesValidationEntryV2Test.buildMisconfiguredRulesInfo(baseId + 3000, misconfigured))
                .build();
    }

    private GroupsValidationEntryV2 buildGroupEntry(
            int baseId, int excess, int missing, int proper, int misconfigured, boolean asExpected) {
        return GroupsValidationEntryV2.builder()
                .asExpected(asExpected)
                .excess(GroupsValidationEntryV2Test.buildGroupsInfo(baseId, excess))
                .missing(GroupsValidationEntryV2Test.buildGroupsInfo(baseId + 1000, missing))
                .proper(GroupsValidationEntryV2Test.buildGroupsInfo(baseId + 2000, proper))
                .misconfigured(GroupsValidationEntryV2Test.buildMisconfiguredGroupsInfo(baseId + 3000, misconfigured))
                .build();
    }

    private LogicalPortsValidationEntryV2 buildLogicalEntry(
            int baseId, int excess, int missing, int proper, int misconfigured, boolean asExpected) {
        return LogicalPortsValidationEntryV2.builder()
                .asExpected(asExpected)
                .excess(LogicalPortsValidationEntryV2Test.buildLogicalPortsInfo(baseId, excess))
                .missing(LogicalPortsValidationEntryV2Test.buildLogicalPortsInfo(baseId + 1000, missing))
                .proper(LogicalPortsValidationEntryV2Test.buildLogicalPortsInfo(baseId + 2000, proper))
                .misconfigured(LogicalPortsValidationEntryV2Test.buildMisconfiguredLogicalPortsInfo(
                        baseId + 3000, misconfigured))
                .build();
    }
}
