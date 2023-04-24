/* Copyright 2023 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.flow.haflow;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.cookie.CookieBase.CookieType.MULTI_TABLE_INGRESS_RULES;
import static org.openkilda.rulemanager.Constants.Priority.DEFAULT_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.DOUBLE_VLAN_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.FLOW_PRIORITY;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.utils.Utils.mapMetadata;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.MacAddress;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class YPointForwardIngressHaRuleGeneratorTest extends HaRuleGeneratorBaseTest {
    private static final HaFlowPath HA_FLOW_PATH = HaFlowPath.builder()
            .haPathId(new PathId("ha_path_id"))
            .sharedSwitch(SWITCH_1)
            .cookie(SHARED_FORWARD_COOKIE)
            .yPointGroupId(GROUP_ID)
            .sharedPointMeterId(METER_ID)
            .build();

    @Test
    public void testDoubleVlanMatch() {
        Set<FieldMatch> match = YPointForwardIngressHaRuleGenerator.buildIngressMatch(
                new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1), SWITCH_1.getFeatures());
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build(SWITCH_1.getFeatures());
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(metadata.getValue()).mask(metadata.getMask()).build());
        assertEquals(expectedIngressMatch, match);
    }

    @Test
    public void testSingleVlanMatch() {
        Set<FieldMatch> match = YPointForwardIngressHaRuleGenerator.buildIngressMatch(
                new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, OUTER_VLAN_ID_1, 0), SWITCH_1.getFeatures());
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build(SWITCH_1.getFeatures());
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(metadata.getValue()).mask(metadata.getMask()).build());
        assertEquals(expectedIngressMatch, match);
    }

    @Test
    public void testFullPortMatch() {
        Set<FieldMatch> match = YPointForwardIngressHaRuleGenerator.buildIngressMatch(
                new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, 0, 0), SWITCH_1.getFeatures());
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build());
        assertEquals(expectedIngressMatch, match);
    }

    @Test
    public void doubleVlanTransitVlanMultiSwitchTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        Set<Action> secondExpectedActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanTransitVlanMultiSwitchTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, Priority.FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        Set<Action> secondExpectedActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortTransitVlanMultiSwitchTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, Priority.DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        Set<Action> secondExpectedActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void doubleVlanVxlanMultiSwitchTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                buildPushVxlan(SWITCH_ID_2),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        Set<Action> secondExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_3.toMacAddressAsLong()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanVxlanMultiSwitchTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                buildPushVxlan(SWITCH_ID_2),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        Set<Action> secondExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_3.toMacAddressAsLong()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortVxlanMultiSwitchTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                buildPushVxlan(SWITCH_ID_2),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        Set<Action> secondExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_3.toMacAddressAsLong()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void doubleVlanTransitVlanHalfOneSwitchDoubleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void doubleVlanTransitVlanHalfOneSwitchSingleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void doubleVlanTransitVlanHalfOneSwitchFullPortOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanTransitVlanHalfOneSwitchDoubleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanTransitVlanHalfOneSwitchSingleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanTransitVlanHalfOneSwitchFullPortOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortTransitVlanHalfOneSwitchDoubleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortTransitVlanHalfOneSwitchSingleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortTransitVlanHalfOneSwitchFullPortOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void doubleVlanVxlanHalfOneSwitchDoubleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PopVlanAction(),
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void doubleVlanVxlanHalfOneSwitchSingleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void doubleVlanVxlanHalfOneSwitchFullPortOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DOUBLE_VLAN_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanVxlanHalfOneSwitchDoubleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PopVlanAction(),
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanVxlanHalfOneSwitchSingleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void singleVlanVxlanHalfOneSwitchFullPortOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortVlanVxlanHalfOneSwitchDoubleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                new PopVlanAction(),
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortVlanVxlanHalfOneSwitchSingleVlanOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, OUTER_VLAN_ID_2, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void fullPortVlanVxlanHalfOneSwitchFullPortOutTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        YPointForwardIngressHaRuleGenerator generator = buildGeneratorWithOverlapping(
                haFlow, VXLAN_ENCAPSULATION, SWITCH_1, SWITCH_3, 0, 0);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertIngressCommand(flowCommand, DEFAULT_FLOW_PRIORITY, expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        Set<Action> secondExpectedActions = newHashSet(
                buildPushVxlan(SWITCH_ID_3),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildNonOverlappedTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        YPointForwardIngressHaRuleGenerator generator = buildGenerator(
                haFlow, HA_FLOW_PATH, VLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0, new HashSet<>());
        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(5, commands.size());
        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);
        FlowSpeakerData preIngressCommand = (FlowSpeakerData) commands.get(1);
        FlowSpeakerData inputCustomerCommand = (FlowSpeakerData) commands.get(2);
        MeterSpeakerData meterCommand = (MeterSpeakerData) commands.get(3);
        GroupSpeakerData groupCommand = (GroupSpeakerData) commands.get(4);

        Set<FieldMatch> expectedPreIngressMatch = newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build());
        FlowSharedSegmentCookie preIngressCookie = FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                .portNumber(PORT_NUMBER_1)
                .vlanId(OUTER_VLAN_ID_1).build();
        RoutingMetadata preIngressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1)
                .build(SWITCH_1.getFeatures());
        assertPreIngressCommand(preIngressCommand, SWITCH_2, preIngressCookie, Priority.FLOW_PRIORITY,
                expectedPreIngressMatch,  newArrayList(new PopVlanAction()), mapMetadata(preIngressMetadata));

        assertInputCustomerCommand(inputCustomerCommand, SWITCH_2,
                new PortColourCookie(MULTI_TABLE_INGRESS_RULES, PORT_NUMBER_1),
                newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()));

        assertEquals(Lists.newArrayList(meterCommand.getUuid(), groupCommand.getUuid()), ingressCommand.getDependsOn());
        assertMeterCommand(meterCommand);
    }

    @Test
    public void buildPortOverlappedTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        FlowPath fake = buildSubPath(PATH_ID_3, SWITCH_2, SWITCH_3, PORT_NUMBER_1, REVERSE_COOKIE, 0, 0);
        HashSet<FlowSideAdapter> overlapping = newHashSet(FlowSideAdapter.makeIngressAdapter(haFlow, fake));
        YPointForwardIngressHaRuleGenerator generator = buildGenerator(
                haFlow, HA_FLOW_PATH, VLAN_ENCAPSULATION, SWITCH_2, SWITCH_3, 0, 0, overlapping);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(4, commands.size());
        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);
        FlowSpeakerData preIngressCommand = (FlowSpeakerData) commands.get(1);
        MeterSpeakerData meterCommand = (MeterSpeakerData) commands.get(2);
        GroupSpeakerData groupCommand = (GroupSpeakerData) commands.get(3);

        Set<FieldMatch> expectedPreIngressMatch = newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build());
        FlowSharedSegmentCookie preIngressCookie = FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                .portNumber(PORT_NUMBER_1)
                .vlanId(OUTER_VLAN_ID_1).build();
        RoutingMetadata preIngressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1)
                .build(SWITCH_1.getFeatures());
        assertPreIngressCommand(preIngressCommand, SWITCH_2, preIngressCookie, Priority.FLOW_PRIORITY,
                expectedPreIngressMatch,  newArrayList(new PopVlanAction()), mapMetadata(preIngressMetadata));

        assertEquals(Lists.newArrayList(meterCommand.getUuid(), groupCommand.getUuid()), ingressCommand.getDependsOn());
        assertMeterCommand(meterCommand);
    }


    @Test(expected = IllegalArgumentException.class)
    public void nullSubPathsTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        buildGenerator(haFlow, null).generateCommands(SWITCH_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySubPathsTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        buildGenerator(haFlow, new ArrayList<>()).generateCommands(SWITCH_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oneSubFlowTest() {
        FlowPath subPath1 = buildSubPath(PATH_ID_1, SWITCH_2, SWITCH_3, FORWARD_COOKIE, 0, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        buildGenerator(haFlow, Lists.newArrayList(subPath1)).generateCommands(SWITCH_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void incorrectSwitchTest() {
        FlowPath subPath1 = buildSubPath(PATH_ID_1, SWITCH_2, SWITCH_2, FORWARD_COOKIE, 0, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        buildGenerator(haFlow, Lists.newArrayList(subPath1)).generateCommands(SWITCH_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void reverseSubPathTest() {
        FlowPath subPath1 = buildSubPath(PATH_ID_1, SWITCH_2, SWITCH_3, FORWARD_COOKIE, 0, 0);
        FlowPath subPath2 = buildSubPath(PATH_ID_1, SWITCH_2, SWITCH_2, REVERSE_COOKIE, 0, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        buildGenerator(haFlow, Lists.newArrayList(subPath1, subPath2)).generateCommands(SWITCH_2);
    }

    private void assertGroup(GroupSpeakerData group, Set<Action> firstBucketActions,
                             Set<Action> secondBucketActions) {
        assertEquals(GROUP_ID, group.getGroupId());
        assertEquals(SWITCH_1.getSwitchId(), group.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), group.getOfVersion().toString());
        assertEquals(GroupType.ALL, group.getType());
        assertEquals(0, group.getDependsOn().size());
        assertEquals(2, group.getBuckets().size());
        for (Bucket bucket : group.getBuckets()) {
            assertEquals(WatchGroup.ANY, bucket.getWatchGroup());
            assertEquals(WatchPort.ANY, bucket.getWatchPort());
        }
        assertEquals(firstBucketActions, group.getBuckets().get(0).getWriteActions());
        assertEquals(secondBucketActions, group.getBuckets().get(1).getWriteActions());
    }

    private void assertIngressCommand(
            FlowSpeakerData command, int expectedPriority,
            List<Action> expectedApplyActions, UUID expectedGroupUuid) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());

        assertEquals(SHARED_FORWARD_COOKIE, command.getCookie());
        assertEquals(OfTable.INGRESS, command.getTable());
        assertEquals(expectedPriority, command.getPriority());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToTable(null)
                .goToMeter(null)
                .writeMetadata(null)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
        assertEquals(Lists.newArrayList(expectedGroupUuid), command.getDependsOn());
    }

    private void assertPreIngressCommand(
            FlowSpeakerData command, Switch expectedSwitch, CookieBase cookie, int expectedPriority,
            Set<FieldMatch> expectedMatch, List<Action> expectedApplyActions, OfMetadata expectedMetadata) {
        assertEquals(expectedSwitch.getSwitchId(), command.getSwitchId());
        assertEquals(expectedSwitch.getOfVersion(), command.getOfVersion().toString());
        assertEquals(cookie, command.getCookie());
        assertEquals(OfTable.PRE_INGRESS, command.getTable());
        assertEquals(expectedPriority, command.getPriority());
        assertTrue(command.getDependsOn().isEmpty());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .writeMetadata(expectedMetadata)
                .applyActions(expectedApplyActions)
                .goToTable(OfTable.INGRESS)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertTrue(command.getFlags().isEmpty());
    }

    private void assertInputCustomerCommand(
            FlowSpeakerData command, Switch expectedSwitch, CookieBase cookie, Set<FieldMatch> expectedMatch) {
        assertEquals(expectedSwitch.getSwitchId(), command.getSwitchId());
        assertEquals(expectedSwitch.getOfVersion(), command.getOfVersion().toString());
        assertEquals(cookie, command.getCookie());
        assertEquals(OfTable.INPUT, command.getTable());
        assertEquals(Priority.INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE, command.getPriority());
        assertTrue(command.getDependsOn().isEmpty());

        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .goToTable(OfTable.PRE_INGRESS)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertTrue(command.getFlags().isEmpty());
    }

    private void assertMeterCommand(MeterSpeakerData command) {
        assertEquals(SWITCH_2.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), command.getOfVersion().toString());
        assertEquals(METER_ID, command.getMeterId());
        assertEquals(newHashSet(MeterFlag.BURST, MeterFlag.KBPS, MeterFlag.STATS), command.getFlags());
        assertEquals(BANDWIDTH, command.getRate());
        assertEquals(Math.round(BANDWIDTH * BURST_COEFFICIENT), command.getBurst());
        assertTrue(command.getDependsOn().isEmpty());

    }

    private YPointForwardIngressHaRuleGenerator buildGeneratorWithOverlapping(
            HaFlow haFlow, FlowTransitEncapsulation encapsulation, Switch firstDstSwitch,
            Switch secondDstSwitch, int firstSubPathOuterVlan, int firstSubPathInnerVlan) {

        FlowPath fakePath = buildSubPath(haFlow.getSharedSwitch(), SWITCH_3, FORWARD_COOKIE_2,
                haFlow.getSharedOuterVlan(), 15);
        return buildGenerator(haFlow, YPointForwardIngressHaRuleGeneratorTest.HA_FLOW_PATH, encapsulation,
                firstDstSwitch, secondDstSwitch, firstSubPathOuterVlan,
                firstSubPathInnerVlan, newHashSet(FlowSideAdapter.makeIngressAdapter(haFlow, fakePath)));
    }

    private YPointForwardIngressHaRuleGenerator buildGenerator(
            HaFlow haFlow, HaFlowPath haFlowPath, FlowTransitEncapsulation encapsulation, Switch firstDstSwitch,
            Switch secondDstSwitch, int firstSubPathOuterVlan, int firstSubPathInnerVlan,
            Set<FlowSideAdapter> overlappingAdapters) {

        FlowPath subPath1 = buildSubPath(PATH_ID_1, haFlow.getSharedSwitch(), firstDstSwitch, PORT_NUMBER_2,
                FORWARD_COOKIE, firstSubPathOuterVlan, firstSubPathInnerVlan);
        FlowPath subPath2 = buildSubPath(PATH_ID_2, haFlow.getSharedSwitch(), secondDstSwitch, PORT_NUMBER_3,
                FORWARD_COOKIE_2, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);

        return YPointForwardIngressHaRuleGenerator.builder()
                .config(config)
                .subPaths(Lists.newArrayList(subPath1, subPath2))
                .haFlow(haFlow)
                .encapsulation(encapsulation)
                .haFlowPath(haFlowPath)
                .overlappingIngressAdapters(overlappingAdapters)
                .build();
    }

    private YPointForwardIngressHaRuleGenerator buildGenerator(HaFlow haFlow, List<FlowPath> subPaths) {
        return YPointForwardIngressHaRuleGenerator.builder()
                .config(config)
                .subPaths(subPaths)
                .haFlow(haFlow)
                .encapsulation(VLAN_ENCAPSULATION)
                .haFlowPath(HA_FLOW_PATH)
                .overlappingIngressAdapters(new HashSet<>())
                .build();
    }

    private HaFlow buildHaFlow(Switch sharedSwitch, int outerVlan, int innerVlan) {
        return buildHaFlow(sharedSwitch, PORT_NUMBER_1, outerVlan, innerVlan);
    }

    private HaFlow buildHaFlow(Switch sharedSwitch, int sharedPort, int outerVlan, int innerVlan) {
        return HaFlow.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedSwitch(sharedSwitch)
                .sharedPort(sharedPort)
                .sharedOuterVlan(outerVlan)
                .sharedInnerVlan(innerVlan)
                .build();
    }

    private PushVxlanAction buildPushVxlan(SwitchId dstSwitchId) {
        return PushVxlanAction.builder()
                .srcMacAddress(new MacAddress(SWITCH_ID_1.toMacAddress()))
                .dstMacAddress(new MacAddress(dstSwitchId.toMacAddress()))
                .srcIpv4Address(Constants.VXLAN_SRC_IPV4_ADDRESS)
                .dstIpv4Address(Constants.VXLAN_DST_IPV4_ADDRESS)
                .udpSrc(Constants.VXLAN_UDP_SRC)
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(VXLAN_ENCAPSULATION.getId()).build();
    }
}
