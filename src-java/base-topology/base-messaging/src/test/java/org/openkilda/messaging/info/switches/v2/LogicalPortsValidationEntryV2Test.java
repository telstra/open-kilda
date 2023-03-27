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

import org.openkilda.messaging.info.switches.LogicalPortType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LogicalPortsValidationEntryV2Test {

    @Test
    public void splitAndUniteEmptyLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .missing(new ArrayList<>())
                .misconfigured(new ArrayList<>())
                .excess(new ArrayList<>())
                .proper(new ArrayList<>())
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(4, 4);
        assertEquals("Method split should return the list with the length of 1", 1, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteEmptyLogicalPortEntryWithErrorTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(false)
                .error("Timeout for waiting response on DumpLogicalPortsRequest()"
                        + " Details: Error in SpeakerWorkerService")
                .missing(new ArrayList<>())
                .misconfigured(new ArrayList<>())
                .excess(new ArrayList<>())
                .proper(new ArrayList<>())
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(4, 4);
        assertEquals("Method split should return the list with the length of 1", 1, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNullLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .missing(null)
                .misconfigured(null)
                .excess(null)
                .proper(null)
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(4, 4);
        assertEquals("Method split should return the list with the length of 1", 1, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteOneLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildLogicalPortsInfo(1, 1))
                .excess(buildLogicalPortsInfo(2, 1))
                .proper(buildLogicalPortsInfo(3, 1))
                .misconfigured(buildMisconfiguredLogicalPortsInfo(4, 1))
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(4, 4);
        assertEquals("Method split should return the list with the length of 1", 1, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteFourLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildLogicalPortsInfo(1, 1))
                .excess(buildLogicalPortsInfo(2, 1))
                .proper(buildLogicalPortsInfo(3, 1))
                .misconfigured(buildMisconfiguredLogicalPortsInfo(4, 1))
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(1, 1);
        assertEquals("Method split should return the list with the length of 4", 4, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteNotDividedLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildLogicalPortsInfo(1, 1))
                .excess(buildLogicalPortsInfo(2, 2))
                .proper(buildLogicalPortsInfo(3, 3))
                .misconfigured(buildMisconfiguredLogicalPortsInfo(4, 4))
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(2, 3);
        assertEquals("Method split should return the list with the length of 4", 4, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteTwoEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildLogicalPortsInfo(1, 2))
                .excess(null)
                .proper(buildLogicalPortsInfo(3, 2))
                .misconfigured(new ArrayList<>())
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(2, 2);
        assertEquals("Method split should return the list with the length of 3", 3, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteHugeChunkLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(false)
                .missing(buildLogicalPortsInfo(1, 1))
                .excess(buildLogicalPortsInfo(2, 2))
                .proper(buildLogicalPortsInfo(3, 3))
                .misconfigured(buildMisconfiguredLogicalPortsInfo(4, 4))
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(100, 200);
        assertEquals("Method split should return the list with the length of 1", 1, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildLogicalPortsInfo(1, 500))
                .excess(buildLogicalPortsInfo(1000, 600))
                .proper(buildLogicalPortsInfo(2000, 700))
                .misconfigured(buildMisconfiguredLogicalPortsInfo(3000, 800))
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(100, 200);
        assertEquals("Method split should return the list with the length of 14", 14, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesLogicalPortEntryWithErrorMessageTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .error("Some error message")
                .missing(buildLogicalPortsInfo(1, 500))
                .excess(buildLogicalPortsInfo(1000, 600))
                .proper(buildLogicalPortsInfo(2000, 700))
                .misconfigured(buildMisconfiguredLogicalPortsInfo(3000, 800))
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(100, 200);
        assertEquals("Method split should return the list with the length of 14", 14, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    @Test
    public void splitAndUniteManyEntriesByOneLogicalPortEntryTest() {
        LogicalPortsValidationEntryV2 entry = LogicalPortsValidationEntryV2.builder()
                .asExpected(true)
                .missing(buildLogicalPortsInfo(1, 500))
                .excess(buildLogicalPortsInfo(1000, 600))
                .proper(buildLogicalPortsInfo(2000, 700))
                .misconfigured(buildMisconfiguredLogicalPortsInfo(3000, 800))
                .build();
        List<LogicalPortsValidationEntryV2> list = entry.split(1, 1);
        assertEquals("Method split should return the list with the length of 2600", 2600, list.size());
        LogicalPortsValidationEntryV2 united = LogicalPortsValidationEntryV2.join(list);
        assertEquals(entry, united);
    }

    static List<LogicalPortInfoEntryV2> buildLogicalPortsInfo(int idBase, int count) {
        List<LogicalPortInfoEntryV2> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildLogicalPortInfo(idBase + i));
        }
        return result;
    }

    static List<MisconfiguredInfo<LogicalPortInfoEntryV2>> buildMisconfiguredLogicalPortsInfo(int idBase, int count) {
        List<MisconfiguredInfo<LogicalPortInfoEntryV2>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(buildMisconfiguredLogicalPortInfo(idBase + i));
        }
        return result;
    }

    private static LogicalPortInfoEntryV2 buildLogicalPortInfo(int id) {
        return LogicalPortInfoEntryV2.builder()
                .logicalPortNumber(id)
                .physicalPorts(newArrayList(id + 1, id + 2))
                .type(LogicalPortType.LAG)
                .build();
    }

    private static MisconfiguredInfo<LogicalPortInfoEntryV2> buildMisconfiguredLogicalPortInfo(int id) {
        return MisconfiguredInfo.<LogicalPortInfoEntryV2>builder()
                .expected(buildLogicalPortInfo(id))
                .discrepancies(LogicalPortInfoEntryV2.builder()
                        .type(LogicalPortType.BFD)
                        .physicalPorts(newArrayList(id))
                        .build())
                .build();
    }
}
