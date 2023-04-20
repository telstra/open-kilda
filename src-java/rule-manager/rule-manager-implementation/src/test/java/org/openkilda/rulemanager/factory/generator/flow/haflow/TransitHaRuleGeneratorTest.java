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
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TransitHaRuleGeneratorTest extends HaRuleGeneratorBaseTest {
    public static final FlowPath SUB_PATH = buildSubPath(OUTER_VLAN_ID_1, INNER_VLAN_ID_2);

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
        assertEquals(1, commands.size());

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        assertEquals(SWITCH_1.getSwitchId(), flowCommandData.getSwitchId());
        assertEquals(SWITCH_1.getOfVersion(), flowCommandData.getOfVersion().toString());
        assertTrue(flowCommandData.getDependsOn().isEmpty());

        assertEquals(expectedCookie, flowCommandData.getCookie());
        assertEquals(OfTable.TRANSIT, flowCommandData.getTable());
        assertEquals(Priority.FLOW_PRIORITY, flowCommandData.getPriority());

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
        assertEquals(expectedInstructions, flowCommandData.getInstructions());
        assertEquals(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS), flowCommandData.getFlags());
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
        assertEquals(0, generator.generateCommands(SWITCH_1).size());
    }

    @Test
    public void createSharedMeterTest() {
        TransitHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION, true, METER_ID,
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
        TransitHaRuleGenerator generator = buildGenerator(
                VLAN_ENCAPSULATION, true, METER_ID, METER_COMMAND_UUID, false);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);

        assertEquals(METER_ID, flowCommand.getInstructions().getGoToMeter());
        assertEquals(Lists.newArrayList(METER_COMMAND_UUID), flowCommand.getDependsOn());
    }

    @Test
    public void nullSharedMeterTest() {
        TransitHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION, true, null, METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_2);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        assertNull(flowCommand.getInstructions().getGoToMeter());
        assertTrue(flowCommand.getDependsOn().isEmpty());
    }

    @Test
    public void sharedMeterSwitchDoesntSupportMetersTest() {
        TransitHaRuleGenerator generator = buildGenerator(VLAN_ENCAPSULATION, true, METER_ID, METER_COMMAND_UUID, true);

        List<SpeakerData> commands = generator.generateCommands(SWITCH_1);
        assertEquals(1, commands.size());
        FlowSpeakerData flowCommand = getCommand(FlowSpeakerData.class, commands);
        assertNull(flowCommand.getInstructions().getGoToMeter());
        assertTrue(flowCommand.getDependsOn().isEmpty());
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
