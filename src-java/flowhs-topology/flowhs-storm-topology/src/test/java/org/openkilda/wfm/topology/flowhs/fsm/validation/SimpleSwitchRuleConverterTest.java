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

package org.openkilda.wfm.topology.flowhs.fsm.validation;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.openkilda.rulemanager.action.ActionType.PUSH_VXLAN_NOVIFLOW;

import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.GroupId;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule.SimpleGroupBucket;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleSwitchRuleConverterTest {
    private static final SwitchId TEST_SWITCH_ID_A = new SwitchId(1);
    private static final SwitchId TEST_SWITCH_ID_B = new SwitchId(2);
    private static final SwitchId TEST_SWITCH_ID_C = new SwitchId(3);
    private static final SwitchId TEST_SWITCH_ID_D = new SwitchId(4);

    private static final String TEST_FLOW_ID_A = "test_flow_id_a";

    private static final long PACKET_COUNT = 7;
    private static final long BYTE_COUNT = 480;
    private static final int FLOW_A_SRC_PORT = 10;
    private static final int FLOW_A_DST_PORT = 20;
    private static final int FLOW_A_SEGMENT_A_SRC_PORT = 11;
    private static final int FLOW_A_SEGMENT_A_DST_PORT = 15;
    private static final int FLOW_A_SEGMENT_B_SRC_PORT = 16;
    private static final int FLOW_A_SEGMENT_B_DST_PORT = 19;
    private static final int FLOW_A_SRC_VLAN = 110;
    private static final int FLOW_A_ENCAP_ID = 120;
    private static final PathId FLOW_A_FORWARD_PATH_ID = new PathId(TEST_FLOW_ID_A + "_forward_path");
    private static final int FLOW_A_DST_VLAN = 140;
    private static final long FLOW_A_FORWARD_METER_ID = 32L;
    private static final FlowSegmentCookie FLOW_A_FORWARD_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1L);
    private static final long FLOW_A_FORWARD_COOKIE_VALUE = FLOW_A_FORWARD_COOKIE.getValue();
    private static final long FLOW_A_FORWARD_MIRROR_COOKIE_VALUE = FLOW_A_FORWARD_COOKIE
            .toBuilder().mirror(true).build().getValue();
    private static final long FLOW_A_BANDWIDTH = 10000;
    private static final int FLOW_GROUP_ID_A = 20;
    private static final int FLOW_GROUP_ID_A_OUT_PORT = 21;
    private static final int FLOW_GROUP_ID_A_OUT_VLAN = 22;
    private static final int FLOW_B_SRC_PORT = 1;
    private static final int FLOW_B_SRC_VLAN = 150;
    private static final int FLOW_B_DST_VLAN = 160;
    private static final FlowSegmentCookie FLOW_B_FORWARD_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 2L);
    private static final long FLOW_B_FORWARD_COOKIE_VALUE = FLOW_B_FORWARD_COOKIE.getValue();
    private static final long FLOW_B_FORWARD_MIRROR_COOKIE_VALUE = FLOW_B_FORWARD_COOKIE
            .toBuilder().mirror(true).build().getValue();
    private static final long FLOW_B_FORWARD_METER_ID = 34L;
    private static final long FLOW_B_BANDWIDTH = 11000;

    private static final long MIN_BURST_SIZE_IN_KBITS = 1024;
    private static final double BURST_COEFFICIENT = 1.05;

    private final SimpleSwitchRuleConverter simpleSwitchRuleConverter = new SimpleSwitchRuleConverter();

    @Test
    public void convertFlowEntriesTransitVlanFlowToSimpleSwitchRules() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan();

        List<FlowDumpResponse> flowDumpList = getSwitchFlowEntriesWithTransitVlan();
        List<MeterDumpResponse> meterDumpList = getMeterDumpResponses();

        for (int i = 0; i < flowDumpList.size(); i++) {
            List<SimpleSwitchRule> actualSimpleSwitchRules =
                    simpleSwitchRuleConverter
                            .convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                                    flowDumpList.get(i).getFlowSpeakerData(),
                                    meterDumpList.get(i).getMeterSpeakerData(),
                                    Collections.singletonList(GroupSpeakerData.builder().build()));
            assertThat(actualSimpleSwitchRules, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), actualSimpleSwitchRules.get(0));
        }
    }

    @Test
    public void convertFlowEntriesVxlanFlowToSimpleSwitchRules() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForVxlan();

        List<FlowDumpResponse> switchFlowEntries = getFlowDumpResponsesWithVxlan();
        List<MeterDumpResponse> switchMeterEntries = getMeterDumpResponses();

        for (int i = 0; i < switchFlowEntries.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                            switchFlowEntries.get(i).getFlowSpeakerData(),
                            switchMeterEntries.get(i).getMeterSpeakerData(),
                            Collections.singletonList(GroupSpeakerData.builder().build()));
            assertThat(switchRules, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void convertFlowEntriesOneSwitchFlowToSimpleSwitchRules() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForOneSwitchFlow();

        List<FlowDumpResponse> switchFlowEntries = getFlowDumpResponseOneSwitchFlow();

        List<SimpleSwitchRule> switchRules =
                simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                        switchFlowEntries.get(0).getFlowSpeakerData(),
                        getMeterDumpResponseOneSwitchFlow().getMeterSpeakerData(),
                        Collections.singletonList(GroupSpeakerData.builder().build()));

        assertEquals(expectedSwitchRules, switchRules);
    }

    @Test
    public void convertFlowEntriesTransitVlanFlowToSimpleSwitchRulesWithGroups() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan(true);

        List<FlowDumpResponse> flowDumpResponses = getFlowDumpResponsEntriesWithTransitVlan(true);
        List<MeterDumpResponse> meterDumpResponses = getMeterDumpResponses();
        List<GroupDumpResponse> groupDumpResponses =
                getGroupDumpResponsesTransitVlan(FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID);

        for (int i = 0; i < flowDumpResponses.size(); i++) {
            List<SimpleSwitchRule> actualList =
                    simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                            flowDumpResponses.get(i).getFlowSpeakerData(),
                            meterDumpResponses.get(0).getMeterSpeakerData(),
                            groupDumpResponses.get(0).getGroupSpeakerData());
            assertThat(actualList, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), actualList.get(0));
        }
    }

    @Test
    public void convertFlowEntriesVxlanFlowToSimpleSwitchRulesWithGroups() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForVxlan(true);

        List<FlowDumpResponse> flowDumpResponses = getFlowDumpResponsesWithVxlan(true);
        List<MeterDumpResponse> meterDumpResponses = getMeterDumpResponses();
        List<GroupDumpResponse> groupDumpResponses =
                getGroupDumpResponsesVxlan(FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID);

        for (int i = 0; i < flowDumpResponses.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                            flowDumpResponses.get(i).getFlowSpeakerData(),
                            meterDumpResponses.get(0).getMeterSpeakerData(),
                            groupDumpResponses.get(0).getGroupSpeakerData());
            assertThat(switchRules, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void convertFlowEntriesOneSwitchFlowToSimpleSwitchRulesWithGroups() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForOneSwitchFlow(true);

        List<FlowDumpResponse> flowDumpResponses = getSwitchFlowEntriesOneSwitchFlowWithGroup();
        List<GroupDumpResponse> groupDumpResponses = getGroupDumpResponsesTransitVlan(FLOW_B_SRC_PORT, 0);

        for (int i = 0; i < flowDumpResponses.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                            flowDumpResponses.get(i).getFlowSpeakerData(),
                            getMeterDumpResponseOneSwitchFlow().getMeterSpeakerData(),
                            groupDumpResponses.get(0).getGroupSpeakerData());

            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void convertSwitchFlowEntriesToSimpleSwitchRulesMultipleMeterAndGroupTest() {
        List<FlowSpeakerData> flowEntries = new ArrayList<>();
        List<MeterSpeakerData> meterEntries = new ArrayList<>();
        List<GroupSpeakerData> groupEntries = new ArrayList<>();

        for (int i = 1; i <= 100; i += 20) {
            long effectiveCookieId = i;
            int srcPort = i + 1;
            int srcVlan = i + 2;
            int segmentSrcPort = i + 3;
            int dstPort = i + 4;
            int dstVlan = i + 5;
            int segmentDstPort = i + 6;
            int encapsulationId = i + 7;
            int meterId = i + 8;
            int groupId = i + 9;
            int groupOutPort = i + 10;
            int groupOutVlan = i + 11;

            flowEntries.addAll(getFlowSpeakerDataWithTransitVlan(effectiveCookieId, TEST_SWITCH_ID_A, srcPort, srcVlan,
                    segmentSrcPort,
                    dstPort, dstVlan, segmentDstPort, encapsulationId, meterId, groupId));
            meterEntries.add(getMeterSpeakerData(meterId, TEST_SWITCH_ID_A, FLOW_A_BANDWIDTH));
            groupEntries.add(getGroupEntry(groupId, TEST_SWITCH_ID_A, srcPort, encapsulationId, groupOutPort,
                    groupOutVlan));

        }
        FlowDumpResponse flowDumpResponses = new FlowDumpResponse(flowEntries, TEST_SWITCH_ID_A);
        MeterDumpResponse meterDumpResponses = new MeterDumpResponse(meterEntries, TEST_SWITCH_ID_A);
        GroupDumpResponse groupDumpResponses = new GroupDumpResponse(groupEntries, TEST_SWITCH_ID_A);

        List<SimpleSwitchRule> switchRules =
                simpleSwitchRuleConverter.convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                        flowDumpResponses.getFlowSpeakerData(),
                        meterDumpResponses.getMeterSpeakerData(),
                        groupDumpResponses.getGroupSpeakerData());

        for (SimpleSwitchRule switchRule : switchRules) {
            FlowSegmentCookie cookie = new FlowSegmentCookie(switchRule.getCookie());
            int base = (int) cookie.getFlowEffectiveId();

            if (cookie.isMirror()) {
                assertEquals(base + 8, switchRule.getMeterId().intValue());
                assertEquals(base + 9, switchRule.getGroupId());
            } else if (cookie.getDirection() == FlowPathDirection.FORWARD) {
                assertEquals(base + 8, switchRule.getMeterId().intValue());
                assertEquals(0, switchRule.getGroupId());
            } else if (cookie.getDirection() == FlowPathDirection.REVERSE) {
                assertNull(switchRule.getMeterId());
                assertEquals(0, switchRule.getGroupId());
            } else {
                throw new AssertionError(String.format("Unknown rule %s", switchRule));
            }
        }
    }

    protected List<FlowDumpResponse> getSwitchFlowEntriesWithTransitVlan() {
        return getFlowDumpResponsEntriesWithTransitVlan(false);
    }

    protected List<FlowDumpResponse> getFlowDumpResponsEntriesWithTransitVlan(boolean checkWithGroup) {
        List<FlowDumpResponse> switchEntries = new ArrayList<>();

        switchEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_A,
                getFlowSpeakerData(FLOW_A_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_A, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0, getSetFieldAction(FLOW_A_ENCAP_ID),
                        FLOW_A_FORWARD_METER_ID, false, null)));

        if (checkWithGroup) {
            switchEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_A,
                    getFlowSpeakerData(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE, TEST_SWITCH_ID_A,
                            FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, null, 0,
                            null, FLOW_A_FORWARD_METER_ID, false,
                            (long) FLOW_GROUP_ID_A)));
        }

        switchEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_B,
                getFlowSpeakerData(FLOW_A_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_B, FLOW_A_SEGMENT_A_DST_PORT,
                        FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null, null,
                        false, null)));

        switchEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_C,
                getFlowSpeakerData(FLOW_A_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_C, FLOW_A_SEGMENT_B_DST_PORT,
                        FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getSetFieldAction(FLOW_A_DST_VLAN), null,
                        false, null)));

        return switchEntries;
    }

    protected List<FlowDumpResponse> getFlowDumpResponsesWithVxlan() {
        return getFlowDumpResponsesWithVxlan(false);
    }

    protected List<FlowDumpResponse> getFlowDumpResponsesWithVxlan(boolean checkWithGroup) {
        List<FlowDumpResponse> flowDumpEntries = new ArrayList<>();

        flowDumpEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_A,
                getFlowSpeakerData(FLOW_A_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_A, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), FLOW_A_ENCAP_ID, null,
                        FLOW_A_FORWARD_METER_ID, true, null)));

        if (checkWithGroup) {
            flowDumpEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_A,
                    getFlowSpeakerData(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE, TEST_SWITCH_ID_A, FLOW_A_SRC_PORT,
                            FLOW_A_SRC_VLAN, null, 0,
                            null, FLOW_A_FORWARD_METER_ID, true,
                            (long) FLOW_GROUP_ID_A)));
        }

        flowDumpEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_B,
                getFlowSpeakerData(FLOW_A_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_B, FLOW_A_SEGMENT_A_DST_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), FLOW_A_ENCAP_ID, null,
                        null, false, null)));

        flowDumpEntries.add(getFlowDumpResponse(TEST_SWITCH_ID_C,
                getFlowSpeakerData(FLOW_A_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_C, FLOW_A_SEGMENT_B_DST_PORT,
                        0, String.valueOf(FLOW_A_DST_PORT),
                        FLOW_A_ENCAP_ID, getSetFieldAction(FLOW_A_DST_VLAN), null,
                        false, null)));

        return flowDumpEntries;
    }

    private List<MeterDumpResponse> getMeterDumpResponses() {
        List<MeterDumpResponse> meterDumpResponses = new ArrayList<>();
        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterSpeakerData(Collections.singletonList(getMeterSpeakerData(FLOW_A_FORWARD_METER_ID,
                        TEST_SWITCH_ID_A, FLOW_A_BANDWIDTH)))
                .build());

        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_B)
                .meterSpeakerData(Collections.emptyList())
                .build());

        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_C)
                .meterSpeakerData(Collections.emptyList())
                .build());

        return meterDumpResponses;
    }

    private MeterSpeakerData getMeterSpeakerData(long meterId, SwitchId switchId, long bandwidth) {
        return MeterSpeakerData.builder()
                .meterId(new MeterId(meterId))
                .switchId(switchId)
                .rate(bandwidth)
                .burst(Meter
                        .calculateBurstSize(bandwidth, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                        .collect(Collectors.toSet()))
                .build();
    }

    private List<FlowDumpResponse> getFlowDumpResponseOneSwitchFlow() {
        return Lists.newArrayList(getFlowDumpResponse(TEST_SWITCH_ID_D,
                getFlowSpeakerData(FLOW_B_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_D, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN,
                        "in_port", 0,
                        getSetFieldAction(FLOW_B_DST_VLAN), FLOW_B_FORWARD_METER_ID,
                        false, null)));
    }

    private List<FlowDumpResponse> getSwitchFlowEntriesOneSwitchFlowWithGroup() {
        return Lists.newArrayList(
                getFlowDumpResponse(TEST_SWITCH_ID_D,
                        getFlowSpeakerData(FLOW_B_FORWARD_COOKIE_VALUE, TEST_SWITCH_ID_D, FLOW_B_SRC_PORT,
                                FLOW_B_SRC_VLAN, "in_port", 0,
                                getSetFieldAction(FLOW_B_DST_VLAN), FLOW_B_FORWARD_METER_ID,
                                false, null)),

                getFlowDumpResponse(TEST_SWITCH_ID_D,
                        getFlowSpeakerData(FLOW_B_FORWARD_MIRROR_COOKIE_VALUE, TEST_SWITCH_ID_D, FLOW_B_SRC_PORT,
                                FLOW_B_SRC_VLAN, null, 0,
                                getSetFieldAction(FLOW_B_DST_VLAN), FLOW_B_FORWARD_METER_ID, false,
                                (long) FLOW_GROUP_ID_A)));
    }

    private MeterDumpResponse getMeterDumpResponseOneSwitchFlow() {
        List<MeterSpeakerData> meterSpeakerData = new ArrayList<>();
        meterSpeakerData.add(getMeterSpeakerData(FLOW_B_FORWARD_METER_ID, TEST_SWITCH_ID_D, FLOW_B_BANDWIDTH));

        return MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_D)
                .meterSpeakerData(meterSpeakerData)
                .build();
    }

    private FlowDumpResponse getFlowDumpResponse(SwitchId switchId, FlowSpeakerData... speakerData) {
        return getFlowDumpResponse(switchId, Lists.newArrayList(speakerData));
    }

    private FlowDumpResponse getFlowDumpResponse(SwitchId switchId, List<FlowSpeakerData> flowEntries) {
        return FlowDumpResponse.builder()
                .switchId(flowEntries.stream().findFirst().map(SpeakerData::getSwitchId).orElse(null))
                .flowSpeakerData(flowEntries)
                .build();
    }


    protected List<FlowSpeakerData> getFlowSpeakerDataWithTransitVlan(
            long effectiveCookieId, SwitchId switchId, int srcPort, int srcVlan, int segmentSrcPort,
            int dstPort, int dstVlan,
            int segmentDstPort, int encapsulationId, long meterId, int groupId) {
        List<FlowSpeakerData> flowEntries = new ArrayList<>();

        FlowSegmentCookie forwardCookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, effectiveCookieId);
        FlowSegmentCookie reverseCookie = new FlowSegmentCookie(FlowPathDirection.REVERSE, effectiveCookieId);
        flowEntries.add(
                getFlowSpeakerData(forwardCookie.getValue(), switchId, srcPort, srcVlan,
                        String.valueOf(segmentSrcPort), 0, getSetFieldAction(encapsulationId),
                        meterId, false, null));

        flowEntries.add(
                getFlowSpeakerData(forwardCookie.toBuilder().mirror(true).build().getValue(), switchId,
                        srcPort, srcVlan,
                        null, 0, null,
                        meterId, false, (long) groupId));

        flowEntries.add(
                getFlowSpeakerData(reverseCookie.getValue(), switchId, segmentDstPort, encapsulationId,
                        String.valueOf(dstPort), 0, getSetFieldAction(dstVlan),
                        null, false, null));

        return flowEntries;
    }

    private FlowSpeakerData getFlowSpeakerData(long cookie, SwitchId switchId, int srcPort, int srcVlan,
                                               String dstPort, int tunnelId,
                                               SetFieldAction flowSetFieldAction, Long meterId,
                                               boolean tunnelIdIngressRule, Long group) {

        Set<FieldMatch> fieldMatchSet
                = Sets.newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(srcPort).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(srcVlan).build());
        if (!tunnelIdIngressRule) {
            fieldMatchSet.add(FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(tunnelId).build());
        }

        List<Action> actions = new ArrayList<>();
        if (StringUtils.isNotBlank(dstPort)) {
            PortNumber portNumber = NumberUtils.isParsable(dstPort)
                    ? new PortNumber(NumberUtils.toInt(dstPort)) :
                    new PortNumber(SpecialPortType.valueOf(dstPort.toUpperCase()));
            actions.add(new PortOutAction(portNumber));
        }
        if (group != null) {
            actions.add(new GroupAction(new GroupId(group)));
        }

        if (flowSetFieldAction != null) {
            actions.add(flowSetFieldAction);
        }
        if (tunnelIdIngressRule) {
            actions.add(PushVxlanAction.builder().vni(tunnelId).type(PUSH_VXLAN_NOVIFLOW).build());
        }


        return FlowSpeakerData.builder()
                .cookie(new Cookie(cookie))
                .switchId(switchId)
                .packetCount(PACKET_COUNT)
                .byteCount(BYTE_COUNT)
                .ofVersion(OfVersion.OF_13)
                .match(fieldMatchSet)
                .instructions(Instructions.builder()
                        .applyActions(actions)
                        .goToMeter(meterId == null ? null : new MeterId(meterId))
                        .build())
                .build();
    }


    private SetFieldAction getSetFieldAction(int dstVlan) {
        return SetFieldAction.builder()
                .field(Field.VLAN_VID)
                .value(dstVlan)
                .build();
    }

    private List<SimpleSwitchRule> getSimpleSwitchRuleForTransitVlan() {
        return getSimpleSwitchRuleForTransitVlan(false);
    }

    private List<SimpleSwitchRule> getSimpleSwitchRuleForTransitVlan(boolean checkWithGroup) {
        List<SimpleSwitchRule> simpleSwitchRules = new ArrayList<>();
        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_A)
                .cookie(FLOW_A_FORWARD_COOKIE_VALUE)
                .inPort(FLOW_A_SRC_PORT)
                .version(OfVersion.OF_13.name())
                .outPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .inVlan(FLOW_A_SRC_VLAN)
                .outVlan(Collections.singletonList(FLOW_A_ENCAP_ID))
                .meterId(FLOW_A_FORWARD_METER_ID)
                .pktCount(PACKET_COUNT)
                .byteCount(BYTE_COUNT)
                .meterRate(FLOW_A_BANDWIDTH)
                .meterBurstSize(Meter
                        .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT,
                                ""))
                .meterFlags(new String[]{"BURST", "KBPS", "STATS"})
                .build();
        simpleSwitchRules.add(rule);
        if (checkWithGroup) {
            simpleSwitchRules.add(rule.toBuilder()
                    .outPort(0)
                    .outVlan(Collections.emptyList())
                    .cookie(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE)
                    .groupId(FLOW_GROUP_ID_A)
                    .groupBuckets(
                            Lists.newArrayList(
                                    new SimpleGroupBucket(FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID, 0),
                                    new SimpleGroupBucket(FLOW_GROUP_ID_A_OUT_PORT, FLOW_GROUP_ID_A_OUT_VLAN, 0)))
                    .build());
        }
        simpleSwitchRules.add(SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_B)
                .cookie(FLOW_A_FORWARD_COOKIE_VALUE)
                .inPort(FLOW_A_SEGMENT_A_DST_PORT)
                .outPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .inVlan(FLOW_A_ENCAP_ID)
                .build());
        simpleSwitchRules.add(SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_C)
                .cookie(FLOW_A_FORWARD_COOKIE_VALUE)
                .inPort(FLOW_A_SEGMENT_B_DST_PORT)
                .outPort(FLOW_A_DST_PORT)
                .inVlan(FLOW_A_ENCAP_ID)
                .outVlan(Collections.singletonList(FLOW_A_DST_VLAN))
                .build());
        return simpleSwitchRules;
    }

    private List<SimpleSwitchRule> getSimpleSwitchRuleForVxlan() {
        return getSimpleSwitchRuleForVxlan(false);
    }

    private List<SimpleSwitchRule> getSimpleSwitchRuleForVxlan(boolean checkWithGroup) {
        List<SimpleSwitchRule> simpleSwitchRules = new ArrayList<>();
        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_A)
                .cookie(FLOW_A_FORWARD_COOKIE_VALUE)
                .inPort(FLOW_A_SRC_PORT)
                .outPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .inVlan(FLOW_A_SRC_VLAN)
                .tunnelId(FLOW_A_ENCAP_ID)
                .meterId(FLOW_A_FORWARD_METER_ID)
                .meterRate(FLOW_A_BANDWIDTH)
                .meterBurstSize(Meter
                        .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT,
                                ""))
                .meterFlags(new String[]{"BURST", "KBPS", "STATS"})
                .build();
        simpleSwitchRules.add(rule);
        if (checkWithGroup) {
            simpleSwitchRules.add(rule.toBuilder()
                    .outPort(0)
                    .tunnelId(0)
                    .cookie(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE)
                    .groupId(FLOW_GROUP_ID_A)
                    .groupBuckets(
                            Lists.newArrayList(
                                    new SimpleGroupBucket(FLOW_A_SEGMENT_A_SRC_PORT, 0, FLOW_A_ENCAP_ID),
                                    new SimpleGroupBucket(FLOW_GROUP_ID_A_OUT_PORT, FLOW_GROUP_ID_A_OUT_VLAN, 0)))
                    .build());
        }
        simpleSwitchRules.add(SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_B)
                .cookie(FLOW_A_FORWARD_COOKIE_VALUE)
                .inPort(FLOW_A_SEGMENT_A_DST_PORT)
                .outPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .tunnelId(FLOW_A_ENCAP_ID)
                .build());
        simpleSwitchRules.add(SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_C)
                .cookie(FLOW_A_FORWARD_COOKIE_VALUE)
                .inPort(FLOW_A_SEGMENT_B_DST_PORT)
                .outPort(FLOW_A_DST_PORT)
                .tunnelId(FLOW_A_ENCAP_ID)
                .outVlan(Collections.singletonList(FLOW_A_DST_VLAN))
                .build());
        return simpleSwitchRules;
    }

    private List<SimpleSwitchRule> getSimpleSwitchRuleForOneSwitchFlow() {
        return getSimpleSwitchRuleForOneSwitchFlow(false);
    }

    private List<SimpleSwitchRule> getSimpleSwitchRuleForOneSwitchFlow(boolean checkWithGroup) {
        List<SimpleSwitchRule> simpleSwitchRules = new ArrayList<>();
        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_D)
                .cookie(FLOW_B_FORWARD_COOKIE_VALUE)
                .inPort(FLOW_B_SRC_PORT)
                .outPort(FLOW_B_SRC_PORT)
                .inVlan(FLOW_B_SRC_VLAN)
                .outVlan(Collections.singletonList(FLOW_B_DST_VLAN))
                .meterId(FLOW_B_FORWARD_METER_ID)
                .meterRate(FLOW_B_BANDWIDTH)
                .meterBurstSize(Meter
                        .calculateBurstSize(FLOW_B_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT,
                                ""))
                .meterFlags(new String[]{"BURST", "KBPS", "STATS"})
                .build();
        simpleSwitchRules.add(rule);

        if (checkWithGroup) {
            simpleSwitchRules.add(rule.toBuilder()
                    .outPort(0)
                    .cookie(FLOW_B_FORWARD_MIRROR_COOKIE_VALUE)
                    .groupId(FLOW_GROUP_ID_A)
                    .groupBuckets(
                            Lists.newArrayList(new SimpleGroupBucket(FLOW_B_SRC_PORT, 0, 0),
                                    new SimpleGroupBucket(FLOW_GROUP_ID_A_OUT_PORT, FLOW_GROUP_ID_A_OUT_VLAN, 0)))
                    .build());
        }

        return simpleSwitchRules;
    }

    private List<GroupDumpResponse> getGroupDumpResponsesTransitVlan(int mainPort, int transitVlan) {
        return Collections.singletonList(GroupDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_A)
                .groupSpeakerData(Lists.newArrayList(getGroupSpeakerData(
                        FLOW_GROUP_ID_A, mainPort, transitVlan, FLOW_GROUP_ID_A_OUT_PORT, FLOW_GROUP_ID_A_OUT_VLAN)))
                .build());
    }

    private GroupSpeakerData getGroupSpeakerData(int groupId, int mainPort, int transitVlan,
                                                 int groupOutPort, int groupOutVlan) {

        return GroupSpeakerData.builder()
                .groupId(new GroupId(groupId))
                .buckets(Lists.newArrayList(
                        Bucket.builder().watchPort(WatchPort.ANY).writeActions(
                                Sets.newHashSet(new PortOutAction(new PortNumber(groupOutPort)),
                                        getSetFieldAction(groupOutVlan))
                        ).build(),
                        Bucket.builder().watchPort(WatchPort.ANY).writeActions(
                                Sets.newHashSet(
                                        new PortOutAction(new PortNumber(mainPort)), getSetFieldAction(transitVlan))
                        ).build()))
                .build();
    }

    private GroupSpeakerData getGroupEntry(int groupId, SwitchId switchId, int mainPort, int transitVlan,
                                           int groupOutPort, int groupOutVlan) {
        return GroupSpeakerData.builder()
                .groupId(new GroupId(groupId))
                .switchId(switchId)
                .buckets(Lists.newArrayList(
                        Bucket.builder()
                                .watchPort(WatchPort.ANY)
                                .writeActions(Sets.newHashSet(
                                        new PortOutAction(new PortNumber(groupOutPort)),
                                        getSetFieldAction(groupOutVlan)))
                                .build(),
                        Bucket.builder()
                                .watchPort(WatchPort.ANY)
                                .writeActions(Sets.newHashSet(
                                        new PortOutAction(new PortNumber(mainPort)),
                                        getSetFieldAction(transitVlan)))
                                .build()
                ))
                .build();
    }

    private List<GroupDumpResponse> getGroupDumpResponsesVxlan(int mainPort, int vni) {
        return Collections.singletonList(GroupDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_A)
                .groupSpeakerData(Lists.newArrayList(GroupSpeakerData.builder()
                        .groupId(new GroupId(FLOW_GROUP_ID_A))
                        .buckets(Lists.newArrayList(
                                Bucket.builder()
                                        .watchPort(WatchPort.ANY)
                                        .writeActions(Sets.newHashSet(
                                                new PortOutAction(new PortNumber(FLOW_GROUP_ID_A_OUT_PORT)),
                                                getSetFieldAction(FLOW_GROUP_ID_A_OUT_VLAN))).build(),
                                Bucket.builder()
                                        .watchGroup(WatchGroup.ANY)
                                        .writeActions(Sets.newHashSet(
                                                new PortOutAction(new PortNumber(mainPort)),
                                                PushVxlanAction.builder().vni(vni).type(PUSH_VXLAN_NOVIFLOW).build()))
                                        .build()))
                        .build())).build());
    }
}
