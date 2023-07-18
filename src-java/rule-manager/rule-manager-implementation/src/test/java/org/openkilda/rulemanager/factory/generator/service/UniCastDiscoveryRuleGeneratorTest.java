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

package org.openkilda.rulemanager.factory.generator.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.DISCOVERY_RULE_PRIORITY;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.Mask;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UniCastDiscoveryRuleGeneratorTest {

    private RuleManagerConfig config;

    private UniCastDiscoveryRuleGenerator generator;
    private Switch sw;

    @BeforeEach
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getBroadcastRateLimit()).thenReturn(200);
        when(config.getSystemMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getDiscoPacketSize()).thenReturn(250);
        when(config.getFlowPingMagicSrcMacAddress()).thenReturn("00:26:E1:FF:FF:FE");

        generator = UniCastDiscoveryRuleGenerator.builder()
                .config(config)
                .build();
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(METERS, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);

        assertEquals(1, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(2, match.size());
        checkMatch(match);

        checkInstructions(flowCommandData.getInstructions(), meterCommandData.getMeterId());

        // Check meter command
        checkMeterCommand(meterCommandData);
    }

    @Test
    public void shouldNotGenerateRuleForOf12() {
        sw = buildSwitch("OF_12", Sets.newHashSet(METERS, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertTrue(commands.isEmpty());
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterForOf15() {
        sw = buildSwitch("OF_15", Sets.newHashSet(METERS, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);

        assertEquals(1, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(2, match.size());
        checkMatch(match);

        // Check flow command has correct instructions for OF 1.5
        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(3, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof MeterAction);
        MeterAction meterAction = (MeterAction) first;
        assertEquals(meterCommandData.getMeterId(), meterAction.getMeterId());
        checkPortOutAction(instructions.getApplyActions().get(1));
        checkSetFieldAction(instructions.getApplyActions().get(2));
        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertNull(instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());

        // Check meter command
        checkMeterCommand(meterCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithoutMeterForOf13() {
        sw = buildSwitch("OF_13", Collections.emptySet());
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);

        Assertions.assertTrue(flowCommandData.getDependsOn().isEmpty());

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(2, match.size());
        checkMatch(match);

        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(2, instructions.getApplyActions().size());
        checkPortOutAction(instructions.getApplyActions().get(0));
        checkSetFieldAction(instructions.getApplyActions().get(1));
        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertNull(instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterInBytesForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(METERS));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);

        assertEquals(1, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(2, match.size());
        checkMatch(match);

        checkInstructions(flowCommandData.getInstructions(), meterCommandData.getMeterId());

        // Check meter command
        assertEquals(createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE), meterCommandData.getMeterId());
        long expectedRate = Meter.convertRateToKiloBits(config.getUnicastRateLimit(), config.getDiscoPacketSize());
        assertEquals(expectedRate, meterCommandData.getRate());
        long expectedBurst = Meter.convertBurstSizeToKiloBits(config.getSystemMeterBurstSizeInPackets(),
                config.getDiscoPacketSize());
        assertEquals(expectedBurst, meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        Assertions.assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.KBPS)
                .containsAll(meterCommandData.getFlags()));
    }

    private void checkFlowCommandBaseProperties(FlowSpeakerData flowCommandData) {
        assertEquals(new Cookie(VERIFICATION_UNICAST_RULE_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(DISCOVERY_RULE_PRIORITY, flowCommandData.getPriority());
    }

    private void checkMatch(Set<FieldMatch> match) {
        FieldMatch ethSrcMatch = getMatchByField(Field.ETH_SRC, match);
        assertEquals(new SwitchId(config.getFlowPingMagicSrcMacAddress()).toLong(), ethSrcMatch.getValue());
        Assertions.assertTrue(ethSrcMatch.isMasked());
        assertEquals(Mask.NO_MASK, ethSrcMatch.getMask().longValue());

        FieldMatch ethDstMatch = getMatchByField(Field.ETH_DST, match);
        assertEquals(sw.getSwitchId().toLong(), ethDstMatch.getValue());
        Assertions.assertTrue(ethDstMatch.isMasked());
        assertEquals(Mask.NO_MASK, ethDstMatch.getMask().longValue());
    }

    private void checkInstructions(Instructions instructions, MeterId meterId) {
        assertEquals(2, instructions.getApplyActions().size());
        checkPortOutAction(instructions.getApplyActions().get(0));
        checkSetFieldAction(instructions.getApplyActions().get(1));
        Assertions.assertNull(instructions.getWriteActions());
        assertEquals(instructions.getGoToMeter(), meterId);
        Assertions.assertNull(instructions.getGoToTable());
    }

    private void checkPortOutAction(Action action) {
        Assertions.assertTrue(action instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) action;
        assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());
    }

    private void checkSetFieldAction(Action action) {
        Assertions.assertTrue(action instanceof SetFieldAction);
        SetFieldAction setFieldAction = (SetFieldAction) action;
        assertEquals(Field.ETH_DST, setFieldAction.getField());
        assertEquals(sw.getSwitchId().toLong(), setFieldAction.getValue());
    }

    private void checkMeterCommand(MeterSpeakerData meterCommandData) {
        assertEquals(createMeterIdForDefaultRule(VERIFICATION_UNICAST_RULE_COOKIE), meterCommandData.getMeterId());
        assertEquals(config.getUnicastRateLimit(), meterCommandData.getRate());
        assertEquals(config.getSystemMeterBurstSizeInPackets(), meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        Assertions.assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.PKTPS)
                .containsAll(meterCommandData.getFlags()));
    }
}
