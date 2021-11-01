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

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.model.cookie.CookieBase.CookieType.MULTI_TABLE_INGRESS_RULES;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.utils.Utils.mapMetadata;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiTableIngressRuleGeneratorTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow";
    public static final MeterId METER_ID = new MeterId(17);
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int PORT_NUMBER_3 = 3;
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final Set<SwitchFeature> FEATURES = Sets.newHashSet(
            RESET_COUNTS_FLAG, METERS, NOVIFLOW_PUSH_POP_VXLAN);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, FEATURES);
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, FEATURES);
    public static final int OUTER_VLAN_ID_1 = 10;
    public static final int OUTER_VLAN_ID_2 = 11;
    public static final int INNER_VLAN_ID_1 = 12;
    public static final int INNER_VLAN_ID_2 = 13;
    public static final int TRANSIT_VLAN_ID = 14;
    public static final int VXLAN_VNI = 15;
    public static final int BANDWIDTH = 1000;
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID, FlowEncapsulationType.TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI, FlowEncapsulationType.VXLAN);
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 123);
    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(COOKIE)
            .meterId(METER_ID)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_2)
            .srcWithMultiTable(true)
            .bandwidth(BANDWIDTH)
            .segments(newArrayList(PathSegment.builder()
                    .pathId(PATH_ID)
                    .srcPort(PORT_NUMBER_2)
                    .srcSwitch(SWITCH_1)
                    .destPort(PORT_NUMBER_3)
                    .destSwitch(SWITCH_2)
                    .build()))
            .build();
    public static final FlowPath ONE_SWITCH_PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(COOKIE)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_1)
            .srcWithMultiTable(true)
            .bandwidth(0)
            .segments(new ArrayList<>())
            .build();
    public static final double BURST_COEFFICIENT = 1.05;

    RuleManagerConfig config;

    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getFlowMeterBurstCoefficient()).thenReturn(BURST_COEFFICIENT);
        when(config.getFlowMeterMinBurstSizeInKbits()).thenReturn(1024L);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationInnerVlanEqualTransitVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(TRANSIT_VLAN_ID, FEATURES);
        assertTrue(transformActions.isEmpty());
    }


    @Test
    public void buildTransformActionsVxlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchDoubleVlanInDoubleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchDoubleVlanInSingleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1, OUTER_VLAN_ID_2, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchDoubleVlanInFullPortOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1, 0, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInDoubleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) INNER_VLAN_ID_2).build(),
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInSingleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, OUTER_VLAN_ID_2, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInFullPortOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0, 0, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList();
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInDoubleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, 0, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) INNER_VLAN_ID_2).build(),
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInSingleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, 0, OUTER_VLAN_ID_2, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInFullPortOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, 0, 0, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList();
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildMatchVlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        Set<FieldMatch> match = generator.buildIngressMatch(new FlowSourceAdapter(flow).getEndpoint());
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build();
        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build()
        );
        assertEqualsMatch(expectedMatch, match);
    }

    @Test
    public void buildMatchVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        Set<FieldMatch> match = generator.buildIngressMatch(new FlowSourceAdapter(flow).getEndpoint());
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build();
        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build()
        );
        assertEqualsMatch(expectedMatch, match);
    }

    @Test
    public void buildMatchVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        Set<FieldMatch> match = generator.buildIngressMatch(new FlowSourceAdapter(flow).getEndpoint());
        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()
        );
        assertEqualsMatch(expectedMatch, match);
    }

    @Test
    public void oneSwitchFlowFullPortRuleTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, 0, OUTER_VLAN_ID_2, 0);
        MultiTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        FlowSpeakerCommandData inputCustomerCommand = (FlowSpeakerCommandData) commands.get(1);

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build());
        List<Action> expectedIngressActions = newArrayList(
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        RoutingMetadata metadata = RoutingMetadata.builder().oneSwitchFlowFlag(true).build();
        assertIngressCommand(ingressCommand, Priority.DEFAULT_FLOW_PRIORITY, expectedIngressMatch,
                expectedIngressActions, null, mapMetadata(metadata));
        assertInputCustomerCommand(inputCustomerCommand, new PortColourCookie(MULTI_TABLE_INGRESS_RULES, PORT_NUMBER_1),
                Sets.newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()));
    }

    @Test
    public void buildCommandsVlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(4, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        FlowSpeakerCommandData preIngressCommand = (FlowSpeakerCommandData) commands.get(1);
        FlowSpeakerCommandData inputCustomerCommand = (FlowSpeakerCommandData) commands.get(2);
        MeterSpeakerCommandData meterCommand = (MeterSpeakerCommandData) commands.get(3);
        assertEquals(newArrayList(meterCommand.getUuid()), new ArrayList<>(ingressCommand.getDependsOn()));

        Set<FieldMatch> expectedPreIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build());
        FlowSharedSegmentCookie preIngressCookie = FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                .portNumber(PORT_NUMBER_1)
                .vlanId(OUTER_VLAN_ID_1).build();
        RoutingMetadata preIngressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build();
        assertPreIngressCommand(preIngressCommand, preIngressCookie, Priority.FLOW_PRIORITY, expectedPreIngressMatch,
                newArrayList(new PopVlanAction()), mapMetadata(preIngressMetadata));

        assertInputCustomerCommand(inputCustomerCommand, new PortColourCookie(MULTI_TABLE_INGRESS_RULES, PORT_NUMBER_1),
                Sets.newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()));

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build();
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build());
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, Priority.FLOW_PRIORITY, expectedIngressMatch, expectedIngressActions,
                METER_ID, null);

        assertMeterCommand(meterCommand);
    }

    @Test
    public void buildCommandsVlanEncapsulationDoubleVlanWithOuterVlanOverlappingTest() {
        Flow oneSwitchFlow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0);
        Set<FlowSideAdapter> overlapping = Sets.newHashSet(
                FlowSideAdapter.makeIngressAdapter(oneSwitchFlow, ONE_SWITCH_PATH));
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION, overlapping);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        MeterSpeakerCommandData meterCommand = (MeterSpeakerCommandData) commands.get(1);
        assertEquals(newArrayList(meterCommand.getUuid()), new ArrayList<>(ingressCommand.getDependsOn()));

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build();
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build()
        );
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, Priority.FLOW_PRIORITY, expectedIngressMatch, expectedIngressActions,
                METER_ID, null);
        assertMeterCommand(meterCommand);
    }

    @Test
    public void buildCommandsVlanEncapsulationDoubleVlanPortOverlappingTest() {
        Flow oneSwitchFlow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_2, 0);
        Set<FlowSideAdapter> overlapping = Sets.newHashSet(
                FlowSideAdapter.makeIngressAdapter(oneSwitchFlow, ONE_SWITCH_PATH));
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION, overlapping);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(3, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        FlowSpeakerCommandData preIngressCommand = (FlowSpeakerCommandData) commands.get(1);
        MeterSpeakerCommandData meterCommand = (MeterSpeakerCommandData) commands.get(2);
        assertEquals(newArrayList(meterCommand.getUuid()), new ArrayList<>(ingressCommand.getDependsOn()));

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build();
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build()
        );
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        Set<FieldMatch> expectedPreIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        FlowSharedSegmentCookie preIngressCookie = FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                .portNumber(PORT_NUMBER_1)
                .vlanId(OUTER_VLAN_ID_1).build();
        RoutingMetadata preIngressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build();
        assertPreIngressCommand(preIngressCommand, preIngressCookie, Priority.FLOW_PRIORITY, expectedPreIngressMatch,
                newArrayList(new PopVlanAction()), mapMetadata(preIngressMetadata));

        assertIngressCommand(ingressCommand, Priority.FLOW_PRIORITY, expectedIngressMatch, expectedIngressActions,
                METER_ID, null);
        assertMeterCommand(meterCommand);
    }

    private void assertIngressCommand(
            FlowSpeakerCommandData command, int expectedPriority, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions, MeterId expectedMeter, OfMetadata expectedMetadata) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());

        assertEquals(COOKIE, command.getCookie());
        assertEquals(OfTable.INGRESS, command.getTable());
        assertEquals(expectedPriority, command.getPriority());

        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToTable(OfTable.POST_INGRESS)
                .goToMeter(expectedMeter)
                .writeMetadata(expectedMetadata)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
    }

    private void assertPreIngressCommand(
            FlowSpeakerCommandData command, CookieBase cookie, int expectedPriority, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions, OfMetadata expectedMetadata) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
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
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
    }

    private void assertInputCustomerCommand(
            FlowSpeakerCommandData command, CookieBase cookie, Set<FieldMatch> expectedMatch) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
        assertEquals(cookie, command.getCookie());
        assertEquals(OfTable.INPUT, command.getTable());
        assertEquals(Priority.INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE, command.getPriority());
        assertTrue(command.getDependsOn().isEmpty());

        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .goToTable(OfTable.PRE_INGRESS)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertNull(command.getFlags());
    }

    private void assertMeterCommand(MeterSpeakerCommandData command) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
        assertEquals(METER_ID, command.getMeterId());
        assertEquals(Sets.newHashSet(MeterFlag.BURST, MeterFlag.KBPS, MeterFlag.STATS), command.getFlags());
        assertEquals(BANDWIDTH, command.getRate());
        assertEquals((long) (BANDWIDTH * BURST_COEFFICIENT), command.getBurst());
        assertTrue(command.getDependsOn().isEmpty());

    }

    private MultiTableIngressRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return buildGenerator(path, flow, encapsulation, new HashSet<>());
    }

    private MultiTableIngressRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation,
            Set<FlowSideAdapter> overlappingAdapters) {
        return MultiTableIngressRuleGenerator.builder()
                .config(config)
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .overlappingIngressAdapters(overlappingAdapters)
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

    private PushVxlanAction buildPushVxlan() {
        return PushVxlanAction.builder()
                .srcMacAddress(new MacAddress(SWITCH_ID_1.toMacAddress()))
                .dstMacAddress(new MacAddress(SWITCH_ID_2.toMacAddress()))
                .srcIpv4Address(Constants.VXLAN_SRC_IPV4_ADDRESS)
                .dstIpv4Address(Constants.VXLAN_DST_IPV4_ADDRESS)
                .udpSrc(Constants.VXLAN_UDP_SRC)
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(VXLAN_VNI).build();
    }
}
