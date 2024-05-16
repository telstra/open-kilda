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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVER_42_FLOW_RTT_INPUT;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.factory.generator.flow.haflow.HaRuleGeneratorBaseTest.TRANSIT_VLAN_ID;
import static org.openkilda.rulemanager.utils.Utils.mapMetadata;

import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.MacAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.FlowSubType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;
import org.openkilda.rulemanager.utils.RoutingMetadata.HaSubFlowType;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SharedYServer42IngressForwardHaRuleGeneratorTest extends HaFlowRulesBaseTest {

    public static final int SERVER_42_PORT_NUMBER = 42;
    public static final MacAddress SERVER_42_MAC_ADDRESS = new MacAddress("42:42:42:42:42:42");
    public static final int OUTER_VLAN_ID_1 = 100;
    public static final int INNER_VLAN_ID_1 = 12;
    private static final Integer PORT_OFFSET = 5000;
    public static final SwitchProperties SWITCH_PROPERTIES = SwitchProperties.builder()
            .server42Port(SERVER_42_PORT_NUMBER)
            .server42MacAddress(SERVER_42_MAC_ADDRESS)
            .server42FlowRtt(true)
            .build();
    RuleManagerConfig config;

    @BeforeEach
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getServer42FlowRttUdpPortOffset()).thenReturn(PORT_OFFSET);
    }

    @Test
    public void generateCommandsSingleVlan() {
        HaFlow haFlow = buildYShapedYEqualsSharedHaFlow();
        haFlow.setSharedInnerVlan(SHARED_INNER_VLAN);
        SharedYServer42IngressForwardHaRuleGenerator generator = buildGenerator(haFlow, VLAN_ENCAPSULATION);

        List<FlowSpeakerData> commands = generator.generateCommands(SWITCH_1).stream()
                .map(FlowSpeakerData.class::cast)
                .sorted(Comparator.comparing(command -> command.getCookie().getValue()))
                .collect(Collectors.toList());

        Assertions.assertEquals(5, commands.size());

        FlowSpeakerData inputCustomerCommand1 = commands.get(0);
        assertInputCommand(inputCustomerCommand1, FlowSubType.HA_SUB_FLOW_1, HaSubFlowType.HA_SUB_FLOW_1,
                SWITCH_1, SWITCH_2);


        FlowSpeakerData inputCustomerCommand2 = commands.get(1);
        assertInputCommand(inputCustomerCommand2, FlowSubType.HA_SUB_FLOW_2, HaSubFlowType.HA_SUB_FLOW_2,
                SWITCH_1, SWITCH_3);


        FlowSpeakerData preIngressCommand = commands.get(2);
        assertPreIngressCommand(preIngressCommand, SWITCH_1, SHARED_INNER_VLAN);

        FlowSpeakerData ingressCommand1 = commands.get(3);
        assertIngressCommand(ingressCommand1,
                HaSubFlowType.HA_SUB_FLOW_1, SWITCH_1, FORWARD_SUB_COOKIE_1,
                Priority.SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY, SHARED_INNER_VLAN, null);
        FlowSpeakerData ingressCommand2 = commands.get(4);
        assertIngressCommand(ingressCommand2,
                HaSubFlowType.HA_SUB_FLOW_2, SWITCH_1, FORWARD_SUB_COOKIE_2,
                Priority.SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY, SHARED_INNER_VLAN, null);
    }

    @Test
    public void generateCommandsOneSubPathIsOneFlowFullPort() {
        final HaFlow haFlow = buildIShapedOneSwitchHaFlow();
        SharedYServer42IngressForwardHaRuleGenerator generator = buildGenerator(haFlow, VLAN_ENCAPSULATION);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);

        Assertions.assertEquals(2, commands.size());

        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);
        FlowSpeakerData inputCommand = (FlowSpeakerData) commands.get(1);

        assertIngressCommand(ingressCommand, HaSubFlowType.HA_SUB_FLOW_2, SWITCH_1, FORWARD_SUB_COOKIE_2,
                Priority.SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY, null, null);
        assertInputCommand(inputCommand, FlowSubType.HA_SUB_FLOW_2, HaSubFlowType.HA_SUB_FLOW_2, SWITCH_1, SWITCH_3);
    }

    @Test
    public void generateCommandsDoubleVlan() {
        HaFlow haFlow = buildYShapedYEqualsSharedHaFlow();
        haFlow.setSharedInnerVlan(INNER_VLAN_ID_1);
        haFlow.setSharedOuterVlan(OUTER_VLAN_ID_1);
        SharedYServer42IngressForwardHaRuleGenerator generator = buildGenerator(haFlow, VLAN_ENCAPSULATION);

        List<FlowSpeakerData> commands = generator.generateCommands(SWITCH_1).stream()
                .map(FlowSpeakerData.class::cast)
                .sorted(Comparator.comparing(command -> command.getCookie().getValue()))
                .collect(Collectors.toList());

        Assertions.assertEquals(5, commands.size());

        FlowSpeakerData inputCustomerCommand1 = commands.get(0);
        assertInputCommand(inputCustomerCommand1, FlowSubType.HA_SUB_FLOW_1, HaSubFlowType.HA_SUB_FLOW_1,
                SWITCH_1, SWITCH_2);


        FlowSpeakerData inputCustomerCommand2 = commands.get(1);
        assertInputCommand(inputCustomerCommand2, FlowSubType.HA_SUB_FLOW_2, HaSubFlowType.HA_SUB_FLOW_2,
                SWITCH_1, SWITCH_3);


        FlowSpeakerData preIngressCommand = commands.get(2);
        assertPreIngressCommand(preIngressCommand, SWITCH_1, OUTER_VLAN_ID_1);

        FlowSpeakerData ingressCommand1 = commands.get(3);
        assertIngressCommand(ingressCommand1,
                HaSubFlowType.HA_SUB_FLOW_1, SWITCH_1, FORWARD_SUB_COOKIE_1,
                Priority.SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        FlowSpeakerData ingressCommand2 = commands.get(4);
        assertIngressCommand(ingressCommand2,
                HaSubFlowType.HA_SUB_FLOW_2, SWITCH_1, FORWARD_SUB_COOKIE_2,
                Priority.SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY, OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
    }


    @Test
    public void generateCommandsNoServer42() {
        HaFlow haFlow = buildYShapedYEqualsSharedHaFlowServer42();
        SharedYServer42IngressForwardHaRuleGenerator generator = SharedYServer42IngressForwardHaRuleGenerator.builder()
                .config(config)
                .haFlow(haFlow)
                .switchProperties(SwitchProperties.builder().build())
                .build();
        List<SpeakerData> commands = generator.generateCommands(SWITCH_8_SERVER42);
        Assertions.assertTrue(commands.isEmpty());
    }

    @Test
    public void generateCommandsNoHaFlow() {
        SharedYServer42IngressForwardHaRuleGenerator generator = SharedYServer42IngressForwardHaRuleGenerator.builder()
                .config(config)
                .switchProperties(SWITCH_PROPERTIES)
                .build();
        List<SpeakerData> commands = generator.generateCommands(SWITCH_8_SERVER42);
        Assertions.assertTrue(commands.isEmpty());
    }



    private void assertIngressCommand(FlowSpeakerData ingressCommand, HaSubFlowType expectedHaSubFlowType,
                                      Switch srcSwitch, CookieBase cookieBase, int expectedPriority,
                                      Integer vlanId, Integer vlanId2) {
        final RoutingMetadata expectedIngressMetadata = RoutingMetadata.builder()
                .inputPort(PORT_NUMBER_1)
                .haSubFlowType(expectedHaSubFlowType)
                .outerVlanId(vlanId)
                .build(srcSwitch.getFeatures());

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.METADATA).value(expectedIngressMetadata.getValue())
                        .mask(expectedIngressMetadata.getMask()).build()
        );

        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.ETH_SRC)
                        .value(srcSwitch.getSwitchId().toMacAddressAsLong()).build());

        if (vlanId2 != null) {
            expectedIngressMatch.add(FieldMatch.builder().field(Field.VLAN_VID).value(vlanId2).build());
        } else {
            expectedIngressActions.add(new PushVlanAction());
        }

        expectedIngressActions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build());
        expectedIngressActions.add(new PortOutAction(new PortNumber(0)));

        final FlowSegmentCookie expectedIngressCookie = new FlowSegmentCookie(cookieBase.getValue())
                .toBuilder()
                .type(CookieType.SERVER_42_FLOW_RTT_INGRESS)
                .build();

        assertIngressCommand(ingressCommand, expectedPriority,
                expectedIngressMatch, expectedIngressActions, expectedIngressCookie, srcSwitch);
    }

    private void assertIngressCommand(
            FlowSpeakerData command, int expectedPriority, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions, CookieBase expectedCookie, Switch srcSwitch) {
        Assertions.assertEquals(srcSwitch.getSwitchId(), command.getSwitchId());
        Assertions.assertEquals(srcSwitch.getOfVersion(), command.getOfVersion().toString());

        Assertions.assertEquals(expectedCookie, command.getCookie());
        Assertions.assertEquals(OfTable.INGRESS, command.getTable());
        Assertions.assertEquals(expectedPriority, command.getPriority());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .build();
        Assertions.assertEquals(expectedInstructions, command.getInstructions());
        Assertions.assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
    }

    private void assertPreIngressCommand(FlowSpeakerData command, Switch sharedSwitch, int vlanId) {
        FlowSharedSegmentCookie cookie = FlowSharedSegmentCookie.builder(SharedSegmentType.SERVER42_QINQ_OUTER_VLAN)
                .portNumber(SERVER_42_PORT_NUMBER)
                .vlanId(vlanId).build();

        Assertions.assertEquals(sharedSwitch.getSwitchId(), command.getSwitchId());
        Assertions.assertEquals(sharedSwitch.getOfVersion(), command.getOfVersion().toString());
        Assertions.assertEquals(cookie, command.getCookie());
        Assertions.assertEquals(OfTable.PRE_INGRESS, command.getTable());
        Assertions.assertEquals(Priority.SERVER_42_PRE_INGRESS_FLOW_PRIORITY, command.getPriority());
        Assertions.assertTrue(command.getDependsOn().isEmpty());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(vlanId).build());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .writeMetadata(mapMetadata(RoutingMetadata.builder().outerVlanId(vlanId)
                        .build(sharedSwitch.getFeatures())))
                .applyActions(Lists.newArrayList(new PopVlanAction()))
                .goToTable(OfTable.INGRESS)
                .build();
        Assertions.assertEquals(expectedInstructions, command.getInstructions());
        Assertions.assertTrue(command.getFlags().isEmpty());
    }


    private void assertInputCommand(FlowSpeakerData command, FlowSubType subType, HaSubFlowType haSubFlowType,
                                    Switch srcSwitch, Switch dstSwitch) {
        Assertions.assertEquals(srcSwitch.getSwitchId(), command.getSwitchId());
        Assertions.assertEquals(srcSwitch.getOfVersion(), command.getOfVersion().toString());
        Assertions.assertEquals(new PortColourCookie(SERVER_42_FLOW_RTT_INPUT, PORT_NUMBER_1, subType),
                command.getCookie());
        Assertions.assertEquals(OfTable.INPUT, command.getTable());
        Assertions.assertEquals(Priority.SERVER_42_FLOW_RTT_INPUT_PRIORITY, command.getPriority());
        Assertions.assertTrue(command.getDependsOn().isEmpty());

        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(SERVER_42_PORT_NUMBER).build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(PORT_NUMBER_1 + PORT_OFFSET).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(SERVER_42_MAC_ADDRESS.toLong()).build(),
                FieldMatch.builder().field(Field.ETH_DST).value(dstSwitch.getSwitchId().toMacAddressAsLong()).build()
        );
        assertEqualsMatch(expectedMatch, command.getMatch());

        List<Action> expectedApplyActions = newArrayList(
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToTable(OfTable.PRE_INGRESS)
                .writeMetadata(mapMetadata(RoutingMetadata.builder()
                        .haSubFlowType(haSubFlowType).inputPort(PORT_NUMBER_1)
                        .build(srcSwitch.getFeatures())))
                .build();
        Assertions.assertEquals(expectedInstructions, command.getInstructions());
        Assertions.assertTrue(command.getFlags().isEmpty());
    }

    private SharedYServer42IngressForwardHaRuleGenerator buildGenerator(HaFlow haFlow,
                                                                        FlowTransitEncapsulation encapsulation) {
        return SharedYServer42IngressForwardHaRuleGenerator.builder()
                .config(config)
                .flowPath(haFlow.getForwardPath().getSubPaths().iterator().next())
                .haFlow(haFlow)
                .encapsulation(encapsulation)
                .overlappingIngressAdapters(Collections.emptySet())
                .switchProperties(SWITCH_PROPERTIES)
                .build();
    }

}

