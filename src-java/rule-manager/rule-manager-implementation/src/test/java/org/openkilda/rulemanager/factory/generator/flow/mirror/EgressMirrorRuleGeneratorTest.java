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
import static org.openkilda.model.MirrorDirection.EGRESS;
import static org.openkilda.model.MirrorDirection.INGRESS;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathSegment;
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
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EgressMirrorRuleGeneratorTest extends MirrorGeneratorBaseTest {
    public static final FlowMirrorPoints MIRROR_POINTS = buildMirrorPoints(SWITCH_2, SWITCH_3, EGRESS);
    public static final Set<Action> MULTI_SWITCH_MIRROR_VLAN_ACTIONS = newHashSet(
            new PushVlanAction(),
            SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID_2).build(),
            new PortOutAction(new PortNumber(PORT_NUMBER_1)));
    public static final Set<Action> MULTI_SWITCH_MIRROR_VXLAN_ACTIONS = newHashSet(
            buildPushVxlan(SWITCH_ID_2, SWITCH_ID_3, VXLAN_VNI_2),
            new PortOutAction(new PortNumber(PORT_NUMBER_1)));

    @Test
    public void buildVlanMultiTableDoubleVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }
    
    @Test
    public void buildVlanMultiTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID_1, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);
        
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }

    @Test
    public void buildVlanMultiTableOuterVlanEqualsTransitEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }


    @Test
    public void buildVlanMultiTableOuterInnerVlanEqualsTransitEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID_1, VLAN_ENCAPSULATION.getId());
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }

    @Test
    public void buildVlanMultiTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, OUTER_VLAN_ID_1, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEqualsTransitVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }

    @Test
    public void buildVlanSingleTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VLAN_ACTIONS);
    }

    @Test
    public void buildVxlanMultiTableOuterInnerVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VXLAN_ACTIONS);
    }

    @Test
    public void buildVxlanMultiTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID_1, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VXLAN_ACTIONS);
    }

    @Test
    public void buildVxlanMultiTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VXLAN_ACTIONS);
    }

    @Test
    public void buildVxlanSingleTableOuterVlanEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, OUTER_VLAN_ID_1, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new GroupAction(GROUP_ID)
        );
        assertEgressCommand(egressCommand, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VXLAN_ACTIONS);
    }

    @Test
    public void buildVxlanSingleTableFullPortEgressMirrorRuleTest() {
        FlowPath path = buildPathWithMirror(false);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData egressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new GroupAction(GROUP_ID));
        assertEgressCommand(egressCommand, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions,
                groupCommand.getUuid());
        assertGroupCommand(groupCommand, MULTI_SWITCH_MIRROR_VXLAN_ACTIONS);
    }

    @Test
    public void oneSwitchFlowEgressMirrorRuleTest() {
        FlowPath path =  FlowPath.builder()
                .pathId(PATH_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_1)
                .build();

        EgressMirrorPointRuleGenerator generator = EgressMirrorPointRuleGenerator.builder().flowPath(path).build();
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

        EgressMirrorPointRuleGenerator generator = EgressMirrorPointRuleGenerator.builder().flowPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    @Test
    public void pathWithoutMirrorsEgressMirrorRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    @Test
    public void pathWithWrongMirrorSwitchMirrorsEgressMirrorRuleTest() {
        FlowPath path = buildPath(true);
        path.addFlowMirrorPoints(buildMirrorPoints(SWITCH_1, SWITCH_3, INGRESS));
        Flow flow = buildFlow(path, 0, 0);
        EgressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);
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

    private void assertGroupCommand(GroupSpeakerData command, Set<Action> multiSwitchMirrorActions) {
        assertEquals(GROUP_ID, command.getGroupId());
        assertEquals(SWITCH_2.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), command.getOfVersion().toString());
        assertEquals(GroupType.ALL, command.getType());
        assertTrue(command.getDependsOn().isEmpty());

        assertEquals(3, command.getBuckets().size());
        Bucket expectedFlowBucket = baseBucket()
                .writeActions(newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_4)))).build();

        Bucket expectedSingleSwitchMirror = baseBucket().writeActions(newHashSet(new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(MIRROR_OUTER_VLAN_1).build(),
                new PortOutAction(new PortNumber(MIRROR_PORT_1)))).build();
        Bucket expectedMultiSwitchMirror = baseBucket().writeActions(multiSwitchMirrorActions).build();

        assertEquals(expectedFlowBucket, command.getBuckets().get(0));
        assertEquals(expectedMultiSwitchMirror, command.getBuckets().get(1));
        assertEquals(expectedSingleSwitchMirror, command.getBuckets().get(2));
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

    private EgressMirrorPointRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return EgressMirrorPointRuleGenerator.builder()
                .flowPath(path)
                .mirrorPathEncapsulationMap(buildEncapsulationMap(encapsulation.getType()))
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
}
