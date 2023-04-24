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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.model.SwitchFeature.RESET_COUNTS_FLAG;
import static org.openkilda.rulemanager.OfTable.EGRESS;
import static org.openkilda.rulemanager.OfTable.INGRESS;
import static org.openkilda.rulemanager.OfTable.INPUT;
import static org.openkilda.rulemanager.OfTable.TRANSIT;
import static org.openkilda.rulemanager.Utils.buildSwitch;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSubType;
import org.openkilda.rulemanager.adapter.InMemoryDataAdapter;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RuleManagerHaFlowRulesTest {

    public static final String HA_FLOW_ID = "ha_flow_id";
    public static final String SUB_FLOW_1 = "sub_flow_1";
    public static final String SUB_FLOW_2 = "sub_flow_2";
    public static final PathId PATH_ID_1 = new PathId("path_id_1");
    public static final PathId PATH_ID_2 = new PathId("path_id_2");
    public static final PathId PATH_ID_3 = new PathId("path_id_3");
    public static final PathId PATH_ID_4 = new PathId("path_id_4");
    public static final PathId PATH_ID_5 = new PathId("path_id_5");
    public static final PathId PATH_ID_6 = new PathId("path_id_6");
    public static final MeterId SHARED_POINT_METER_ID = new MeterId(1);
    public static final MeterId Y_POINT_METER_ID = new MeterId(2);
    public static final MeterId SUB_FLOW_1_METER_ID = new MeterId(3);
    public static final MeterId SUB_FLOW_2_METER_ID = new MeterId(4);
    public static final GroupId GROUP_ID = new GroupId(5);
    public static final Set<SwitchFeature> FEATURES = Sets.newHashSet(
            RESET_COUNTS_FLAG, METERS, NOVIFLOW_PUSH_POP_VXLAN);
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    public static final SwitchId SWITCH_ID_4 = new SwitchId(4);
    public static final SwitchId SWITCH_ID_5 = new SwitchId(5);
    public static final SwitchId SWITCH_ID_6 = new SwitchId(6);
    public static final SwitchId SWITCH_ID_7 = new SwitchId(7);
    public static final Switch SWITCH_1 = buildSwitch(SWITCH_ID_1, FEATURES);
    public static final Switch SWITCH_2 = buildSwitch(SWITCH_ID_2, FEATURES);
    public static final Switch SWITCH_3 = buildSwitch(SWITCH_ID_3, FEATURES);
    public static final Switch SWITCH_4 = buildSwitch(SWITCH_ID_4, FEATURES);
    public static final Switch SWITCH_5 = buildSwitch(SWITCH_ID_5, FEATURES);
    public static final Switch SWITCH_6 = buildSwitch(SWITCH_ID_6, FEATURES);
    public static final Switch SWITCH_7 = buildSwitch(SWITCH_ID_7, FEATURES);

    public static final FlowTransitEncapsulation VLAN_ENCAPSULATION = new FlowTransitEncapsulation(
            14, FlowEncapsulationType.TRANSIT_VLAN);

    public static final FlowSegmentCookie FORWARD_COOKIE = FlowSegmentCookie.builder()
            .direction(FlowPathDirection.FORWARD).flowEffectiveId(1).subType(FlowSubType.SHARED).build();
    public static final FlowSegmentCookie REVERSE_COOKIE = FlowSegmentCookie.builder()
            .direction(FlowPathDirection.REVERSE).flowEffectiveId(1).subType(FlowSubType.SHARED).build();

    public static final FlowSegmentCookie FORWARD_SUB_COOKIE_1 = FORWARD_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_1).build();
    public static final FlowSegmentCookie REVERSE_SUB_COOKIE_1 = REVERSE_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_1).build();
    public static final FlowSegmentCookie FORWARD_SUB_COOKIE_2 = FORWARD_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_2).build();
    public static final FlowSegmentCookie REVERSE_SUB_COOKIE_2 = REVERSE_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_2).build();

    private RuleManagerImpl ruleManager;

    @Before
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

        assertEquals(7, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        assertEquals(3, forwardCommands.get(SWITCH_ID_1).size());
        assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        assertEquals(2, forwardCommands.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), TRANSIT);

        assertEquals(1, forwardCommands.get(SWITCH_ID_3).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), EGRESS);

        assertEquals(1, forwardCommands.get(SWITCH_ID_4).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_4)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_4), EGRESS);
    }

    @Test
    public void buildYShapedHaFlowReverseCommands() {
        HaFlow haFlow = buildYShapedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        assertEquals(10, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_2).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT, TRANSIT);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_3).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_4).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), INPUT, INGRESS);
    }

    @Test
    public void buildLongYShapedHaFlowForwardCommands() {
        HaFlow haFlow = buildLongYShapedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);
        assertEquals(10, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(forwardSpeakerData);
        assertEquals(3, switchCommandMap.get(SWITCH_ID_1).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), INPUT, INGRESS);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        assertEquals(2, switchCommandMap.get(SWITCH_ID_3).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        assertEquals(1, getGroupCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), TRANSIT);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_4).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), TRANSIT);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_5).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_5)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_5), EGRESS);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_6).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_6)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_6), TRANSIT);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_7).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_7)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_7), EGRESS);
    }

    @Test
    public void buildLongYShapedHaFlowReverseCommands() {
        HaFlow haFlow = buildLongYShapedHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);
        assertEquals(13, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_3).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), TRANSIT, TRANSIT);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_4).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), TRANSIT);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_5).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_5)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_5)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_5), INPUT, INGRESS);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_6).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_6)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_6), TRANSIT);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_7).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_7)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_7)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_7), INPUT, INGRESS);
    }


    @Test
    public void buildISharedDifferentLengthHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedDifferentLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        assertEquals(7, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        assertEquals(3, forwardCommands.get(SWITCH_ID_1).size());
        assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        assertEquals(1, forwardCommands.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), TRANSIT);

        assertEquals(2, forwardCommands.get(SWITCH_ID_3).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), TRANSIT);

        assertEquals(1, forwardCommands.get(SWITCH_ID_4).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_4)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_4), EGRESS);
    }

    @Test
    public void buildIShapedDifferentLengthHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedDifferentLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        assertEquals(9, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        assertEquals(4, switchCommandMap.get(SWITCH_ID_3).size());
        assertEquals(3, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS, TRANSIT);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_4).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_4)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_4)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_4), INPUT, INGRESS);
    }

    @Test
    public void buildIShapedEqualLengthHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedEqualLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        assertEquals(6, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        assertEquals(3, forwardCommands.get(SWITCH_ID_1).size());
        assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        assertEquals(1, forwardCommands.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), TRANSIT);

        assertEquals(2, forwardCommands.get(SWITCH_ID_3).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), EGRESS);
    }

    @Test
    public void buildIShapedEqualLengthHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedEqualLengthHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        assertEquals(7, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        assertEquals(1, switchCommandMap.get(SWITCH_ID_1).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        assertEquals(5, switchCommandMap.get(SWITCH_ID_3).size());
        assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS, INPUT, INGRESS);
    }

    @Test
    public void buildIShapedEqualLengthDifferentIslHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedEqualLengthDifferentIslsHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        assertEquals(6, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        assertEquals(4, forwardCommands.get(SWITCH_ID_1).size());
        assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        assertEquals(2, forwardCommands.get(SWITCH_ID_2).size());
        assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), EGRESS, EGRESS);
    }

    @Test
    public void buildIShapedEqualLengthDifferentIslHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedEqualLengthDifferentIslsHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        assertEquals(9, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        assertEquals(3, switchCommandMap.get(SWITCH_ID_1).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), EGRESS, EGRESS);

        assertEquals(6, switchCommandMap.get(SWITCH_ID_2).size());
        assertEquals(4, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertEquals(2, getMeterCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), INPUT, INGRESS, INPUT, INGRESS);
    }

    @Test
    public void buildIShapedOneSwitchHaFlowForwardCommands() {
        HaFlow haFlow = buildIShapedOneSwitchHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> forwardSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getForwardPath(), false, adapter);

        assertEquals(6, forwardSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> forwardCommands = groupBySwitchId(forwardSpeakerData);
        assertEquals(4, forwardCommands.get(SWITCH_ID_1).size());
        assertEquals(2, getFlowCount(forwardCommands.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(forwardCommands.get(SWITCH_ID_1)));
        assertEquals(1, getGroupCount(forwardCommands.get(SWITCH_ID_1)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_1), INPUT, INGRESS);

        assertEquals(1, forwardCommands.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_2)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_2), TRANSIT);

        assertEquals(1, forwardCommands.get(SWITCH_ID_3).size());
        assertEquals(1, getFlowCount(forwardCommands.get(SWITCH_ID_3)));
        assertFlowTables(forwardCommands.get(SWITCH_ID_3), EGRESS);
    }

    @Test
    public void buildIShapedOneSwitchHaFlowReverseCommands() {
        HaFlow haFlow = buildIShapedOneSwitchHaFlow();
        DataAdapter adapter = buildAdapter(haFlow);
        List<SpeakerData> reverseSpeakerData = ruleManager.buildRulesHaFlowPath(
                haFlow.getReversePath(), false, adapter);

        assertEquals(8, reverseSpeakerData.size());

        Map<SwitchId, List<SpeakerData>> switchCommandMap = groupBySwitchId(reverseSpeakerData);
        assertEquals(4, switchCommandMap.get(SWITCH_ID_1).size());
        assertEquals(3, getFlowCount(switchCommandMap.get(SWITCH_ID_1)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_1)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_1), INPUT, INGRESS, EGRESS);

        assertEquals(1, switchCommandMap.get(SWITCH_ID_2).size());
        assertEquals(1, getFlowCount(switchCommandMap.get(SWITCH_ID_2)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_2), TRANSIT);

        assertEquals(3, switchCommandMap.get(SWITCH_ID_3).size());
        assertEquals(2, getFlowCount(switchCommandMap.get(SWITCH_ID_3)));
        assertEquals(1, getMeterCount(switchCommandMap.get(SWITCH_ID_3)));
        assertFlowTables(switchCommandMap.get(SWITCH_ID_3), INPUT, INGRESS);
    }

    private HaFlow buildYShapedHaFlow() {
        // HA-flow       3
        //              /
        //      1------2
        //              \
        //               4

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_3, SWITCH_4);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_4);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_2);
        return haFlow;
    }

    private HaFlow buildLongYShapedHaFlow() {
        // HA-flow             4-----5
        //                    /
        //      1------2-----3
        //                    \
        //                     6-----7

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_5, SWITCH_7);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3, SWITCH_4, SWITCH_5);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3, SWITCH_6, SWITCH_7);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_3);
        return haFlow;
    }

    private HaFlow buildIShapedDifferentLengthHaFlow() {
        // HA-flow             4
        //                    /
        //      1------2-----3
        //                   ^
        //                   Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_3, SWITCH_4);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3, SWITCH_4);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_3);
        return haFlow;
    }

    private HaFlow buildIShapedEqualLengthHaFlow() {
        // HA-flow
        //
        //      1------2-----3
        //                   ^
        //                   Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_3, SWITCH_3);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2, SWITCH_3);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_3);
        return haFlow;
    }

    private HaFlow buildIShapedEqualLengthDifferentIslsHaFlow() {
        // HA-flow
        //
        //      1======2
        //      ^
        //      Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_2, SWITCH_2);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1, SWITCH_2);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_1);
        return haFlow;
    }

    private HaFlow buildIShapedOneSwitchHaFlow() {
        // HA-flow
        //
        //      1------2-----3
        //      ^
        //  Y-point

        HaFlow haFlow = buildHaFlow(SWITCH_1, SWITCH_1, SWITCH_3);
        HaSubFlow subFlow1 = haFlow.getHaSubFlow(SUB_FLOW_1).get();
        HaSubFlow subFlow2 = haFlow.getHaSubFlow(SUB_FLOW_2).get();
        FlowPath[] subPaths1 = buildSubPathPair(PATH_ID_1, PATH_ID_2, FORWARD_SUB_COOKIE_1, REVERSE_SUB_COOKIE_1,
                subFlow1, SWITCH_1);
        FlowPath[] subPaths2 = buildSubPathPair(PATH_ID_3, PATH_ID_4, FORWARD_SUB_COOKIE_2, REVERSE_SUB_COOKIE_2,
                subFlow2, SWITCH_1, SWITCH_2, SWITCH_3);
        setMainPaths(haFlow, PATH_ID_5, PATH_ID_6, subPaths1, subPaths2);
        setYPoint(haFlow, SWITCH_ID_1);
        return haFlow;
    }

    private static void setYPoint(HaFlow haFlow, SwitchId switchId3) {
        haFlow.getForwardPath().setYPointSwitchId(switchId3);
        haFlow.getReversePath().setYPointSwitchId(switchId3);
    }

    private void setMainPaths(HaFlow haFlow, PathId forwardId, PathId reverseId,
                              FlowPath[] firstSubPaths, FlowPath[] secondSubPaths) {
        firstSubPaths[1].setMeterId(SUB_FLOW_1_METER_ID);
        secondSubPaths[1].setMeterId(SUB_FLOW_2_METER_ID);

        HaFlowPath forwardHaPath = HaFlowPath.builder()
                .cookie(FORWARD_COOKIE)
                .sharedPointMeterId(SHARED_POINT_METER_ID)
                .yPointMeterId(null)
                .yPointGroupId(GROUP_ID)
                .haPathId(forwardId)
                .sharedSwitch(haFlow.getSharedSwitch())
                .build();
        forwardHaPath.setHaSubFlows(haFlow.getHaSubFlows());
        forwardHaPath.setSubPaths(Lists.newArrayList(firstSubPaths[0], secondSubPaths[0]));
        haFlow.setForwardPath(forwardHaPath);

        HaFlowPath reverseHaPath = HaFlowPath.builder()
                .cookie(REVERSE_COOKIE)
                .sharedPointMeterId(null)
                .yPointMeterId(Y_POINT_METER_ID)
                .yPointGroupId(null)
                .haPathId(reverseId)
                .sharedSwitch(haFlow.getSharedSwitch())
                .build();
        reverseHaPath.setHaSubFlows(haFlow.getHaSubFlows());
        reverseHaPath.setSubPaths(Lists.newArrayList(firstSubPaths[1], secondSubPaths[1]));
        haFlow.setReversePath(reverseHaPath);
    }

    private FlowPath[] buildSubPathPair(
            PathId forwardId, PathId reverseId, FlowSegmentCookie forwardCookie, FlowSegmentCookie reverseCookie,
            HaSubFlow haSubFlow, Switch... switches) {
        Switch[] reverseSwitches = Arrays.copyOf(switches, switches.length);
        ArrayUtils.reverse(reverseSwitches);
        return new FlowPath[]{
                buildSubPath(forwardId, haSubFlow, forwardCookie, switches),
                buildSubPath(reverseId, haSubFlow, reverseCookie, reverseSwitches)
        };
    }

    private HaFlow buildHaFlow(Switch sharedSwitch, Switch endpointSwitch1, Switch endpointSwitch2) {
        HaFlow haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID)
                .sharedSwitch(sharedSwitch)
                .build();
        haFlow.setHaSubFlows(Lists.newArrayList(
                buildHaSubFlow(endpointSwitch1, SUB_FLOW_1), buildHaSubFlow(endpointSwitch2, SUB_FLOW_2)));
        return haFlow;
    }

    private HaSubFlow buildHaSubFlow(Switch sw, String subFlowId) {
        return HaSubFlow.builder()
                .haSubFlowId(subFlowId)
                .endpointSwitch(sw)
                .build();
    }

    private FlowPath buildSubPath(PathId pathId, HaSubFlow haSubFlow, FlowSegmentCookie cookie, Switch... switches) {
        FlowPath subPath = FlowPath.builder()
                .cookie(cookie)
                .pathId(pathId)
                .srcSwitch(switches[0])
                .destSwitch(switches[switches.length - 1])
                .build();
        List<PathSegment> segments = new ArrayList<>();
        for (int i = 1; i < switches.length; i++) {
            segments.add(PathSegment.builder()
                    .pathId(pathId)
                    .srcSwitch(switches[i - 1])
                    .destSwitch(switches[i])
                    .build());
        }
        subPath.setSegments(segments);
        subPath.setHaSubFlow(haSubFlow);
        return subPath;
    }

    private DataAdapter buildAdapter(HaFlow haFlow) {
        List<FlowPath> subPaths = haFlow.getPaths().stream().flatMap(path -> path.getSubPaths().stream())
                .collect(Collectors.toList());

        Set<Switch> switches = Sets.newHashSet(haFlow.getSharedSwitch());
        haFlow.getHaSubFlows().stream().map(HaSubFlow::getEndpointSwitch).forEach(switches::add);
        subPaths.stream()
                .flatMap(path -> path.getSegments().stream())
                .flatMap(segment -> Stream.of(segment.getSrcSwitch(), segment.getDestSwitch()))
                .forEach(switches::add);

        Map<PathId, HaFlow> haFlowMap = haFlow.getPaths().stream()
                .collect(Collectors.toMap(HaFlowPath::getHaPathId, HaFlowPath::getHaFlow));
        for (FlowPath subPath : subPaths) {
            haFlowMap.put(subPath.getPathId(), subPath.getHaFlowPath().getHaFlow());
        }

        Map<PathId, FlowTransitEncapsulation> encapsulationMap = new HashMap<>();
        for (HaFlowPath haFlowPath : haFlow.getPaths()) {
            encapsulationMap.put(haFlowPath.getHaPathId(), VLAN_ENCAPSULATION);
        }

        return InMemoryDataAdapter.builder()
                .commonFlowPaths(new HashMap<>())
                .haFlowSubPaths(subPaths.stream().collect(toMap(FlowPath::getPathId, identity())))
                .transitEncapsulations(encapsulationMap)
                .switches(switches.stream().collect(Collectors.toMap(Switch::getSwitchId, identity())))
                .haFlowMap(haFlowMap)
                .haFlowPathMap(haFlow.getPaths().stream().collect(toMap(HaFlowPath::getHaPathId, identity())))
                .build();
    }


    private void assertFlowTables(Collection<SpeakerData> commands, OfTable... expectedTables) {
        List<OfTable> actualTables = commands.stream()
                .filter(FlowSpeakerData.class::isInstance)
                .map(FlowSpeakerData.class::cast)
                .map(FlowSpeakerData::getTable)
                .sorted()
                .collect(Collectors.toList());

        Arrays.sort(expectedTables);
        assertEquals(Arrays.asList(expectedTables), actualTables);
    }

    private int getFlowCount(Collection<SpeakerData> commands) {
        return getCommandCount(commands, FlowSpeakerData.class);
    }

    private int getMeterCount(Collection<SpeakerData> commands) {
        return getCommandCount(commands, MeterSpeakerData.class);
    }

    private int getGroupCount(Collection<SpeakerData> commands) {
        return getCommandCount(commands, GroupSpeakerData.class);
    }

    private int getCommandCount(Collection<SpeakerData> commands, Class<? extends SpeakerData> clazz) {
        return (int) commands.stream().filter(clazz::isInstance).count();
    }

    private Map<SwitchId, List<SpeakerData>> groupBySwitchId(Collection<SpeakerData> commands) {
        return commands.stream().collect(Collectors.groupingBy(SpeakerData::getSwitchId));
    }
}
