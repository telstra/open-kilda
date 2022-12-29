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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EgressSinkRuleGeneratorTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow";
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int PORT_NUMBER_3 = 3;
    public static final int PORT_NUMBER_4 = 4;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, Sets.newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN));
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, Sets.newHashSet(
            RESET_COUNTS_FLAG, NOVIFLOW_PUSH_POP_VXLAN));
    public static final int OUTER_VLAN_ID = 10;
    public static final int INNER_VLAN_ID = 11;
    public static final int TRANSIT_VLAN_ID = 12;
    public static final int VXLAN_VNI = 13;
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID, FlowEncapsulationType.TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI, FlowEncapsulationType.VXLAN);
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 123);

    @Test
    public void buildVlanMultiTableOuterInnerVlanEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, OUTER_VLAN_ID, INNER_VLAN_ID);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }
    
    @Test
    public void buildVlanMultiTableOuterVlanEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, OUTER_VLAN_ID, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanMultiTableOuterVlanEqualsTransitEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }


    @Test
    public void buildVlanMultiTableOuterInnerVlanEqualsTransitEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, OUTER_VLAN_ID, VLAN_ENCAPSULATION.getId());
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanMultiTableFullPortEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, 0, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(false);
        FlowMirror flow = buildFlowMirror(path, OUTER_VLAN_ID, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEqualsTransitVlanEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(false);
        FlowMirror flow = buildFlowMirror(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanSingleTableFullPortEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(false);
        FlowMirror flow = buildFlowMirror(path, 0, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanMultiTableOuterInnerVlanEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, OUTER_VLAN_ID, INNER_VLAN_ID);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanMultiTableOuterVlanEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, OUTER_VLAN_ID, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanMultiTableFullPortEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(true);
        FlowMirror flow = buildFlowMirror(path, 0, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanSingleTableOuterVlanEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(false);
        FlowMirror flow = buildFlowMirror(path, OUTER_VLAN_ID, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanSingleTableFullPortEgressSinkRuleTest() {
        FlowMirrorPath path = buildMirrorPath(false);
        FlowMirror flow = buildFlowMirror(path, 0, 0);
        EgressSinkRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    private void assertEgressCommands(List<SpeakerData> commands, OfTable table,
                                      FlowTransitEncapsulation encapsulation, List<Action> expectedApplyActions) {
        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(SWITCH_2.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(COOKIE, flowCommandData.getCookie());
        assertEquals(table, flowCommandData.getTable());
        assertEquals(Priority.MIRROR_FLOW_PRIORITY, flowCommandData.getPriority());


        Set<FieldMatch> expectedMatch;
        if (encapsulation.getType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
            expectedMatch = buildExpectedVlanMatch(PORT_NUMBER_3, encapsulation.getId());
        } else {
            expectedMatch = buildExpectedVxlanMatch(PORT_NUMBER_3, encapsulation.getId());
        }
        assertEqualsMatch(expectedMatch, flowCommandData.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .build();
        assertEquals(expectedInstructions, flowCommandData.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), flowCommandData.getFlags());
    }

    @Test
    public void oneSwitchFlowEgressSinkRuleTest() {
        FlowMirrorPath path =  FlowMirrorPath.builder()
                .mirrorPathId(PATH_ID)
                .mirrorSwitch(SWITCH_1)
                .egressSwitch(SWITCH_1)
                .build();

        EgressSinkRuleGenerator generator = EgressSinkRuleGenerator.builder().mirrorPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void pathWithoutSegmentsFlowEgressSinkRuleTest() {
        FlowMirrorPath path =  FlowMirrorPath.builder()
                .mirrorPathId(PATH_ID)
                .mirrorSwitch(SWITCH_1)
                .egressSwitch(SWITCH_2)
                .segments(new ArrayList<>())
                .build();

        EgressSinkRuleGenerator generator = EgressSinkRuleGenerator.builder().mirrorPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void pathReverseDirectionFlowEgressSinkRuleTest() {
        FlowMirrorPath path =  FlowMirrorPath.builder()
                .mirrorPathId(PATH_ID)
                .mirrorSwitch(SWITCH_1)
                .egressSwitch(SWITCH_2)
                .cookie(COOKIE.toBuilder().direction(FlowPathDirection.REVERSE).build())
                .segments(Lists.newArrayList(PathSegment.builder()
                        .pathId(PATH_ID)
                        .srcPort(PORT_NUMBER_2)
                        .srcSwitch(SWITCH_1)
                        .destPort(PORT_NUMBER_3)
                        .destSwitch(SWITCH_2)
                        .build()))
                .build();

        EgressSinkRuleGenerator generator = EgressSinkRuleGenerator.builder().mirrorPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    private Set<FieldMatch> buildExpectedVlanMatch(int port, int vlanId) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(vlanId).build());
    }

    private Set<FieldMatch> buildExpectedVxlanMatch(int port, int vni) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(port).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_DST).value(Constants.VXLAN_UDP_DST).build(),
                FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(vni).build());
    }

    private EgressSinkRuleGenerator buildGenerator(
            FlowMirrorPath mirrorPath, FlowMirror flowMirror, FlowTransitEncapsulation encapsulation) {
        return EgressSinkRuleGenerator.builder()
                .mirrorPath(mirrorPath)
                .flowMirror(flowMirror)
                .encapsulation(encapsulation)
                .build();
    }

    private FlowMirrorPath buildMirrorPath(boolean multiTable) {
        return FlowMirrorPath.builder()
                .mirrorPathId(PATH_ID)
                .cookie(COOKIE)
                .mirrorSwitch(SWITCH_1)
                .egressSwitch(SWITCH_2)
                .egressWithMultiTable(multiTable)
                .dummy(false)
                .segments(Lists.newArrayList(PathSegment.builder()
                        .pathId(PATH_ID)
                        .srcPort(PORT_NUMBER_2)
                        .srcSwitch(SWITCH_1)
                        .destPort(PORT_NUMBER_3)
                        .destSwitch(SWITCH_2)
                        .build()))
                .build();
    }

    private FlowMirror buildFlowMirror(FlowMirrorPath flowMirrorPath, int outerVlan, int innerVlan) {
        FlowMirror flowMirror = FlowMirror.builder()
                .flowMirrorId(FLOW_ID)
                .mirrorSwitch(SWITCH_1)
                .egressPort(PORT_NUMBER_4)
                .egressSwitch(SWITCH_2)
                .egressOuterVlan(outerVlan)
                .egressInnerVlan(innerVlan)
                .build();
        flowMirror.addMirrorPaths(flowMirrorPath);
        return flowMirror;
    }
}
