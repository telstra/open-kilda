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

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.MirrorDirection.INGRESS;
import static org.openkilda.rulemanager.OfTable.INPUT;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class IngressMirrorPointRuleGeneratorTest extends MirrorGeneratorBaseTest {
    private static final FlowMirrorPoints MIRROR_POINTS = buildMirrorPoints(SWITCH_1, SWITCH_3, INGRESS);
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID_1, TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI_1, FlowEncapsulationType.VXLAN);

    public static final FlowPath MULTI_TABLE_PATH = buildPathWithMirror(true);
    public static final FlowPath SINGLE_TABLE_PATH = buildPathWithMirror(false);
    public static final FlowPath MULTI_TABLE_ONE_SWITCH_PATH = buildOneSwitchFlowPathWithMirror(true);
    public static final FlowPath SINGLE_TABLE_ONE_SWITCH_PATH = buildOneSwitchFlowPathWithMirror(false);

    RuleManagerConfig config;

    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getFlowMeterBurstCoefficient()).thenReturn(BURST_COEFFICIENT);
        when(config.getFlowMeterMinBurstSizeInKbits()).thenReturn(1024L);
    }

    @Test
    public void buildIngressActionsVlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, OUTER_VLAN_ID_1, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsVlanEncapsulationInnerVlanEqualTransitVlanTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID_1);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }


    @Test
    public void buildIngressActionsVxlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, OUTER_VLAN_ID_1, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsVxlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildSingleTableIngressActionsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(SINGLE_TABLE_PATH, OUTER_VLAN_ID_1, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildSingleTableIngressActionsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(SINGLE_TABLE_PATH, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildSingleTableIngressActionsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(SINGLE_TABLE_PATH, OUTER_VLAN_ID_1, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildSingleTableIngressActionsVxlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(SINGLE_TABLE_PATH, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchDoubleVlanInDoubleVlanOutTest() {
        Flow flow = buildFlow(
                MULTI_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchDoubleVlanInSingleVlanOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1, OUTER_VLAN_ID_2, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchDoubleVlanInFullPortOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchSingleVlanInDoubleVlanOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchSingleVlanInSingleVlanOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchSingleVlanInFullPortOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchFullPortInDoubleVlanOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, 0, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchFullPortInSingleVlanOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, 0, 0, OUTER_VLAN_ID_2, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildIngressActionsOneSwitchFullPortInFullPortOutTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, 0, 0, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    ///////

    @Test
    public void buildSingleTableIngressActionsOneSwitchSingleVlanInSingleVlanOutTest() {
        Flow flow = buildFlow(SINGLE_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value((short) OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildSingleTableIngressActionsOneSwitchSingleVlanInFullPortOutTest() {
        Flow flow = buildFlow(SINGLE_TABLE_ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildSingleTableIngressActionsOneSwitchFullPortInSingleVlanOutTest() {
        Flow flow = buildFlow(SINGLE_TABLE_ONE_SWITCH_PATH, 0, 0, OUTER_VLAN_ID_2, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildSingleTableIngressActionsOneSwitchFullPortInFullPortOutTest() {
        Flow flow = buildFlow(SINGLE_TABLE_ONE_SWITCH_PATH, 0, 0, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildIngressActions(getEndpoint(flow), GROUP_ID);
        List<Action> expectedActions = newArrayList(new GroupAction(GROUP_ID));
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void oneSwitchFlowFullPortRuleTest() {
        Flow flow = buildFlow(MULTI_TABLE_ONE_SWITCH_PATH, 0, 0, OUTER_VLAN_ID_2, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_ONE_SWITCH_PATH, flow,
                VLAN_ENCAPSULATION);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerData ingressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        Set<FieldMatch> expectedIngressMatch = newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build());
        List<Action> expectedIngressActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new GroupAction(GROUP_ID));
        assertIngressCommand(ingressCommand, Priority.MIRROR_DEFAULT_FLOW_PRIORITY, OfTable.INGRESS,
                expectedIngressMatch, expectedIngressActions, null, groupCommand.getUuid());

        Set<Action> expectedFlowBucketActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertGroupCommand(groupCommand, expectedFlowBucketActions);
    }

    @Test
    public void buildCommandsVxlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(MULTI_TABLE_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressMirrorPointRuleGenerator generator = buildGenerator(MULTI_TABLE_PATH, flow, VXLAN_ENCAPSULATION);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerData ingressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1)
                .build(SWITCH_1.getFeatures());
        Set<FieldMatch> expectedIngressMatch = newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build());
        List<Action> expectedIngressActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertIngressCommand(ingressCommand, Priority.MIRROR_DOUBLE_VLAN_FLOW_PRIORITY, OfTable.INGRESS,
                expectedIngressMatch, expectedIngressActions, METER_ID, groupCommand.getUuid());

        Set<Action> expectedFlowBucketActions = newHashSet(
                buildPushVxlan(SWITCH_ID_1, SWITCH_ID_2, VXLAN_VNI_1),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertGroupCommand(groupCommand, expectedFlowBucketActions);
    }

    @Test
    public void buildSingleTableCommandsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(SINGLE_TABLE_PATH, OUTER_VLAN_ID_1, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerData ingressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        Set<FieldMatch> expectedIngressMatch = newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build());
        List<Action> expectedIngressActions = newArrayList(new PopVlanAction(), new GroupAction(GROUP_ID));
        assertIngressCommand(ingressCommand, Priority.MIRROR_FLOW_PRIORITY, INPUT, expectedIngressMatch,
                expectedIngressActions, METER_ID, groupCommand.getUuid());

        Set<Action> expectedFlowBucketActions = newHashSet(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertGroupCommand(groupCommand, expectedFlowBucketActions);
    }

    @Test
    public void buildSingleTableCommandsOneSwitchFullPortTest() {
        Flow flow = buildFlow(SINGLE_TABLE_ONE_SWITCH_PATH, 0, 0, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(SINGLE_TABLE_ONE_SWITCH_PATH, flow,
                VXLAN_ENCAPSULATION);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerData ingressCommand = getCommand(FlowSpeakerData.class, commands);
        GroupSpeakerData groupCommand = getCommand(GroupSpeakerData.class, commands);

        Set<FieldMatch> expectedIngressMatch = newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build());
        List<Action> expectedIngressActions = newArrayList(new GroupAction(GROUP_ID));
        assertIngressCommand(ingressCommand, Priority.MIRROR_DEFAULT_FLOW_PRIORITY, INPUT, expectedIngressMatch,
                expectedIngressActions, null, groupCommand.getUuid());

        Set<Action> expectedFlowBucketActions = newHashSet(new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertGroupCommand(groupCommand, expectedFlowBucketActions);
    }

    @Test
    public void pathWithoutMirrorsEgressMirrorRuleTest() {
        FlowPath path = buildPath(true);
        Flow flow = buildFlow(path, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void pathWithWrongMirrorSwitchMirrorsEgressMirrorRuleTest() {
        FlowPath path = buildPath(true);
        path.addFlowMirrorPoints(buildMirrorPoints(SWITCH_2, SWITCH_1, INGRESS));
        Flow flow = buildFlow(path, 0, 0);
        IngressMirrorPointRuleGenerator generator = buildGenerator(path, flow, VXLAN_ENCAPSULATION);
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    private void assertGroupCommand(GroupSpeakerData command, Set<Action> flowActions) {
        assertEquals(GROUP_ID, command.getGroupId());
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
        assertEquals(GroupType.ALL, command.getType());
        assertTrue(command.getDependsOn().isEmpty());

        assertEquals(2, command.getBuckets().size());

        Bucket expectedFlowBucket = baseBucket().writeActions(flowActions).build();
        Bucket expectedSingleSwitchMirror = baseBucket().writeActions(newHashSet(
                        new PushVlanAction(),
                        SetFieldAction.builder().field(Field.VLAN_VID).value(MIRROR_OUTER_VLAN_1).build(),
                        new PortOutAction(new PortNumber(MIRROR_PORT_1)))).build();

        assertEquals(expectedFlowBucket, command.getBuckets().get(0));
        assertEquals(expectedSingleSwitchMirror, command.getBuckets().get(1));
    }

    private void assertIngressCommand(
            FlowSpeakerData command, int expectedPriority, OfTable expectedTable, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions, MeterId expectedMeter, UUID groupCommandUuid) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());

        assertEquals(MIRROR_COOKIE, command.getCookie());
        assertEquals(expectedTable, command.getTable());
        assertEquals(expectedPriority, command.getPriority());

        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToMeter(expectedMeter)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
        assertEquals(newArrayList(groupCommandUuid), new ArrayList<>(command.getDependsOn()));
    }

    private IngressMirrorPointRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return IngressMirrorPointRuleGenerator.builder()
                .config(config)
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .multiTable(path.isSrcWithMultiTable())
                .build();
    }

    private Flow buildFlow(FlowPath path, int srcOuterVlan, int srcInnerVlan) {
        return buildFlow(path, srcOuterVlan, srcInnerVlan, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
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
                .build();
        flow.setForwardPath(path);
        return flow;
    }

    private static FlowPath buildPathWithMirror(boolean multiTable) {
        FlowPath path = buildPath(multiTable);
        path.addFlowMirrorPoints(MIRROR_POINTS);
        return path;
    }

    private static FlowPath buildPath(boolean multiTable) {
        return FlowPath.builder()
                .pathId(PATH_ID)
                .cookie(COOKIE)
                .meterId(METER_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .srcWithMultiTable(multiTable)
                .bandwidth(BANDWIDTH)
                .segments(newArrayList(PathSegment.builder()
                        .pathId(PATH_ID)
                        .srcPort(PORT_NUMBER_2)
                        .srcSwitch(SWITCH_1)
                        .destPort(PORT_NUMBER_3)
                        .destSwitch(SWITCH_2)
                        .build()))
                .build();
    }

    private static FlowPath buildOneSwitchFlowPathWithMirror(boolean multiTable) {
        FlowPath path = FlowPath.builder()
                .pathId(PATH_ID)
                .cookie(COOKIE)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_1)
                .srcWithMultiTable(multiTable)
                .bandwidth(0)
                .segments(new ArrayList<>())
                .build();
        path.addFlowMirrorPoints(MIRROR_POINTS);
        return path;
    }

    private static FlowEndpoint getEndpoint(Flow flow) {
        return new FlowSourceAdapter(flow).getEndpoint();
    }
}
