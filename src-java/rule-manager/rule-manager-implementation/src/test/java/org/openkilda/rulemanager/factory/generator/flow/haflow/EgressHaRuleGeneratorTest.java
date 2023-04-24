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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EgressHaRuleGeneratorTest extends HaRuleGeneratorBaseTest {

    @Test
    public void buildVlanOuterInnerVlanEgressRuleTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, SHARED_FORWARD_COOKIE);
    }

    @Test
    public void buildVlanOuterVlanEgressRuleTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, 0);
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, FORWARD_COOKIE);
    }

    @Test
    public void buildVlanOuterVlanEqualsTransitEgressRuleTest() {
        FlowPath subPath = buildSubPath(VLAN_ENCAPSULATION.getId(), 0);
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, SHARED_FORWARD_COOKIE);
    }

    @Test
    public void buildVlanOuterInnerVlanEqualsTransitEgressRuleTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, VLAN_ENCAPSULATION.getId());
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, FORWARD_COOKIE);
    }

    @Test
    public void buildVlanFullPortEgressRuleTest() {
        FlowPath subPath = buildSubPath(0, 0);
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVlanAction(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, SHARED_FORWARD_COOKIE);
    }

    @Test
    public void buildVxlanOuterInnerVlanEgressRuleTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VXLAN_ENCAPSULATION, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_1).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VXLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, FORWARD_COOKIE);
    }

    @Test
    public void buildVxlanOuterVlanEgressRuleTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, 0);
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VXLAN_ENCAPSULATION, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_1).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VXLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, SHARED_FORWARD_COOKIE);
    }

    @Test
    public void buildVxlanFullPortEgressRuleTest() {
        FlowPath subPath = buildSubPath(0, 0);
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VXLAN_ENCAPSULATION, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW),
                new PortOutAction(new PortNumber(PORT_NUMBER_4))
        );
        assertEgressCommands(commands, VXLAN_ENCAPSULATION, SWITCH_2, expectedApplyActions, FORWARD_COOKIE);
    }

    @Test
    public void buildVlanOuterInnerVlanEgressReverseRuleTest() {
        FlowPath subPath = buildReversePath();
        EgressHaRuleGenerator generator = buildMeterlessGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        ArrayList<Action> expectedApplyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.VLAN_VID).value(INNER_VLAN_ID_2).build(),
                new PushVlanAction(),
                SetFieldAction.builder().field(Field.VLAN_VID).value(OUTER_VLAN_ID_2).build(),
                new PortOutAction(new PortNumber(PORT_NUMBER_1))
        );
        assertEgressCommands(commands, VLAN_ENCAPSULATION, SWITCH_1, expectedApplyActions, REVERSE_COOKIE);
    }

    @Test
    public void oneSwitchFlowEgressRuleTest() {
        FlowPath subPath = FlowPath.builder()
                .pathId(PATH_ID_1)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_1)
                .build();

        EgressHaRuleGenerator generator = EgressHaRuleGenerator.builder().subPath(subPath).build();
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void pathWithoutSegmentsFlowEgressRuleTest() {
        FlowPath path = FlowPath.builder()
                .pathId(PATH_ID_1)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_2)
                .segments(new ArrayList<>())
                .build();

        EgressHaRuleGenerator generator = EgressHaRuleGenerator.builder().subPath(path).build();
        assertEquals(0, generator.generateCommands(SWITCH_2).size());
    }

    @Test
    public void createSharedMeterTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        EgressHaRuleGenerator generator = buildGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, true, METER_ID,
                METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommand = getCommand(MeterSpeakerData.class, commands);

        assertEquals(METER_ID, flowCommand.getInstructions().getGoToMeter());
        assertEquals(Lists.newArrayList(METER_COMMAND_UUID), flowCommand.getDependsOn());

        assertEquals(METER_ID, meterCommand.getMeterId());
        assertEquals(METER_COMMAND_UUID, meterCommand.getUuid());
        assertEquals(MeteredRuleGenerator.FLOW_METER_STATS, meterCommand.getFlags());
        assertEquals(BANDWIDTH, meterCommand.getRate());
        assertEquals(Math.round(BANDWIDTH * BURST_COEFFICIENT), meterCommand.getBurst());
        assertEquals(0, meterCommand.getDependsOn().size());
        assertEquals(SWITCH_2.getSwitchId(), meterCommand.getSwitchId());
        assertEquals(SWITCH_2.getOfVersion(), meterCommand.getOfVersion().toString());
    }

    @Test
    public void dependsOnSharedMeterTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        EgressHaRuleGenerator generator = buildGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, true, METER_ID,
                METER_COMMAND_UUID, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);

        assertEquals(METER_ID, flowCommand.getInstructions().getGoToMeter());
        assertEquals(Lists.newArrayList(METER_COMMAND_UUID), flowCommand.getDependsOn());
    }

    @Test
    public void nullSharedMeterTest() {
        FlowPath subPath = buildSubPath(OUTER_VLAN_ID_1, INNER_VLAN_ID_1);
        EgressHaRuleGenerator generator = buildGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, true, null,
                METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        assertNull(flowCommand.getInstructions().getGoToMeter());
        assertTrue(flowCommand.getDependsOn().isEmpty());
    }

    @Test
    public void sharedMeterSwitchDoesntSupportMetersTest() {
        FlowPath subPath = buildReversePath();
        EgressHaRuleGenerator generator = buildGenerator(HA_FLOW, subPath, VLAN_ENCAPSULATION, true, METER_ID,
                METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        assertNull(flowCommand.getInstructions().getGoToMeter());
        assertTrue(flowCommand.getDependsOn().isEmpty());
    }

    private void assertEgressCommands(
            List<SpeakerData> commands, FlowTransitEncapsulation encapsulation, Switch expectedSwitch,
            List<Action> expectedApplyActions, FlowSegmentCookie expectedCookie) {
        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(expectedSwitch.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(expectedSwitch.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(expectedCookie, flowCommandData.getCookie());
        assertEquals(OfTable.EGRESS, flowCommandData.getTable());
        assertEquals(Priority.FLOW_PRIORITY, flowCommandData.getPriority());

        Set<FieldMatch> expectedMatch;
        if (encapsulation.getType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
            expectedMatch = buildExpectedTransitVlanMatch(PORT_NUMBER_3, encapsulation.getId());
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

    private EgressHaRuleGenerator buildMeterlessGenerator(
            HaFlow haFlow, FlowPath subPath, FlowTransitEncapsulation encapsulation, boolean isSharedPath) {
        return buildGenerator(haFlow, subPath, encapsulation, isSharedPath, null, null, false);
    }

    private EgressHaRuleGenerator buildGenerator(
            HaFlow haFlow, FlowPath subPath, FlowTransitEncapsulation encapsulation, boolean isSharedPath,
            MeterId sharedMeterId, UUID externalMeterCommandUuid, boolean generateCreateMeterCommand) {
        return EgressHaRuleGenerator.builder()
                .haFlow(haFlow)
                .subPath(subPath)
                .encapsulation(encapsulation)
                .isSharedPath(isSharedPath)
                .sharedMeterId(sharedMeterId)
                .externalMeterCommandUuid(externalMeterCommandUuid)
                .generateCreateMeterCommand(generateCreateMeterCommand)
                .config(config)
                .build();
    }

    private FlowPath buildReversePath() {
        FlowPath subPath = FlowPath.builder()
                .pathId(PATH_ID_1)
                .cookie(REVERSE_COOKIE)
                .srcSwitch(SWITCH_2)
                .destSwitch(SWITCH_1)
                .segments(Lists.newArrayList(PathSegment.builder()
                        .pathId(PATH_ID_1)
                        .srcPort(PORT_NUMBER_2)
                        .srcSwitch(SWITCH_2)
                        .destPort(PORT_NUMBER_3)
                        .destSwitch(SWITCH_1)
                        .build()))
                .build();
        subPath.setHaSubFlow(buildHaSubFlow(OUTER_VLAN_ID_1, INNER_VLAN_ID_1));
        return subPath;
    }
}
