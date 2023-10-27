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
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SingleTableIngressRuleGeneratorTest {
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
    public static final FlowSegmentCookie COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 123);
    public static final FlowPath PATH = FlowPath.builder()
            .pathId(PATH_ID)
            .cookie(COOKIE)
            .meterId(METER_ID)
            .srcSwitch(SWITCH_1)
            .destSwitch(SWITCH_2)
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
            .bandwidth(0)
            .segments(new ArrayList<>())
            .build();
    public static final double BURST_COEFFICIENT = 1.05;

    RuleManagerConfig config;

    @BeforeEach
    public void setup() {
        config = mock(RuleManagerConfig.class);
        when(config.getFlowMeterBurstCoefficient()).thenReturn(BURST_COEFFICIENT);
        when(config.getFlowMeterMinBurstSizeInKbits()).thenReturn(1024L);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build()
        );
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build()
        );
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVlanEncapsulationOuterVlanEqualTransitVlanTest() {
        Flow flow = buildFlow(PATH, TRANSIT_VLAN_ID, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(TRANSIT_VLAN_ID, FEATURES);
        Assertions.assertTrue(transformActions.isEmpty());
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction(), buildPushVxlan());
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsVxlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VXLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(buildPushVxlan());
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInSingleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, OUTER_VLAN_ID_2);
        SingleTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build()
        );
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchSingleVlanInFullPortOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(OUTER_VLAN_ID_1, FEATURES);
        List<Action> expectedActions = newArrayList(new PopVlanAction());
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInSingleVlanOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, OUTER_VLAN_ID_2);
        SingleTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build()
        );
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildTransformActionsOneSwitchFullPortInFullPortOutTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<Action> transformActions = generator.buildTransformActions(0, FEATURES);
        List<Action> expectedActions = newArrayList();
        Assertions.assertEquals(expectedActions, transformActions);
    }

    @Test
    public void buildMatchVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        Set<FieldMatch> match = generator.buildMatch(new FlowSourceAdapter(flow).getEndpoint(), FEATURES);
        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        assertEqualsMatch(expectedMatch, match);
    }

    @Test
    public void buildMatchVlanEncapsulationFullPortTest() {
        Flow flow = buildFlow(PATH, 0, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        Set<FieldMatch> match = generator.buildMatch(new FlowSourceAdapter(flow).getEndpoint(), FEATURES);
        Set<FieldMatch> expectedMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build()
        );
        assertEqualsMatch(expectedMatch, match);
    }

    @Test
    public void oneSwitchFlowFullPortRuleTest() {
        Flow flow = buildFlow(ONE_SWITCH_PATH, 0, OUTER_VLAN_ID_2);
        SingleTableIngressRuleGenerator generator = buildGenerator(ONE_SWITCH_PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        Assertions.assertEquals(1, commands.size());

        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build());
        List<Action> expectedIngressActions = newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2)));
        assertIngressCommand(ingressCommand, Priority.DEFAULT_FLOW_PRIORITY, expectedIngressMatch,
                expectedIngressActions, null);
    }

    @Test
    public void buildCommandsVlanEncapsulationSingleVlanTest() {
        Flow flow = buildFlow(PATH, OUTER_VLAN_ID_1, 0);
        SingleTableIngressRuleGenerator generator = buildGenerator(PATH, flow, VLAN_ENCAPSULATION);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        Assertions.assertEquals(2, commands.size());

        FlowSpeakerData ingressCommand = (FlowSpeakerData) commands.get(0);
        MeterSpeakerData meterCommand = (MeterSpeakerData) commands.get(1);
        Assertions.assertEquals(newArrayList(meterCommand.getUuid()), new ArrayList<>(ingressCommand.getDependsOn()));

        Set<FieldMatch> expectedIngressMatch = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(PORT_NUMBER_1).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build()
        );
        List<Action> expectedIngressActions = newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(TRANSIT_VLAN_ID).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_2))
        );
        assertIngressCommand(ingressCommand, Priority.FLOW_PRIORITY, expectedIngressMatch, expectedIngressActions,
                METER_ID);

        assertMeterCommand(meterCommand);
    }

    private void assertIngressCommand(
            FlowSpeakerData command, int expectedPriority, Set<FieldMatch> expectedMatch,
            List<Action> expectedApplyActions, MeterId expectedMeter) {
        Assertions.assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        Assertions.assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());

        Assertions.assertEquals(COOKIE, command.getCookie());
        Assertions.assertEquals(OfTable.INPUT, command.getTable());
        Assertions.assertEquals(expectedPriority, command.getPriority());
        assertEqualsMatch(expectedMatch, command.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(expectedApplyActions)
                .goToMeter(expectedMeter)
                .build();
        Assertions.assertEquals(expectedInstructions, command.getInstructions());
        Assertions.assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), command.getFlags());
    }

    private void assertMeterCommand(MeterSpeakerData command) {
        Assertions.assertEquals(SWITCH_1.getSwitchId(), command.getSwitchId());
        Assertions.assertEquals(SWITCH_1.getOfVersion(), command.getOfVersion().toString());
        Assertions.assertEquals(METER_ID, command.getMeterId());
        Assertions.assertEquals(Sets.newHashSet(MeterFlag.BURST, MeterFlag.KBPS, MeterFlag.STATS), command.getFlags());
        Assertions.assertEquals(BANDWIDTH, command.getRate());
        Assertions.assertEquals((long) (BANDWIDTH * BURST_COEFFICIENT), command.getBurst());
        Assertions.assertTrue(command.getDependsOn().isEmpty());

    }

    private SingleTableIngressRuleGenerator buildGenerator(
            FlowPath path, Flow flow, FlowTransitEncapsulation encapsulation) {
        return SingleTableIngressRuleGenerator.builder()
                .config(config)
                .flowPath(path)
                .flow(flow)
                .encapsulation(encapsulation)
                .build();
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
