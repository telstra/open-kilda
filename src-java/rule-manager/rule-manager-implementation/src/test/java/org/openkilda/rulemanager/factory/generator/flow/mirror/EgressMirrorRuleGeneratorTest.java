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

package org.openkilda.rulemanager.factory.generator.flow.mirror;

import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
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

public class EgressMirrorRuleGeneratorTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow";
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int PORT_NUMBER_3 = 3;
    public static final int PORT_NUMBER_4 = 4;
    public static final GroupId GROUP_ID = new GroupId(555);
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN));
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN));
    public static final int OUTER_VLAN_ID = 10;
    public static final int INNER_VLAN_ID = 11;
    public static final int TRANSIT_VLAN_ID = 12;
    public static final int VXLAN_VNI = 13;
    public static final int MIRROR_PORT = 14;
    public static final short MIRROR_VLAN = 15;
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID, FlowEncapsulationType.TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI, FlowEncapsulationType.VXLAN);
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 123);
    public static final FlowSegmentCookie MIRROR_COOKIE = COOKIE.toBuilder().mirror(true).build();
    private static final FlowMirrorPoints MIRROR_POINTS = buildMirrorPoints(SWITCH_2);

    @Test
    public void buildVlanMultiTableDoubleVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, INNER_VLAN_ID);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }
    
    @Test
    public void buildVlanMultiTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);
        
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVlanMultiTableOuterVlanEqualsTransitEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }


    @Test
    public void buildVlanMultiTableOuterInnerVlanEqualsTransitEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, VLAN_ENCAPSULATION.getId());
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVlanMultiTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEqualsTransitVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVlanSingleTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVxlanMultiTableOuterInnerVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, INNER_VLAN_ID);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVxlanMultiTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVxlanMultiTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVxlanSingleTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new GroupAction(GROUP_ID)
        );
        assertEgressCommand(egressCommand, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void buildVxlanSingleTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand);
    }

    @Test
    public void oneSwitchFlowEgressMirrorRuleTest() {
        FlowPath path =  FlowPath.builder()
                .pathId(PATH_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_1)
                .build();

        EgressMirrorRuleGenerator generator = EgressMirrorRuleGenerator.builder().flowPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void pathWithoutSegmentsFlowEgressMirrorRuleTest() {
        FlowPath path =  FlowPath.builder()
                .pathId(PATH_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .segments(new ArrayList<>())
                .build();

        EgressMirrorRuleGenerator generator = EgressMirrorRuleGenerator.builder().flowPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    @Test
    public void pathWithoutMirrorsEgressMirrorRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    @Test
    public void pathWithWrongMirrorSwitchMirrorsEgressMirrorRuleTest() {
        FlowPath path = buildPath(true);
        path.addFlowMirrorPoints(buildMirrorPoints(SWITCH_1));
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    private void assertEgressCommand(
            FlowSpeakerData command, OfTable table, FlowTransitEncapsulation encapsulation,
            List<Action> expectedApplyActions, UUID groupCommandUuid) {
        assertEquals(SWITCH_2.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), command.getOfVersion().toString());
        assertEquals(Lists.newArrayList(groupCommandUuid), new ArrayList<>(command.getDependsOn()));

        assertEquals(MIRROR_COOKIE, command.getCookie());
        assertEquals(table, command.getTable());
        assertEquals(Priority.MIRROR_FLOW_PRIORITY, command.getPriority());


        Set<FieldMatch> expectedMatch;
        if (encapsulation.getType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
            expectedMatch = buildExpectedVlanMatch(PORT_NUMBER_3, encapsulation.getId());
        } else {
            expectedMatch = buildExpectedVxlanMatch(PORT_NUMBER_3, encapsulation.getId());
        }
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder().applyActions(expectedApplyActions).build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertTrue(command.getFlags().isEmpty());
    }

    private void assertGroupCommand(GroupSpeakerData command) {
        assertEquals(GROUP_ID, command.getGroupId());
        assertEquals(SWITCH_2.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), command.getOfVersion().toString());
        assertEquals(GroupType.ALL, command.getType());
        assertTrue(command.getDependsOn().isEmpty());

        assertEquals(2, command.getBuckets().size());
        Bucket flowBucket = command.getBuckets().get(0);
        assertBucketCommon(flowBucket);
        assertEquals(newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_4))), flowBucket.getWriteActions());

        Bucket mirrorBucket = command.getBuckets().get(1);
        assertBucketCommon(mirrorBucket);
        assertEquals(newHashSet(new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(MIRROR_VLAN).build(),
                new PortOutAction(new PortNumber(MIRROR_PORT))), mirrorBucket.getWriteActions());
    }

    private void assertBucketCommon(Bucket bucket) {
        assertEquals(WatchGroup.ANY, bucket.getWatchGroup());
        assertEquals(WatchPort.ANY, bucket.getWatchPort());
    }

    private Set<FieldMatch> buildExpectedVlanMatch(int port, int vlanId) {
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(vlanId).build());
    }

    private Set<FieldMatch> buildExpectedVxlanMatch(int port, int vni) {
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(Constants.VXLAN_UDP_DST).build(),
                FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(vni).build());
    }

    private EgressMirrorRuleGenerator buildGenerator(FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return EgressMirrorRuleGenerator.builder()
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .build();
    }

    private FlowPath buildPathWithMirror(boolean multiTable) {
        FlowPath path = buildPath(multiTable);
        path.addFlowMirrorPoints(MIRROR_POINTS);
        return path;
    }

    private FlowPath buildPath(boolean multiTable) {
        return FlowPath.builder()
                .pathId(PATH_ID)
                .cookie(COOKIE)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .destWithMultiTable(multiTable)
                .segments(Lists.newArrayList(PathSegment.builder()
                        .pathId(PATH_ID)
                        .srcPort(PORT_NUMBER_2)
                        .srcSwitch(SWITCH_1)
                        .destPort(PORT_NUMBER_3)
                        .destSwitch(SWITCH_2)
                        .build()))
                .build();
    }

    private Flow buildFlow(FlowPath path, int outerVlan, int innerVlan) {
        Flow flow = Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(SWITCH_1)
                .srcPort(PORT_NUMBER_1)
                .destPort(PORT_NUMBER_4)
                .destSwitch(SWITCH_2)
                .destVlan(outerVlan)
                .destInnerVlan(innerVlan)
                .build();
        flow.setForwardPath(path);
        return flow;
    }

    private static FlowMirrorPoints buildMirrorPoints(Switch sw) {
        FlowMirrorPoints mirrorPoints = FlowMirrorPoints.builder()
                .mirrorSwitch(sw)
                .mirrorGroup(MirrorGroup.builder()
                        .flowId(FLOW_ID)
                        .pathId(PATH_ID)
                        .groupId(GROUP_ID)
                        .switchId(SWITCH_ID_2)
                        .mirrorDirection(MirrorDirection.EGRESS)
                        .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                        .build())
                .build();
        mirrorPoints.addPaths(FlowMirrorPath.builder()
                        .mirrorSwitch(SWITCH_2)
                        .egressSwitch(SWITCH_2)
                        .pathId(PATH_ID)
                        .egressPort(MIRROR_PORT)
                        .egressOuterVlan(MIRROR_VLAN)
                .build());
        return mirrorPoints;
    }
}
