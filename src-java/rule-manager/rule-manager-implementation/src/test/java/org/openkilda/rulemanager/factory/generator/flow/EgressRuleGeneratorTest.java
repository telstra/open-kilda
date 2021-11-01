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

package org.openkilda.rulemanager.factory.generator.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
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
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
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

public class EgressRuleGeneratorTest {
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
    public void buildVlanMultiTableOuterInnerVlanEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, INNER_VLAN_ID);
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID).build(),
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }
    
    @Test
    public void buildVlanMultiTableOuterVlanEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanMultiTableOuterVlanEqualsTransitEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }


    @Test
    public void buildVlanMultiTableOuterInnerVlanEqualsTransitEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, VLAN_ENCAPSULATION.getId());
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanMultiTableFullPortEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEgressRuleTest() {
        FlowPath path = buildPath(false);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanSingleTableOuterVlanEqualsTransitVlanEgressRuleTest() {
        FlowPath path = buildPath(false);
        Flow flow = buildFlow(path, VLAN_ENCAPSULATION.getId(), 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVlanSingleTableFullPortEgressRuleTest() {
        FlowPath path = buildPath(false);
        Flow flow = buildFlow(path, 0, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanMultiTableOuterInnerVlanEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, INNER_VLAN_ID);
        EgressRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                PushVlanAction.builder().vlanId((short) INNER_VLAN_ID).build(),
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanMultiTableOuterVlanEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanMultiTableFullPortEgressRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, 0, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.EGRESS, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanSingleTableOuterVlanEgressRuleTest() {
        FlowPath path = buildPath(false);
        Flow flow = buildFlow(path, OUTER_VLAN_ID, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    @Test
    public void buildVxlanSingleTableFullPortEgressRuleTest() {
        FlowPath path = buildPath(false);
        Flow flow = buildFlow(path, 0, 0);
        EgressRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);

        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, OfTable.INPUT, VXLAN_ENCAPSULATION, expectedApplyActions);
    }

    private void assertEgressCommands(List<SpeakerCommandData> commands, OfTable table,
                                      FlowTransitEncapsulation encapsulation, List<Action> expectedApplyActions) {
        assertEquals(1, commands.size());

        FlowSpeakerCommandData flowCommandData = getCommand(FlowSpeakerCommandData.class, commands);
        assertEquals(SWITCH_2.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(COOKIE, flowCommandData.getCookie());
        assertEquals(table, flowCommandData.getTable());
        assertEquals(Priority.FLOW_PRIORITY, flowCommandData.getPriority());


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
    public void oneSwitchFlowEgressRuleTest() {
        FlowPath path =  FlowPath.builder()
                .pathId(PATH_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_1)
                .build();

        EgressRuleGenerator generator = EgressRuleGenerator.builder().flowPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void pathWithoutSegmentsFlowEgressRuleTest() {
        FlowPath path =  FlowPath.builder()
                .pathId(PATH_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .segments(new ArrayList<>())
                .build();

        EgressRuleGenerator generator = EgressRuleGenerator.builder().flowPath(path).build();
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

    private EgressRuleGenerator buildGenerator(FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return EgressRuleGenerator.builder()
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .build();
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
