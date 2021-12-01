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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.buildSwitch;

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
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfFlowFlag;
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

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SingleTableIngressYRuleGeneratorTest {
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
    public static final int TRANSIT_VLAN_ID = 14;
    public static final int VXLAN_VNI = 15;
    public static final int BANDWIDTH = 1000;
    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            TRANSIT_VLAN_ID, FlowEncapsulationType.TRANSIT_VLAN);
    public static final FlowTransitEncapsulation VXLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            VXLAN_VNI, FlowEncapsulationType.VXLAN);
    public static final FlowSegmentCookie COOKIE = FlowSegmentCookie.builder().yFlow(true)
            .direction(FlowPathDirection.FORWARD).flowEffectiveId(123).build();
    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(COOKIE)
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
            .cookie(COOKIE)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_1)
            .srcWithMultiTable(false)
            .destWithMultiTable(false)
            .bandwidth(0)
            .segments(new ArrayList<>())
            .build();
    public static final double BURST_COEFFICIENT = 1.05;

    RuleManagerConfig config;

    public static final MeterId SHARED_METER_ID = new MeterId(34);
    public static final String SHARED_METER_UUID = "uuid";



    @Before
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getFlowMeterBurstCoefficient()).thenReturn(BURST_COEFFICIENT);
        when(config.getFlowMeterMinBurstSizeInKbits()).thenReturn(1024L);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) TRANSIT_VLAN_ID).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationOuterVlanEqualTransitVlanTest() {
        Flow flow = buildFlow(PATH, TRANSIT_VLAN_ID, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(TRANSIT_VLAN_ID, FEATURES);
        assertTrue(transformActions.isEmpty());
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInSingleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, OUTER_VLAN_ID_2);
        SingleTableIngressYRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInFullPortOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction());
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInSingleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, OUTER_VLAN_ID_2);
        SingleTableIngressYRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                PushVlanAction.builder().vlanId((short) OUTER_VLAN_ID_2).build()
        );
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInFullPortOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList();
        assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildMatchVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        Set<FieldMatch> match = generator.buildMatch(new FlowSourceAdapter(flow).getEndpoint());
        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEqualsMatch(expectedMatch, match);
    }

    @Test
    public void buildMatchVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        Set<FieldMatch> match = generator.buildMatch(new FlowSourceAdapter(flow).getEndpoint());
        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()
        );
        assertEqualsMatch(expectedMatch, match);
    }

    @Test
    public void oneSwitchFlowFullPortRuleTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, OUTER_VLAN_ID_2);
        SingleTableIngressYRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        assertThrows(IllegalStateException.class, () -> generator.generateCommands(SWITCH_1));
    }

    @Test
    public void buildCommandsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(2, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        MeterSpeakerCommandData meterCommand = (MeterSpeakerCommandData) commands.get(1);
        assertEquals(newArrayList(meterCommand.getUuid()), new ArrayList<>(ingressCommand.getDependsOn()));

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, Priority.Y_FLOW_PRIORITY, expectedIngressMatch, expectedIngressActions,
                SHARED_METER_ID);

        assertMeterCommand(meterCommand);
    }

    @Test
    public void buildCommandsWithoutMeter() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressYRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION, false);
        List<SpeakerCommandData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(1, commands.size());

        FlowSpeakerCommandData ingressCommand = (FlowSpeakerCommandData) commands.get(0);
        assertEquals(newArrayList(SHARED_METER_UUID), new ArrayList<>(ingressCommand.getDependsOn()));

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, Priority.Y_FLOW_PRIORITY, expectedIngressMatch, expectedIngressActions,
                SHARED_METER_ID);

    }

    private void assertIngressCommand(
            FlowSpeakerCommandData command, int expectedPriority, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions, MeterId expectedMeter) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());

        assertEquals(COOKIE, command.getCookie());
        assertEquals(OfTable.INPUT, command.getTable());
        assertEquals(expectedPriority, command.getPriority());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToMeter(expectedMeter)
                .build();
        assertEquals(expectedInstructions, command.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
    }

    private void assertMeterCommand(MeterSpeakerCommandData command) {
        assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
        assertEquals(SHARED_METER_ID, command.getMeterId());
        assertEquals(Sets.newHashSet(MeterFlag.BURST, MeterFlag.KBPS, MeterFlag.STATS), command.getFlags());
        assertEquals(BANDWIDTH, command.getRate());
        assertEquals((long) (BANDWIDTH * BURST_COEFFICIENT), command.getBurst());
        assertTrue(command.getDependsOn().isEmpty());

    }

    private SingleTableIngressYRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation, boolean generateMeterCommand) {
        return SingleTableIngressYRuleGenerator.builder()
                .config(config)
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .sharedMeterId(SHARED_METER_ID)
                .generateMeterCommand(generateMeterCommand)
                .externalMeterCommandUuid(SHARED_METER_UUID)
                .build();
    }

    private SingleTableIngressYRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return buildGenerator(path, flow, encapsulation, true);
    }

    private Flow buildFlow(FlowPath path, int srcOuterVlan, int dstOuterVlan) {
        Flow flow = Flow.builder()
                .flowId(FLOW_ID)
                .srcSwitch(path.getSrcSwitch())
                .srcPort(PORT_NUMBER_1)
                .srcVlan(srcOuterVlan)
                .srcInnerVlan(0)
                .destSwitch(path.getDestSwitch())
                .destPort(PORT_NUMBER_2)
                .destVlan(dstOuterVlan)
                .destInnerVlan(0)
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
