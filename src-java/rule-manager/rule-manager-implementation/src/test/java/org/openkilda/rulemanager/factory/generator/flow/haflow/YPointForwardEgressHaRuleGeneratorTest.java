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

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class YPointForwardEgressHaRuleGeneratorTest extends HaRuleGeneratorBaseTest {
    private static final HaFlowPath HA_FLOW_PATH = HaFlowPath.builder()
            .haPathId(new PathId("ha_path_id"))
            .sharedSwitch(SWITCH_1)
            .cookie(SHARED_FORWARD_COOKIE)
            .yPointGroupId(GROUP_ID)
            .build();

    @Test
    public void buildVlanEncapsulationOuterInnerFirstOuterInnerSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, INNER_VLAN_ID_1, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVlanEncapsulationOuterFirstOuterInnerSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVlanEncapsulationFullPortFirstOuterInnerSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION,
                0, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVlanEncapsulationOuterPortFirstOuterSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, 0);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVlanEncapsulationOuterFirstFullPortSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, 0, 0, 0);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVlanEncapsulationFullPortFirstFullPortSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION, 0, 0, 0, 0);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVxlanEncapsulationOuterInnerFirstOuterInnerSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VXLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, INNER_VLAN_ID_1, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVxlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVxlanEncapsulationOuterFirstOuterInnerSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VXLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVxlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVxlanEncapsulationFullPortFirstOuterInnerSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VXLAN_ENCAPSULATION,
                0, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVxlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVxlanEncapsulationOuterPortFirstOuterSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VXLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, 0);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVxlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVxlanEncapsulationOuterFirstFullPortSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VXLAN_ENCAPSULATION,
                OUTER_VLAN_ID_1, 0, 0, 0);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVxlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test
    public void buildVxlanEncapsulationFullPortFirstFullPortSecondTest() {
        YPointForwardEgressHaRuleGenerator generator = buildGenerator(VXLAN_ENCAPSULATION, 0, 0, 0, 0);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new GroupAction(GROUP_ID));
        assertCommand(flowCommand, buildExpectedVxlanMatch(), expectedApplyActions, groupCommand.getUuid());

        Set<Action> firstExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_3)));
        Set<Action> secondExpectedActions = newHashSet(
                new PortOutAction(new PortNumber(PORT_NUMBER_4)));
        assertGroup(groupCommand, firstExpectedActions, secondExpectedActions);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullSubPathsTest() {
        buildGenerator(null).generateCommands(SWITCH_1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySubPathsTest() {
        buildGenerator(new ArrayList<>()).generateCommands(SWITCH_1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void singleSubPathsTest() {
        buildGenerator(Lists.newArrayList(buildSubPath(0, 0))).generateCommands(SWITCH_1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void differentDestinationSubPathsTest() {
        FlowPath subPath1 = buildSubPath(PATH_ID_1, SWITCH_1, SWITCH_2, FORWARD_COOKIE, 0, 0);
        FlowPath subPath2 = buildSubPath(PATH_ID_2, SWITCH_1, SWITCH_3, FORWARD_COOKIE_2, 0, 0);
        buildGenerator(Lists.newArrayList(subPath1, subPath2)).generateCommands(SWITCH_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oneSwitchSubPathsTest() {
        FlowPath subPath1 = buildSubPath(PATH_ID_1, SWITCH_1, SWITCH_2, FORWARD_COOKIE, 0, 0);
        FlowPath subPath2 = buildSubPath(PATH_ID_2, SWITCH_1, SWITCH_1, FORWARD_COOKIE_2, 0, 0);
        buildGenerator(Lists.newArrayList(subPath1, subPath2)).generateCommands(SWITCH_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void reverseSubPathTest() {
        FlowPath subPath1 = buildSubPath(PATH_ID_1, SWITCH_1, SWITCH_2, REVERSE_COOKIE, 0, 0);
        FlowPath subPath2 = buildSubPath(PATH_ID_2, SWITCH_1, SWITCH_2, FORWARD_COOKIE_2, 0, 0);
        buildGenerator(Lists.newArrayList(subPath1, subPath2)).generateCommands(SWITCH_2);
    }

    private static Set<FieldMatch> buildExpectedVlanMatch() {
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(VLAN_ENCAPSULATION.getId()).build());
    }

    private static Set<FieldMatch> buildExpectedVxlanMatch() {
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(Constants.VXLAN_UDP_DST).build(),
                FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(VXLAN_ENCAPSULATION.getId()).build());
    }

    private void assertGroup(GroupSpeakerData group, Set<Action> firstBucketActions, Set<Action> secondBucketActions) {
        assertEquals(GROUP_ID, group.getGroupId());
        assertEquals(SWITCH_2.getSwitchId(), group.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), group.getOfVersion().toString());
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

    private void assertCommand(
            FlowSpeakerData command, Set<FieldMatch> expectedMatch, List<Action> expectedApplyActions,
            UUID expectedGroupUuid) {
        assertEquals(SWITCH_2.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), command.getOfVersion().toString());
        assertEquals(HA_FLOW_PATH.getCookie(), command.getCookie());
        assertEquals(OfTable.EGRESS, command.getTable());
        assertEquals(Priority.FLOW_PRIORITY, command.getPriority());
        assertEquals(newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder().applyActions(expectedApplyActions).build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(Lists.newArrayList(expectedGroupUuid), command.getDependsOn());
    }

    private YPointForwardEgressHaRuleGenerator buildGenerator(
            FlowTransitEncapsulation encapsulation, int firstSubPathOuterVlan, int firstSubPathInnerVlan,
            int secondSubPathOuterVlan, int secondSubPathInnerVLan) {
        FlowPath subPath1 = buildSubPath(
                PATH_ID_1, FORWARD_COOKIE, firstSubPathOuterVlan, firstSubPathInnerVlan, PORT_NUMBER_3);
        FlowPath subPath2 = buildSubPath(
                PATH_ID_2, FORWARD_COOKIE_2, secondSubPathOuterVlan, secondSubPathInnerVLan, PORT_NUMBER_4);

        return YPointForwardEgressHaRuleGenerator.builder()
                .subPaths(Lists.newArrayList(subPath1, subPath2))
                .inPort(PORT_NUMBER_1)
                .haFlowPath(HA_FLOW_PATH)
                .encapsulation(encapsulation)
                .build();
    }

    private YPointForwardEgressHaRuleGenerator buildGenerator(List<FlowPath> subPaths) {
        return YPointForwardEgressHaRuleGenerator.builder()
                .subPaths(subPaths)
                .inPort(PORT_NUMBER_1)
                .haFlowPath(HA_FLOW_PATH)
                .encapsulation(VLAN_ENCAPSULATION)
                .build();
    }

    private FlowPath buildSubPath(PathId pathId, FlowSegmentCookie cookie, int outerVlan, int innerVlan, int port) {
        FlowPath subPath = buildSubPath(pathId, SWITCH_1, SWITCH_2, cookie, outerVlan, innerVlan);
        subPath.getHaSubFlow().setEndpointPort(port);
        return subPath;
    }
}
