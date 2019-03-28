/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import com.google.common.collect.Lists;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlowValidationTestBase extends Neo4jBasedTest {
    protected static final SwitchId TEST_SWITCH_ID_A = new SwitchId(1);
    protected static final SwitchId TEST_SWITCH_ID_B = new SwitchId(2);
    protected static final SwitchId TEST_SWITCH_ID_C = new SwitchId(3);
    protected static final SwitchId TEST_SWITCH_ID_D = new SwitchId(4);
    protected static final String TEST_FLOW_ID_A = "test_flow_id_a";
    protected static final String TEST_FLOW_ID_B = "test_flow_id_b";

    protected static final long MIN_BURST_SIZE_IN_KBITS = 1024;
    protected static final double BURST_COEFFICIENT = 1.05;

    private static final int FLOW_A_SRC_PORT = 10;
    private static final int FLOW_A_DST_PORT = 20;
    private static final int FLOW_A_SEGMENT_A_SRC_PORT = 11;
    private static final int FLOW_A_SEGMENT_A_DST_PORT = 15;
    private static final int FLOW_A_SEGMENT_B_SRC_PORT = 16;
    private static final int FLOW_A_SEGMENT_B_DST_PORT = 19;
    private static final int FLOW_A_SRC_VLAN = 11;
    private static final int FLOW_A_FORWARD_TRANSIT_VLAN = 12;
    private static final int FLOW_A_REVERSE_TRANSIT_VLAN = 13;
    private static final int FLOW_A_DST_VLAN = 14;
    private static final int FLOW_A_FORWARD_METER_ID = 32;
    private static final int FLOW_A_REVERSE_METER_ID = 33;
    private static final long FLOW_A_FORWARD_COOKIE = 1 | Cookie.FORWARD_FLOW_COOKIE_MASK;
    private static final long FLOW_A_REVERSE_COOKIE = 1 | Cookie.REVERSE_FLOW_COOKIE_MASK;
    private static final long FLOW_A_BANDWIDTH = 10000;
    private static final int FLOW_B_SRC_PORT = 1;
    private static final int FLOW_B_SRC_VLAN = 15;
    private static final int FLOW_B_DST_VLAN = 16;
    private static final long FLOW_B_FORWARD_COOKIE = 2 | Cookie.FORWARD_FLOW_COOKIE_MASK;
    private static final long FLOW_B_REVERSE_COOKIE = 2 | Cookie.REVERSE_FLOW_COOKIE_MASK;
    private static final int FLOW_B_FORWARD_METER_ID = 34;
    private static final int FLOW_B_REVERSE_METER_ID = 35;
    private static final long FLOW_B_BANDWIDTH = 11000;

    private static SwitchRepository switchRepository;
    protected static FlowRepository flowRepository;
    private static TransitVlanRepository transitVlanRepository;

    protected static void setUpOnce() {
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
    }

    protected void buildFlowA() {
        Switch switchA = Switch.builder().switchId(TEST_SWITCH_ID_A).description("").build();
        switchRepository.createOrUpdate(switchA);
        Switch switchB = Switch.builder().switchId(TEST_SWITCH_ID_B).description("").build();
        switchRepository.createOrUpdate(switchB);
        Switch switchC = Switch.builder().switchId(TEST_SWITCH_ID_C).description("").build();
        switchRepository.createOrUpdate(switchC);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_A)
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SRC_PORT)
                .srcVlan(FLOW_A_SRC_VLAN)
                .destSwitch(switchC)
                .destPort(FLOW_A_DST_PORT)
                .destVlan(FLOW_A_DST_VLAN)
                .bandwidth(FLOW_A_BANDWIDTH)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(FLOW_A_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardFlowPath);

        PathSegment forwardSegmentA = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT)
                .path(forwardFlowPath)
                .build();

        PathSegment forwardSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT)
                .path(forwardFlowPath)
                .build();
        forwardFlowPath.setSegments(Lists.newArrayList(forwardSegmentA, forwardSegmentB));

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_reverse_path"))
                .flow(flow)
                .cookie(new Cookie(FLOW_A_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_A_REVERSE_METER_ID))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reverseFlowPath);

        PathSegment reverseSegmentA = PathSegment.builder()
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .path(reverseFlowPath)
                .build();

        PathSegment reverseSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .path(reverseFlowPath)
                .build();
        reverseFlowPath.setSegments(Lists.newArrayList(reverseSegmentA, reverseSegmentB));

        flowRepository.createOrUpdate(flow);

        TransitVlan forwardTransitVlan = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(new PathId(TEST_FLOW_ID_A + "_forward_path"))
                .vlan(FLOW_A_FORWARD_TRANSIT_VLAN)
                .build();

        transitVlanRepository.createOrUpdate(forwardTransitVlan);

        TransitVlan reverseTransitVlan = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(new PathId(TEST_FLOW_ID_A + "_reverse_path"))
                .vlan(FLOW_A_REVERSE_TRANSIT_VLAN)
                .build();

        transitVlanRepository.createOrUpdate(reverseTransitVlan);
    }

    protected void buildOneSwitchPortFlowB() {
        Switch switchD = Switch.builder().switchId(TEST_SWITCH_ID_D).description("").build();
        switchRepository.createOrUpdate(switchD);

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_B)
                .srcSwitch(switchD)
                .srcPort(FLOW_B_SRC_PORT)
                .srcVlan(FLOW_B_SRC_VLAN)
                .destSwitch(switchD)
                .destPort(FLOW_B_SRC_PORT)
                .destVlan(FLOW_B_DST_VLAN)
                .bandwidth(FLOW_B_BANDWIDTH)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_forward_path"))
                .flow(flow)
                .cookie(new Cookie(FLOW_B_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_B_FORWARD_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .bandwidth(FLOW_B_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setForwardPath(forwardFlowPath);

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_reverse_path"))
                .flow(flow)
                .cookie(new Cookie(FLOW_B_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_B_REVERSE_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .bandwidth(FLOW_B_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setReversePath(reverseFlowPath);

        flowRepository.createOrUpdate(flow);
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesFlowA() {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), getFlowSetFieldAction(FLOW_A_FORWARD_TRANSIT_VLAN),
                        (long) FLOW_A_FORWARD_METER_ID),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_REVERSE_TRANSIT_VLAN,
                        String.valueOf(FLOW_A_SRC_PORT), getFlowSetFieldAction(FLOW_A_SRC_VLAN), null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, FLOW_A_FORWARD_TRANSIT_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), null, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_REVERSE_TRANSIT_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), null, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_FORWARD_TRANSIT_VLAN,
                        String.valueOf(FLOW_A_DST_PORT), getFlowSetFieldAction(FLOW_A_DST_VLAN), null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), getFlowSetFieldAction(FLOW_A_REVERSE_TRANSIT_VLAN),
                        (long) FLOW_A_REVERSE_METER_ID)));

        return switchEntries;
    }

    protected List<SwitchMeterEntries> getSwitchMeterEntriesFlowA() {
        List<SwitchMeterEntries> switchMeterEntries = new ArrayList<>();
        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(FLOW_A_FORWARD_METER_ID)
                        .rate(FLOW_A_BANDWIDTH)
                        .burstSize(Meter
                                .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterFlags())
                        .build()))
                .build());

        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_B)
                .meterEntries(Collections.emptyList())
                .build());

        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_C)
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(FLOW_A_REVERSE_METER_ID)
                        .rate(FLOW_A_BANDWIDTH)
                        .burstSize(Meter
                                .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterFlags())
                        .build()))
                .build());

        return switchMeterEntries;
    }

    protected List<SwitchFlowEntries> getWrongSwitchFlowEntriesFlowA() {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(123, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT),
                        getFlowSetFieldAction(FLOW_A_FORWARD_TRANSIT_VLAN), (long) FLOW_A_FORWARD_METER_ID),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, 123, FLOW_A_REVERSE_TRANSIT_VLAN,
                        String.valueOf(FLOW_A_SRC_PORT), getFlowSetFieldAction(FLOW_A_SRC_VLAN), null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 123,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), null, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_REVERSE_TRANSIT_VLAN,
                        String.valueOf(123), null, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_FORWARD_TRANSIT_VLAN,
                        String.valueOf(FLOW_A_DST_PORT), getFlowSetFieldAction(123), null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), getFlowSetFieldAction(FLOW_A_REVERSE_TRANSIT_VLAN),
                        123L)));

        return switchEntries;
    }

    protected List<SwitchMeterEntries> getWrongSwitchMeterEntriesFlowA() {
        List<SwitchMeterEntries> switchMeterEntries = new ArrayList<>();
        int delta = 5000;
        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(FLOW_A_FORWARD_METER_ID)
                        .rate(FLOW_A_BANDWIDTH + delta)
                        .burstSize(Meter.calculateBurstSize(FLOW_A_BANDWIDTH + delta, MIN_BURST_SIZE_IN_KBITS,
                                BURST_COEFFICIENT, ""))
                        .flags(new String[]{"PKTPS", "BURST", "STATS"})
                        .build()))
                .build());

        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_B)
                .meterEntries(Collections.emptyList())
                .build());

        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_C)
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(FLOW_A_REVERSE_METER_ID)
                        .rate(FLOW_A_BANDWIDTH)
                        .burstSize(Meter
                                .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterFlags())
                        .build()))
                .build());

        return switchMeterEntries;
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesFlowB() {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_D,
                getFlowEntry(FLOW_B_FORWARD_COOKIE, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, "in_port",
                        getFlowSetFieldAction(FLOW_B_DST_VLAN), (long) FLOW_B_FORWARD_METER_ID),
                getFlowEntry(FLOW_B_REVERSE_COOKIE, FLOW_B_SRC_PORT, FLOW_B_DST_VLAN, "in_port",
                        getFlowSetFieldAction(FLOW_B_SRC_VLAN), (long) FLOW_B_REVERSE_METER_ID)));

        return switchEntries;
    }

    protected List<SwitchMeterEntries> getSwitchMeterEntriesFlowB() {
        List<MeterEntry> meterEntries = new ArrayList<>();
        meterEntries.add(MeterEntry.builder()
                .meterId(FLOW_B_FORWARD_METER_ID)
                .rate(FLOW_B_BANDWIDTH)
                .burstSize(Meter
                        .calculateBurstSize(FLOW_B_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Meter.getMeterFlags())
                .build());
        meterEntries.add(MeterEntry.builder()
                .meterId(FLOW_B_REVERSE_METER_ID)
                .rate(FLOW_B_BANDWIDTH)
                .burstSize(Meter
                        .calculateBurstSize(FLOW_B_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Meter.getMeterFlags())
                .build());

        return Collections.singletonList(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_D)
                .meterEntries(meterEntries)
                .build());
    }

    private SwitchFlowEntries getSwitchFlowEntries(SwitchId switchId, FlowEntry forwardFlowEntry,
                                                   FlowEntry reverseFlowEntry) {
        return SwitchFlowEntries.builder()
                .switchId(switchId)
                .flowEntries(Lists.newArrayList(forwardFlowEntry, reverseFlowEntry))
                .build();
    }

    private FlowEntry getFlowEntry(long cookie, int srcPort, int srcVlan, String dstPort,
                                   FlowSetFieldAction flowSetFieldAction, Long meterId) {
        return FlowEntry.builder()
                .cookie(cookie)
                .packetCount(7)
                .byteCount(480)
                .version("OF_13")
                .match(FlowMatchField.builder()
                        .inPort(String.valueOf(srcPort))
                        .vlanVid(String.valueOf(srcVlan))
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .flowOutput(dstPort)
                                .fieldAction(flowSetFieldAction)
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
}
