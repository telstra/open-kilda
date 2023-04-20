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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.cookie.CookieBase.CookieType.MULTI_TABLE_INGRESS_RULES;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.getCommand;
import static org.openkilda.rulemanager.utils.Utils.mapMetadata;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
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
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
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

public class IngressHaRuleGeneratorTest extends HaRuleGeneratorBaseTest {
    public static final FlowPath FORWARD_PATH = buildSubPath(
            SWITCH_1, SWITCH_2, FORWARD_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);

    @Test
    public void buildTransformActionsVlanEncapsulationDoubleVlanTest() {
        HaFlow haFlow = buildHaFlow(FORWARD_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(FORWARD_PATH, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationSingleVlanTest() {
        HaFlow haFlow = buildHaFlow(FORWARD_PATH, OUTER_VLAN_ID_1, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(FORWARD_PATH, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationFullPortTest() {
        HaFlow haFlow = buildHaFlow(FORWARD_PATH, 0, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(FORWARD_PATH, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationInnerVlanEqualTransitVlanTest() {
        HaFlow haFlow = buildHaFlow(FORWARD_PATH, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(FORWARD_PATH, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(TRANSIT_VLAN_ID, FEATURES);
        assertTrue(transformActions.isEmpty());
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationDoubleVlanTest() {
        HaFlow haFlow = buildHaFlow(FORWARD_PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(FORWARD_PATH, haFlow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationSingleVlanTest() {
        HaFlow haFlow = buildHaFlow(FORWARD_PATH, OUTER_VLAN_ID_1, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(FORWARD_PATH, haFlow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationFullPortTest() {
        HaFlow haFlow = buildHaFlow(FORWARD_PATH, 0, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(FORWARD_PATH, haFlow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unableToBuildIngressRulesForForwardOneSwitchPathTest() {
        FlowPath oneSwitchForward = buildSubPath(SWITCH_1, SWITCH_1, FORWARD_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchForward, haFlow, VLAN_ENCAPSULATION);
        generator.generateCommands(SWITCH_1);
    }

    @Test
    public void buildTransformActionsOneSwitchDoubleVlanInDoubleVlanOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_2, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchDoubleVlanInSingleVlanOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_2, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchDoubleVlanInFullPortOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInDoubleVlanOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, OUTER_VLAN_ID_2, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInSingleVlanOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, OUTER_VLAN_ID_2, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInFullPortOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, OUTER_VLAN_ID_2, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList();
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInDoubleVlanOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, 0, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInSingleVlanOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, 0, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInFullPortOutTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, 0, 0);
        HaFlow haFlow = buildHaFlow(SWITCH_1, 0, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haFlow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList();
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void oneSwitchFlowFullPortRuleTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, 0, 0);
        HaFlow haflow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, 0);
        IngressHaRuleGenerator generator = buildMeterlessGenerator(oneSwitchReverse, haflow, VLAN_ENCAPSULATION);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());

        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);
        FlowSpeakerData inputCustomerCommand = (FlowSpeakerData) commands.get(1);

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_4).build());
        List<Action> expectedIngressActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_1)));
        assertIngressCommand(ingressCommand, SWITCH_2, REVERSE_COOKIE, Priority.DEFAULT_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions, null, null);
        assertInputCustomerCommand(inputCustomerCommand, SWITCH_2,
                new PortColourCookie(MULTI_TABLE_INGRESS_RULES, PORT_NUMBER_4),
                Sets.newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_4).build()));
    }

    @Test
    public void buildCommandsVlanEncapsulationDoubleVlanTest() {
        FlowPath subPath = buildSubPath(SWITCH_2, SWITCH_1, FORWARD_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        HaFlow haFlow = buildHaFlow(subPath, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildGenerator(subPath, haFlow, VLAN_ENCAPSULATION, true,
                METER_ID, null, true, new HashSet<>());
        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(4, commands.size());

        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);
        FlowSpeakerData preIngressCommand = (FlowSpeakerData) commands.get(1);
        FlowSpeakerData inputCustomerCommand = (FlowSpeakerData) commands.get(2);
        MeterSpeakerData meterCommand = (MeterSpeakerData) commands.get(3);
        assertEquals(newArrayList(meterCommand.getUuid()), new ArrayList<>(ingressCommand.getDependsOn()));

        Set<FieldMatch> expectedPreIngressMatch = Sets.newHashSet(
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
                Sets.newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()));

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1)
                .build(SWITCH_2.getFeatures());
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build());
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, SWITCH_2, SHARED_FORWARD_COOKIE, Priority.DOUBLE_VLAN_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions, METER_ID, meterCommand.getUuid());

        assertMeterCommand(SWITCH_2, meterCommand);
    }

    @Test
    public void buildCommandsVlanEncapsulationDoubleVlanWithOuterVlanOverlappingTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_1, SWITCH_1, REVERSE_COOKIE, OUTER_VLAN_ID_1, 0);
        HaFlow oneSwitchHaFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);
        Set<FlowSideAdapter> overlapping = Sets.newHashSet(
                FlowSideAdapter.makeIngressAdapter(oneSwitchHaFlow, oneSwitchReverse));
        HaFlow flow = buildHaFlow(SWITCH_1, oneSwitchReverse.getHaSubFlow().getEndpointPort(),
                OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        IngressHaRuleGenerator generator = buildGenerator(FORWARD_PATH, flow, VLAN_ENCAPSULATION, false, null, null,
                false, overlapping);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(1, commands.size());

        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1)
                .build(SWITCH_1.getFeatures());
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_4).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build()
        );
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, SWITCH_1, FORWARD_COOKIE, Priority.DOUBLE_VLAN_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions, null, null);
    }

    @Test
    public void buildCommandsVlanEncapsulationDoubleVlanPortOverlappingTest() {
        FlowPath oneSwitchReverse = buildSubPath(SWITCH_2, SWITCH_2, REVERSE_COOKIE, OUTER_VLAN_ID_2, 0);
        HaFlow oneSwitchHaFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);
        Set<FlowSideAdapter> overlapping = Sets.newHashSet(
                FlowSideAdapter.makeIngressAdapter(oneSwitchHaFlow, oneSwitchReverse));
        HaFlow flow = buildHaFlow(SWITCH_2, oneSwitchReverse.getHaSubFlow().getEndpointPort(),
                OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        FlowPath subPath = buildSubPath(SWITCH_2, SWITCH_1, FORWARD_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        IngressHaRuleGenerator generator = buildGenerator(subPath, flow, VLAN_ENCAPSULATION, true, METER_ID, null,
                true, overlapping);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(3, commands.size());

        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);
        FlowSpeakerData preIngressCommand = (FlowSpeakerData) commands.get(1);
        MeterSpeakerData meterCommand = (MeterSpeakerData) commands.get(2);
        assertEquals(newArrayList(meterCommand.getUuid()), new ArrayList<>(ingressCommand.getDependsOn()));

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1)
                .build(SWITCH_2.getFeatures());
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_4).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build()
        );
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        Set<FieldMatch> expectedPreIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_4).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        FlowSharedSegmentCookie preIngressCookie = FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                .portNumber(PORT_NUMBER_4)
                .vlanId(OUTER_VLAN_ID_1).build();
        RoutingMetadata preIngressMetadata = RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1)
                .build(SWITCH_2.getFeatures());
        assertPreIngressCommand(preIngressCommand, SWITCH_2, preIngressCookie, Priority.FLOW_PRIORITY,
                expectedPreIngressMatch, newArrayList(new PopVlanAction()), mapMetadata(preIngressMetadata));

        assertIngressCommand(ingressCommand, SWITCH_2, SHARED_FORWARD_COOKIE, Priority.DOUBLE_VLAN_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions, METER_ID, meterCommand.getUuid());
        assertMeterCommand(SWITCH_2, meterCommand);
    }

    @Test
    public void createSharedMeterTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID);
        FlowPath subPath = buildSubPath(SWITCH_2, SWITCH_1, FORWARD_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        IngressHaRuleGenerator generator = buildGenerator(subPath, haFlow, VLAN_ENCAPSULATION, true, METER_ID,
                METER_COMMAND_UUID, true, Sets.newHashSet(FlowSideAdapter.makeIngressAdapter(haFlow, subPath)));

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommand = getCommand(MeterSpeakerData.class, commands);

        assertEquals(METER_ID, flowCommand.getInstructions().getGoToMeter());
        assertEquals(Lists.newArrayList(METER_COMMAND_UUID), flowCommand.getDependsOn());
        assertMeterCommand(SWITCH_2, METER_COMMAND_UUID, meterCommand);
    }

    @Test
    public void dependsOnSharedMeterTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID);
        FlowPath subPath = buildSubPath(SWITCH_2, SWITCH_1, FORWARD_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        IngressHaRuleGenerator generator = buildGenerator(subPath, haFlow, VLAN_ENCAPSULATION, true, METER_ID,
                METER_COMMAND_UUID, false, Sets.newHashSet(FlowSideAdapter.makeIngressAdapter(haFlow, subPath)));

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);

        assertEquals(METER_ID, flowCommand.getInstructions().getGoToMeter());
        assertEquals(Lists.newArrayList(METER_COMMAND_UUID), flowCommand.getDependsOn());
    }

    @Test
    public void nullSharedMeterTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_2, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID);
        FlowPath subPath = buildSubPath(SWITCH_2, SWITCH_1, FORWARD_COOKIE, OUTER_VLAN_ID_2, INNER_VLAN_ID_2);
        IngressHaRuleGenerator generator = buildGenerator(subPath, haFlow, VLAN_ENCAPSULATION, true, null,
                METER_COMMAND_UUID, true, Sets.newHashSet(FlowSideAdapter.makeIngressAdapter(haFlow, subPath)));

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        assertNull(flowCommand.getInstructions().getGoToMeter());
        assertTrue(flowCommand.getDependsOn().isEmpty());
    }

    @Test
    public void sharedMeterSwitchDoesntSupportMetersTest() {
        HaFlow haFlow = buildHaFlow(SWITCH_1, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID);
        IngressHaRuleGenerator generator = buildGenerator(FORWARD_PATH, haFlow, VLAN_ENCAPSULATION, true, METER_ID,
                METER_COMMAND_UUID, true, Sets.newHashSet(FlowSideAdapter.makeIngressAdapter(haFlow, FORWARD_PATH)));

        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        assertNull(flowCommand.getInstructions().getGoToMeter());
        assertTrue(flowCommand.getDependsOn().isEmpty());
    }

    private void assertIngressCommand(
            FlowSpeakerData command, Switch expectedSwitch, FlowSegmentCookie expectedCookie,  int expectedPriority,
            Set<FieldMatch> expectedMatch, List<Action> expectedApplyActions, MeterId expectedMeter,
            UUID expectedMeterUuid) {
        assertEquals(expectedSwitch.getSwitchId(), command.getSwitchId());
        assertEquals(expectedSwitch.getOfVersion(), command.getOfVersion().toString());

        assertEquals(expectedCookie, command.getCookie());
        assertEquals(OfTable.INGRESS, command.getTable());
        assertEquals(expectedPriority, command.getPriority());

        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToTable(null)
                .goToMeter(expectedMeter)
                .writeMetadata(null)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());

        List<UUID> expectedDependencies = new ArrayList<>();
        if (expectedMeterUuid != null) {
            expectedDependencies.add(expectedMeterUuid);
        }
        assertEquals(expectedDependencies, command.getDependsOn());
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

    private void assertMeterCommand(Switch expectedSwitch, UUID expectedUuid, MeterSpeakerData command) {
        assertMeterCommand(expectedSwitch, command);
        assertEquals(expectedUuid, command.getUuid());
    }

    private void assertMeterCommand(Switch expectedSwitch, MeterSpeakerData command) {
        assertEquals(expectedSwitch.getSwitchId(), command.getSwitchId());
        assertEquals(expectedSwitch.getOfVersion(), command.getOfVersion().toString());
        assertEquals(METER_ID, command.getMeterId());
        assertEquals(Sets.newHashSet(MeterFlag.BURST, MeterFlag.KBPS, MeterFlag.STATS), command.getFlags());
        assertEquals(BANDWIDTH, command.getRate());
        assertEquals(Math.round(BANDWIDTH * BURST_COEFFICIENT), command.getBurst());
        assertTrue(command.getDependsOn().isEmpty());

    }

    private IngressHaRuleGenerator buildMeterlessGenerator(
            FlowPath path, HaFlow haFlow, FlowTransitEncapsulation encapsulation) {
        return buildGenerator(path, haFlow, encapsulation, false, null, null, false, new HashSet<>());
    }

    private IngressHaRuleGenerator buildGenerator(
            FlowPath path, HaFlow haFlow, FlowTransitEncapsulation encapsulation, boolean sharedPath, MeterId meterId,
            UUID externalMeterUuid, boolean generateMeterCommand, Set<FlowSideAdapter> overlappingAdapters) {
        return IngressHaRuleGenerator.builder()
                .config(config)
                .subPath(path)
                .haFlow(haFlow)
                .encapsulation(encapsulation)
                .isSharedPath(sharedPath)
                .meterId(meterId)
                .externalMeterCommandUuid(externalMeterUuid)
                .generateCreateMeterCommand(generateMeterCommand)
                .overlappingIngressAdapters(overlappingAdapters)
                .build();
    }

    private HaFlow buildHaFlow(FlowPath subPath, int outerVlan, int innerVlan) {
        return buildHaFlow(subPath.getSrcSwitch(), PORT_NUMBER_1, outerVlan, innerVlan);
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
