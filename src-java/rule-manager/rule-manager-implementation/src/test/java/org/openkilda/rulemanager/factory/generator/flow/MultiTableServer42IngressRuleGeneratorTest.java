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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVER_42_FLOW_RTT_INPUT;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.utils.Utils.mapMetadata;

import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
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
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
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

public class MultiTableServer42IngressRuleGeneratorTest {
    public static final PathId PATH_ID = new PathId("path_id");
    public static final String FLOW_ID = "flow";
    public static final MeterId METER_ID = new MeterId(17);
    public static final int PORT_NUMBER_1 = 1;
    public static final int PORT_NUMBER_2 = 2;
    public static final int PORT_NUMBER_3 = 3;
    public static final int SERVER_42_PORT_NUMBER = 42;
    public static final MacAddress SERVER_42_MAC_ADDRESS = new MacAddress("42:42:42:42:42:42");
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
    private static final Integer PORT_OFFSET = 5000;
    public static final SwitchProperties SWITCH_PROPERTIES = SwitchProperties.builder()
            .server42Port(SERVER_42_PORT_NUMBER)
            .server42MacAddress(SERVER_42_MAC_ADDRESS)
            .multiTable(true)
            .server42FlowRtt(true)
            .build();
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID, FlowEncapsulationType.TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI, FlowEncapsulationType.VXLAN);
    public static final FlowSegmentCookie PATH_COOKIE_1 = new FlowSegmentCookie(FlowPathDirection.FORWARD, 123);
    public static final FlowSegmentCookie SERVER_42_INGRESS_COOKIE = new FlowSegmentCookie(PATH_COOKIE_1.getValue())
            .toBuilder()
            .type(CookieType.SERVER_42_FLOW_RTT_INGRESS)
            .build();
    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(PATH_COOKIE_1)
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
            .cookie(PATH_COOKIE_1)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_1)
            .srcWithMultiTable(true)
            .bandwidth(0)
            .segments(new ArrayList<>())
            .build();

    RuleManagerConfig config;

    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getServer42FlowRttUdpPortOffset()).thenReturn(PORT_OFFSET);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationInnerVlanEqualTransitVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, TRANSIT_VLAN_ID);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(TRANSIT_VLAN_ID, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(INNER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void oneSwitchFlowTest() {
        MultiTableServer42IngressRuleGenerator generator = MultiTableServer42IngressRuleGenerator.builder()
                .switchProperties(SWITCH_PROPERTIES)
                .flowPath(ONE_SWITCH_PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void nullSwitchPropertiesTest() {
        MultiTableServer42IngressRuleGenerator generator = MultiTableServer42IngressRuleGenerator.builder()
                .switchProperties(null)
                .flowPath(PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void singleTableSwitchPropertiesTest() {
        MultiTableServer42IngressRuleGenerator generator = MultiTableServer42IngressRuleGenerator.builder()
                .switchProperties(SwitchProperties.builder().multiTable(false).server42FlowRtt(true).build())
                .flowPath(PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void falseServer42SwitchPropertiesTest() {
        MultiTableServer42IngressRuleGenerator generator = MultiTableServer42IngressRuleGenerator.builder()
                .switchProperties(SwitchProperties.builder().multiTable(true).server42FlowRtt(false).build())
                .flowPath(PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void buildCommandsVlanEncapsulationDoubleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(3, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        FlowSpeakerCommandData preIngressCommand = (FlowSpeakerCommandData) commands.get(1);
        FlowSpeakerCommandData inputCustomerCommand = (FlowSpeakerCommandData) commands.get(2);

        assertPreIngressCommand(preIngressCommand, newArrayList(new PopVlanAction()));
        assertInputCommand(inputCustomerCommand);

        RoutingMetadata ingressMetadata = RoutingMetadata.builder()
                .inputPort(PORT_NUMBER_1).outerVlanId(OUTER_VLAN_ID_1).build();
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build());
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, Priority.SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions);
    }

    @Test
    public void buildCommandsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(3, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        FlowSpeakerCommandData preIngressCommand = (FlowSpeakerCommandData) commands.get(1);
        FlowSpeakerCommandData inputCustomerCommand = (FlowSpeakerCommandData) commands.get(2);

        assertPreIngressCommand(preIngressCommand, newArrayList(new PopVlanAction()));
        assertInputCommand(inputCustomerCommand);

        RoutingMetadata ingressMetadata = RoutingMetadata.builder()
                .inputPort(PORT_NUMBER_1).outerVlanId(OUTER_VLAN_ID_1).build();
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build());
        List<Action> expectedIngressActions = newArrayList(
                buildPushVxlan(), new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertIngressCommand(ingressCommand, Priority.SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions);
    }

    @Test
    public void buildCommandsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        FlowSpeakerCommandData inputCustomerCommand = (FlowSpeakerCommandData) commands.get(1);

        assertInputCommand(inputCustomerCommand);

        RoutingMetadata ingressMetadata = RoutingMetadata.builder().inputPort(PORT_NUMBER_1).build();
        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.METADATA)
                        .value(ingressMetadata.getValue()).mask(ingressMetadata.getMask()).build());
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertIngressCommand(ingressCommand, Priority.SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions);
    }

    @Test
    public void noOverlappingFlowsTest() {
        MultiTableServer42IngressRuleGenerator generator = MultiTableServer42IngressRuleGenerator.builder()
                .overlappingIngressAdapters(new HashSet<>()).build();
        FlowEndpoint ingressEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        assertTrue(generator.needToBuildServer42PreIngressRule(ingressEndpoint));
        assertTrue(generator.needToBuildServer42InputRule(ingressEndpoint));
    }

    @Test
    public void noOverlappingFlowsFullPortFlowTest() {
        MultiTableServer42IngressRuleGenerator generator = MultiTableServer42IngressRuleGenerator.builder()
                .overlappingIngressAdapters(new HashSet<>()).build();
        FlowEndpoint ingressEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, 0, 0);
        assertFalse(generator.needToBuildServer42PreIngressRule(ingressEndpoint));
        assertTrue(generator.needToBuildServer42InputRule(ingressEndpoint));
    }

    @Test
    public void outerVlanInPortOverlappingFlowTest() {
        Flow overlappingFlow = buildOverlappingFlow(PORT_NUMBER_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(overlappingFlow);
        FlowEndpoint ingressEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        assertFalse(generator.needToBuildServer42PreIngressRule(ingressEndpoint));
        assertFalse(generator.needToBuildServer42InputRule(ingressEndpoint));
    }

    @Test
    public void outerVlanOverlappingFlowTest() {
        Flow overlappingFlow = buildOverlappingFlow(PORT_NUMBER_2, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(overlappingFlow);
        FlowEndpoint ingressEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        assertFalse(generator.needToBuildServer42PreIngressRule(ingressEndpoint));
        assertTrue(generator.needToBuildServer42InputRule(ingressEndpoint));
    }

    @Test
    public void overlappingOfFullFlowTest() {
        Flow overlappingFlow = buildOverlappingFlow(PORT_NUMBER_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(overlappingFlow);
        FlowEndpoint ingressEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, 0, 0);
        assertFalse(generator.needToBuildServer42PreIngressRule(ingressEndpoint));
        assertFalse(generator.needToBuildServer42InputRule(ingressEndpoint));
    }

    @Test
    public void fullPortOverlappingFlowTest() {
        Flow overlappingFlow = buildOverlappingFlow(PORT_NUMBER_1, 0, 0);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(overlappingFlow);
        FlowEndpoint ingressEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, OUTER_VLAN_ID_1, 0);
        assertTrue(generator.needToBuildServer42PreIngressRule(ingressEndpoint));
        assertFalse(generator.needToBuildServer42InputRule(ingressEndpoint));
    }

    @Test
    public void inPortOverlappingFlowTest() {
        Flow overlappingFlow = buildOverlappingFlow(PORT_NUMBER_1, OUTER_VLAN_ID_2, INNER_VLAN_ID_1);
        MultiTableServer42IngressRuleGenerator generator = buildGenerator(overlappingFlow);
        FlowEndpoint ingressEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_NUMBER_1, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        assertTrue(generator.needToBuildServer42PreIngressRule(ingressEndpoint));
        assertFalse(generator.needToBuildServer42InputRule(ingressEndpoint));
    }

    private void assertIngressCommand(
            FlowSpeakerCommandData command, int expectedPriority, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());

        assertEquals(SERVER_42_INGRESS_COOKIE, command.getCookie());
        assertEquals(OfTable.INGRESS, command.getTable());
        assertEquals(expectedPriority, command.getPriority());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
    }

    private void assertPreIngressCommand(FlowSpeakerCommandData command, List<Action> expectedApplyActions) {
        FlowSharedSegmentCookie cookie = FlowSharedSegmentCookie.builder(SharedSegmentType.SERVER42_QINQ_OUTER_VLAN)
                .portNumber(SERVER_42_PORT_NUMBER)
                .vlanId(OUTER_VLAN_ID_1).build();

        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
        assertEquals(cookie, command.getCookie());
        assertEquals(OfTable.PRE_INGRESS, command.getTable());
        assertEquals(Priority.SERVER_42_PRE_INGRESS_FLOW_PRIORITY, command.getPriority());
        assertTrue(command.getDependsOn().isEmpty());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .writeMetadata(mapMetadata(RoutingMetadata.builder().outerVlanId(OUTER_VLAN_ID_1).build()))
                .applyActions(expectedApplyActions)
                .goToTable(OfTable.INGRESS)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertNull(command.getFlags());
    }

    private void assertInputCommand(FlowSpeakerCommandData command) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
        assertEquals(new PortColourCookie(SERVER_42_FLOW_RTT_INPUT, PORT_NUMBER_1), command.getCookie());
        assertEquals(OfTable.INPUT, command.getTable());
        assertEquals(Priority.SERVER_42_FLOW_RTT_INPUT_PRIORITY, command.getPriority());
        assertTrue(command.getDependsOn().isEmpty());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(PORT_NUMBER_1 + PORT_OFFSET).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(SERVER_42_MAC_ADDRESS.toLong()).build()
        );
        assertEqualsMatch(expectedMatch, command.getMatch());

        List<Action> expectedApplyActions = newArrayList(
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToTable(OfTable.PRE_INGRESS)
                .writeMetadata(mapMetadata(RoutingMetadata.builder().inputPort(PORT_NUMBER_1).build()))
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertNull(command.getFlags());
    }

    private MultiTableServer42IngressRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return MultiTableServer42IngressRuleGenerator.builder()
                .config(config)
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .overlappingIngressAdapters(new HashSet<>())
                .switchProperties(SWITCH_PROPERTIES)
                .build();
    }

    private MultiTableServer42IngressRuleGenerator buildGenerator(Flow overLappingFlow) {
        return MultiTableServer42IngressRuleGenerator.builder()
                .config(config)
                .overlappingIngressAdapters(Sets.newHashSet(new FlowSourceAdapter(overLappingFlow)))
                .switchProperties(SWITCH_PROPERTIES)
                .build();
    }

    private Flow buildFlow(FlowPath path, int srcOuterVlan, int srcInnerVlan) {
        Flow flow = Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(path.getSrcSwitch())
                .srcPort(PORT_NUMBER_1)
                .srcVlan(srcOuterVlan)
                .srcInnerVlan(srcInnerVlan)
                .destSwitch(path.getDestSwitch())
                .destPort(PORT_NUMBER_2)
                .destVlan(OUTER_VLAN_ID_2)
                .destInnerVlan(INNER_VLAN_ID_2)
                .build();
        flow.setForwardPath(path);
        return flow;
    }

    private Flow buildOverlappingFlow(int srcPort, int srcOuterVlan, int srcInnerVlan) {
        return Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .srcPort(srcPort)
                .srcVlan(srcOuterVlan)
                .srcInnerVlan(srcInnerVlan)
                .build();

    }

    private PushVxlanAction buildPushVxlan() {
        return PushVxlanAction.builder()
                .srcMacAddress(new MacAddress(SWITCH_ID_1.toMacAddress()))
                .dstMacAddress(new MacAddress(SWITCH_ID_2.toMacAddress()))
                .srcIpv4Address(Constants.VXLAN_SRC_IPV4_ADDRESS)
                .dstIpv4Address(Constants.VXLAN_DST_IPV4_ADDRESS)
                .udpSrc(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT)
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(VXLAN_VNI).build();
    }
}
