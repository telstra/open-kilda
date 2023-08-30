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
import static org.openkilda.model.SwitchFeature.GROUP_PACKET_OUT_CONTROLLER;
import static org.openkilda.model.SwitchFeature.MATCH_UDP_PORT;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE;
import static org.openkilda.rulemanager.Constants.DISCOVERY_PACKET_UDP_PORT;
import static org.openkilda.rulemanager.Constants.LATENCY_PACKET_UDP_PORT;
import static org.openkilda.rulemanager.Constants.Priority.DISCOVERY_RULE_PRIORITY;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getActionByType;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.Utils.getMatchByField;

import org.openkilda.model.GroupId;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.Mask;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class BroadCastDiscoveryRuleGeneratorTest {

    private RuleManagerConfig config;

    private BroadCastDiscoveryRuleGenerator generator;
    private Switch sw;

    @BeforeEach
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getBroadcastRateLimit()).thenReturn(200);
        when(config.getSystemMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getDiscoPacketSize()).thenReturn(250);
        when(config.getDiscoveryBcastPacketDst()).thenReturn("00:26:E1:FF:FF:FF");

        generator = BroadCastDiscoveryRuleGenerator.builder()
                .config(config)
                .build();
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterAndGroupForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(METERS, GROUP_PACKET_OUT_CONTROLLER, MATCH_UDP_PORT, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(3, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);
        GroupSpeakerData groupCommandData = getCommand(GroupSpeakerData.class, commands);

        assertEquals(2, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(groupCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(4, match.size());
        checkEthDstMatch(match);
        checkUpdDstMatch(match);

        checkGroupInstructions(flowCommandData.getInstructions(),
                meterCommandData.getMeterId(), groupCommandData.getGroupId());

        // Check meter command
        checkMeterCommand(meterCommandData);

        // Check group command
        checkGroupCommand(groupCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterAndGroupForOf15() {
        sw = buildSwitch("OF_15", Sets.newHashSet(METERS, GROUP_PACKET_OUT_CONTROLLER, MATCH_UDP_PORT, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(3, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);
        GroupSpeakerData groupCommandData = getCommand(GroupSpeakerData.class, commands);

        assertEquals(2, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(groupCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(4, match.size());
        checkEthDstMatch(match);
        checkUpdDstMatch(match);

        // Check flow command has correct instructions for OF 1.5
        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(2, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof MeterAction);
        MeterAction meterAction = (MeterAction) first;
        assertEquals(meterCommandData.getMeterId(), meterAction.getMeterId());
        checkGroupAction(instructions.getApplyActions().get(1), groupCommandData.getGroupId());
        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertNull(instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());

        // Check meter command
        checkMeterCommand(meterCommandData);

        // Check group command
        checkGroupCommand(groupCommandData);
    }


    @Test
    public void shouldBuildCorrectRuleWithMeterAndWithoutGroupForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(METERS, MATCH_UDP_PORT, PKTPS_FLAG));
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
        assertEquals(4, match.size());
        checkEthDstMatch(match);
        checkUpdDstMatch(match);

        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(1, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) first;
        assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());
        Assertions.assertNull(instructions.getWriteActions());
        assertEquals(meterCommandData.getMeterId(), instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());

        // Check meter command
        checkMeterCommand(meterCommandData);
    }


    @Test
    public void shouldBuildCorrectRuleWithGroupAndWithoutMeterForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(GROUP_PACKET_OUT_CONTROLLER, MATCH_UDP_PORT, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(2, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommandData = getCommand(GroupSpeakerData.class, commands);

        assertEquals(1, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(groupCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(4, match.size());
        checkEthDstMatch(match);
        checkUpdDstMatch(match);

        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(1, instructions.getApplyActions().size());
        Action action = instructions.getApplyActions().get(0);
        checkGroupAction(action, groupCommandData.getGroupId());

        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertNull(instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());

        // Check group command
        checkGroupCommand(groupCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithoutMeterAndGroupForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(MATCH_UDP_PORT, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(1, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);

        Assertions.assertTrue(flowCommandData.getDependsOn().isEmpty());

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(4, match.size());
        checkEthDstMatch(match);
        checkUpdDstMatch(match);

        // Check flow command has correct instructions without meter and group
        Instructions instructions = flowCommandData.getInstructions();
        assertEquals(1, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) first;
        assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());
        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertNull(instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());
    }

    @Test
    public void shouldBuildCorrectRuleWithoutUpdDstMatchForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(METERS, GROUP_PACKET_OUT_CONTROLLER, PKTPS_FLAG));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(3, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);
        GroupSpeakerData groupCommandData = getCommand(GroupSpeakerData.class, commands);

        assertEquals(2, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(groupCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        // Check match is only by eth_dst
        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(1, match.size());
        checkEthDstMatch(match);

        checkGroupInstructions(flowCommandData.getInstructions(),
                meterCommandData.getMeterId(), groupCommandData.getGroupId());

        // Check meter command
        checkMeterCommand(meterCommandData);

        // Check group command
        checkGroupCommand(groupCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterInBytesForOf13() {
        sw = buildSwitch("OF_13", Sets.newHashSet(METERS, GROUP_PACKET_OUT_CONTROLLER, MATCH_UDP_PORT));
        List<SpeakerData> commands = generator.generateCommands(sw);

        assertEquals(3, commands.size());
        commands.forEach(c -> assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);
        GroupSpeakerData groupCommandData = getCommand(GroupSpeakerData.class, commands);

        assertEquals(2, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(groupCommandData.getUuid()));

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        Set<FieldMatch> match = flowCommandData.getMatch();
        assertEquals(4, match.size());
        checkEthDstMatch(match);
        checkUpdDstMatch(match);

        checkGroupInstructions(flowCommandData.getInstructions(),
                meterCommandData.getMeterId(), groupCommandData.getGroupId());

        // Check meter command
        assertEquals(createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE), meterCommandData.getMeterId());
        long expectedRate = Meter.convertRateToKiloBits(config.getBroadcastRateLimit(), config.getDiscoPacketSize());
        assertEquals(expectedRate, meterCommandData.getRate());
        long expectedBurst = Meter.convertBurstSizeToKiloBits(config.getSystemMeterBurstSizeInPackets(),
                config.getDiscoPacketSize());
        assertEquals(expectedBurst, meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        Assertions.assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.KBPS)
                .containsAll(meterCommandData.getFlags()));

        // Check group command
        checkGroupCommand(groupCommandData);
    }

    private void checkFlowCommandBaseProperties(FlowSpeakerData flowCommandData) {
        assertEquals(new Cookie(VERIFICATION_BROADCAST_RULE_COOKIE), flowCommandData.getCookie());
        assertEquals(OfTable.INPUT, flowCommandData.getTable());
        assertEquals(DISCOVERY_RULE_PRIORITY, flowCommandData.getPriority());
    }

    private void checkEthDstMatch(Set<FieldMatch> match) {
        FieldMatch ethDstMatch = getMatchByField(Field.ETH_DST, match);
        assertEquals(new SwitchId(config.getDiscoveryBcastPacketDst()).toLong(), ethDstMatch.getValue());
        Assertions.assertTrue(ethDstMatch.isMasked());
        assertEquals(Mask.NO_MASK, ethDstMatch.getMask().longValue());
    }

    private void checkUpdDstMatch(Set<FieldMatch> match) {
        FieldMatch ipProtoMatch = getMatchByField(Field.IP_PROTO, match);
        assertEquals(IpProto.UDP, ipProtoMatch.getValue());
        Assertions.assertFalse(ipProtoMatch.isMasked());

        FieldMatch ethTypeMatch = getMatchByField(Field.ETH_TYPE, match);
        assertEquals(EthType.IPv4, ethTypeMatch.getValue());
        Assertions.assertFalse(ethTypeMatch.isMasked());

        FieldMatch updDestMatch = getMatchByField(Field.UDP_DST, match);
        assertEquals(DISCOVERY_PACKET_UDP_PORT, updDestMatch.getValue());
        Assertions.assertFalse(updDestMatch.isMasked());
    }

    private void checkGroupInstructions(Instructions instructions, MeterId meterId, GroupId groupId) {
        assertEquals(1, instructions.getApplyActions().size());
        Action action = instructions.getApplyActions().get(0);
        checkGroupAction(action, groupId);

        Assertions.assertNull(instructions.getWriteActions());
        assertEquals(instructions.getGoToMeter(), meterId);
        Assertions.assertNull(instructions.getGoToTable());
    }

    private void checkGroupAction(Action action, GroupId groupId) {
        Assertions.assertTrue(action instanceof GroupAction);
        GroupAction groupAction = (GroupAction) action;
        assertEquals(groupAction.getGroupId(), groupId);
    }

    private void checkMeterCommand(MeterSpeakerData meterCommandData) {
        assertEquals(createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE), meterCommandData.getMeterId());
        assertEquals(config.getBroadcastRateLimit(), meterCommandData.getRate());
        assertEquals(config.getSystemMeterBurstSizeInPackets(), meterCommandData.getBurst());
        assertEquals(3, meterCommandData.getFlags().size());
        Assertions.assertTrue(Sets.newHashSet(MeterFlag.BURST, MeterFlag.STATS, MeterFlag.PKTPS)
                .containsAll(meterCommandData.getFlags()));
    }

    private void checkGroupCommand(GroupSpeakerData groupCommandData) {
        assertEquals(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, groupCommandData.getGroupId());
        assertEquals(GroupType.ALL, groupCommandData.getType());
        List<Bucket> buckets = groupCommandData.getBuckets();
        assertEquals(2, buckets.size());

        Bucket first = buckets.get(0);
        assertEquals(2, first.getWriteActions().size());
        SetFieldAction setEthDstAction = getActionByType(SetFieldAction.class, first.getWriteActions());
        assertEquals(Field.ETH_DST, setEthDstAction.getField());
        assertEquals(sw.getSwitchId().toLong(), setEthDstAction.getValue());
        PortOutAction sendToControllerAction = getActionByType(PortOutAction.class, first.getWriteActions());
        assertEquals(SpecialPortType.CONTROLLER, sendToControllerAction.getPortNumber().getPortType());

        Bucket second = buckets.get(1);
        assertEquals(2, second.getWriteActions().size());
        SetFieldAction setUdpDstAction = getActionByType(SetFieldAction.class, second.getWriteActions());
        assertEquals(Field.UDP_DST, setUdpDstAction.getField());
        assertEquals(LATENCY_PACKET_UDP_PORT, setUdpDstAction.getValue());
        PortOutAction sendToPortInAction = getActionByType(PortOutAction.class, second.getWriteActions());
        assertEquals(SpecialPortType.IN_PORT, sendToPortInAction.getPortNumber().getPortType());
    }
}
