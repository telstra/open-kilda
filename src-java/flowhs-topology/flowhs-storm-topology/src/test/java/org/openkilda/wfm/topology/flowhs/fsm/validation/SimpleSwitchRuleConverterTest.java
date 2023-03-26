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
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.GroupBucket;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.Vxlan;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule.SimpleGroupBucket;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimpleSwitchRuleConverterTest {
    private static final SwitchId TEST_SWITCH_ID_A = new SwitchId(1);
    private static final SwitchId TEST_SWITCH_ID_B = new SwitchId(2);
    private static final SwitchId TEST_SWITCH_ID_C = new SwitchId(3);
    private static final SwitchId TEST_SWITCH_ID_D = new SwitchId(4);

    private static final String TEST_FLOW_ID_A = "test_flow_id_a";
    private static final String TEST_FLOW_ID_B = "test_flow_id_b";

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

    private SimpleSwitchRuleConverter simpleSwitchRuleConverter = new SimpleSwitchRuleConverter();

    @Test
    public void shouldConvertFlowPathWithTransitVlanEncapToSimpleSwitchRules() {
        Flow flow = buildFlow(FlowEncapsulationType.TRANSIT_VLAN);
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan();

        List<SimpleSwitchRule> switchRules = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow,
                flow.getForwardPath(),
                TransitVlan.builder()
                        .flowId(TEST_FLOW_ID_A)
                        .pathId(FLOW_A_FORWARD_PATH_ID)
                        .vlan(FLOW_A_ENCAP_ID)
                        .build(),
                MIN_BURST_SIZE_IN_KBITS,
                BURST_COEFFICIENT);

        assertEquals(expectedSwitchRules, switchRules);
    }

    @Test
    public void shouldConvertFlowPathWithTransitVlanEncapToSimpleSwitchRulesWithGroup() {
        Flow flow = buildFlow(FlowEncapsulationType.TRANSIT_VLAN, true);
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan(true);

        List<SimpleSwitchRule> switchRules = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow,
                flow.getForwardPath(),
                TransitVlan.builder()
                        .flowId(TEST_FLOW_ID_A)
                        .pathId(FLOW_A_FORWARD_PATH_ID)
                        .vlan(FLOW_A_ENCAP_ID)
                        .build(),
                MIN_BURST_SIZE_IN_KBITS,
                BURST_COEFFICIENT);

        assertEquals(expectedSwitchRules, switchRules);
    }

    @Test
    public void shouldConvertFlowPathWithVxlanEncapToSimpleSwitchRulesWithGroup() {
        Flow flow = buildFlow(FlowEncapsulationType.VXLAN, true);
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForVxlan(true);

        List<SimpleSwitchRule> switchRules = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow,
                flow.getForwardPath(),
                Vxlan.builder()
                        .flowId(TEST_FLOW_ID_A)
                        .pathId(FLOW_A_FORWARD_PATH_ID)
                        .vni(FLOW_A_ENCAP_ID)
                        .build(),
                MIN_BURST_SIZE_IN_KBITS,
                BURST_COEFFICIENT);

        assertEquals(expectedSwitchRules, switchRules);
    }

    @Test
    public void shouldConvertLoopedFlowPathWithTransitVlanEncapToSimpleSwitchRules() {
        Flow flow = buildFlow(FlowEncapsulationType.TRANSIT_VLAN);
        flow.setLoopSwitchId(flow.getSrcSwitchId());
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan();
        expectedSwitchRules.add(SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_A)
                .cookie(new FlowSegmentCookie(FLOW_A_FORWARD_COOKIE_VALUE).toBuilder().looped(true).build().getValue())
                .inPort(FLOW_A_SRC_PORT)
                .outPort(FLOW_A_SRC_PORT)
                .inVlan(FLOW_A_SRC_VLAN)
                .build());

        List<SimpleSwitchRule> switchRules = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow,
                flow.getForwardPath(),
                TransitVlan.builder()
                        .flowId(TEST_FLOW_ID_A)
                        .pathId(FLOW_A_FORWARD_PATH_ID)
                        .vlan(FLOW_A_ENCAP_ID)
                        .build(),
                MIN_BURST_SIZE_IN_KBITS,
                BURST_COEFFICIENT);

        assertEquals(expectedSwitchRules.size(), switchRules.size());
        assertTrue(expectedSwitchRules.containsAll(switchRules));
    }

    @Test
    public void shouldConvertReverseLoopedFlowPathWithTransitVlanEncapToSimpleSwitchRules() {
        Flow flow = buildFlow(FlowEncapsulationType.TRANSIT_VLAN);
        flow.setLoopSwitchId(flow.getDestSwitchId());
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan();
        expectedSwitchRules.add(SimpleSwitchRule.builder()
                .switchId(TEST_SWITCH_ID_C)
                .cookie(new FlowSegmentCookie(FLOW_A_FORWARD_COOKIE_VALUE).toBuilder()
                        .looped(true).build().getValue())
                .inPort(FLOW_A_SEGMENT_B_DST_PORT)
                .outPort(FLOW_A_SEGMENT_B_DST_PORT)
                .inVlan(FLOW_A_ENCAP_ID)
                .build());

        List<SimpleSwitchRule> switchRules = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow,
                flow.getForwardPath(),
                TransitVlan.builder()
                        .flowId(TEST_FLOW_ID_A)
                        .pathId(FLOW_A_FORWARD_PATH_ID)
                        .vlan(FLOW_A_ENCAP_ID)
                        .build(),
                MIN_BURST_SIZE_IN_KBITS,
                BURST_COEFFICIENT);

        assertEquals(expectedSwitchRules.size(), switchRules.size());
        assertTrue(expectedSwitchRules.containsAll(switchRules));
    }

    @Test
    public void convertFlowWithIngressVlanIdMatchesTransitVlanId() {
        Flow flow = buildFlow(FlowEncapsulationType.TRANSIT_VLAN);
        Assert.assertNotEquals(0, flow.getSrcVlan());
        Assert.assertNotNull(flow.getForwardPath());

        TransitVlan encapsulation = TransitVlan.builder()
                .flowId(flow.getFlowId())
                .pathId(flow.getForwardPathId())
                .vlan(flow.getSrcVlan())
                .build();

        List<SimpleSwitchRule> pathView = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(
                flow, flow.getForwardPath(), encapsulation, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);
        Assert.assertFalse(pathView.isEmpty());

        SimpleSwitchRule ingress = pathView.get(0);
        assertEquals(Collections.emptyList(), ingress.getOutVlan());
    }

    @Test
    public void shouldConvertFlowPathWithVxlanEncapToSimpleSwitchRules() {
        Flow flow = buildFlow(FlowEncapsulationType.VXLAN);
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForVxlan();

        List<SimpleSwitchRule> switchRules = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow,
                flow.getForwardPath(),
                Vxlan.builder()
                        .flowId(TEST_FLOW_ID_A)
                        .pathId(FLOW_A_FORWARD_PATH_ID)
                        .vni(FLOW_A_ENCAP_ID)
                        .build(),
                MIN_BURST_SIZE_IN_KBITS,
                BURST_COEFFICIENT);

        assertEquals(expectedSwitchRules, switchRules);
    }

    @Test
    public void shouldConvertFlowPathOneSwitchFlowToSimpleSwitchRules() {
        Flow flow = buildOneSwitchPortFlow();
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForOneSwitchFlow();

        List<SimpleSwitchRule> switchRules = simpleSwitchRuleConverter.convertFlowPathToSimpleSwitchRules(flow,
                flow.getForwardPath(), null, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);

        assertEquals(expectedSwitchRules, switchRules);
    }

    @Test
    public void shouldConvertFlowEntriesTransitVlanFlowToSimpleSwitchRules() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan();

        List<SwitchFlowEntries> switchFlowEntries = getSwitchFlowEntriesWithTransitVlan();
        List<SwitchMeterEntries> switchMeterEntries = getSwitchMeterEntries();

        for (int i = 0; i < switchFlowEntries.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(switchFlowEntries.get(i),
                            switchMeterEntries.get(i), SwitchGroupEntries.builder().build());
            assertThat(switchRules, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void shouldConvertFlowEntriesVxlanFlowToSimpleSwitchRules() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForVxlan();

        List<SwitchFlowEntries> switchFlowEntries = getSwitchFlowEntriesWithVxlan();
        List<SwitchMeterEntries> switchMeterEntries = getSwitchMeterEntries();

        for (int i = 0; i < switchFlowEntries.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(switchFlowEntries.get(i),
                            switchMeterEntries.get(i), SwitchGroupEntries.builder().build());
            assertThat(switchRules, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void shouldConvertFlowEntriesOneSwitchFlowToSimpleSwitchRules() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForOneSwitchFlow();

        List<SwitchFlowEntries> switchFlowEntries = getSwitchFlowEntriesOneSwitchFlow();

        List<SimpleSwitchRule> switchRules =
                simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(switchFlowEntries.get(0),
                        getSwitchMeterEntriesOneSwitchFlow(), SwitchGroupEntries.builder().build());

        assertEquals(expectedSwitchRules, switchRules);
    }

    @Test
    public void shouldConvertFlowEntriesTransitVlanFlowToSimpleSwitchRulesWithGroups() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForTransitVlan(true);

        List<SwitchFlowEntries> switchFlowEntries = getSwitchFlowEntriesWithTransitVlan(true);
        List<SwitchMeterEntries> switchMeterEntries = getSwitchMeterEntries();
        List<SwitchGroupEntries> switchGroupEntries =
                getSwitchGroupEntriesTransitVlan(FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID);

        for (int i = 0; i < switchFlowEntries.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(switchFlowEntries.get(i),
                            switchMeterEntries.get(0), switchGroupEntries.get(0));
            assertThat(switchRules, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void shouldConvertFlowEntriesVxlanFlowToSimpleSwitchRulesWithGroups() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForVxlan(true);

        List<SwitchFlowEntries> switchFlowEntries = getSwitchFlowEntriesWithVxlan(true);
        List<SwitchMeterEntries> switchMeterEntries = getSwitchMeterEntries();
        List<SwitchGroupEntries> switchGroupEntries =
                getSwitchGroupEntriesVxlan(FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID);

        for (int i = 0; i < switchFlowEntries.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(switchFlowEntries.get(i),
                            switchMeterEntries.get(0), switchGroupEntries.get(0));
            assertThat(switchRules, hasSize(1));
            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void shouldConvertFlowEntriesOneSwitchFlowToSimpleSwitchRulesWithGroups() {
        List<SimpleSwitchRule> expectedSwitchRules = getSimpleSwitchRuleForOneSwitchFlow(true);

        List<SwitchFlowEntries> switchFlowEntries = getSwitchFlowEntriesOneSwitchFlowWithGroup();
        List<SwitchGroupEntries> switchGroupEntries = getSwitchGroupEntriesTransitVlan(FLOW_B_SRC_PORT, 0);

        for (int i = 0; i < switchFlowEntries.size(); i++) {
            List<SimpleSwitchRule> switchRules =
                    simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(switchFlowEntries.get(i),
                            getSwitchMeterEntriesOneSwitchFlow(), switchGroupEntries.get(0));

            assertEquals(expectedSwitchRules.get(i), switchRules.get(0));
        }
    }

    @Test
    public void convertSwitchFlowEntriesToSimpleSwitchRulesMultipleMeterAndGroupTest() {
        List<FlowEntry> flowEntries = new ArrayList<>();
        List<MeterEntry> meterEntries = new ArrayList<>();
        List<GroupEntry> groupEntries = new ArrayList<>();

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

            flowEntries.addAll(getFlowEntriesWithTransitVlan(effectiveCookieId, srcPort, srcVlan, segmentSrcPort,
                    dstPort, dstVlan, segmentDstPort, encapsulationId, meterId, groupId));
            meterEntries.add(getMeterEntry(meterId, FLOW_A_BANDWIDTH));
            groupEntries.add(getGroupEntry(groupId, srcPort, encapsulationId, groupOutPort, groupOutVlan));

        }
        SwitchFlowEntries switchFlowEntries = new SwitchFlowEntries(TEST_SWITCH_ID_A, flowEntries);
        SwitchMeterEntries switchMeterEntries = new SwitchMeterEntries(TEST_SWITCH_ID_A, meterEntries);
        SwitchGroupEntries switchGroupEntries = new SwitchGroupEntries(TEST_SWITCH_ID_A, groupEntries);

        List<SimpleSwitchRule> switchRules =
                simpleSwitchRuleConverter.convertSwitchFlowEntriesToSimpleSwitchRules(
                        switchFlowEntries, switchMeterEntries, switchGroupEntries);

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

    private Flow buildFlow(FlowEncapsulationType flowEncapsulationType) {
        return buildFlow(flowEncapsulationType, false);
    }

    private Flow buildFlow(FlowEncapsulationType flowEncapsulationType, boolean checkWithGroup) {
        Switch switchA = Switch.builder().switchId(TEST_SWITCH_ID_A).description("").build();
        Switch switchB = Switch.builder().switchId(TEST_SWITCH_ID_B).description("").build();
        Switch switchC = Switch.builder().switchId(TEST_SWITCH_ID_C).description("").build();

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_A)
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SRC_PORT)
                .srcVlan(FLOW_A_SRC_VLAN)
                .destSwitch(switchC)
                .destPort(FLOW_A_DST_PORT)
                .destVlan(FLOW_A_DST_VLAN)
                .allocateProtectedPath(true)
                .encapsulationType(flowEncapsulationType)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowStatus.UP)
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .cookie(new FlowSegmentCookie(FLOW_A_FORWARD_COOKIE_VALUE))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .status(FlowPathStatus.ACTIVE)
                .bandwidth(FLOW_A_BANDWIDTH)
                .build();
        flow.setForwardPath(forwardFlowPath);

        PathSegment forwardSegmentA = PathSegment.builder()
                .pathId(forwardFlowPath.getPathId())
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT)
                .build();

        PathSegment forwardSegmentB = PathSegment.builder()
                .pathId(forwardFlowPath.getPathId())
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT)
                .build();
        forwardFlowPath.setSegments(Lists.newArrayList(forwardSegmentA, forwardSegmentB));

        if (checkWithGroup) {

            FlowMirrorPoints flowMirrorPoints = FlowMirrorPoints.builder()
                    .mirrorSwitch(switchA)
                    .mirrorGroup(MirrorGroup.builder()
                            .switchId(TEST_SWITCH_ID_A)
                            .groupId(new GroupId(FLOW_GROUP_ID_A))
                            .pathId(FLOW_A_FORWARD_PATH_ID)
                            .flowId(TEST_FLOW_ID_A)
                            .mirrorGroupType(MirrorGroupType.TRAFFIC_INTEGRITY)
                            .mirrorDirection(MirrorDirection.INGRESS)
                            .build())
                    .build();

            FlowMirrorPath flowMirrorPath = FlowMirrorPath.builder()
                    .pathId(new PathId("mirror_path"))
                    .mirrorSwitch(switchA)
                    .egressSwitch(switchA)
                    .egressPort(FLOW_GROUP_ID_A_OUT_PORT)
                    .egressOuterVlan(FLOW_GROUP_ID_A_OUT_VLAN)
                    .build();
            flowMirrorPoints.addPaths(flowMirrorPath);

            forwardFlowPath.addFlowMirrorPoints(flowMirrorPoints);
        }

        return flow;
    }

    private Flow buildOneSwitchPortFlow() {
        Switch switchD = Switch.builder().switchId(TEST_SWITCH_ID_D).description("").build();

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_B)
                .srcSwitch(switchD)
                .srcPort(FLOW_B_SRC_PORT)
                .srcVlan(FLOW_B_SRC_VLAN)
                .destSwitch(switchD)
                .destPort(FLOW_B_SRC_PORT)
                .destVlan(FLOW_B_DST_VLAN)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .bandwidth(FLOW_B_BANDWIDTH)
                .status(FlowStatus.UP)
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_forward_path"))
                .cookie(new FlowSegmentCookie(FLOW_B_FORWARD_COOKIE_VALUE))
                .meterId(new MeterId(FLOW_B_FORWARD_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .status(FlowPathStatus.ACTIVE)
                .bandwidth(FLOW_B_BANDWIDTH)
                .build();
        flow.setForwardPath(forwardFlowPath);

        return flow;
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithTransitVlan() {
        return getSwitchFlowEntriesWithTransitVlan(false);
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithTransitVlan(boolean checkWithGroup) {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_VALUE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                        FLOW_A_FORWARD_METER_ID, false)));

        if (checkWithGroup) {
            switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                    getFlowEntry(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, 0,
                            null, FLOW_A_FORWARD_METER_ID, false, String.valueOf(FLOW_GROUP_ID_A))));
        }

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_VALUE, FLOW_A_SEGMENT_A_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null, null, false)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_VALUE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false)));

        return switchEntries;
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithVxlan() {
        return getSwitchFlowEntriesWithVxlan(false);
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithVxlan(boolean checkWithGroup) {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_VALUE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), FLOW_A_ENCAP_ID, null,
                        FLOW_A_FORWARD_METER_ID, true)));

        if (checkWithGroup) {
            switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                    getFlowEntry(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, 0,
                            null, FLOW_A_FORWARD_METER_ID, true, String.valueOf(FLOW_GROUP_ID_A))));
        }

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_VALUE, FLOW_A_SEGMENT_A_DST_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), FLOW_A_ENCAP_ID, null, null, false)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_VALUE, FLOW_A_SEGMENT_B_DST_PORT, 0, String.valueOf(FLOW_A_DST_PORT),
                        FLOW_A_ENCAP_ID, getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false)));

        return switchEntries;
    }

    private List<SwitchMeterEntries> getSwitchMeterEntries() {
        List<SwitchMeterEntries> switchMeterEntries = new ArrayList<>();
        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterEntries(Collections.singletonList(getMeterEntry(FLOW_A_FORWARD_METER_ID, FLOW_A_BANDWIDTH)))
                .build());

        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_B)
                .meterEntries(Collections.emptyList())
                .build());

        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_C)
                .meterEntries(Collections.emptyList())
                .build());

        return switchMeterEntries;
    }

    private MeterEntry getMeterEntry(long meterId, long bandwidth) {
        return MeterEntry.builder()
                .meterId(meterId)
                .rate(bandwidth)
                .burstSize(Meter
                        .calculateBurstSize(bandwidth, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Meter.getMeterKbpsFlags())
                .build();
    }

    private List<SwitchFlowEntries> getSwitchFlowEntriesOneSwitchFlow() {
        return Lists.newArrayList(getSwitchFlowEntries(TEST_SWITCH_ID_D,
                getFlowEntry(FLOW_B_FORWARD_COOKIE_VALUE, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, "in_port", 0,
                        getFlowSetFieldAction(FLOW_B_DST_VLAN), FLOW_B_FORWARD_METER_ID, false)));
    }

    private List<SwitchFlowEntries> getSwitchFlowEntriesOneSwitchFlowWithGroup() {
        return Lists.newArrayList(getSwitchFlowEntries(TEST_SWITCH_ID_D,
                getFlowEntry(FLOW_B_FORWARD_COOKIE_VALUE, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, "in_port", 0,
                        getFlowSetFieldAction(FLOW_B_DST_VLAN), FLOW_B_FORWARD_METER_ID, false)),

                getSwitchFlowEntries(TEST_SWITCH_ID_D,
                        getFlowEntry(FLOW_B_FORWARD_MIRROR_COOKIE_VALUE, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, 0,
                                getFlowSetFieldAction(FLOW_B_DST_VLAN), FLOW_B_FORWARD_METER_ID, false,
                                String.valueOf(FLOW_GROUP_ID_A))));
    }

    private SwitchMeterEntries getSwitchMeterEntriesOneSwitchFlow() {
        List<MeterEntry> meterEntries = new ArrayList<>();
        meterEntries.add(getMeterEntry(FLOW_B_FORWARD_METER_ID, FLOW_B_BANDWIDTH));

        return SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_D)
                .meterEntries(meterEntries)
                .build();
    }

    private SwitchFlowEntries getSwitchFlowEntries(SwitchId switchId, FlowEntry... flowEntries) {
        return getSwitchFlowEntries(switchId, Lists.newArrayList(flowEntries));
    }

    private SwitchFlowEntries getSwitchFlowEntries(SwitchId switchId, List<FlowEntry> flowEntries) {
        return SwitchFlowEntries.builder()
                .switchId(switchId)
                .flowEntries(flowEntries)
                .build();
    }

    protected List<FlowEntry> getFlowEntriesWithTransitVlan(
            long effectiveCookieId, int srcPort, int srcVlan, int segmentSrcPort, int dstPort, int dstVlan,
            int segmentDstPort, int encapsulationId, long meterId, int groupId) {
        List<FlowEntry> flowEntries = new ArrayList<>();

        FlowSegmentCookie forwardCookie = new FlowSegmentCookie(FlowPathDirection.FORWARD, effectiveCookieId);
        FlowSegmentCookie reverseCookie = new FlowSegmentCookie(FlowPathDirection.REVERSE, effectiveCookieId);
        flowEntries.add(getFlowEntry(forwardCookie.getValue(), srcPort, srcVlan,
                String.valueOf(segmentSrcPort), 0, getFlowSetFieldAction(encapsulationId),
                meterId, false));

        flowEntries.add(getFlowEntry(forwardCookie.toBuilder().mirror(true).build().getValue(), srcPort, srcVlan, 0,
                null, meterId, false, String.valueOf(groupId)));
        flowEntries.add(getFlowEntry(reverseCookie.getValue(), segmentDstPort, encapsulationId,
                String.valueOf(dstPort), 0, getFlowSetFieldAction(dstVlan), null, false));

        return flowEntries;
    }

    private FlowEntry getFlowEntry(long cookie, int srcPort, int srcVlan, String dstPort, int tunnelId,
                                   FlowSetFieldAction flowSetFieldAction, Long meterId, boolean tunnelIdIngressRule) {
        return FlowEntry.builder()
                .cookie(cookie)
                .packetCount(7)
                .byteCount(480)
                .version("OF_13")
                .match(FlowMatchField.builder()
                        .inPort(String.valueOf(srcPort))
                        .vlanVid(String.valueOf(srcVlan))
                        .tunnelId(!tunnelIdIngressRule ? String.valueOf(tunnelId) : null)
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .flowOutput(dstPort)
                                .setFieldActions(flowSetFieldAction == null
                                        ? Lists.newArrayList() : Lists.newArrayList(flowSetFieldAction))
                                .pushVxlan(tunnelIdIngressRule ? String.valueOf(tunnelId) : null)
                                .build())
                        .goToMeter(meterId)
                        .build())
                .build();
    }

    private FlowEntry getFlowEntry(long cookie, int srcPort, int srcVlan, int tunnelId,
                                   FlowSetFieldAction flowSetFieldAction, Long meterId, boolean tunnelIdIngressRule,
                                   String group) {
        return FlowEntry.builder()
                .cookie(cookie)
                .packetCount(7)
                .byteCount(480)
                .version("OF_13")
                .match(FlowMatchField.builder()
                        .inPort(String.valueOf(srcPort))
                        .vlanVid(String.valueOf(srcVlan))
                        .tunnelId(!tunnelIdIngressRule ? String.valueOf(tunnelId) : null)
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .group(group)
                                .setFieldActions(flowSetFieldAction == null
                                        ? Lists.newArrayList() : Lists.newArrayList(flowSetFieldAction))
                                .pushVxlan(tunnelIdIngressRule ? String.valueOf(tunnelId) : null)
                                .build())
                        .goToMeter(meterId)
                        .build())
                .build();
    }

    private FlowSetFieldAction getFlowSetFieldAction(int dstVlan) {
        return FlowSetFieldAction.builder()
                .fieldName("vlan_vid")
                .fieldValue(String.valueOf(dstVlan))
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
                .outPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .inVlan(FLOW_A_SRC_VLAN)
                .outVlan(Collections.singletonList(FLOW_A_ENCAP_ID))
                .meterId(FLOW_A_FORWARD_METER_ID)
                .meterRate(FLOW_A_BANDWIDTH)
                .meterBurstSize(Meter
                        .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .meterFlags(Meter.getMeterKbpsFlags())
                .build();
        simpleSwitchRules.add(rule);
        if (checkWithGroup) {
            simpleSwitchRules.add(rule.toBuilder()
                    .outPort(0)
                    .outVlan(Collections.emptyList())
                    .cookie(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE)
                    .groupId(FLOW_GROUP_ID_A)
                    .groupBuckets(
                            Lists.newArrayList(new SimpleGroupBucket(FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID, 0),
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
                        .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .meterFlags(Meter.getMeterKbpsFlags())
                .build();
        simpleSwitchRules.add(rule);
        if (checkWithGroup) {
            simpleSwitchRules.add(rule.toBuilder()
                    .outPort(0)
                    .tunnelId(0)
                    .cookie(FLOW_A_FORWARD_MIRROR_COOKIE_VALUE)
                    .groupId(FLOW_GROUP_ID_A)
                    .groupBuckets(
                            Lists.newArrayList(new SimpleGroupBucket(FLOW_A_SEGMENT_A_SRC_PORT, 0, FLOW_A_ENCAP_ID),
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
                        .calculateBurstSize(FLOW_B_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .meterFlags(Meter.getMeterKbpsFlags())
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

    private List<SwitchGroupEntries> getSwitchGroupEntriesTransitVlan(int mainPort, int transitVlan) {
        return Collections.singletonList(SwitchGroupEntries.builder()
                .switchId(TEST_SWITCH_ID_A)
                .groupEntries(Lists.newArrayList(getGroupEntry(
                        FLOW_GROUP_ID_A, mainPort, transitVlan, FLOW_GROUP_ID_A_OUT_PORT, FLOW_GROUP_ID_A_OUT_VLAN)))
                .build());
    }

    private GroupEntry getGroupEntry(int groupId, int mainPort, int transitVlan, int groupOutPort, int groupOutVlan) {
        return GroupEntry.builder()
                .groupId(groupId)
                .buckets(Lists.newArrayList(new GroupBucket(0, FlowApplyActions.builder()
                                .flowOutput(String.valueOf(groupOutPort))
                                .setFieldActions(Collections.singletonList(FlowSetFieldAction.builder()
                                        .fieldName("vlan_vid")
                                        .fieldValue(String.valueOf(groupOutVlan))
                                        .build()))
                                .build()),
                        new GroupBucket(0, FlowApplyActions.builder()
                                .flowOutput(String.valueOf(mainPort))
                                .setFieldActions(Collections.singletonList(FlowSetFieldAction.builder()
                                        .fieldName("vlan_vid")
                                        .fieldValue(String.valueOf(transitVlan))
                                        .build()))
                                .build())))
                .build();
    }

    private List<SwitchGroupEntries> getSwitchGroupEntriesVxlan(int mainPort, int vni) {
        return Collections.singletonList(SwitchGroupEntries.builder()
                .switchId(TEST_SWITCH_ID_A)
                .groupEntries(Lists.newArrayList(GroupEntry.builder()
                        .groupId(FLOW_GROUP_ID_A)
                        .buckets(Lists.newArrayList(new GroupBucket(0, FlowApplyActions.builder()
                                        .flowOutput(String.valueOf(FLOW_GROUP_ID_A_OUT_PORT))
                                        .setFieldActions(Collections.singletonList(FlowSetFieldAction.builder()
                                                .fieldName("vlan_vid")
                                                .fieldValue(String.valueOf(FLOW_GROUP_ID_A_OUT_VLAN))
                                                .build()))
                                        .build()),
                                new GroupBucket(0, FlowApplyActions.builder()
                                        .flowOutput(String.valueOf(mainPort))
                                        .pushVxlan(String.valueOf(vni))
                                        .build())))
                        .build()))
                .build());
    }
}
