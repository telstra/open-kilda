/* Copyright 2020 Telstra Open Source
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

import static org.openkilda.rulemanager.action.ActionType.PUSH_VXLAN_NOVIFLOW;

import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.Vxlan;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;
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
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FlowValidationTestBase extends InMemoryGraphBasedTest {
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
    private static final long FLOW_A_FORWARD_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 1L).getValue();
    private static final long FLOW_A_REVERSE_COOKIE = new FlowSegmentCookie(FlowPathDirection.REVERSE, 1L).getValue();
    private static final long FLOW_A_FORWARD_COOKIE_PROTECTED = new FlowSegmentCookie(
            FlowPathDirection.FORWARD, 2L).getValue();
    private static final long FLOW_A_REVERSE_COOKIE_PROTECTED = new FlowSegmentCookie(
            FlowPathDirection.REVERSE, 2L).getValue();
    private static final long FLOW_A_BANDWIDTH = 10000;
    private static final int FLOW_GROUP_ID_A = 20;
    private static final int FLOW_GROUP_ID_A_OUT_PORT = 21;
    private static final int FLOW_GROUP_ID_A_OUT_VLAN = 22;
    private static final int FLOW_B_SRC_PORT = 1;
    private static final int FLOW_B_SRC_VLAN = 15;
    private static final int FLOW_B_DST_VLAN = 16;
    private static final long FLOW_B_FORWARD_COOKIE = new FlowSegmentCookie(FlowPathDirection.FORWARD, 2L).getValue();
    private static final long FLOW_B_REVERSE_COOKIE = new FlowSegmentCookie(FlowPathDirection.REVERSE, 2L).getValue();
    private static final int FLOW_B_FORWARD_METER_ID = 34;
    private static final int FLOW_B_REVERSE_METER_ID = 35;
    private static final long FLOW_B_BANDWIDTH = 11000;

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

    protected void buildTransitVlanFlow(String endpointSwitchManufacturer) {
        buildFlow(FlowEncapsulationType.TRANSIT_VLAN, endpointSwitchManufacturer);

        TransitVlan transitVlan = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .vlan(FLOW_A_ENCAP_ID)
                .build();
        transitVlanRepository.add(transitVlan);

        TransitVlan transitVlanProtected = TransitVlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .vlan(FLOW_A_ENCAP_ID_PROTECTED)
                .build();
        transitVlanRepository.add(transitVlanProtected);
    }

    protected void buildVxlanFlow() {
        buildFlow(FlowEncapsulationType.VXLAN, "");

        Vxlan vxlan = Vxlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID)
                .vni(FLOW_A_ENCAP_ID)
                .build();
        vxlanRepository.add(vxlan);

        Vxlan vxlanProtected = Vxlan.builder()
                .flowId(TEST_FLOW_ID_A)
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .vni(FLOW_A_ENCAP_ID_PROTECTED)
                .build();
        vxlanRepository.add(vxlanProtected);
    }

    protected void buildFlow(FlowEncapsulationType flowEncapsulationType, String endpointSwitchManufacturer) {
        Switch switchA = buildSwitch(TEST_SWITCH_ID_A);
        switchRepository.add(switchA);
        switchA.setOfDescriptionManufacturer(endpointSwitchManufacturer);

        Switch switchB = buildSwitch(TEST_SWITCH_ID_B);
        switchRepository.add(switchB);
        Switch switchC = buildSwitch(TEST_SWITCH_ID_C);
        switchRepository.add(switchC);
        Switch switchE = buildSwitch(TEST_SWITCH_ID_E);
        switchRepository.add(switchE);

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
                .cookie(new FlowSegmentCookie(FLOW_A_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
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

        FlowPath forwardProtectedFlowPath = FlowPath.builder()
                .pathId(FLOW_A_FORWARD_PATH_ID_PROTECTED)
                .cookie(new FlowSegmentCookie(FLOW_A_FORWARD_COOKIE_PROTECTED))
                .meterId(new MeterId(FLOW_A_FORWARD_METER_ID_PROTECTED))
                .srcSwitch(switchA)
                .destSwitch(switchC)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setProtectedForwardPath(forwardProtectedFlowPath);

        PathSegment forwardProtectedSegmentA = PathSegment.builder()
                .pathId(forwardProtectedFlowPath.getPathId())
                .srcSwitch(switchA)
                .srcPort(FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED)
                .destSwitch(switchE)
                .destPort(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED)
                .build();

        PathSegment forwardProtectedSegmentB = PathSegment.builder()
                .pathId(forwardProtectedFlowPath.getPathId())
                .srcSwitch(switchE)
                .srcPort(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED)
                .destSwitch(switchC)
                .destPort(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED)
                .build();
        forwardProtectedFlowPath.setSegments(Lists.newArrayList(forwardProtectedSegmentA, forwardProtectedSegmentB));

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(FLOW_A_REVERSE_PATH_ID)
                .cookie(new FlowSegmentCookie(FLOW_A_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_A_REVERSE_METER_ID))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setReversePath(reverseFlowPath);

        PathSegment reverseSegmentA = PathSegment.builder()
                .pathId(reverseFlowPath.getPathId())
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT)
                .destSwitch(switchB)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT)
                .build();

        PathSegment reverseSegmentB = PathSegment.builder()
                .pathId(reverseFlowPath.getPathId())
                .srcSwitch(switchB)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT)
                .build();
        reverseFlowPath.setSegments(Lists.newArrayList(reverseSegmentA, reverseSegmentB));

        FlowPath reverseProtectedFlowPath = FlowPath.builder()
                .pathId(FLOW_A_REVERSE_PATH_ID_PROTECTED)
                .cookie(new FlowSegmentCookie(FLOW_A_REVERSE_COOKIE_PROTECTED))
                .meterId(new MeterId(FLOW_A_REVERSE_METER_ID_PROTECTED))
                .srcSwitch(switchC)
                .destSwitch(switchA)
                .bandwidth(FLOW_A_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setProtectedReversePath(reverseProtectedFlowPath);

        PathSegment reverseProtectedSegmentA = PathSegment.builder()
                .pathId(reverseProtectedFlowPath.getPathId())
                .srcSwitch(switchC)
                .srcPort(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED)
                .destSwitch(switchE)
                .destPort(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED)
                .build();

        PathSegment reverseProtectedSegmentB = PathSegment.builder()
                .pathId(reverseProtectedFlowPath.getPathId())
                .srcSwitch(switchE)
                .srcPort(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED)
                .destSwitch(switchA)
                .destPort(FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED)
                .build();
        reverseProtectedFlowPath.setSegments(Lists.newArrayList(reverseProtectedSegmentA, reverseProtectedSegmentB));

        flowRepository.add(flow);
    }

    protected void buildOneSwitchPortFlow() {
        Switch switchD = buildSwitch(TEST_SWITCH_ID_D);

        switchRepository.add(switchD);

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
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_forward_path"))
                .cookie(new FlowSegmentCookie(FLOW_B_FORWARD_COOKIE))
                .meterId(new MeterId(FLOW_B_FORWARD_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .bandwidth(FLOW_B_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setForwardPath(forwardFlowPath);

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID_B + "_reverse_path"))
                .cookie(new FlowSegmentCookie(FLOW_B_REVERSE_COOKIE))
                .meterId(new MeterId(FLOW_B_REVERSE_METER_ID))
                .srcSwitch(switchD)
                .destSwitch(switchD)
                .bandwidth(FLOW_B_BANDWIDTH)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setReversePath(reverseFlowPath);

        flowRepository.add(flow);
    }

    private static Switch buildSwitch(SwitchId switchId) {
        return Switch.builder().switchId(switchId).ofVersion("OF_13")
                .features(ImmutableSet.of(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN, SwitchFeature.METERS))
                .ofDescriptionSoftware("").ofDescriptionManufacturer("").description("").build();
    }

    protected List<FlowDumpResponse> getFlowDumpResponseWithTransitVlan() {
        List<FlowDumpResponse> switchEntries = new ArrayList<>();

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0, getSetFieldAction(FLOW_A_ENCAP_ID),
                        (long) FLOW_A_FORWARD_METER_ID, false),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getSetFieldAction(FLOW_A_SRC_VLAN),
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SRC_PORT), 0,
                        getSetFieldAction(FLOW_A_SRC_VLAN), null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null,
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), 0, null,
                        null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getSetFieldAction(FLOW_A_DST_VLAN),
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), 0, getSetFieldAction(FLOW_A_ENCAP_ID),
                        (long) FLOW_A_REVERSE_METER_ID, false),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_DST_PORT), 0,
                        getSetFieldAction(FLOW_A_DST_VLAN), null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_A_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 0,
                        null, null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED), 0,
                        null, null, false)));

        return switchEntries;
    }

    protected List<FlowDumpResponse> getWrongSwitchFlowEntriesWithTransitVlan() {
        List<FlowDumpResponse> switchEntries = new ArrayList<>();

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_A, 123, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), 0,
                        getSetFieldAction(FLOW_A_ENCAP_ID), (long) FLOW_A_FORWARD_METER_ID, false),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE, 123, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getSetFieldAction(FLOW_A_SRC_VLAN),
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        123, FLOW_A_ENCAP_ID_PROTECTED,
                        String.valueOf(FLOW_A_SRC_PORT), 0, getSetFieldAction(FLOW_A_SRC_VLAN),
                        null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 123,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), 0, null,
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(123), 0, null, null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE,
                        FLOW_A_SEGMENT_B_DST_PORT, FLOW_A_ENCAP_ID,
                        String.valueOf(FLOW_A_DST_PORT), 0, getSetFieldAction(123),
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), 0, getSetFieldAction(FLOW_A_ENCAP_ID),
                        123L, false),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(FLOW_A_DST_PORT), 0,
                        getSetFieldAction(123), null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_A_DST_PORT_PROTECTED, 123,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), 0,
                        null, null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED,
                        FLOW_A_ENCAP_ID_PROTECTED, String.valueOf(123), 0, null,
                        null, false)));

        return switchEntries;
    }

    protected List<FlowDumpResponse> getSwitchFlowEntriesWithVxlan() {
        List<FlowDumpResponse> switchEntries = new ArrayList<>();

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_FORWARD_COOKIE, FLOW_A_SRC_PORT, FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT), FLOW_A_ENCAP_ID, null,
                        (long) FLOW_A_FORWARD_METER_ID, true),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_A_SRC_PORT,
                        0, String.valueOf(FLOW_A_SRC_PORT),
                        FLOW_A_ENCAP_ID, getSetFieldAction(FLOW_A_SRC_VLAN), null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_A_SRC_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_SRC_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getSetFieldAction(FLOW_A_SRC_VLAN), null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_A_DST_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT), FLOW_A_ENCAP_ID, null,
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_REVERSE_COOKIE, FLOW_A_SEGMENT_B_SRC_PORT, 0,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT), FLOW_A_ENCAP_ID, null,
                        null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE, FLOW_A_SEGMENT_B_DST_PORT,
                        0, String.valueOf(FLOW_A_DST_PORT),
                        FLOW_A_ENCAP_ID, getSetFieldAction(FLOW_A_DST_VLAN), null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_REVERSE_COOKIE, FLOW_A_DST_PORT, FLOW_A_DST_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_B_DST_PORT), FLOW_A_ENCAP_ID, null,
                        (long) FLOW_A_REVERSE_METER_ID, true),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_DST_PORT_PROTECTED,
                        0, String.valueOf(FLOW_A_DST_PORT), FLOW_A_ENCAP_ID_PROTECTED,
                        getSetFieldAction(FLOW_A_DST_VLAN), null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_A_DST_PORT_PROTECTED, 0,
                        String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED), FLOW_A_ENCAP_ID_PROTECTED,
                        null, null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED, 0,
                        String.valueOf(FLOW_A_SEGMENT_A_DST_PORT_PROTECTED), FLOW_A_ENCAP_ID_PROTECTED,
                        null, null, false)));

        return switchEntries;
    }

    protected List<FlowDumpResponse> getWrongSwitchFlowEntriesWithVxlan() {
        List<FlowDumpResponse> switchEntries = new ArrayList<>();

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_A, 123, FLOW_A_SRC_PORT,
                        FLOW_A_SRC_VLAN,
                        String.valueOf(FLOW_A_SEGMENT_A_SRC_PORT),
                        FLOW_A_ENCAP_ID,
                        null,
                        (long) FLOW_A_FORWARD_METER_ID,
                        true),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE,
                        123, 0, String.valueOf(FLOW_A_SRC_PORT),
                        FLOW_A_ENCAP_ID, getSetFieldAction(FLOW_A_SRC_VLAN),
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_A, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        123, 0, String.valueOf(FLOW_A_SRC_PORT),
                        FLOW_A_ENCAP_ID_PROTECTED, getSetFieldAction(FLOW_A_SRC_VLAN),
                        null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_FORWARD_COOKIE,
                        FLOW_A_SEGMENT_A_DST_PORT, 0, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT),
                        123, null,
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_B, FLOW_A_REVERSE_COOKIE,
                        FLOW_A_SEGMENT_B_SRC_PORT, 0, String.valueOf(123),
                        FLOW_A_ENCAP_ID, null,
                        null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE,
                        FLOW_A_SEGMENT_B_DST_PORT, 0, String.valueOf(FLOW_A_DST_PORT),
                        FLOW_A_ENCAP_ID, getSetFieldAction(123),
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_REVERSE_COOKIE,
                        FLOW_A_DST_PORT, FLOW_A_DST_VLAN, String.valueOf(FLOW_A_SEGMENT_B_DST_PORT),
                        FLOW_A_ENCAP_ID, null,
                        123L, true),
                getFlowSpeakerData(TEST_SWITCH_ID_C, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_DST_PORT_PROTECTED, 0, String.valueOf(FLOW_A_DST_PORT),
                        FLOW_A_ENCAP_ID_PROTECTED, getSetFieldAction(123),
                        null, false)));

        switchEntries.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_FORWARD_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_A_DST_PORT_PROTECTED, 0, String.valueOf(FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED),
                        123, null,
                        null, false),
                getFlowSpeakerData(TEST_SWITCH_ID_E, FLOW_A_REVERSE_COOKIE_PROTECTED,
                        FLOW_A_SEGMENT_B_SRC_PORT_PROTECTED, 0, String.valueOf(123),
                        FLOW_A_ENCAP_ID_PROTECTED, null,
                        null, false)));

        return switchEntries;
    }

    protected List<MeterDumpResponse> getMeterDumpResponses() {
        List<MeterDumpResponse> meterDumpResponses = new ArrayList<>();
        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(TEST_SWITCH_ID_A)
                        .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                        .rate(FLOW_A_BANDWIDTH)
                        .burst(Meter.calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS,
                                BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build()))
                .build());

        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_C)
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(TEST_SWITCH_ID_C)
                        .meterId(new MeterId(FLOW_A_REVERSE_METER_ID))
                        .rate(FLOW_A_BANDWIDTH)
                        .burst(Meter.calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS,
                                BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build()))
                .build());


        return meterDumpResponses;
    }

    protected List<MeterDumpResponse> getWrongSwitchMeterEntries() {
        List<MeterDumpResponse> meterDumpResponses = new ArrayList<>();
        int delta = 5000;
        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(TEST_SWITCH_ID_A)
                        .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                        .rate(FLOW_A_BANDWIDTH + delta)
                        .burst(Meter.calculateBurstSize(FLOW_A_BANDWIDTH + delta, MIN_BURST_SIZE_IN_KBITS,
                                BURST_COEFFICIENT, ""))
                        .flags(Stream.of("PKTPS", "BURST", "STATS")
                                .map(MeterFlag::valueOf).collect(Collectors.toSet()))
                        .build()))
                .build());

        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_B)
                .meterSpeakerData(Collections.emptyList())
                .build());

        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_C)
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(TEST_SWITCH_ID_C)
                        .meterId(new MeterId(FLOW_A_REVERSE_METER_ID))
                        .rate(FLOW_A_BANDWIDTH)
                        .burst(Meter.calculateBurstSize(FLOW_A_BANDWIDTH,
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build()))
                .build());

        return meterDumpResponses;
    }

    protected List<MeterDumpResponse> getSwitchMeterEntriesWithESwitch() {
        long rateESwitch = FLOW_A_BANDWIDTH + (long) (FLOW_A_BANDWIDTH * 0.01) - 1;
        long burstSize = (long) (FLOW_A_BANDWIDTH * 1.05);
        long burstSizeESwitch = burstSize + (long) (burstSize * 0.01) - 1;

        List<MeterDumpResponse> switchMeterEntries = new ArrayList<>();
        switchMeterEntries.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_A)
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(TEST_SWITCH_ID_A)
                        .meterId(new MeterId(FLOW_A_FORWARD_METER_ID))
                        .rate(rateESwitch)
                        .burst(burstSizeESwitch)
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build()))
                .build());

        switchMeterEntries.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_B)
                .meterSpeakerData(Collections.emptyList())
                .build());

        switchMeterEntries.add(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_C)
                .meterSpeakerData(Collections.singletonList(MeterSpeakerData.builder()
                        .switchId(TEST_SWITCH_ID_C)
                        .meterId(new MeterId(FLOW_A_REVERSE_METER_ID))
                        .rate(FLOW_A_BANDWIDTH)
                        .burst(Meter
                                .calculateBurstSize(FLOW_A_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                        .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                                .collect(Collectors.toSet()))
                        .build()))
                .build());

        return switchMeterEntries;
    }

    protected List<FlowDumpResponse> getSwitchFlowEntriesOneSwitchFlow() {
        List<FlowDumpResponse> flowDumpResponses = new ArrayList<>();

        flowDumpResponses.add(getFlowDumpResponse(
                getFlowSpeakerData(TEST_SWITCH_ID_D, FLOW_B_FORWARD_COOKIE,
                        FLOW_B_SRC_PORT, FLOW_B_SRC_VLAN, "in_port", 0,
                        getSetFieldAction(FLOW_B_DST_VLAN), (long) FLOW_B_FORWARD_METER_ID, false),
                getFlowSpeakerData(TEST_SWITCH_ID_D, FLOW_B_REVERSE_COOKIE,
                        FLOW_B_SRC_PORT, FLOW_B_DST_VLAN, "in_port", 0,
                        getSetFieldAction(FLOW_B_SRC_VLAN), (long) FLOW_B_REVERSE_METER_ID, false)));

        return flowDumpResponses;
    }

    protected List<MeterDumpResponse> getSwitchMeterEntriesOneSwitchFlow() {
        List<MeterSpeakerData> meterEntries = new ArrayList<>();
        meterEntries.add(MeterSpeakerData.builder()
                .switchId(TEST_SWITCH_ID_D)
                .meterId(new MeterId(FLOW_B_FORWARD_METER_ID))
                .rate(FLOW_B_BANDWIDTH)
                .burst(Meter
                        .calculateBurstSize(FLOW_B_BANDWIDTH, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                        .collect(Collectors.toSet()))
                .build());
        meterEntries.add(MeterSpeakerData.builder()
                .switchId(TEST_SWITCH_ID_D)
                .meterId(new MeterId(FLOW_B_REVERSE_METER_ID))
                .rate(FLOW_B_BANDWIDTH)
                .burst(Meter
                        .calculateBurstSize(FLOW_B_BANDWIDTH,
                                MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT, ""))
                .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                        .collect(Collectors.toSet()))
                .build());

        return Collections.singletonList(MeterDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_D)
                .meterSpeakerData(meterEntries)
                .build());
    }

    private FlowDumpResponse getFlowDumpResponse(FlowSpeakerData... flowSpeakerData) {
        return FlowDumpResponse.builder()
                .switchId(Arrays.stream(flowSpeakerData).findFirst().map(SpeakerData::getSwitchId).orElse(null))
                .flowSpeakerData(Lists.newArrayList(flowSpeakerData))
                .build();
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId, long cookie,
                                               int srcPort, int srcVlan,
                                               String dstPort, int tunnelId,
                                               SetFieldAction setFieldAction,
                                               Long meterId, boolean tunnelIdIngressRule) {

        Set<FieldMatch> fieldMatchSet
                = Sets.newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(srcPort).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(srcVlan).build());
        if (!tunnelIdIngressRule) {
            fieldMatchSet.add(FieldMatch.builder().field(Field.NOVIFLOW_TUNNEL_ID).value(tunnelId).build());
        }
        List<Action> actions = new ArrayList<>();
        PortNumber portNumber = NumberUtils.isParsable(dstPort)
                ? new PortNumber(NumberUtils.toInt(dstPort)) :
                new PortNumber(SpecialPortType.valueOf(dstPort.toUpperCase()));
        actions.add(new PortOutAction(portNumber));
        if (setFieldAction != null) {
            actions.add(setFieldAction);
        }
        if (tunnelIdIngressRule) {
            actions.add(PushVxlanAction.builder().vni(tunnelId).type(PUSH_VXLAN_NOVIFLOW).build());
        }

        return FlowSpeakerData.builder()
                .switchId(switchId)
                .cookie(new Cookie(cookie))
                .packetCount(7)
                .byteCount(480)
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

    protected List<GroupDumpResponse> getSwitchGroupEntries() {
        List<GroupDumpResponse> switchGroupEntries = new ArrayList<>();
        switchGroupEntries.add(GroupDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_A)
                .groupSpeakerData(Lists.newArrayList(GroupSpeakerData.builder()
                        .groupId(new GroupId(FLOW_GROUP_ID_A))
                        .buckets(Lists.newArrayList(
                                Bucket.builder().watchPort(WatchPort.ANY).writeActions(
                                        Sets.newHashSet(new PortOutAction(new PortNumber(FLOW_GROUP_ID_A_OUT_PORT)),
                                                getSetFieldAction(FLOW_GROUP_ID_A_OUT_VLAN))
                                ).build(),
                                Bucket.builder().watchPort(WatchPort.ANY).writeActions(
                                        Sets.newHashSet(
                                                new PortOutAction(new PortNumber(FLOW_A_SEGMENT_B_DST_PORT_PROTECTED)))
                                ).build()))
                        .build()))
                .build());

        switchGroupEntries.add(GroupDumpResponse.builder()
                .switchId(TEST_SWITCH_ID_C)
                .groupSpeakerData(Collections.emptyList())
                .build());

        return switchGroupEntries;
    }
}
