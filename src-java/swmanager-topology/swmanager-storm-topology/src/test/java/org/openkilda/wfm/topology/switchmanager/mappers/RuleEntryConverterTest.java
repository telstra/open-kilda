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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.action.SetFieldActionEntry;
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

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RuleEntryConverterTest {
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
        writeActions.add(SET_FIELD_ACTION);
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
    public void mapRuleEntryTest() {
        RuleInfoEntryV2 entry = RuleEntryConverter.INSTANCE.toRuleEntry(data);

        assertEquals(COOKIE_VALUE, entry.getCookie().longValue());
        assertEquals(OF_TABLE_FIELD.getTableId(), entry.getTableId().intValue());
        assertEquals(PRIORITY, entry.getPriority().intValue());

        // Field match
        Map<String, RuleInfoEntryV2.FieldMatch> entryMatchField = entry.getMatch();

        data.getMatch().forEach(match -> {
            String fieldName = match.getField().name();

            assertNotNull(entryMatchField.get(fieldName));
            assertEquals(match.getValue(), entryMatchField.get(fieldName).getValue().intValue());
            assertEquals(match.getMask().intValue(), entryMatchField.get(fieldName).getMask().intValue());
        });

        // Instructions
        RuleInfoEntryV2.Instructions instructions = entry.getInstructions();
        assertEquals(METER_ID.getValue(), instructions.getGoToMeter().longValue());
        assertEquals(OF_TABLE_FIELD.getTableId(), instructions.getGoToTable().longValue());
        assertEquals(OF_METADATA.getValue(), instructions.getWriteMetadata().getValue().longValue());
        assertEquals(OF_METADATA.getMask(), instructions.getWriteMetadata().getMask().longValue());

        //actions
        assertEquals(applyActions.get(0).getType().name(),
                ((SetFieldActionEntry) instructions.getApplyActions().get(0)).getActionType());
        assertEquals(((SetFieldAction) applyActions.get(0)).getField().name(),
                ((SetFieldActionEntry) instructions.getApplyActions().get(0)).getField());
        assertEquals(((SetFieldAction) applyActions.get(0)).getValue(),
                ((SetFieldActionEntry) instructions.getApplyActions().get(0)).getValue());

        ArrayList<Action> writeActionsList = Lists.newArrayList(writeActions);
        assertEquals(writeActionsList.get(0).getType().name(),
                ((SetFieldActionEntry) instructions.getApplyActions().get(0)).getActionType());
        assertEquals(((SetFieldAction) writeActionsList.get(0)).getField().name(),
                ((SetFieldActionEntry) instructions.getApplyActions().get(0)).getField());
        assertEquals(((SetFieldAction) writeActionsList.get(0)).getValue(),
                ((SetFieldActionEntry) instructions.getApplyActions().get(0)).getValue());

        // Flags
        assertEquals(flags.stream().map(OfFlowFlag::toString).collect(Collectors.toList()), entry.getFlags());
    }
}
