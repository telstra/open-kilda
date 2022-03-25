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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.openkilda.wfm.topology.switchmanager.mappers.MeterEntryConverter.INSTANCE;

import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.model.MeterId;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class MeterEntryConverterTest {
    public static MeterId METER_ID = new MeterId(3L);
    private static final Long RATE = 4L;
    private static final Long BURST_SIZE = 5L;

    public static MeterSpeakerData data;

    public static Set<MeterFlag> flags = new HashSet<>();

    private void initializeData() {
        flags.add(MeterFlag.BURST);

        data = MeterSpeakerData.builder()
                .meterId(METER_ID)
                .rate(RATE)
                .burst(BURST_SIZE)
                .flags(flags)
                .build();

    }

    @Test
    public void mapMeterEntryTest() {
        initializeData();

        MeterInfoEntry entry = INSTANCE.toMeterEntry(data);

        assertEquals(METER_ID.getValue(), entry.getMeterId());
        assertEquals(RATE, entry.getRate());
        assertEquals(BURST_SIZE, entry.getBurstSize());
        assertEquals(flags.toArray()[0].toString(), entry.getFlags()[0]);
    }
}
