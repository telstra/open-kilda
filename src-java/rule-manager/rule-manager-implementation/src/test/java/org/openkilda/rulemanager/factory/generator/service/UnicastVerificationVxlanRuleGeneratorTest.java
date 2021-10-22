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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.MeterId.createMeterIdForDefaultRule;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_UNICAST_VXLAN_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.Priority.VERIFICATION_RULE_VXLAN_PRIORITY;
import static org.openkilda.rulemanager.Constants.STUB_VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.Mask;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public class UnicastVerificationVxlanRuleGeneratorTest {

    private RuleManagerConfig config;
    private UnicastVerificationVxlanRuleGenerator generator;
    private Switch sw;

    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getUnicastRateLimit()).thenReturn(200);
        when(config.getSystemMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getDiscoPacketSize()).thenReturn(250);
        when(config.getFlowPingMagicSrcMacAddress()).thenReturn("00:26:E1:FF:FF:FE");

        generator = UnicastVerificationVxlanRuleGenerator.builder()
                .config(config)
                .build();
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN, METERS, PKTPS_FLAG));
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        MeterSpeakerCommandData meterCommandData = getCommand(MeterSpeakerCommandData.class, commands);

        assertEquals(1, flowCommandData.getDependsOn().size());
        assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);
        checkMatch(flowCommandData.getMatch());
        checkInstructions(flowCommandData.getInstructions(), meterCommandData.getMeterId());

        // Check meter command
        checkMeterCommand(meterCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterForOf15() {
        sw = buildSwitch("OF_15", Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN, METERS, PKTPS_FLAG));
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        MeterSpeakerCommandData meterCommandData = getCommand(MeterSpeakerCommandData.class, commands);

        assertEquals(1, flowCommandData.getDependsOn().size());
        assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);
        checkMatch(flowCommandData.getMatch());

        // Check flow command has correct instructions for OF 1.5
        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(3, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        assertTrue(first instanceof PopVxlanAction);
        PopVxlanAction popVxlanAction = (PopVxlanAction) first;
        assertEquals(ActionType.POP_VXLAN_NOVIFLOW, popVxlanAction.getType());

        Action second = instructions.getApplyActions().get(1);
        assertTrue(second instanceof PortOutAction);
        PortOutAction sendToControllerAction = (PortOutAction) second;
        assertEquals(SpecialPortType.CONTROLLER, sendToControllerAction.getPortNumber().getPortType());

        Action third = instructions.getApplyActions().get(2);
        assertTrue(third instanceof MeterAction);
        MeterAction meterAction = (MeterAction) third;
        assertEquals(meterCommandData.getMeterId(), meterAction.getMeterId());

        assertNull(instructions.getWriteActions());
        assertNull(instructions.getGoToMeter());
        assertNull(instructions.getGoToTable());

        // Check meter command
        checkMeterCommand(meterCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithoutMeterForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN, PKTPS_FLAG));
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);

        assertTrue(flowCommandData.getDependsOn().isEmpty());

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);
        checkMatch(flowCommandData.getMatch());

        // Check instructions without meter
        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(2, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        assertTrue(first instanceof PopVxlanAction);
        PopVxlanAction popVxlanAction = (PopVxlanAction) first;
        assertEquals(ActionType.POP_VXLAN_NOVIFLOW, popVxlanAction.getType());

        Action second = instructions.getApplyActions().get(1);
        assertTrue(second instanceof PortOutAction);
        PortOutAction sendToControllerAction = (PortOutAction) second;
        assertEquals(SpecialPortType.CONTROLLER, sendToControllerAction.getPortNumber().getPortType());

        assertNull(instructions.getWriteActions());
        assertNull(instructions.getGoToMeter());
        assertNull(instructions.getGoToTable());
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterInBytesForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN, METERS));
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        MeterSpeakerCommandData meterCommandData = getCommand(MeterSpeakerCommandData.class, commands);

        assertEquals(1, flowCommandData.getDependsOn().size());
        assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);
        checkMatch(flowCommandData.getMatch());
        checkInstructions(flowCommandData.getInstructions(), meterCommandData.getMeterId());

        // Check meter command
        assertEquals(createMeterIdForDefaultRule(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE),
                meterCommandData.getMeterId());
        long expectedRate = Meter.convertRateToKiloBits(config.getUnicastRateLimit(), config.getDiscoPacketSize());
        assertEquals(expectedRate, meterCommandData.getRate());
        long expectedBurst = Meter.convertBurstSizeToKiloBits(config.getSystemMeterBurstSizeInPackets(),
                config.getDiscoPacketSize());
        assertEquals(expectedBurst, meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.KBPS)
                .containsAll(meterCommandData.getFlags()));
    }

    @Test
    public void shouldSkipRuleWithoutVxlanPushPopFeatureForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(METERS));
        List<SpeakerCommandData> commands = generator.generateCommands(sw);

        assertTrue(commands.isEmpty());
    }

    private void checkFlowCommandBaseProperties(FlowSpeakerCommandData flowCommandData) {
        assertEquals(new Cookie(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(VERIFICATION_RULE_VXLAN_PRIORITY, flowCommandData.getPriority());
    }

    private void checkMatch(Set<FieldMatch> match) {
        assertEquals(5, match.size());

        FieldMatch ethSrcMatch = getMatchByField(Field.ETH_SRC, match);
        assertEquals(new SwitchId(config.getFlowPingMagicSrcMacAddress()).toLong(), ethSrcMatch.getValue());
        assertTrue(ethSrcMatch.isMasked());
        assertEquals(Mask.NO_MASK, ethSrcMatch.getMask().longValue());

        FieldMatch ethDstMatch = getMatchByField(Field.ETH_DST, match);
        assertEquals(sw.getSwitchId().toLong(), ethDstMatch.getValue());
        assertTrue(ethDstMatch.isMasked());
        assertEquals(Mask.NO_MASK, ethDstMatch.getMask().longValue());

        FieldMatch ipProtoMatch = getMatchByField(Field.IP_PROTO, match);
        assertEquals(IpProto.UDP, ipProtoMatch.getValue());
        assertFalse(ipProtoMatch.isMasked());

        FieldMatch ethTypeMatch = getMatchByField(Field.ETH_TYPE, match);
        assertEquals(EthType.IPv4, ethTypeMatch.getValue());
        assertFalse(ethTypeMatch.isMasked());

        FieldMatch updDestMatch = getMatchByField(Field.UDP_SRC, match);
        assertEquals(STUB_VXLAN_UDP_SRC, updDestMatch.getValue());
        assertFalse(updDestMatch.isMasked());
    }

    private void checkInstructions(Instructions instructions, MeterId meterId) {
        assertEquals(2, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        assertTrue(first instanceof PopVxlanAction);
        PopVxlanAction popVxlanAction = (PopVxlanAction) first;
        assertEquals(ActionType.POP_VXLAN_NOVIFLOW, popVxlanAction.getType());

        Action second = instructions.getApplyActions().get(1);
        assertTrue(second instanceof PortOutAction);
        PortOutAction sendToControllerAction = (PortOutAction) second;
        assertEquals(SpecialPortType.CONTROLLER, sendToControllerAction.getPortNumber().getPortType());

        assertNull(instructions.getWriteActions());
        assertEquals(instructions.getGoToMeter(), meterId);
        assertNull(instructions.getGoToTable());
    }

    private void checkMeterCommand(MeterSpeakerCommandData meterCommandData) {
        assertEquals(createMeterIdForDefaultRule(VERIFICATION_UNICAST_VXLAN_RULE_COOKIE),
                meterCommandData.getMeterId());
        assertEquals(config.getUnicastRateLimit(), meterCommandData.getRate());
        assertEquals(config.getSystemMeterBurstSizeInPackets(), meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.PKTPS)
                .containsAll(meterCommandData.getFlags()));
    }
}
