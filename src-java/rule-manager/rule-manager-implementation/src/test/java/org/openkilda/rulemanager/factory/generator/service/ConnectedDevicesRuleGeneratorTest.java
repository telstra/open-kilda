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

package org.openkilda.rulemanager.factory.generator.service;

import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.PKTPS_FLAG;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.getCommand;

import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public abstract class ConnectedDevicesRuleGeneratorTest {

    protected RuleManagerConfig config;
    protected RuleGenerator generator;
    protected Set<SwitchFeature> expectedFeatures;
    protected Switch sw;

    protected Cookie cookie;
    protected OfTable table;
    protected int priority;

    @Test
    public void shouldBuildCorrectRuleWithMeterForOf13() {
        sw = buildSwitch("OF_13", expectedFeatures);
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertEquals(2, commands.size());
        commands.forEach(c -> Assertions.assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> Assertions.assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);

        Assertions.assertEquals(1, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));
        Assertions.assertTrue(meterCommandData.getDependsOn().isEmpty());

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        checkMatch(flowCommandData.getMatch());

        checkInstructions(flowCommandData.getInstructions(), meterCommandData.getMeterId());

        // Check meter command
        checkMeterCommand(meterCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterForOf15() {
        sw = buildSwitch("OF_15", expectedFeatures);
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertEquals(2, commands.size());
        commands.forEach(c -> Assertions.assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> Assertions.assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);

        Assertions.assertEquals(1, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));
        Assertions.assertTrue(meterCommandData.getDependsOn().isEmpty());

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        checkMatch(flowCommandData.getMatch());

        // Check correct meter instructions for OF 1.5
        checkInstructionsOf15(flowCommandData.getInstructions(), meterCommandData.getMeterId());

        // Check meter command
        checkMeterCommand(meterCommandData);
    }

    @Test
    public void shouldBuildCorrectRuleWithoutMeterForOf13() {
        expectedFeatures.remove(METERS);
        sw = buildSwitch("OF_13", expectedFeatures);
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertEquals(1, commands.size());
        commands.forEach(c -> Assertions.assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> Assertions.assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);

        Assertions.assertTrue(flowCommandData.getDependsOn().isEmpty());

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        checkMatch(flowCommandData.getMatch());

        checkInstructions(flowCommandData.getInstructions(), null);
    }

    @Test
    public void shouldBuildCorrectRuleWithMeterInBytesForOf13() {
        expectedFeatures.remove(PKTPS_FLAG);
        sw = buildSwitch("OF_13", expectedFeatures);
        List<SpeakerData> commands = generator.generateCommands(sw);

        Assertions.assertEquals(2, commands.size());
        commands.forEach(c -> Assertions.assertEquals(sw.getSwitchId(), c.getSwitchId()));
        commands.forEach(c -> Assertions.assertEquals(sw.getOfVersion(), c.getOfVersion().toString()));

        FlowSpeakerData flowCommandData = getCommand(FlowSpeakerData.class, commands);
        MeterSpeakerData meterCommandData = getCommand(MeterSpeakerData.class, commands);

        Assertions.assertEquals(1, flowCommandData.getDependsOn().size());
        Assertions.assertTrue(flowCommandData.getDependsOn().contains(meterCommandData.getUuid()));
        Assertions.assertTrue(meterCommandData.getDependsOn().isEmpty());

        // Check flow command
        checkFlowCommandBaseProperties(flowCommandData);

        checkMatch(flowCommandData.getMatch());

        checkInstructions(flowCommandData.getInstructions(), meterCommandData.getMeterId());

        // Check meter command
        checkMeterInBytesCommand(meterCommandData);
    }

    protected void checkFlowCommandBaseProperties(FlowSpeakerData flowCommandData) {
        Assertions.assertEquals(cookie, flowCommandData.getCookie());
        Assertions.assertEquals(table, flowCommandData.getTable());
        Assertions.assertEquals(priority, flowCommandData.getPriority());
    }

    protected abstract void checkMatch(Set<FieldMatch> match);

    protected void checkInstructions(Instructions instructions, MeterId meterId) {
        Assertions.assertEquals(1, instructions.getApplyActions().size());
        Action action = instructions.getApplyActions().get(0);
        Assertions.assertTrue(action instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) action;
        Assertions.assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());

        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertEquals(instructions.getGoToMeter(), meterId);
        Assertions.assertNull(instructions.getGoToTable());
    }

    protected void checkInstructionsOf15(Instructions instructions, MeterId meterId) {
        Assertions.assertEquals(2, instructions.getApplyActions().size());
        Action first = instructions.getApplyActions().get(0);
        Assertions.assertTrue(first instanceof PortOutAction);
        PortOutAction portOutAction = (PortOutAction) first;
        Assertions.assertEquals(SpecialPortType.CONTROLLER, portOutAction.getPortNumber().getPortType());

        Action second = instructions.getApplyActions().get(1);
        Assertions.assertTrue(second instanceof MeterAction);
        MeterAction meterAction = (MeterAction) second;
        Assertions.assertEquals(meterId, meterAction.getMeterId());

        Assertions.assertNull(instructions.getWriteActions());
        Assertions.assertNull(instructions.getGoToMeter());
        Assertions.assertNull(instructions.getGoToTable());
    }

    protected abstract void checkMeterCommand(MeterSpeakerData meterCommandData);

    protected abstract void checkMeterInBytesCommand(MeterSpeakerData meterCommandData);
}
