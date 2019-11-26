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
import org.openkilda.model.FlowApplication;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Metadata;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.Vxlan;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;

import com.google.common.collect.Lists;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class FlowValidationTestBase extends Neo4jBasedTest {
    protected static final SwitchId TEST_SWITCH_ID_A = new SwitchId(1);
    protected static final SwitchId TEST_SWITCH_ID_B = new SwitchId(2);
    protected static final SwitchId TEST_SWITCH_ID_C = new SwitchId(3);
    protected static final SwitchId TEST_SWITCH_ID_D = new SwitchId(4);
    protected static final SwitchId TEST_SWITCH_ID_E = new SwitchId(5);
    protected static final String TEST_FLOW_ID_A = "test_flow_id_a";
    protected static final String TEST_FLOW_ID_B = "test_flow_id_b";

    protected static final long MIN_BURST_SIZE_IN_KBITS = 1024;
    protected static final double BURST_COEFFICIENT = 1.05;

    private static final int FLOW_A_SRC_PORT = 10;
    private static final int FLOW_A_DST_PORT = 20;
    private static final int FLOW_A_SEGMENT_A_SRC_PORT = 11;
    private static final int FLOW_A_SEGMENT_A_DST_PORT = 15;
    private static final int FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED = 21;
    private static final int FLOW_A_SEGMENT_A_DST_PORT_PROTECTED = 25;
    private static final int FLOW_A_SEGMENT_B_SRC_PORT = 16;
    private static final int FLOW_A_SEGMENT_B_DST_PORT = 19;
    private static final int FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED = 26;
    private static final int FLOW_A_SEGMENT_B_DST_PORT_PROTECTED = 29;
    private static final int FLOW_A_SRC_VLAN = 11;
    private static final int FLOW_A_ENCAP_ID = 12;
    private static final PathId FLOW_A_FORWARD_PATH_ID = new PathId(TEST_FLOW_ID_A + "_forward_path");
    private static final PathId FLOW_A_REVERSE_PATH_ID = new PathId(TEST_FLOW_ID_A + "_reverse_path");
    private static final PathId FLOW_A_FORWARD_PATH_ID_PROTECTED = new PathId(TEST_FLOW_ID_A + "_forward_protect_path");
    private static final PathId FLOW_A_REVERSE_PATH_ID_PROTECTED = new PathId(TEST_FLOW_ID_A + "_reverse_protect_path");
    private static final int FLOW_A_ENCAP_ID_PROTECTED = 22;
    private static final int FLOW_A_DST_VLAN = 14;
    private static final int FLOW_A_FORWARD_METER_ID = 32;
    private static final int FLOW_A_REVERSE_METER_ID = 33;
    private static final int FLOW_A_FORWARD_METER_ID_PROTECTED = 42;
    private static final int FLOW_A_REVERSE_METER_ID_PROTECTED = 43;
    private static final long FLOW_A_FORWARD_COOKIE = Cookie.buildForwardCookie(1L).getValue();
    private static final long FLOW_A_REVERSE_COOKIE = Cookie.buildReverseCookie(1L).getValue();
    private static final long FLOW_A_FORWARD_COOKIE_PROTECTED = Cookie.buildForwardCookie(2L).getValue();
    private static final long FLOW_A_REVERSE_COOKIE_PROTECTED = Cookie.buildReverseCookie(2L).getValue();
    private static final long FLOW_A_BANDWIDTH = 10000;
    private static final long FLOW_A_FORWARD_METADATA = new Metadata(FLOW_A_ENCAP_ID, true).getRawValue();
    private static final long FLOW_A_REVERSE_METADATA = new Metadata(FLOW_A_ENCAP_ID, false).getRawValue();
    private static final int FLOW_B_SRC_PORT = 1;
    private static final int FLOW_B_SRC_VLAN = 15;
    private static final int FLOW_B_DST_VLAN = 16;
    private static final long FLOW_B_FORWARD_COOKIE = Cookie.buildForwardCookie(2L).getValue();
    private static final long FLOW_B_REVERSE_COOKIE = Cookie.buildReverseCookie(2L).getValue();
    private static final int FLOW_B_FORWARD_METER_ID = 34;
    private static final int FLOW_B_REVERSE_METER_ID = 35;
    private static final long FLOW_B_BANDWIDTH = 11000;
    private static final PathId FLOW_B_FORWARD_PATH_ID = new PathId(TEST_FLOW_ID_B + "_forward_path");
    private static final PathId FLOW_B_REVERSE_PATH_ID = new PathId(TEST_FLOW_ID_B + "_reverse_path");
    private static final int FLOW_B_ENCAP_ID = 36;
    private static final long FLOW_B_FORWARD_METADATA = new Metadata(FLOW_B_ENCAP_ID, true).getRawValue();
    private static final long FLOW_B_REVERSE_METADATA = new Metadata(FLOW_B_ENCAP_ID, false).getRawValue();

    protected static FlowResourcesConfig flowResourcesConfig;
    protected static FlowRepository flowRepository;
    private static SwitchRepository switchRepository;
    private static TransitVlanRepository transitVlanRepository;
    private static VxlanRepository vxlanRepository;

    protected static void setUpOnce() {
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
        vxlanRepository = persistenceManager.getRepositoryFactory().createVxlanRepository();
        flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
    }

    protected void buildTransitVlanFlow(String endpointSwitchManufacturer, boolean isTelescopeEnabled) {
        buildFlow(FlowEncapsulationType.TRANSIT_VLAN, endpointSwitchManufacturer, isTelescopeEnabled);

        TransitVlan transitVlan = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .vlan(FLOW_A_ENCAP_ID)
                .build();
        transitVlanRepository.createOrUpdate(transitVlan);

        TransitVlan transitVlanProtected = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .vlan(FLOW_A_ENCAP_ID_PROTECTED)
                .build();
        transitVlanRepository.createOrUpdate(transitVlanProtected);
    }

    protected void buildVxlanFlow() {
        buildFlow(FlowEncapsulationType.VXLAN, "", false);

        Vxlan vxlan = Vxlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .vni(FLOW_A_ENCAP_ID)
                .build();
        vxlanRepository.createOrUpdate(vxlan);

        Vxlan vxlanProtected = Vxlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .vni(FLOW_A_ENCAP_ID_PROTECTED)
                .build();
        vxlanRepository.createOrUpdate(vxlanProtected);
    }

    protected void buildFlow(FlowEncapsulationType flowEncapsulationType, String endpointSwitchManufacturer,
                             boolean isTelescopeEnabled) {
        Switch switchA = Switch.builder().switchId(TEST_SWITCH_ID_A).description("").build();
        switchA.setOfDescriptionManufacturer(endpointSwitchManufacturer);
        switchRepository.createOrUpdate(switchA);
        Switch switchB = Switch.builder().switchId(TEST_SWITCH_ID_B).description("").build();
        switchRepository.createOrUpdate(switchB);
        Switch switchC = Switch.builder().switchId(TEST_SWITCH_ID_C).description("").build();
        switchRepository.createOrUpdate(switchC);
        Switch switchE = Switch.builder().switchId(TEST_SWITCH_ID_E).description("").build();
        switchRepository.createOrUpdate(switchE);

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
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(FLOW_A_FORWARD_PATH_ID)
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

        if (isTelescopeEnabled) {
            forwardFlowPath.setApplications(EnumSet.of(FlowApplication.TELESCOPE));
        }

        flow.setForwardPath(forwardFlowPath);

        PathSegment forwardSegmentA = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT)
                .build();

        PathSegment forwardSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT)
                .build();
        forwardFlowPath.setSegments(Lists.newArrayList(forwardSegmentA, forwardSegmentB));

        FlowPath forwardProtectedFlowPath = FlowPath.builder()
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .flow(flow)
                .cookie(new Cookie(FLOW_A_FORWARD_COOKIE_PROTECTED))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID_PROTECTED))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setProtectedForwardPath(forwardProtectedFlowPath);

        PathSegment forwardProtectedSegmentA = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED)
                .destSwitch(switchE)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED)
                .build();

        PathSegment forwardProtectedSegmentB = PathSegment.builder()
                .srcSwitch(switchE)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED)
                .build();
        forwardProtectedFlowPath.setSegments(Lists.newArrayList(forwardProtectedSegmentA, forwardProtectedSegmentB));

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(FLOW_A_REVERSE_PATH_ID)
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

        if (isTelescopeEnabled) {
            reverseFlowPath.setApplications(EnumSet.of(FlowApplication.TELESCOPE));
        }

        flow.setReversePath(reverseFlowPath);

        PathSegment reverseSegmentA = PathSegment.builder()
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .build();

        PathSegment reverseSegmentB = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .build();
        reverseFlowPath.setSegments(Lists.newArrayList(reverseSegmentA, reverseSegmentB));

        FlowPath reverseProtectedFlowPath = FlowPath.builder()
                .pathId(FLOW_A_REVERSE_PATH_ID_PROTECTED)
                .flow(flow)
                .cookie(new Cookie(FLOW_A_REVERSE_COOKIE_PROTECTED))
                .meterId(new MeterId(FLOW_A_REVERSE_METER_ID_PROTECTED))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flow.setProtectedReversePath(reverseProtectedFlowPath);

        PathSegment reverseProtectedSegmentA = PathSegment.builder()
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED)
                .destSwitch(switchE)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED)
                .build();

        PathSegment reverseProtectedSegmentB = PathSegment.builder()
                .srcSwitch(switchE)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED)
                .build();
        reverseProtectedFlowPath.setSegments(Lists.newArrayList(reverseProtectedSegmentA, reverseProtectedSegmentB));

        flowRepository.createOrUpdate(flow);
    }

    protected void buildOneSwitchPortFlow(boolean isTelescopeEnabled) {
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
                .encapsulationType(FlowEncapsulationType.VXLAN)
                .status(FlowStatus.UP)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(FLOW_B_FORWARD_PATH_ID)
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
        if (isTelescopeEnabled) {
            forwardFlowPath.setApplications(EnumSet.of(FlowApplication.TELESCOPE));
        }
        flow.setForwardPath(forwardFlowPath);

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(FLOW_B_REVERSE_PATH_ID)
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
        if (isTelescopeEnabled) {
            reverseFlowPath.setApplications(EnumSet.of(FlowApplication.TELESCOPE));
        }
        flow.setReversePath(reverseFlowPath);

        flowRepository.createOrUpdate(flow);

        if (isTelescopeEnabled) {
            Vxlan vxlan = Vxlan.builder()
                    .flowId(TEST_FLOW_ID_B)
                    .pathId(FLOW_B_FORWARD_PATH_ID)
                    .vni(FLOW_B_ENCAP_ID)
                    .build();
            vxlanRepository.createOrUpdate(vxlan);
        }
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithTransitVlan(boolean isTelescopeEnabled) {

        if (isTelescopeEnabled) {
            return getSwitchFlowEntriesWithTransitVlan(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                    getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                            String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                            (long) FLOW_A_FORWARD_METER_ID, false, FLOW_A_FORWARD_METADATA),
                    getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID,
                            String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN),
                            null, false, FLOW_A_REVERSE_METADATA),
                    getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED,
                            FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SRC_PORT), 0,
                            getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false, null)));
        } else {
            return getSwitchFlowEntriesWithTransitVlan(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                    getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                            String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                            (long) FLOW_A_FORWARD_METER_ID, false, null),
                    getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID,
                            String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN),
                            null, false, null),
                    getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED,
                            FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SRC_PORT), 0,
                            getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false, null)));
        }
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithTransitVlan(SwitchFlowEntries firstSwitchFlowEntries) {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(firstSwitchFlowEntries);

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), 0, null, null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                        (long) FLOW_A_REVERSE_METER_ID, false, null),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_DST_PORT), 0,
                        getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 0,
                        null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED), 0,
                        null, null, false, null)));

        return switchEntries;
    }

    protected List<SwitchFlowEntries> getWrongSwitchFlowEntriesWithTransitVlan() {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(123, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0,
                        getFlowSetFieldAction(FLOW_A_ENCAP_ID), (long) FLOW_A_FORWARD_METER_ID, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, 123, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, 123, FLOW_A_ENCAP_ID_PROTECTED,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getFlowSetFieldAction(FLOW_A_SRC_VLAN),
                        null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 123,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(123), 0, null, null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getFlowSetFieldAction(123), null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), 0, getFlowSetFieldAction(FLOW_A_ENCAP_ID),
                        123L, false, null),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_DST_PORT), 0,
                        getFlowSetFieldAction(123), null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED, 123,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 0, null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(123), 0, null, null, false, null)));

        return switchEntries;
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesWithVxlan() {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), FLOW_A_ENCAP_ID, null,
                        (long) FLOW_A_FORWARD_METER_ID, true, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, 0, String.valueOf(FLOW_A_SRC_PORT),
                        FLOW_A_ENCAP_ID, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_SRC_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), FLOW_A_ENCAP_ID, null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), FLOW_A_ENCAP_ID, null, null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, 0, String.valueOf(FLOW_A_DST_PORT),
                        FLOW_A_ENCAP_ID, getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), FLOW_A_ENCAP_ID, null,
                        (long) FLOW_A_REVERSE_METER_ID, true, null),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_DST_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getFlowSetFieldAction(FLOW_A_DST_VLAN), null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), FLOW_A_ENCAP_ID_PROTECTED,
                        null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED), FLOW_A_ENCAP_ID_PROTECTED,
                        null, null, false, null)));

        return switchEntries;
    }

    protected List<SwitchFlowEntries> getWrongSwitchFlowEntriesWithVxlan() {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_A,
                getFlowEntry(123, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN, String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT),
                        FLOW_A_ENCAP_ID, null, (long) FLOW_A_FORWARD_METER_ID, true, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, 123, 0, String.valueOf(FLOW_A_SRC_PORT), FLOW_A_ENCAP_ID,
                        getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, 123, 0, String.valueOf(FLOW_A_SRC_PORT),
                        FLOW_A_ENCAP_ID_PROTECTED, getFlowSetFieldAction(FLOW_A_SRC_VLAN), null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_B,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 123, null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, 0,
                        String.valueOf(123), FLOW_A_ENCAP_ID, null, null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_C,
                getFlowEntry(FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, 0,
                        String.valueOf(FLOW_A_DST_PORT), FLOW_A_ENCAP_ID, getFlowSetFieldAction(123),
                        null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), FLOW_A_ENCAP_ID, null,
                        123L, true, null),
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_DST_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getFlowSetFieldAction(123), null, false, null)));

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_E,
                getFlowEntry(FLOW_A_FORWARD_COOKIE_PROTECTED, FLOW_A_SEGMENT_A_DST_PORT_PROTECTED, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 123, null, null, false, null),
                getFlowEntry(FLOW_A_REVERSE_COOKIE_PROTECTED, FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        0, String.valueOf(123), FLOW_A_ENCAP_ID_PROTECTED, null, null, false, null)));

        return switchEntries;
    }

    protected List<SwitchMeterEntries> getSwitchMeterEntries() {
        List<SwitchMeterEntries> switchMeterEntries = new ArrayList<>();
        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(FLOW_A_FORWARD_METER_ID)
                        .rate(FLOW_A_BANDWIDTH)
                        .burstSize(Meter
                                .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Meter.getMeterKbpsFlags())
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
                        .flags(Meter.getMeterKbpsFlags())
                        .build()))
                .build());

        return switchMeterEntries;
    }

    protected List<SwitchMeterEntries> getWrongSwitchMeterEntries() {
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
                        .flags(Meter.getMeterKbpsFlags())
                        .build()))
                .build());

        return switchMeterEntries;
    }

    protected List<SwitchMeterEntries> getSwitchMeterEntriesWithESwitch() {
        long rateESwitch = FLOW_A_BANDWIDTH + (long) (FLOW_A_BANDWIDTH * 0.01) - 1;
        long burstSize = (long) (FLOW_A_BANDWIDTH * 1.05);
        long burstSizeESwitch = burstSize + (long) (burstSize * 0.01) - 1;

        List<SwitchMeterEntries> switchMeterEntries = new ArrayList<>();
        switchMeterEntries.add(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterEntries(Collections.singletonList(MeterEntry.builder()
                        .meterId(FLOW_A_FORWARD_METER_ID)
                        .rate(rateESwitch)
                        .burstSize(burstSizeESwitch)
                        .flags(Meter.getMeterKbpsFlags())
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
                        .flags(Meter.getMeterKbpsFlags())
                        .build()))
                .build());

        return switchMeterEntries;
    }

    protected List<SwitchFlowEntries> getSwitchFlowEntriesOneSwitchFlow(boolean isTelescopeEnabled) {
        List<SwitchFlowEntries> switchEntries = new ArrayList<>();

        Long forwardMetadata = isTelescopeEnabled ? FLOW_B_FORWARD_METADATA : null;
        Long reverseMetadata = isTelescopeEnabled ? FLOW_B_REVERSE_METADATA : null;

        switchEntries.add(getSwitchFlowEntries(TEST_SWITCH_ID_D,
                getFlowEntry(FLOW_B_FORWARD_COOKIE, FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, "in_port", 0,
                        getFlowSetFieldAction(FLOW_B_DST_VLAN), (long) FLOW_B_FORWARD_METER_ID, false,
                        forwardMetadata),
                getFlowEntry(FLOW_B_REVERSE_COOKIE, FLOW_B_SRC_PORT, FLOW_B_DST_VLAN, "in_port", 0,
                        getFlowSetFieldAction(FLOW_B_SRC_VLAN), (long) FLOW_B_REVERSE_METER_ID, false,
                        reverseMetadata)));

        return switchEntries;
    }

    protected List<SwitchMeterEntries> getSwitchMeterEntriesOneSwitchFlow() {
        List<MeterEntry> meterEntries = new ArrayList<>();
        meterEntries.add(MeterEntry.builder()
                .meterId(FLOW_B_FORWARD_METER_ID)
                .rate(FLOW_B_BANDWIDTH)
                .burstSize(Meter
                        .calculateBurstSize(FLOW_B_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Meter.getMeterKbpsFlags())
                .build());
        meterEntries.add(MeterEntry.builder()
                .meterId(FLOW_B_REVERSE_METER_ID)
                .rate(FLOW_B_BANDWIDTH)
                .burstSize(Meter
                        .calculateBurstSize(FLOW_B_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Meter.getMeterKbpsFlags())
                .build());

        return Collections.singletonList(SwitchMeterEntries.builder()
                .switchId(TEST_SWITCH_ID_D)
                .meterEntries(meterEntries)
                .build());
    }

    private SwitchFlowEntries getSwitchFlowEntries(SwitchId switchId, FlowEntry... flowEntries) {
        return SwitchFlowEntries.builder()
                .switchId(switchId)
                .flowEntries(Lists.newArrayList(flowEntries))
                .build();
    }

    private FlowEntry getFlowEntry(long cookie, int srcPort, int srcVlan, String dstPort, int tunnelId,
                                   FlowSetFieldAction flowSetFieldAction, Long meterId, boolean isIngressRule,
                                   Long metadata) {
        return FlowEntry.builder()
                .cookie(cookie)
                .packetCount(7)
                .byteCount(480)
                .version("OF_13")
                .match(FlowMatchField.builder()
                        .inPort(String.valueOf(srcPort))
                        .vlanVid(String.valueOf(srcVlan))
                        .tunnelId(!isIngressRule ? String.valueOf(tunnelId) : null)
                        .build())
                .instructions(FlowInstructions.builder()
                        .applyActions(FlowApplyActions.builder()
                                .flowOutput(dstPort)
                                .fieldAction(flowSetFieldAction)
                                .pushVxlan(isIngressRule ? String.valueOf(tunnelId) : null)
                                .build())
                        .goToMeter(meterId)
                        .writeMetadata(metadata)
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
