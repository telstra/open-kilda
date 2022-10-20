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

package org.openkilda.rulemanager.factory.generator.service.lacp;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.rulemanager.Constants.Priority.LACP_RULE_PRIORITY;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LacpReplyRuleGeneratorTest {
    public static final int LOGICAL_PORT = 1234;
    private static RuleManagerConfig config;

    @BeforeClass
    public static void init() {
        config = mock(RuleManagerConfig.class);
        when(config.getLacpPacketSize()).thenReturn(150);
        when(config.getLacpRateLimit()).thenReturn(100);
        when(config.getLacpMeterBurstSizeInPackets()).thenReturn(1000L);
    }

    @Test
    public void shouldBuildCorrectRuleWithoutOtherLacpPorts() {
        LacpReplyRuleGenerator generator = buildGenerator(false);
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(METERS, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);

        assertLacpReplyFlow(sw, flowCommandData, newArrayList(meterCommandData.getUuid()));

        assertEquals(sw.getSwitchId(), meterCommandData.getSwitchId());
        assertEquals(MeterId.LACP_REPLY_METER_ID, meterCommandData.getMeterId());
        assertEquals(config.getLacpRateLimit(), meterCommandData.getRate());
        assertEquals(config.getLacpMeterBurstSizeInPackets(), meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.PKTPS)
                .containsAll(meterCommandData.getFlags()));
        assertTrue(meterCommandData.getDependsOn().isEmpty());
    }

    @Test
    public void shouldBuildCorrectRuleWithOtherLacpPorts() {
        LacpReplyRuleGenerator generator = buildGenerator(true);
        Switch sw = buildSwitch("OF_13", Sets.newHashSet(METERS, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertLacpReplyFlow(sw, flowCommandData, newArrayList());
    }

    private static void assertLacpReplyFlow(Switch sw, FlowSpeakerData flowCommandData, List<UUID> expectedDependsOn) {
        assertEquals(sw.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(sw.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertEquals(expectedDependsOn, new ArrayList<>(flowCommandData.getDependsOn()));

        assertEquals(new PortColourCookie(CookieType.LACP_REPLY_INPUT, LOGICAL_PORT), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(LACP_RULE_PRIORITY, flowCommandData.getPriority());

        Instructions expectedInstructions = Instructions.builder()
                .goToMeter(MeterId.LACP_REPLY_METER_ID)
                .applyActions(newArrayList(new PortOutAction(new PortNumber(SpecialPortType.CONTROLLER))))
                .build();
        assertEquals(expectedInstructions, flowCommandData.getInstructions());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(LOGICAL_PORT).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.SLOW_PROTOCOLS).build(),
                FieldMatch.builder().field(Field.ETH_DST).value(MacAddress.SLOW_PROTOCOLS.toLong()).build());
        assertEqualsMatch(expectedMatch, flowCommandData.getMatch());
    }

    private LacpReplyRuleGenerator buildGenerator(boolean hasOtherLacpPorts) {
        return LacpReplyRuleGenerator.builder()
                .inPort(LOGICAL_PORT)
                .config(config)
                .switchHasOtherLacpPorts(hasOtherLacpPorts)
                .build();
    }
}
