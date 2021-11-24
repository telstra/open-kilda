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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Constants.NOVIFLOW_TIMESTAMP_SIZE_IN_BITS;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;

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
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.CookieBase.CookieType;
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
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.Oxm;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SingleTableServer42IngressRuleGeneratorTest {
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
            RESET_COUNTS_FLAG, METERS, NOVIFLOW_PUSH_POP_VXLAN, NOVIFLOW_COPY_FIELD);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, FEATURES);
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, FEATURES);
    public static final int OUTER_VLAN_ID_1 = 10;
    public static final int OUTER_VLAN_ID_2 = 11;
    public static final int TRANSIT_VLAN_ID = 12;
    public static final int VXLAN_VNI = 13;
    public static final int BANDWIDTH = 1000;
    private static final Integer PORT_OFFSET = 5000;
    public static final SwitchProperties SWITCH_PROPERTIES = SwitchProperties.builder()
            .server42Port(SERVER_42_PORT_NUMBER)
            .server42MacAddress(SERVER_42_MAC_ADDRESS)
            .multiTable(false)
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
            .srcWithMultiTable(false)
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
            .srcWithMultiTable(false)
            .bandwidth(0)
            .segments(new ArrayList<>())
            .build();
    public static final CopyFieldAction COPY_FIELD_ACTION = CopyFieldAction.builder()
            .oxmSrcHeader(Oxm.NOVIFLOW_TX_TIMESTAMP)
            .oxmDstHeader(Oxm.NOVIFLOW_UDP_PAYLOAD_OFFSET)
            .numberOfBits(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
            .dstOffset(0)
            .srcOffset(0)
            .build();

    RuleManagerConfig config;

    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getServer42FlowRttUdpPortOffset()).thenReturn(PORT_OFFSET);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                COPY_FIELD_ACTION,
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                COPY_FIELD_ACTION,
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationFullPortCantCopyFieldTest() {
        Flow flow = buildFlow(PATH, 0);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, new HashSet<>());
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationOuterVlanEqualTransitVlanTest() {
        Flow flow = buildFlow(PATH, TRANSIT_VLAN_ID);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(TRANSIT_VLAN_ID, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                COPY_FIELD_ACTION);
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), COPY_FIELD_ACTION, buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationSingleVlanCantCopyFieldTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(
                OUTER_VLAN_ID_1, Sets.newHashSet(NOVIFLOW_PUSH_POP_VXLAN));
        List<Action> expectedActions = newArrayList(new PopVlanAction(), buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(COPY_FIELD_ACTION, buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void oneSwitchFlowTest() {
        SingleTableServer42IngressRuleGenerator generator = SingleTableServer42IngressRuleGenerator.builder()
                .switchProperties(SWITCH_PROPERTIES)
                .flowPath(ONE_SWITCH_PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void nullSwitchPropertiesTest() {
        SingleTableServer42IngressRuleGenerator generator = SingleTableServer42IngressRuleGenerator.builder()
                .switchProperties(null)
                .flowPath(PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void multyTableSwitchPropertiesTest() {
        SingleTableServer42IngressRuleGenerator generator = SingleTableServer42IngressRuleGenerator.builder()
                .switchProperties(SwitchProperties.builder().multiTable(true).server42FlowRtt(true).build())
                .flowPath(PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void falseServer42SwitchPropertiesTest() {
        SingleTableServer42IngressRuleGenerator generator = SingleTableServer42IngressRuleGenerator.builder()
                .switchProperties(SwitchProperties.builder().multiTable(false).server42FlowRtt(false).build())
                .flowPath(PATH)
                .build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void buildCommandsFullPortTransitVlanTest() {
        Flow flow = buildFlow(PATH, 0);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(1, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(SERVER_42_MAC_ADDRESS.toLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(PORT_OFFSET + PORT_NUMBER_1).build());
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC).value(SWITCH_ID_1.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.ETH_DST).value(SWITCH_ID_2.toMacAddressAsLong()).build(),
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                COPY_FIELD_ACTION,
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertIngressCommand(ingressCommand, Priority.SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY, expectedIngressMatch,
                expectedIngressActions);
    }

    @Test
    public void buildCommandsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1);
        SingleTableServer42IngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(1, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(SERVER_42_MAC_ADDRESS.toLong()).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(PORT_OFFSET + PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build());
        List<Action> expectedIngressActions = newArrayList(
                new PopVlanAction(), COPY_FIELD_ACTION, buildPushVxlan(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));

        assertIngressCommand(ingressCommand, Priority.SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY,
                expectedIngressMatch, expectedIngressActions);
    }

    private void assertIngressCommand(
            FlowSpeakerCommandData command, int expectedPriority, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());

        assertEquals(SERVER_42_INGRESS_COOKIE, command.getCookie());
        assertEquals(OfTable.INPUT, command.getTable());
        assertEquals(expectedPriority, command.getPriority());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
    }

    private SingleTableServer42IngressRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return SingleTableServer42IngressRuleGenerator.builder()
                .config(config)
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .switchProperties(SWITCH_PROPERTIES)
                .build();
    }

    private Flow buildFlow(FlowPath path, int srcOuterVlan) {
        Flow flow = Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(path.getSrcSwitch())
                .srcPort(PORT_NUMBER_1)
                .srcVlan(srcOuterVlan)
                .destSwitch(path.getDestSwitch())
                .destPort(PORT_NUMBER_2)
                .destVlan(OUTER_VLAN_ID_2)
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
                .udpSrc(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT)
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(VXLAN_VNI).build();
    }
}
