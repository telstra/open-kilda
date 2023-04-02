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

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.FieldMatch;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.Instructions;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.WriteMetadata;
import org.openkilda.messaging.info.switches.v2.action.PopVlanActionEntry;
import org.openkilda.messaging.info.switches.v2.action.PortOutActionEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RulesValidationEntryV2Test {

    @Test
    public void splitAndUniteEmptyRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(true)
                .missing(new ArrayList<>())
                .misconfigured(new ArrayList<>())
                .excess(new ArrayList<>())
                .proper(new ArrayList<>())
                .build();
        List<RulesValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNullRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(true)
                .missing(null)
                .misconfigured(null)
                .excess(null)
                .proper(null)
                .build();
        List<RulesValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteOneRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildRulesInfo(1, 1))
                .excess(buildRulesInfo(2, 1))
                .proper(buildRulesInfo(3, 1))
                .misconfigured(buildMisconfiguredRulesInfo(4, 1))
                .build();
        List<RulesValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteFourRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildRulesInfo(1, 1))
                .excess(buildRulesInfo(2, 1))
                .proper(buildRulesInfo(3, 1))
                .misconfigured(buildMisconfiguredRulesInfo(4, 1))
                .build();
        List<RulesValidationEntryV2> list = entry.split(1, 1);
        assertEquals(4, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNotDividedRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildRulesInfo(1, 1))
                .excess(buildRulesInfo(2, 2))
                .proper(buildRulesInfo(3, 3))
                .misconfigured(buildMisconfiguredRulesInfo(4, 4))
                .build();
        List<RulesValidationEntryV2> list = entry.split(2, 3);
        assertEquals(4, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteHugeChunkRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildRulesInfo(1, 1))
                .excess(buildRulesInfo(2, 2))
                .proper(buildRulesInfo(3, 3))
                .misconfigured(buildMisconfiguredRulesInfo(4, 4))
                .build();
        List<RulesValidationEntryV2> list = entry.split(100, 200);
        assertEquals(1, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteTwoEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildRulesInfo(1, 2))
                .excess(null)
                .proper(buildRulesInfo(3, 2))
                .misconfigured(new ArrayList<>())
                .build();
        List<RulesValidationEntryV2> list = entry.split(2, 2);
        assertEquals(3, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildRulesInfo(1, 500))
                .excess(buildRulesInfo(1000, 600))
                .proper(buildRulesInfo(2000, 700))
                .misconfigured(buildMisconfiguredRulesInfo(3000, 800))
                .build();
        List<RulesValidationEntryV2> list = entry.split(100, 200);
        assertEquals(14, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesByOneRuleEntryTest() {
        RulesValidationEntryV2 entry = RulesValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildRulesInfo(1, 500))
                .excess(buildRulesInfo(1000, 600))
                .proper(buildRulesInfo(2000, 700))
                .misconfigured(buildMisconfiguredRulesInfo(3000, 800))
                .build();
        List<RulesValidationEntryV2> list = entry.split(1, 1);
        assertEquals(2600, list.size());
        RulesValidationEntryV2 united = RulesValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    static List<RuleInfoEntryV2> buildRulesInfo(int idBase, int count) {
        List<RuleInfoEntryV2> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildRuleInfo(idBase + i));
        }
        return result;
    }

    static List<MisconfiguredInfo<RuleInfoEntryV2>> buildMisconfiguredRulesInfo(int idBase, int count) {
        List<MisconfiguredInfo<RuleInfoEntryV2>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildMisconfiguredRuleInfo(idBase + i));
        }
        return result;
    }

    private static RuleInfoEntryV2 buildRuleInfo(int id) {
        return RuleInfoEntryV2.builder()
                .tableId(id)
                .cookie((long) (id + 1))
                .cookieHex("ab" + id)
                .cookieKind("some_" + id)
                .priority(id + 2)
                .match(buildMatch(id + 3))
                .instructions(buildInstructions(id + 4))
                .flowPathId("path_" + id)
                .flowId("flow_" + id)
                .yFlowId("y_flow_" + id)
                .flags(newArrayList("flog_" + id, "flag_" + (id + 1)))
                .build();
    }

    private static Map<String, FieldMatch> buildMatch(long baseId) {
        Map<String, FieldMatch> map = new HashMap<>();
        map.put("field_" + baseId, FieldMatch.builder().value(baseId).mask(baseId + 1).build());
        map.put("field_" + (baseId + 1), FieldMatch.builder().value(baseId + 2).mask(baseId + 3).build());
        return map;
    }

    private static Instructions buildInstructions(int baseId) {
        return Instructions.builder()
                .goToTable(baseId)
                .goToMeter((long) (baseId + 1))
                .applyActions(newArrayList(PopVlanActionEntry.builder().build()))
                .writeActions(newArrayList(PortOutActionEntry.builder().portNumber(15).build()))
                .writeMetadata(WriteMetadata.builder().value((long) (baseId + 2)).value((long) (baseId + 3)).build())
                .build();
    }

    private static MisconfiguredInfo<RuleInfoEntryV2> buildMisconfiguredRuleInfo(int id) {
        return MisconfiguredInfo.<RuleInfoEntryV2>builder()
                .expected(buildRuleInfo(id))
                .discrepancies(RuleInfoEntryV2.builder()
                        .tableId(id + 5)
                        .priority(id + 7)
                        .build())
                .build();
    }
}
