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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.validation;

import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.GROUP_ID_1;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.INNER_VLAN_1;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.INNER_VLAN_2;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.METER_ID_1;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.PORT_0;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.PORT_1;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.PORT_2;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.PORT_3;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.VLAN_1;
import static org.openkilda.persistence.inmemory.InMemoryGraphBasedTest.VLAN_2;
import static org.openkilda.rulemanager.OfVersion.OF_13;

import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.GroupId;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSubType;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class HaFlowValidationTestBase {
    protected static final SwitchId TEST_SWITCH_ID_A = new SwitchId(1);
    protected static final SwitchId TEST_SWITCH_ID_B = new SwitchId(2);
    protected static final SwitchId TEST_SWITCH_ID_C = new SwitchId(3);
    protected static final SwitchId TEST_SWITCH_ID_D = new SwitchId(4);
    private static final Long SWITCH_A_METADATA = 268435544L;
    private static final Long SWITCH_D_METADATA = 268435560L;
    private static final int VLAN_12 = 12;

    public static final FlowSegmentCookie FORWARD_COOKIE = FlowSegmentCookie.builder()
            .direction(FlowPathDirection.FORWARD).flowEffectiveId(1)
            .subType(FlowSubType.SHARED).build();
    public static final FlowSegmentCookie REVERSE_COOKIE = FlowSegmentCookie.builder()
            .direction(FlowPathDirection.REVERSE).flowEffectiveId(1)
            .subType(FlowSubType.SHARED).build();

    public static final FlowSegmentCookie FORWARD_SUB_COOKIE_1 = FORWARD_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_1).build();
    public static final FlowSegmentCookie REVERSE_SUB_COOKIE_1 = REVERSE_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_1).build();
    public static final FlowSegmentCookie FORWARD_SUB_COOKIE_2 = FORWARD_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_2).build();
    public static final FlowSegmentCookie REVERSE_SUB_COOKIE_2 = REVERSE_COOKIE.toBuilder()
            .subType(FlowSubType.HA_SUB_FLOW_2).build();


    protected static Switch buildSwitch(SwitchId switchId) {
        return Switch.builder()
                .switchId(switchId)
                .ofVersion(OF_13.name())
                .features(ImmutableSet.of(SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN, SwitchFeature.METERS))
                .ofDescriptionSoftware("").ofDescriptionManufacturer("").description("")
                .description("test_description")
                .socketAddress(new IpSocketAddress("10.0.0.1", 30070))
                .controller("test_ctrl")
                .hostname("test_host_" + switchId)
                .status(SwitchStatus.ACTIVE)
                .build();
    }

    protected List<SpeakerData> getForwardExpectedSpeakerDataList() {
        List<SpeakerData> forwardSpeakerDataList = new ArrayList<>();
        forwardSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_A,
                FORWARD_SUB_COOKIE_1.getValue(),
                PORT_0, VLAN_12,
                getApplyActions(null, INNER_VLAN_1, true, false, VLAN_1, PORT_1)));
        forwardSpeakerDataList.add(getGroupSpeakerData(TEST_SWITCH_ID_B));
        forwardSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_B,
                FORWARD_COOKIE.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(GROUP_ID_1, null, false, false,
                        null, null)));
        forwardSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_C,
                -4611686018427387903L,
                PORT_1,
                OfTable.PRE_INGRESS));
        forwardSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_C,
                FORWARD_COOKIE.getValue(),
                PORT_1,
                getApplyActions(null, VLAN_12, true,
                        false, null, 0),
                METER_ID_1.getValue()));
        forwardSpeakerDataList.add(getMeterSpeakerData(TEST_SWITCH_ID_C, METER_ID_1));
        forwardSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_D,
                FORWARD_SUB_COOKIE_2.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(null, INNER_VLAN_2, true, false, VLAN_2, PORT_2)));
        forwardSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_D,
                FORWARD_COOKIE.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(null, null, false,
                        false, null, PORT_0),
                OfFlowFlag.RESET_COUNTERS));

        return forwardSpeakerDataList;
    }

    protected List<SpeakerData> getReverseExpectedSpeakerDataList() {
        List<SpeakerData> reverseSpeakerDataList = new ArrayList<>();
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_A,
                -4611686018427387903L,
                PORT_1,
                OfTable.PRE_INGRESS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_A,
                576460800000001L,
                PORT_1, VLAN_1,
                getApplyActions(null, null, false,
                        true, null, null),
                OfTable.INGRESS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_A,
                REVERSE_SUB_COOKIE_1.getValue(),
                PORT_1,
                INNER_VLAN_1,
                SWITCH_A_METADATA,
                getApplyActions(null, VLAN_12, false,
                        false, null, PORT_0)));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_C,
                REVERSE_COOKIE.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(null, null, false,
                        true, null, PORT_1)));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_C,
                REVERSE_SUB_COOKIE_2.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(null, null, false,
                        false, null, PORT_0),
                OfFlowFlag.RESET_COUNTERS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_C,
                REVERSE_SUB_COOKIE_1.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(null, null, false,
                        false, null, PORT_0),
                OfFlowFlag.RESET_COUNTERS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_D,
                -4611686018427387902L,
                PORT_2,
                OfTable.PRE_INGRESS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_D,
                576460800000002L,
                PORT_2,
                VLAN_2,
                getApplyActions(null, null, false,
                        true, null, null),
                OfTable.INGRESS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_D,
                REVERSE_SUB_COOKIE_2.getValue(),
                PORT_2,
                INNER_VLAN_2,
                SWITCH_D_METADATA,
                getApplyActions(null, VLAN_12, false,
                        false, null, PORT_0)));

        return reverseSpeakerDataList;
    }

    protected List<SpeakerData> getReverseCorruptedFloodlightSpeakerDataList() {
        List<SpeakerData> reverseSpeakerDataList = new ArrayList<>();
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_A,
                -4611686018427387903L,
                PORT_1,
                OfTable.PRE_INGRESS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_A,
                REVERSE_SUB_COOKIE_1.getValue(),
                PORT_1,
                INNER_VLAN_1,
                SWITCH_A_METADATA,
                getApplyActions(null, VLAN_12, false,
                        false, null, PORT_0)));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_C,
                REVERSE_COOKIE.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(null, null, false,
                        true, null, PORT_1)));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_C,
                REVERSE_SUB_COOKIE_1.getValue(),
                PORT_0,
                VLAN_12,
                getApplyActions(null, null, false,
                        false, null, PORT_0),
                OfFlowFlag.RESET_COUNTERS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_D,
                576460800000002L,
                PORT_2,
                VLAN_2,
                getApplyActions(null, null, false,
                        true, VLAN_1, PORT_3),
                OfTable.INGRESS));
        reverseSpeakerDataList.add(getFlowSpeakerData(
                TEST_SWITCH_ID_D,
                REVERSE_SUB_COOKIE_2.getValue(),
                PORT_2,
                INNER_VLAN_2,
                SWITCH_D_METADATA,
                getApplyActions(null, VLAN_12, false,
                        false, null, PORT_0)));

        return reverseSpeakerDataList;
    }


    protected List<MeterDumpResponse> getMeterDumpResponses(List<MeterSpeakerData> meterSpeakerData) {
        return getMeterDumpResponses(meterSpeakerData.toArray(new MeterSpeakerData[0]));
    }

    protected List<MeterDumpResponse> getMeterDumpResponses(MeterSpeakerData... meterSpeakerData) {
        List<MeterDumpResponse> meterDumpResponses = new ArrayList<>();
        meterDumpResponses.add(MeterDumpResponse.builder()
                .switchId(Arrays.stream(meterSpeakerData).findFirst().map(SpeakerData::getSwitchId).orElse(null))
                .meterSpeakerData(Lists.newArrayList(meterSpeakerData))
                .build());
        return meterDumpResponses;
    }

    protected MeterSpeakerData getMeterSpeakerData(SwitchId switchId, MeterId meterId) {
        return MeterSpeakerData.builder()
                .switchId(switchId)
                .meterId(meterId)
                .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()).stream().map(MeterFlag::valueOf)
                        .collect(Collectors.toSet()))
                .build();
    }

    protected FlowDumpResponse getFlowDumpResponse(List<FlowSpeakerData> flowSpeakerData) {
        return getFlowDumpResponse(flowSpeakerData.toArray(new FlowSpeakerData[0]));
    }

    protected FlowDumpResponse getFlowDumpResponse(FlowSpeakerData... flowSpeakerData) {
        return FlowDumpResponse.builder()
                .switchId(Arrays.stream(flowSpeakerData).findFirst().map(SpeakerData::getSwitchId).orElse(null))
                .flowSpeakerData(Lists.newArrayList(flowSpeakerData))
                .build();
    }

    protected GroupDumpResponse getGroupDumpResponse(List<GroupSpeakerData> groupSpeakerData) {
        return getGroupDumpResponse(groupSpeakerData.toArray(new GroupSpeakerData[0]));
    }

    protected GroupDumpResponse getGroupDumpResponse(GroupSpeakerData... groupSpeakerData) {
        return GroupDumpResponse.builder()
                .switchId(Arrays.stream(groupSpeakerData).findFirst().map(SpeakerData::getSwitchId).orElse(null))
                .groupSpeakerData(Lists.newArrayList(groupSpeakerData))
                .build();
    }

    protected GroupSpeakerData getGroupSpeakerData(SwitchId switchId) {
        return GroupSpeakerData.builder()
                .switchId(switchId)
                .groupId(GROUP_ID_1)
                .type(GroupType.ALL)
                .buckets(Lists.newArrayList(
                        Bucket.builder().watchGroup(WatchGroup.ANY).watchPort(WatchPort.ANY)
                                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(PORT_2)))).build(),
                        Bucket.builder().watchGroup(WatchGroup.ANY).watchPort(WatchPort.ANY)
                                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(PORT_1)))).build()))
                .build();
    }

    protected List<FlowSpeakerData> filterFlowSpeakerData(
            List<SpeakerData> speakerData) {
        return speakerData.stream()
                .filter(Objects::nonNull)
                .filter(e -> e instanceof FlowSpeakerData)
                .map(e -> (FlowSpeakerData) e)
                .collect(Collectors.toList());
    }

    protected List<MeterSpeakerData> filterMeterSpeakerData(
            List<SpeakerData> speakerData) {
        return speakerData.stream()
                .filter(Objects::nonNull)
                .filter(e -> e instanceof MeterSpeakerData)
                .map(e -> (MeterSpeakerData) e)
                .collect(Collectors.toList());
    }

    protected List<GroupSpeakerData> filterGroupSpeakerData(
            List<SpeakerData> speakerData) {
        return speakerData.stream()
                .filter(Objects::nonNull)
                .filter(e -> e instanceof GroupSpeakerData)
                .map(e -> (GroupSpeakerData) e)
                .collect(Collectors.toList());
    }

    private List<Action> getApplyActions(GroupId groupId, Integer innerVlan,
                                         boolean pushVlanAction,
                                         boolean popVlanAction,
                                         Integer outerVlan,
                                         Integer portOut) {
        List<Action> actions = new ArrayList<>();
        if (groupId != null) {
            actions.add(new GroupAction(groupId));
        }
        if (innerVlan != null) {
            actions.add(SetFieldAction.builder().value(innerVlan).field(Field.VLAN_VID).build());
        }
        if (pushVlanAction) {
            actions.add(new PushVlanAction());
        }
        if (popVlanAction) {
            actions.add(new PopVlanAction());
        }
        if (outerVlan != null) {
            actions.add(SetFieldAction.builder().value(outerVlan).field(Field.VLAN_VID).build());
        }
        if (portOut != null) {
            actions.add(new PortOutAction(new PortNumber(portOut)));
        }
        return actions;
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId,
                                               long cookie,
                                               int inPortMatch,
                                               OfTable goToTable) {
        return getFlowSpeakerData(switchId, cookie, inPortMatch, null,
                null, null, null, goToTable, null);
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId,
                                               long cookie,
                                               int inPortMatch,
                                               int vlanMatch,
                                               List<Action> applyActions) {
        return getFlowSpeakerData(switchId, cookie, inPortMatch, vlanMatch,
                null, applyActions, null, null, null);
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId,
                                               long cookie,
                                               int inPortMatch,
                                               List<Action> applyActions,
                                               Long goToMeterId) {
        return getFlowSpeakerData(switchId, cookie, inPortMatch, null,
                null, applyActions, goToMeterId, null, null);
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId,
                                               long cookie,
                                               int inPortMatch,
                                               int vlanMatch,
                                               List<Action> applyActions,
                                               OfFlowFlag flowFlag) {
        return getFlowSpeakerData(switchId, cookie, inPortMatch, vlanMatch,
                null, applyActions, null, null, flowFlag);
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId,
                                               long cookie,
                                               int inPortMatch,
                                               int vlanMatch,
                                               List<Action> applyActions,
                                               OfTable goToTable) {
        return getFlowSpeakerData(switchId, cookie, inPortMatch, vlanMatch,
                null, applyActions, null, goToTable, null);
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId,
                                               long cookie,
                                               int inPortMatch,
                                               Integer vlanMatch,
                                               Long metadataMatch,
                                               List<Action> applyActions) {
        return getFlowSpeakerData(switchId, cookie, inPortMatch, vlanMatch,
                metadataMatch, applyActions, null, null, null);
    }

    private FlowSpeakerData getFlowSpeakerData(SwitchId switchId,
                                               long cookie,
                                               int inPortMatch,
                                               Integer vlanMatch,
                                               Long metadataMatch,
                                               List<Action> applyActions,
                                               Long goToMeterId,
                                               OfTable goToTable,
                                               OfFlowFlag flag) {

        Set<FieldMatch> fieldMatchSet = new HashSet<>();
        if (vlanMatch != null) {
            fieldMatchSet.add(FieldMatch.builder().field(Field.VLAN_VID).value(vlanMatch).build());
        }
        fieldMatchSet.add(FieldMatch.builder().field(Field.IN_PORT).value(inPortMatch).build());
        if (metadataMatch != null) {
            fieldMatchSet.add(FieldMatch.builder().field(Field.METADATA).value(metadataMatch).build());
        }

        return FlowSpeakerData.builder()
                .switchId(switchId)
                .cookie(new Cookie(cookie))
                .packetCount(7)
                .byteCount(480)
                .ofVersion(OF_13)
                .match(fieldMatchSet)
                .flags(Sets.newHashSet(flag))
                .instructions(Instructions.builder()
                        .applyActions(applyActions)
                        .goToMeter(goToMeterId == null ? null : new MeterId(goToMeterId))
                        .goToTable(goToTable)
                        .build())
                .build();
    }
}
