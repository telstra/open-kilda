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

package org.openkilda.rulemanager.factory.generator.flow.loop;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlowLoopIngressRuleGeneratorTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow1";
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, newHashSet(RESET_COUNTS_FLAG));
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, newHashSet(RESET_COUNTS_FLAG));
    public static final int OUTER_VLAN_1 = 1;
    public static final int OUTER_VLAN_2 = 2;
    public static final int INNER_VLAN_1 = 3;
    public static final int INNER_VLAN_2 = 4;
    public static final FlowSegmentCookie PATH_COOKIE = FlowSegmentCookie.builder()
            .flowEffectiveId(123).direction(FlowPathDirection.FORWARD).build();
    public static final FlowSegmentCookie LOOP_COOKIE = PATH_COOKIE.toBuilder().looped(true).build();
    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(PATH_COOKIE)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_2)
            .build();

    @Test
    public void buildMultiTableDoubleVlanRuleTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_1, INNER_VLAN_1, 0, 0);
        List<SpeakerData> commands = buildGenerator(flow, true).generateCommands(SWITCH_1);
        assertIngressCommands(commands, OfTable.INGRESS, Priority.LOOP_DOUBLE_VLAN_FLOW_PRIORITY,
                newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                        FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_1).build(),
                        buildMetadataMatch(OUTER_VLAN_1)),
                newArrayList(
                        new PushVlanAction(),
                        SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_1).build(),
                        new PortOutAction(new PortNumber(SpecialPortType.IN_PORT))));
    }

    @Test
    public void buildMultiTableSingleVlanRuleTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_1, 0, OUTER_VLAN_2, INNER_VLAN_2);
        List<SpeakerData> commands = buildGenerator(flow, true).generateCommands(SWITCH_1);
        assertIngressCommands(commands, OfTable.INGRESS, Priority.LOOP_FLOW_PRIORITY,
                newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                        buildMetadataMatch(OUTER_VLAN_1)),
                newArrayList(
                        new PushVlanAction(),
                        SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_1).build(),
                        new PortOutAction(new PortNumber(SpecialPortType.IN_PORT))));
    }

    @Test
    public void buildMultiTableDefaultFlowRuleTest() {
        Flow flow = buildFlow(PATH, 0, 0, OUTER_VLAN_2, INNER_VLAN_2);
        List<SpeakerData> commands = buildGenerator(flow, true).generateCommands(SWITCH_1);
        assertIngressCommands(commands, OfTable.INGRESS, Priority.LOOP_DEFAULT_FLOW_PRIORITY,
                newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()),
                newArrayList(new PortOutAction(new PortNumber(SpecialPortType.IN_PORT))));
    }

    @Test
    public void buildSingleTableSingleVlanRuleTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_1, 0, OUTER_VLAN_2, 0);
        List<SpeakerData> commands = buildGenerator(flow, false).generateCommands(SWITCH_1);
        assertIngressCommands(commands, OfTable.INPUT, Priority.LOOP_FLOW_PRIORITY,
                newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                        FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_1).build()),
                newArrayList(new PortOutAction(new PortNumber(SpecialPortType.IN_PORT))));
    }

    @Test
    public void buildSingleTableDefaultFlowRuleTest() {
        Flow flow = buildFlow(PATH, 0, 0, 0, 0);
        List<SpeakerData> commands = buildGenerator(flow, false).generateCommands(SWITCH_1);
        assertIngressCommands(commands, OfTable.INPUT, Priority.LOOP_DEFAULT_FLOW_PRIORITY,
                newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()),
                newArrayList(new PortOutAction(new PortNumber(SpecialPortType.IN_PORT))));
    }

    @Test
    public void buildRulesForLoopLessFlowTest() {
        Flow flow = buildFlow(PATH, 0, 0, 0, 0);
        flow.setLoopSwitchId(null);
        List<SpeakerData> commands = buildGenerator(flow, false).generateCommands(SWITCH_1);
        assertEquals(0, commands.size());
    }

    private FieldMatch buildMetadataMatch(int outerVlan) {
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(outerVlan).build(new HashSet<>());
        return FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build();
    }

    private void assertIngressCommands(List<SpeakerData> commands, OfTable table, int priority,
                                       Set<FieldMatch> expectedMatch, List<Action> expectedApplyActions) {
        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(SWITCH_1.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(LOOP_COOKIE, flowCommandData.getCookie());
        assertEquals(table, flowCommandData.getTable());
        assertEquals(priority, flowCommandData.getPriority());

        assertEqualsMatch(expectedMatch, flowCommandData.getMatch());

        Instructions expectedInstructions = Instructions.builder().applyActions(expectedApplyActions).build();
        assertEquals(expectedInstructions, flowCommandData.getInstructions());
        assertEquals(newHashSet(OfFlowFlag.RESET_COUNTERS), flowCommandData.getFlags());
    }

    private Flow buildFlow(FlowPath path, int srcOuterVlan, int srcInnerVlan, int dstOuterVlan, int dstInnerVlan) {
        Flow flow = Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(path.getSrcSwitch())
                .srcPort(PORT_NUMBER_1)
                .srcVlan(srcOuterVlan)
                .srcInnerVlan(srcInnerVlan)
                .destSwitch(path.getDestSwitch())
                .destPort(PORT_NUMBER_2)
                .destVlan(dstOuterVlan)
                .destInnerVlan(dstInnerVlan)
                .loopSwitchId(SWITCH_ID_1)
                .build();
        flow.setForwardPath(path);
        return flow;
    }

    private FlowLoopIngressRuleGenerator buildGenerator(Flow flow, boolean multiTable) {
        return FlowLoopIngressRuleGenerator.builder()
                .flow(flow)
                .flowPath(PATH)
                .multiTable(multiTable)
                .build();
    }
}
