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

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MetersValidationEntryV2Test {

    @Test
    public void splitAndUniteEmptyMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(true)
                .missing(new ArrayList<>())
                .misconfigured(new ArrayList<>())
                .excess(new ArrayList<>())
                .proper(new ArrayList<>())
                .build();
        List<MetersValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNullMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(true)
                .missing(null)
                .misconfigured(null)
                .excess(null)
                .proper(null)
                .build();
        List<MetersValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteOneMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildMetersInfo(1, 1))
                .excess(buildMetersInfo(2, 1))
                .proper(buildMetersInfo(3, 1))
                .misconfigured(buildMisconfiguredMetersInfo(4, 1))
                .build();
        List<MetersValidationEntryV2> list = entry.split(4, 4);
        assertEquals(1, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteTwoEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildMetersInfo(1, 2))
                .excess(null)
                .proper(buildMetersInfo(3, 2))
                .misconfigured(new ArrayList<>())
                .build();
        List<MetersValidationEntryV2> list = entry.split(2, 2);
        assertEquals(3, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteFourMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildMetersInfo(1, 1))
                .excess(buildMetersInfo(2, 1))
                .proper(buildMetersInfo(3, 1))
                .misconfigured(buildMisconfiguredMetersInfo(4, 1))
                .build();
        List<MetersValidationEntryV2> list = entry.split(1, 1);
        assertEquals(4, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNotDividedMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildMetersInfo(1, 1))
                .excess(buildMetersInfo(2, 2))
                .proper(buildMetersInfo(3, 3))
                .misconfigured(buildMisconfiguredMetersInfo(4, 4))
                .build();
        List<MetersValidationEntryV2> list = entry.split(2, 3);
        assertEquals(4, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteHugeChunkMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildMetersInfo(1, 1))
                .excess(buildMetersInfo(2, 2))
                .proper(buildMetersInfo(3, 3))
                .misconfigured(buildMisconfiguredMetersInfo(4, 4))
                .build();
        List<MetersValidationEntryV2> list = entry.split(100, 200);
        assertEquals(1, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildMetersInfo(1, 500))
                .excess(buildMetersInfo(1000, 600))
                .proper(buildMetersInfo(2000, 700))
                .misconfigured(buildMisconfiguredMetersInfo(3000, 800))
                .build();
        List<MetersValidationEntryV2> list = entry.split(100, 200);
        assertEquals(14, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesByOneMeterEntryTest() {
        MetersValidationEntryV2 entry = MetersValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildMetersInfo(1, 500))
                .excess(buildMetersInfo(1000, 600))
                .proper(buildMetersInfo(2000, 700))
                .misconfigured(buildMisconfiguredMetersInfo(3000, 800))
                .build();
        List<MetersValidationEntryV2> list = entry.split(1, 1);
        assertEquals(2600, list.size());
        MetersValidationEntryV2 united = MetersValidationEntryV2.unite(list);
        assertEquals(entry, united);
    }

    static List<MeterInfoEntryV2> buildMetersInfo(int idBase, int count) {
        List<MeterInfoEntryV2> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildMeterInfo(idBase + i));
        }
        return result;
    }

    static List<MisconfiguredInfo<MeterInfoEntryV2>> buildMisconfiguredMetersInfo(int idBase, int count) {
        List<MisconfiguredInfo<MeterInfoEntryV2>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildMisconfiguredMeterInfo(idBase + i));
        }
        return result;
    }

    private static MeterInfoEntryV2 buildMeterInfo(long id) {
        return MeterInfoEntryV2.builder()
                .meterId(id)
                .burstSize(id + 1)
                .rate(id + 2)
                .cookie(id + 3)
                .flowPathId("path_" + id)
                .flowId("flow_" + id)
                .yFlowId("y_flow_" + id)
                .flags(Lists.newArrayList("flog_" + id, "flag_" + (id + 1)))
                .build();
    }

    private static MisconfiguredInfo<MeterInfoEntryV2> buildMisconfiguredMeterInfo(long id) {
        return MisconfiguredInfo.<MeterInfoEntryV2>builder()
                .expected(buildMeterInfo(id))
                .discrepancies(MeterInfoEntryV2.builder()
                        .rate(id + 5)
                        .burstSize(id + 7)
                        .build())
                .build();
    }
}
