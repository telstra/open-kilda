/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.converter;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.stats.MeterStatsData;
import org.openkilda.messaging.info.stats.MeterStatsEntry;
import org.openkilda.model.SwitchId;

import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFMeterBandStats;
import org.projectfloodlight.openflow.protocol.OFMeterStats;
import org.projectfloodlight.openflow.protocol.OFMeterStatsReply;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.U64;

import java.util.Collections;

public class OfMeterStatsConverterTest {
    public static final int bandPacketCount = 1;
    public static final int bandByteCount = 2;
    public static final int meterByteCount = 3;
    public static final int meterPacketCount = 4;
    public static final int meterId = 5;
    public static final SwitchId switchId = new SwitchId(7);

    @Test
    public void testToPortStatsDataV13() {
        OFFactoryVer13 factory = new OFFactoryVer13();

        OFMeterBandStats bandStats = factory.meterBandStats(U64.of(bandPacketCount), U64.of(bandByteCount));
        OFMeterStats meterStats = factory.buildMeterStats()
                .setMeterId(meterId)
                .setByteInCount(U64.of(meterByteCount))     // we will put meter byte/packet count
                .setPacketInCount(U64.of(meterPacketCount)) // but we will expect to get band byte/packet count
                .setBandStats(Collections.singletonList(bandStats))
                .build();

        OFMeterStatsReply reply = factory.buildMeterStatsReply()
                .setEntries(Collections.singletonList(meterStats))
                .build();

        MeterStatsData data = OfMeterStatsConverter.toMeterStatsData(Collections.singletonList(reply), switchId);

        assertEquals(switchId, data.getSwitchId());
        assertEquals(1, data.getStats().size());

        MeterStatsEntry statsEntry = data.getStats().get(0);
        assertEquals(bandByteCount, statsEntry.getByteInCount());       // expected band byte/packet count instead of
        assertEquals(bandPacketCount, statsEntry.getPacketsInCount());  // meter byte/packet count
    }
}
