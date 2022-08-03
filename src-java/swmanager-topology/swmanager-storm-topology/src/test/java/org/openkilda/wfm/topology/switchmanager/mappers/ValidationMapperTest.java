/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.switchmanager.mappers;

import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.GroupsValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.LogicalPortInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.LogicalPortsValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.MeterInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MetersValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.RulesValidationEntryV2;
import org.openkilda.messaging.info.switches.v2.SwitchValidationResponseV2;
import org.openkilda.model.GroupId;
import org.openkilda.model.IPv4Address;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateGroupsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateLogicalPortsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateMetersResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateRulesResultV2;

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ValidationMapperTest {

    public static final int GROUP_ID_VALUE = 1;
    public static final String MAC_ADDRESS_VALUE = "00:00:00:00:00:01";
    public static final String IPV4_ADDRESS_VALUE = "255.255.255.255";
    public static final int PORT_NUMBER_VALUE = 3;
    public static final int SET_FIELD_VALUE = 4;
    public static final int VNI_NOVIFLOW_ACTION_VALUE = 5;
    public static final int UDP_NOVIFLOW_ACTION_VALUE = 6;
    public static final int VNI_OVS_ACTION_VALUE = 7;
    public static final int UDP_OVS_ACTION_VALUE = 8;
    public static final int METER_ID_VALUE = 9;
    public static final int OF_METADATA_VALUE = 10;
    public static final int OF_METADATA_MASK = 11;
    public static final int COOKIE_VALUE = 12;
    public static final int MATCH_VALUE = 13;
    public static final long MATCH_MASK = 3L;
    public static final int DURATION_SECONDS = 14;
    public static final int DURATION_NANOSECONDS = 15;
    public static final int PACKET_COUNT = 16;
    public static final int IDLE_TIMEOUT = 18;
    public static final int HARD_TIMEOUT = 19;
    public static final int BYTE_COUNT = 20;
    public static Long RATE = 4L;
    public static Long BURST_SIZE = 5L;
    public static int LAG_PORT = 21;
    public static int PHYSICAL_PORT_1 = 22;
    public static int PHYSICAL_PORT_2 = 23;

    public static MeterId METER_ID = new MeterId(METER_ID_VALUE);
    public static OfTable OF_TABLE_FIELD = OfTable.INGRESS;
    public static OfVersion OF_VERSION = OfVersion.OF_15;
    public static OfMetadata OF_METADATA = new OfMetadata(OF_METADATA_VALUE, OF_METADATA_MASK);
    public static Cookie COOKIE = new Cookie(COOKIE_VALUE);
    public static Field FIELD = Field.METADATA;
    public static SetFieldAction SET_FIELD_ACTION = new SetFieldAction(SET_FIELD_VALUE, FIELD);
    public static GroupId GROUP_ID = new GroupId(GROUP_ID_VALUE);
    public static GroupType GROUP_TYPE = GroupType.ALL;
    public static UUID RANDOM_UUID = UUID.randomUUID();
    public static MacAddress MAC_ADDRESS = new MacAddress(MAC_ADDRESS_VALUE);
    public static IPv4Address IPV4_ADDRESS = new IPv4Address(IPV4_ADDRESS_VALUE);
    public static ProtoConstants.PortNumber PORT_NUMBER = new ProtoConstants.PortNumber(PORT_NUMBER_VALUE);
    public static PortOutAction PORT_OUT_ACTION = new PortOutAction(PORT_NUMBER);
    public static PushVxlanAction PUSH_VXLAN_NOVIFLOW_ACTION = PushVxlanAction.builder()
            .type(ActionType.PUSH_VXLAN_NOVIFLOW)
            .vni(VNI_NOVIFLOW_ACTION_VALUE)
            .srcMacAddress(MAC_ADDRESS)
            .dstMacAddress(MAC_ADDRESS)
            .srcIpv4Address(IPV4_ADDRESS)
            .dstIpv4Address(IPV4_ADDRESS)
            .udpSrc(UDP_NOVIFLOW_ACTION_VALUE)
            .build();
    public static PushVxlanAction PUSH_VXLAN_OVS_ACTION = PushVxlanAction.builder()
            .type(ActionType.PUSH_VXLAN_OVS)
            .vni(VNI_OVS_ACTION_VALUE)
            .srcMacAddress(MAC_ADDRESS)
            .dstMacAddress(MAC_ADDRESS)
            .srcIpv4Address(IPV4_ADDRESS)
            .dstIpv4Address(IPV4_ADDRESS)
            .udpSrc(UDP_OVS_ACTION_VALUE)
            .build();

    public static Set<Action> groupSpeakerWriteActions = new HashSet<>();
    public static List<Bucket> buckets = new LinkedList<>();

    private static GroupSpeakerData initializeGroupSpeakerData(SwitchId uniqueSwitchIdField) {
        groupSpeakerWriteActions.add(SET_FIELD_ACTION);
        groupSpeakerWriteActions.add(PORT_OUT_ACTION);
        groupSpeakerWriteActions.add(PUSH_VXLAN_NOVIFLOW_ACTION);
        groupSpeakerWriteActions.add(PUSH_VXLAN_OVS_ACTION);

        buckets.add(new Bucket(WatchGroup.ALL, WatchPort.ANY, groupSpeakerWriteActions));

        return GroupSpeakerData.builder()
                .uuid(RANDOM_UUID)
                .switchId(uniqueSwitchIdField)
                .dependsOn(Collections.emptyList())
                .ofVersion(OF_VERSION)
                .groupId(GROUP_ID)
                .type(GROUP_TYPE)
                .buckets(buckets)
                .build();
    }

    public static List<Action> applyActions = new LinkedList<>();
    public static Set<Action> flowSpeakerWriteActions = new HashSet<>();
    public static Set<OfFlowFlag> flowFlags = new HashSet<>();
    public static Set<FieldMatch> matches = new HashSet<>();
    public static Instructions instructions;

    private static FlowSpeakerData initializeFlowSpeakerData(int uniquePriorityField) {

        applyActions.add(SET_FIELD_ACTION);
        for (Field field : Field.values()) {
            matches.add(new FieldMatch(MATCH_VALUE, MATCH_MASK, field));
        }
        flowFlags.add(OfFlowFlag.RESET_COUNTERS);

        instructions = new Instructions(applyActions, flowSpeakerWriteActions, METER_ID, OF_TABLE_FIELD,
                OF_METADATA);

        return FlowSpeakerData.builder()
                .cookie(COOKIE)
                .durationSeconds(DURATION_SECONDS)
                .durationNanoSeconds(DURATION_NANOSECONDS)
                .table(OF_TABLE_FIELD)
                .packetCount(PACKET_COUNT)
                .ofVersion(OF_VERSION)
                .priority(uniquePriorityField)
                .idleTimeout(IDLE_TIMEOUT)
                .hardTimeout(HARD_TIMEOUT)
                .byteCount(BYTE_COUNT)
                .match(matches)
                .instructions(instructions)
                .flags(flowFlags)
                .build();
    }

    public static Set<MeterFlag> meterFlags = new HashSet<>();

    private static MeterSpeakerData initializeMeterSpeakerData(MeterId uniqueMeterIdValue) {
        meterFlags.add(MeterFlag.BURST);

        return MeterSpeakerData.builder()
                .meterId(uniqueMeterIdValue)
                .rate(RATE)
                .burst(BURST_SIZE)
                .flags(meterFlags)
                .build();

    }

    private static LagLogicalPort initializeLogicalPortData(SwitchId uniqueSwitchIdField) {
        return new LagLogicalPort(uniqueSwitchIdField, LAG_PORT,
                Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2));
    }

    private static List<GroupInfoEntryV2> missingGroups = new LinkedList<>();
    private static List<GroupInfoEntryV2> properGroups = new LinkedList<>();
    private static List<GroupInfoEntryV2> excessGroups = new LinkedList<>();
    private static List<MisconfiguredInfo<GroupInfoEntryV2>> misconfiguredGroups = new LinkedList<>();

    private static Set<RuleInfoEntryV2> missingRules = new HashSet<>();
    private static Set<RuleInfoEntryV2> properRules = new HashSet<>();
    private static Set<RuleInfoEntryV2> excessRules = new HashSet<>();
    private static Set<MisconfiguredInfo<RuleInfoEntryV2>> misconfiguredRules = new HashSet<>();

    private static List<MeterInfoEntryV2> missingMeters = new LinkedList<>();
    private static List<MeterInfoEntryV2> properMeters = new LinkedList<>();
    private static List<MeterInfoEntryV2> excessMeters = new LinkedList<>();
    private static List<MisconfiguredInfo<MeterInfoEntryV2>> misconfiguredMeters = new LinkedList<>();

    private static List<LogicalPortInfoEntryV2> missingPorts = new LinkedList<>();
    private static List<LogicalPortInfoEntryV2> properPorts = new LinkedList<>();
    private static List<LogicalPortInfoEntryV2> excessPorts = new LinkedList<>();
    private static List<MisconfiguredInfo<LogicalPortInfoEntryV2>> misconfiguredPorts = new LinkedList<>();

    private static ValidateGroupsResultV2 groupsResult;
    private static ValidateRulesResultV2 rulesResult;
    private static ValidateMetersResultV2 metersResult;
    private static ValidateLogicalPortsResultV2 logicalPortsResult;

    @BeforeClass
    public static void initializeData() {
        missingGroups.add(GroupEntryConverter.INSTANCE.toGroupEntry(initializeGroupSpeakerData(new SwitchId(1))));
        properGroups.add(GroupEntryConverter.INSTANCE.toGroupEntry(initializeGroupSpeakerData(new SwitchId(2))));
        excessGroups.add(GroupEntryConverter.INSTANCE.toGroupEntry(initializeGroupSpeakerData(new SwitchId(3))));
        misconfiguredGroups.add(MisconfiguredInfo.<GroupInfoEntryV2>builder()
                .id(11L)
                .expected(GroupEntryConverter.INSTANCE.toGroupEntry(initializeGroupSpeakerData(new SwitchId(4))))
                .discrepancies(GroupEntryConverter.INSTANCE.toGroupEntry(initializeGroupSpeakerData(new SwitchId(5))))
                .build());

        missingRules.add(RuleEntryConverter.INSTANCE.toRuleEntry(initializeFlowSpeakerData(1)));
        properRules.add(RuleEntryConverter.INSTANCE.toRuleEntry(initializeFlowSpeakerData(2)));
        excessRules.add(RuleEntryConverter.INSTANCE.toRuleEntry(initializeFlowSpeakerData(3)));
        misconfiguredRules.add(MisconfiguredInfo.<RuleInfoEntryV2>builder()
                .id(1L)
                .expected(RuleEntryConverter.INSTANCE.toRuleEntry(initializeFlowSpeakerData(4)))
                .discrepancies(RuleEntryConverter.INSTANCE.toRuleEntry(initializeFlowSpeakerData(5)))
                .build());

        missingMeters.add(MeterEntryConverter.INSTANCE.toMeterEntry(initializeMeterSpeakerData(new MeterId(1))));
        properMeters.add(MeterEntryConverter.INSTANCE.toMeterEntry(initializeMeterSpeakerData(
                new MeterId(2))));
        excessMeters.add(MeterEntryConverter.INSTANCE.toMeterEntry(initializeMeterSpeakerData(
                new MeterId(3))));
        misconfiguredMeters.add(MisconfiguredInfo.<MeterInfoEntryV2>builder()
                .id(1L)
                .expected(MeterEntryConverter.INSTANCE.toMeterEntry(initializeMeterSpeakerData(new MeterId(4))))
                .discrepancies(MeterEntryConverter.INSTANCE.toMeterEntry(
                        initializeMeterSpeakerData(new MeterId(5))))
                .build());

        properPorts.add(LogicalPortMapper.INSTANCE.map(initializeLogicalPortData(new SwitchId(1))));
        missingPorts.add(LogicalPortMapper.INSTANCE.map(initializeLogicalPortData(new SwitchId(2))));
        excessPorts.add(LogicalPortMapper.INSTANCE.map(initializeLogicalPortData(new SwitchId(3))));
        misconfiguredPorts.add(MisconfiguredInfo.<LogicalPortInfoEntryV2>builder()
                .id(1L)
                .expected(LogicalPortMapper.INSTANCE.map(initializeLogicalPortData(new SwitchId(4))))
                .discrepancies(LogicalPortMapper.INSTANCE.map(initializeLogicalPortData(new SwitchId(5))))
                .build());

        groupsResult = new ValidateGroupsResultV2(false, missingGroups, properGroups, excessGroups,
                misconfiguredGroups);
        rulesResult = new ValidateRulesResultV2(false, missingRules, properRules, excessRules,
                misconfiguredRules);
        metersResult = new ValidateMetersResultV2(false,
                missingMeters, properMeters, excessMeters, misconfiguredMeters);
        logicalPortsResult = new ValidateLogicalPortsResultV2(false, missingPorts, properPorts,
                excessPorts, misconfiguredPorts, "Test error");
    }

    @Test
    public void mapValidationTest() {

        SwitchValidationContext context = SwitchValidationContext.builder(new SwitchId(1))
                .validateGroupsResult(groupsResult)
                .metersValidationReport(metersResult)
                .validateLogicalPortResult(logicalPortsResult)
                .ofFlowsValidationReport(rulesResult)
                .build();

        SwitchValidationResponseV2 response = ValidationMapper.INSTANCE.toSwitchResponse(context);

        GroupsValidationEntryV2 groupsEntry = response.getGroups();
        assertEquals(missingGroups, groupsEntry.getMissing());
        assertEquals(properGroups, groupsEntry.getProper());
        assertEquals(excessGroups, groupsEntry.getExcess());
        assertEquals(misconfiguredGroups, groupsEntry.getMisconfigured());

        RulesValidationEntryV2 rulesEntry = response.getRules();
        assertEquals(missingRules, rulesEntry.getMissing());
        assertEquals(properRules, rulesEntry.getProper());
        assertEquals(excessRules, rulesEntry.getExcess());
        assertEquals(misconfiguredRules, rulesEntry.getMisconfigured());

        LogicalPortsValidationEntryV2 portsEntry = response.getLogicalPorts();
        assertEquals(missingPorts, portsEntry.getMissing());
        assertEquals(properPorts, portsEntry.getProper());
        assertEquals(excessPorts, portsEntry.getExcess());
        assertEquals(misconfiguredPorts, portsEntry.getMisconfigured());

        MetersValidationEntryV2 metersEntry = response.getMeters();
        assertEquals(missingMeters, metersEntry.getMissing());
        assertEquals(properMeters, metersEntry.getProper());
        assertEquals(excessMeters, metersEntry.getExcess());
        assertEquals(misconfiguredMeters, metersEntry.getMisconfigured());
    }
}
