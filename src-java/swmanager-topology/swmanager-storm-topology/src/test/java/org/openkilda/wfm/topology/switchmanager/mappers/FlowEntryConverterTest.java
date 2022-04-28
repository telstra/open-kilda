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

package org.openkilda.wfm.topology.switchmanager.mappers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.openkilda.wfm.topology.switchmanager.mappers.FlowEntryConverter.INSTANCE;

import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class FlowEntryConverterTest {

    // Test constants
    public static final int METER_ID_VALUE = 1;
    public static final Long OF_METADATA_VALUE = 2L;
    public static final Long OF_METADATA_MASK = 3L;
    public static final int COOKIE_VALUE = 4;
    public static final int SET_FIELD_VALUE = 5;
    public static final int MATCH_VALUE = 6;
    public static final long MATCH_MASK = 1L;
    public static final int DURATION_SECONDS = 7;
    public static final int DURATION_NANOSECONDS = 8;
    public static final int PACKET_COUNT = 9;
    public static final int PRIORITY = 10;
    public static final int IDLE_TIMEOUT = 11;
    public static final int HARD_TIMEOUT = 12;
    public static final int BYTE_COUNT = 13;

    public static MeterId METER_ID = new MeterId(METER_ID_VALUE);
    public static OfTable OF_TABLE_FIELD = OfTable.INGRESS;
    public static OfVersion OF_VERSION = OfVersion.OF_15;
    public static OfMetadata OF_METADATA = new OfMetadata(OF_METADATA_VALUE, OF_METADATA_MASK);
    public static Cookie COOKIE = new Cookie(COOKIE_VALUE);
    public static Field FIELD = Field.METADATA;
    public static SetFieldAction SET_FIELD_ACTION = new SetFieldAction(SET_FIELD_VALUE, FIELD);

    public static List<Action> applyActions = new LinkedList<>();
    public static Set<Action> writeActions = new HashSet<>();
    public static Set<OfFlowFlag> flags = new HashSet<>();
    public static Set<FieldMatch> matches = new HashSet<>();

    public static Instructions instructions;
    public static FlowSpeakerData data;

    @BeforeClass
    public static void initializeData() {

        applyActions.add(SET_FIELD_ACTION);
        matches.add(new FieldMatch(OF_METADATA_VALUE, (long) OF_METADATA_MASK, Field.METADATA));

        for (Field field : Field.values()) {
            if (field.equals(Field.METADATA)) {
                continue;
            }
            matches.add(new FieldMatch(MATCH_VALUE, MATCH_MASK, field));
        }

        flags.add(OfFlowFlag.RESET_COUNTERS);
        instructions = new Instructions(applyActions, writeActions, METER_ID, OF_TABLE_FIELD, OF_METADATA);

        data = FlowSpeakerData.builder()
                .cookie(COOKIE)
                .durationSeconds(DURATION_SECONDS)
                .durationNanoSeconds(DURATION_NANOSECONDS)
                .table(OF_TABLE_FIELD)
                .packetCount(PACKET_COUNT)
                .ofVersion(OF_VERSION)
                .priority(PRIORITY)
                .idleTimeout(IDLE_TIMEOUT)
                .hardTimeout(HARD_TIMEOUT)
                .byteCount(BYTE_COUNT)
                .match(matches)
                .instructions(instructions)
                .flags(flags)
                .build();
    }

    @Test
    public void mapFlowEntryTest() {

        FlowEntry entry = INSTANCE.toFlowEntry(data);
        // General asserts
        assertEquals(COOKIE_VALUE, entry.getCookie());
        assertEquals(DURATION_SECONDS, entry.getDurationSeconds());
        assertEquals(DURATION_NANOSECONDS, entry.getDurationNanoSeconds());
        assertEquals(OF_TABLE_FIELD.getTableId(), entry.getTableId());
        assertEquals(PACKET_COUNT, entry.getPacketCount());
        assertEquals(OF_VERSION.toString(), entry.getVersion());
        assertEquals(PRIORITY, entry.getPriority());
        assertEquals(IDLE_TIMEOUT, entry.getIdleTimeout());
        assertEquals(HARD_TIMEOUT, entry.getHardTimeout());
        assertEquals(BYTE_COUNT, entry.getByteCount());

        // Field match
        FlowMatchField entryMatchField = entry.getMatch();
        // Metadata field case
        assertEquals(OF_METADATA_MASK.toString(), entryMatchField.getMetadataMask());
        assertEquals(OF_METADATA_VALUE.toString(), entryMatchField.getMetadataValue());
        // Other cases
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getEthType());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getEthSrc());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getEthDst());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getInPort());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getIpProto());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getUdpSrc());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getUdpDst());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getVlanVid());
        assertEquals(Integer.toString(MATCH_VALUE), entryMatchField.getTunnelId());

        // Instructions
        FlowInstructions instructions = entry.getInstructions();
        assertEquals(METER_ID.getValue(), instructions.getGoToMeter().longValue());
        assertEquals(OF_TABLE_FIELD.getTableId(), instructions.getGoToTable().longValue());
        FlowApplyActions applyActions = instructions.getApplyActions();
        FlowSetFieldAction setFieldAction = applyActions.getSetFieldActions().get(0);
        assertEquals(SET_FIELD_ACTION.getField().toString(), setFieldAction.getFieldName());
        assertEquals(Long.toString(SET_FIELD_ACTION.getValue()), setFieldAction.getFieldValue());

        // Flags
        assertArrayEquals(flags.stream().map(OfFlowFlag::toString).toArray(), entry.getFlags());

    }

}
