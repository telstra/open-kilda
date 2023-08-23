/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.service.lldp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;

import org.openkilda.model.Meter;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.factory.generator.service.ConnectedDevicesRuleGeneratorTest;

import com.google.common.collect.Sets;

public abstract class LldpRuleGeneratorTest extends ConnectedDevicesRuleGeneratorTest {

    protected static RuleManagerConfig prepareConfig() {
        RuleManagerConfig config = mock(RuleManagerConfig.class);
        when(config.getLldpRateLimit()).thenReturn(1);
        when(config.getLldpMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getLldpPacketSize()).thenReturn(100);

        return config;
    }

    @Override
    protected void checkMeterCommand(MeterSpeakerData meterCommandData) {
        assertEquals(createMeterIdForDefaultRule(cookie.getValue()), meterCommandData.getMeterId());
        assertEquals(config.getLldpRateLimit(), meterCommandData.getRate());
        assertEquals(config.getLldpMeterBurstSizeInPackets(), meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.PKTPS)
                .containsAll(meterCommandData.getFlags()));
    }

    @Override
    protected void checkMeterInBytesCommand(MeterSpeakerData meterCommandData) {
        assertEquals(createMeterIdForDefaultRule(cookie.getValue()), meterCommandData.getMeterId());
        long expectedRate = Meter.convertRateToKiloBits(config.getLldpRateLimit(), config.getLldpPacketSize());
        assertEquals(expectedRate, meterCommandData.getRate());
        long expectedBurst = Meter.convertBurstSizeToKiloBits(config.getLldpMeterBurstSizeInPackets(),
                config.getLldpPacketSize());
        assertEquals(expectedBurst, meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.KBPS)
                .containsAll(meterCommandData.getFlags()));
    }
}
