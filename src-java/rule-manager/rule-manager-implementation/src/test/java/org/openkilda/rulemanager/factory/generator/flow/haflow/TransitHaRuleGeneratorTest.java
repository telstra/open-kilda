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

import static org.openkilda.rulemanager.Utils.assertEqualsMatch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.MeterId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TransitHaRuleGeneratorTest extends HaRuleGeneratorBaseTest {
    public static final FlowPath SUB_PATH = buildSubPath(null);

    @Test
    public void buildVlanTransitRuleTest() {
        TransitHaRuleGenerator generator = buildMeterlessGenerator(VLAN_ENCAPSULATION, false);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertTransitCommands(commands, VLAN_ENCAPSULATION, FORWARD_COOKIE);
    }

    @Test
    public void buildVxlanTransitRuleTest() {
        TransitHaRuleGenerator generator = buildMeterlessGenerator(VXLAN_ENCAPSULATION, true);
        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertTransitCommands(commands, VXLAN_ENCAPSULATION, SHARED_FORWARD_COOKIE);
    }

    private void assertTransitCommands(
            List<SpeakerData> commands, FlowTransitEncapsulation expectedEncapsulation,
            FlowSegmentCookie expectedCookie) {
        Assertions.assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        Assertions.assertEquals(SWITCH_1.getSwitchId(), flowCommandData.getSwitchId());
        Assertions.assertEquals(SWITCH_1.getOfVersion(), flowCommandData.getOfVersion().toString());
        Assertions.assertTrue(flowCommandData.getDependsOn().isEmpty());

        Assertions.assertEquals(expectedCookie, flowCommandData.getCookie());
        Assertions.assertEquals(OfTable.TRANSIT, flowCommandData.getTable());
        Assertions.assertEquals(Priority.FLOW_PRIORITY, flowCommandData.getPriority());

        Set<FieldMatch> expectedMatch;
        if (expectedEncapsulation.getType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
            expectedMatch = buildExpectedTransitVlanMatch(PORT_NUMBER_1, expectedEncapsulation.getId());
        } else {
            expectedMatch = buildExpectedVxlanMatch(PORT_NUMBER_1, expectedEncapsulation.getId());
        }
        assertEqualsMatch(expectedMatch, flowCommandData.getMatch());

        Instructions expectedInstructions = Instructions.builder()
                .applyActions(Lists.newArrayList(new PortOutAction(new PortNumber(PORT_NUMBER_2))))
                .build();
        Assertions.assertEquals(expectedInstructions, flowCommandData.getInstructions());
        Assertions.assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), flowCommandData.getFlags());
    }

    @Test
    public void buildOneSwitchFlowTransitRuleTest() {
        FlowPath subPath = FlowPath.builder()
                .pathId(PATH_ID_1)
                .cookie(FORWARD_COOKIE)
                .srcSwitch(SWITCH_1)
                .destSwitch(SWITCH_1)
                .build();

        TransitHaRuleGenerator generator = TransitHaRuleGenerator.builder()
                .subPath(subPath)
                .inPort(PORT_NUMBER_1)
                .outPort(PORT_NUMBER_2)
                .encapsulation(VLAN_ENCAPSULATION)
                .build();
        Assertions.assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void createSharedMeterTest() {
        TransitHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION, true, METER_ID,
                METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        Assertions.assertEquals(2, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommand = getCommand(MeterSpeakerData.class, commands);

        Assertions.assertEquals(METER_ID, flowCommand.getInstructions().getGoToMeter());
        Assertions.assertEquals(Lists.newArrayList(METER_COMMAND_UUID), flowCommand.getDependsOn());

        Assertions.assertEquals(METER_ID, meterCommand.getMeterId());
        Assertions.assertEquals(METER_COMMAND_UUID, meterCommand.getUuid());
        Assertions.assertEquals(MeteredRuleGenerator.FLOW_METER_STATS, meterCommand.getFlags());
        Assertions.assertEquals(BANDWIDTH, meterCommand.getRate());
        Assertions.assertEquals(Math.round(BANDWIDTH * BURST_COEFFICIENT), meterCommand.getBurst());
        Assertions.assertEquals(0, meterCommand.getDependsOn().size());
        Assertions.assertEquals(SWITCH_2.getSwitchId(), meterCommand.getSwitchId());
        Assertions.assertEquals(SWITCH_2.getOfVersion(), meterCommand.getOfVersion().toString());
    }

    @Test
    public void dependsOnSharedMeterTest() {
        TransitHaRuleGenerator generator = buildGenerator(
                VLAN_ENCAPSULATION, true, METER_ID, METER_COMMAND_UUID, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        Assertions.assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);

        Assertions.assertEquals(METER_ID, flowCommand.getInstructions().getGoToMeter());
        Assertions.assertEquals(Lists.newArrayList(METER_COMMAND_UUID), flowCommand.getDependsOn());
    }

    @Test
    public void nullSharedMeterTest() {
        TransitHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION, true, null, METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        Assertions.assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        Assertions.assertNull(flowCommand.getInstructions().getGoToMeter());
        Assertions.assertTrue(flowCommand.getDependsOn().isEmpty());
    }

    @Test
    public void sharedMeterSwitchDoesntSupportMetersTest() {
        TransitHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION, true, METER_ID, METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        Assertions.assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        Assertions.assertNull(flowCommand.getInstructions().getGoToMeter());
        Assertions.assertTrue(flowCommand.getDependsOn().isEmpty());
    }

    private TransitHaRuleGenerator buildMeterlessGenerator(
            FlowTransitEncapsulation encapsulation, boolean sharedSegment) {
        return buildGenerator(encapsulation, sharedSegment, null, null, false);
    }

    private TransitHaRuleGenerator buildGenerator(
            FlowTransitEncapsulation encapsulation, boolean sharedSegment, MeterId sharedMeterId,
            UUID externalUuid, boolean generateMeter) {
        return TransitHaRuleGenerator.builder()
                .subPath(SUB_PATH)
                .inPort(PORT_NUMBER_1)
                .outPort(PORT_NUMBER_2)
                .encapsulation(encapsulation)
                .sharedSegment(sharedSegment)
                .sharedMeterId(sharedMeterId)
                .externalMeterCommandUuid(externalUuid)
                .generateCreateMeterCommand(generateMeter)
                .config(config)
                .build();
    }
}
