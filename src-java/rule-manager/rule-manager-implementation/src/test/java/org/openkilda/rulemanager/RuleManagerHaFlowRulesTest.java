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

package org.openkilda.rulemanager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.rulemanager.OfTable.EGRESS;
import static org.openkilda.rulemanager.OfTable.INGRESS;
import static org.openkilda.rulemanager.OfTable.INPUT;
import static org.openkilda.rulemanager.OfTable.PRE_INGRESS;
import static org.openkilda.rulemanager.OfTable.TRANSIT;

import org.openkilda.model.HaFlow;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.factory.generator.flow.haflow.HaFlowRulesBaseTest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class RuleManagerHaFlowRulesTest extends HaFlowRulesBaseTest {


    private RuleManagerImpl ruleManager;

    @BeforeEach
    public void setup() {
        RuleManagerConfig config = mock(RuleManagerConfig.class);
        when(config.getBroadcastRateLimit()).thenReturn(200);
        when(config.getSystemMeterBurstSizeInPackets()).thenReturn(4096L);
        when(config.getDiscoPacketSize()).thenReturn(250);
        when(config.getFlowPingMagicSrcMacAddress()).thenReturn("00:26:E1:FF:FF:FE");
        when(config.getDiscoveryBcastPacketDst()).thenReturn("00:26:E1:FF:FF:FF");
        ruleManager = new RuleManagerImpl(config);
    }

    @Test
    public void buildYShapedHaFlowForwardCommands() {
        HaFlow haFlow = buildYShapedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        Assertions.assertEquals(9, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        Assertions.assertEquals(3, forwardCommands.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        Assertions.assertEquals(4, forwardCommands.get(SWITCH_ID_2).size());
        Assertions.assertEquals(3, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), INPUT, INPUT, TRANSIT);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_3).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), EGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_4).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_4)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_4), EGRESS);
    }

    @Test
    public void buildYShapedHaFlowReverseCommands() {
        HaFlow haFlow = buildYShapedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        Assertions.assertEquals(10, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT, TRANSIT);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_4).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), INPUT, INGRESS);
    }

    @Test
    public void buildLongYShapedHaFlowForwardCommands() {
        HaFlow haFlow = buildLongYShapedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);
        Assertions.assertEquals(12, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(forwardSpeakerData);
        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), INPUT, INGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(4, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(3, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getGroupCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INPUT, TRANSIT);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_4).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), TRANSIT);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_5).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_5)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_5), EGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_6).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_6)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_6), TRANSIT);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_7).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_7)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_7), EGRESS);
    }

    @Test
    public void buildLongYShapedHaFlowReverseCommands() {
        HaFlow haFlow = buildLongYShapedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);
        Assertions.assertEquals(13, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), TRANSIT, TRANSIT);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_4).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), TRANSIT);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_5).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_5)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_5)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_5), INPUT, INGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_6).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_6)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_6), TRANSIT);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_7).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_7)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_7)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_7), INPUT, INGRESS);
    }

    @Test
    public void buildISharedDifferentLengthHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedDifferentLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        Assertions.assertEquals(9, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        Assertions.assertEquals(3, forwardCommands.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(4, forwardCommands.get(SWITCH_ID_3).size());
        Assertions.assertEquals(3, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), INPUT, INPUT, TRANSIT);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_4).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_4)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_4), EGRESS);
    }

    @Test
    public void buildIShapedDifferentLengthHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedDifferentLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        Assertions.assertEquals(9, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(4, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(3, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS, TRANSIT);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_4).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), INPUT, INGRESS);
    }

    @Test
    public void buildIShapedDifferentLengthHaFlowReverseIngressOnlyCommands() {
        HaFlow haFlow = buildIShapedDifferentLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), true, false, true, false, adapter);

        Assertions.assertEquals(5, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(2, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_4).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), INPUT, INGRESS);
    }

    @Test
    public void buildIShapedDifferentLengthHaFlowReverseNonIngressOnlyCommands() {
        HaFlow haFlow = buildIShapedDifferentLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), true, false, false, true, adapter);

        Assertions.assertEquals(4, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(2, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), TRANSIT);
    }

    @Test
    public void buildIShapedEqualLengthHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedEqualLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        Assertions.assertEquals(6, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        Assertions.assertEquals(3, forwardCommands.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(2, forwardCommands.get(SWITCH_ID_3).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), EGRESS);
    }

    @Test
    public void buildIShapedEqualLengthHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedEqualLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        Assertions.assertEquals(7, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(5, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS, INPUT, INGRESS);
    }

    @Test
    public void buildIShapedEqualLengthHaFlowReverseIngressOnlyCommands() {
        HaFlow haFlow = buildIShapedEqualLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), true, false, true, false, adapter);

        Assertions.assertEquals(5, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(5, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS, INPUT, INGRESS);
    }

    @Test
    public void buildIShapedEqualLengthHaFlowReverseNonIngressOnlyCommands() {
        HaFlow haFlow = buildIShapedEqualLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), true, false, false, true, adapter);

        Assertions.assertEquals(2, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);
    }

    @Test
    public void buildIShapedEqualLengthDifferentIslHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedEqualLengthDifferentIslsHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        Assertions.assertEquals(6, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        Assertions.assertEquals(4, forwardCommands.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        Assertions.assertEquals(2, forwardCommands.get(SWITCH_ID_2).size());
        Assertions.assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), EGRESS, EGRESS);
    }

    @Test
    public void buildIShapedEqualLengthDifferentIslHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedEqualLengthDifferentIslsHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        Assertions.assertEquals(9, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS, EGRESS);

        Assertions.assertEquals(6, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        Assertions.assertEquals(2, getMeterCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), INPUT, INGRESS, INPUT, INGRESS);
    }

    @Test
    public void buildIShapedOneSwitchHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedOneSwitchHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        Assertions.assertEquals(6, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        Assertions.assertEquals(4, forwardCommands.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_3).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));

        assertFlowTables(forwardCommands.get(SWITCH_ID_3), EGRESS);
    }

    @Test
    public void buildIShapedOneSwitchHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedOneSwitchHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        Assertions.assertEquals(8, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(4, switchCommandMap.get(SWITCH_ID_1).size());
        Assertions.assertEquals(3, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), INPUT, INGRESS, EGRESS);

        Assertions.assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_3).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS);
    }

    @Test
    public void buildIShapedOneSwitchHaFlowServer42ForwardCommands() {
        HaFlow haFlow = buildIShapedOneSwitchHaFlowServer42();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        Assertions.assertEquals(9, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        Assertions.assertEquals(8, forwardCommands.get(SWITCH_ID_8_SERVER42).size());
        Assertions.assertEquals(6, getFlowCount(forwardCommands.get(SWITCH_ID_8_SERVER42)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_8_SERVER42)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_8_SERVER42)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_8_SERVER42), INPUT, INPUT, PRE_INGRESS, PRE_INGRESS,
                INGRESS, INGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_9_SERVER42).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_9_SERVER42)));

        assertFlowTables(forwardCommands.get(SWITCH_ID_9_SERVER42), EGRESS);
    }

    @Test
    public void buildIShapedOneSwitchHaFlowServer42ReverseCommands() {
        HaFlow haFlow = buildIShapedOneSwitchHaFlowServer42();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        Assertions.assertEquals(9, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(4, switchCommandMap.get(SWITCH_ID_8_SERVER42).size());
        Assertions.assertEquals(3, getFlowCount(switchCommandMap.get(SWITCH_ID_8_SERVER42)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_8_SERVER42)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_8_SERVER42), INPUT, INGRESS, EGRESS);

        Assertions.assertEquals(5, switchCommandMap.get(SWITCH_ID_9_SERVER42).size());
        Assertions.assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_9_SERVER42)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_9_SERVER42)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_9_SERVER42), INPUT, INPUT, INGRESS, INGRESS);
    }

    @Test
    public void buildYShapedYEqualsSharedHaFlowHaFlowForwardCommands() {
        HaFlow haFlow = buildYShapedYEqualsSharedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);
        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);

        Assertions.assertEquals(6, forwardSpeakerData.size());

        Assertions.assertEquals(4, forwardCommands.get(SWITCH_ID_1).size());
        Assertions.assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_1)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_2).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), EGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_3).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), EGRESS);
    }

    @Test
    public void buildYShapedYEqualsSharedHaFlowServer42HaFlowForwardCommands() {
        HaFlow haFlow = buildYShapedYEqualsSharedHaFlowServer42();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);

        Assertions.assertEquals(12, forwardSpeakerData.size());

        Assertions.assertEquals(10, forwardCommands.get(SWITCH_ID_8_SERVER42).size());
        Assertions.assertEquals(8, getFlowCount(forwardCommands.get(SWITCH_ID_8_SERVER42)));
        Assertions.assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_8_SERVER42)));
        Assertions.assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_8_SERVER42)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_8_SERVER42), INPUT, INPUT, INPUT, PRE_INGRESS,
                PRE_INGRESS, INGRESS, INGRESS, INGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_9_SERVER42).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_9_SERVER42)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_9_SERVER42), EGRESS);

        Assertions.assertEquals(1, forwardCommands.get(SWITCH_ID_10_SERVER42).size());
        Assertions.assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_10_SERVER42)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_10_SERVER42), EGRESS);
    }

    @Test
    public void buildYShapedHaFlowReverseServer42Commands() {
        HaFlow haFlow = buildYShapedYEqualsSharedHaFlowServer42();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        Assertions.assertEquals(13, reverseSpeakerData.size());

        Assertions.assertEquals(3, switchCommandMap.get(SWITCH_ID_8_SERVER42).size());
        Assertions.assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_8_SERVER42)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_8_SERVER42)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_8_SERVER42), EGRESS, EGRESS);

        Assertions.assertEquals(5, switchCommandMap.get(SWITCH_ID_9_SERVER42).size());
        Assertions.assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_9_SERVER42)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_9_SERVER42)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_9_SERVER42), INPUT, INPUT, INGRESS, INGRESS);

        Assertions.assertEquals(5, switchCommandMap.get(SWITCH_ID_10_SERVER42).size());
        Assertions.assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_10_SERVER42)));
        Assertions.assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_10_SERVER42)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_10_SERVER42), INPUT, INPUT, INGRESS, INGRESS);
    }

}
