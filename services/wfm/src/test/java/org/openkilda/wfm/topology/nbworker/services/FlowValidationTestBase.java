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
    private static final int FLOW_B_SRC_PORT = 1;
    private static final int FLOW_B_SRC_VLAN = 15;
    private static final int FLOW_B_DST_VLAN = 16;
    private static final long FLOW_B_FORWARD_COOKIE = 2 | Cookie.FORWARD_FLOW_COOKIE_MASK;
    private static final long FLOW_B_REVERSE_COOKIE = 2 | Cookie.REVERSE_FLOW_COOKIE_MASK;
    private static final int FLOW_B_FORWARD_METER_ID = 34;
    private static final int FLOW_B_REVERSE_METER_ID = 35;

    private static SwitchRepository switchRepository;
    protected static FlowRepository flowRepository;
    private static TransitVlanRepository transitVlanRepository;

    protected static void setUpOnce() {
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
    }

    protected void buildFlowA() {
        Switch switchA = Switch.builder().switchId(TEST_SWITCH_ID_A).build();
        switchRepository.createOrUpdate(switchA);
        Switch switchB = Switch.builder().switchId(TEST_SWITCH_ID_B).build();
        switchRepository.createOrUpdate(switchB);
        Switch switchC = Switch.builder().switchId(TEST_SWITCH_ID_C).build();
        switchRepository.createOrUpdate(switchC);

        PathSegment forwardSegmentA = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT)
                .pathId(new PathId(TEST_FLOW_ID_A + "_forward_path"))
                .build();

        PathSegment forwardSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT)
                .pathId(new PathId(TEST_FLOW_ID_A + "_forward_path"))
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_forward_path"))
                .flowId(TEST_FLOW_ID_A)
                .cookie(new Cookie(FLOW_A_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .status(FlowPathStatus.ACTIVE)
                .segments(Lists.newArrayList(forwardSegmentA, forwardSegmentB))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        PathSegment reverseSegmentA = PathSegment.builder()
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .pathId(new PathId(TEST_FLOW_ID_A + "_reverse_path"))
                .build();

        PathSegment reverseSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .pathId(new PathId(TEST_FLOW_ID_A + "_reverse_path"))
                .build();

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_A + "_reverse_path"))
                .flowId(TEST_FLOW_ID_A)
                .cookie(new Cookie(FLOW_A_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_A_REVERSE_METER_ID))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .status(FlowPathStatus.ACTIVE)
                .segments(Lists.newArrayList(reverseSegmentA, reverseSegmentB))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_A)
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SRC_PORT)
                .srcVlan(FLOW_A_SRC_VLAN)
                .destSwitch(switchC)
                .destPort(FLOW_A_DST_PORT)
                .destVlan(FLOW_A_DST_VLAN)
                .forwardPath(forwardFlowPath)
                .reversePath(reverseFlowPath)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

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
        Switch switchD = Switch.builder().switchId(TEST_SWITCH_ID_D).build();
        switchRepository.createOrUpdate(switchD);

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_forward_path"))
                .flowId(TEST_FLOW_ID_B)
                .cookie(new Cookie(FLOW_B_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_B_FORWARD_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_reverse_path"))
                .flowId(TEST_FLOW_ID_B)
                .cookie(new Cookie(FLOW_B_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_B_REVERSE_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.emptyList())
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID_B)
                .srcSwitch(switchD)
                .srcPort(FLOW_B_SRC_PORT)
                .srcVlan(FLOW_B_SRC_VLAN)
                .destSwitch(switchD)
                .destPort(FLOW_B_SRC_PORT)
                .destVlan(FLOW_B_DST_VLAN)
                .forwardPath(forwardFlowPath)
                .reversePath(reverseFlowPath)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

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

    protected List<SwitchFlowEntries> getSwitchFlowEntriesFlowB() {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_D,
                getFlowEntry(FLOW_B_FORWARD_COOKIE, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, "in_port",
                        getFlowSetFieldAction(FLOW_B_DST_VLAN), (long) FLOW_B_FORWARD_METER_ID),
                getFlowEntry(FLOW_B_REVERSE_COOKIE, FLOW_B_SRC_PORT, FLOW_B_DST_VLAN, "in_port",
                        getFlowSetFieldAction(FLOW_B_SRC_VLAN), (long) FLOW_B_REVERSE_METER_ID)));

        return switchEntries;
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
