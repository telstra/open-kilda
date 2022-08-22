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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.LogicalPortInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MeterInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.action.BaseAction;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.LogicalPortType;
import org.openkilda.model.GroupId;
import org.openkilda.model.IPv4Address;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
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
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.MeterAction;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.wfm.topology.switchmanager.mappers.LogicalPortMapper;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateGroupsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateLogicalPortsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateMetersResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateRulesResultV2;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class ValidationServiceImplTest {

    private static final SwitchId SWITCH_ID_A = new SwitchId("00:10");
    private static final SwitchId SWITCH_ID_B = new SwitchId("00:20");
    private static final SwitchId SWITCH_ID_E = new SwitchId("00:30");
    private static final long FLOW_E_BANDWIDTH = 10000L;
    private static final Switch switchA = Switch.builder()
            .switchId(SWITCH_ID_A)
            .description("Nicira, Inc. OF_13 2.5.5")
            .build();
    private static final Switch switchB = Switch.builder()
            .switchId(SWITCH_ID_B)
            .description("Nicira, Inc. OF_13 2.5.5")
            .build();
    public static final int LOGICAL_PORT_NUMBER_1 = 2001;
    public static final int LOGICAL_PORT_NUMBER_2 = 2003;
    public static final int LOGICAL_PORT_NUMBER_3 = 2005;
    public static final int LOGICAL_PORT_NUMBER_4 = 2006;
    public static final int LOGICAL_PORT_NUMBER_5 = 2007;
    public static final int PHYSICAL_PORT_1 = 1;
    public static final int PHYSICAL_PORT_2 = 2;
    public static final int PHYSICAL_PORT_3 = 3;
    public static final int PHYSICAL_PORT_4 = 4;
    public static final int PHYSICAL_PORT_5 = 5;
    public static final int PHYSICAL_PORT_6 = 6;
    public static final int PHYSICAL_PORT_7 = 7;

    public static final MeterId METER_ID_1 = new MeterId(1);
    public static final GroupId GROUP_ID_1 = new GroupId(3);
    public static final PortNumber PORT_NUMBER = new PortNumber(5);
    public static final short VLAN_ID = 6;
    public static final int VNI = 7;
    public static final MacAddress SRC_MAC_ADDRESS = new MacAddress("11:11:11:11:11:11");
    public static final MacAddress DST_MAC_ADDRESS = new MacAddress("22:22:22:22:22:22");
    public static final IPv4Address SRC_IPV4_ADDRESS = new IPv4Address("192.168.0.1");
    public static final IPv4Address DST_IPV4_ADDRESS = new IPv4Address("192.168.0.2");
    public static final int UDP_SRC = 8;
    public static final int NUMBER_OF_BITS = 9;
    public static final SwitchId SWITCH_ID = new SwitchId(10);
    public static final int PRIORITY = 11;

    @Mock
    private RuleManager ruleManager;

    @Test
    public void validateGroupsEmpty() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        ValidateGroupsResultV2 response = validationService.validateGroups(SWITCH_ID_A, emptyList(), emptyList());

        assertTrue(response.getExcessGroups().isEmpty());
        assertTrue(response.getMissingGroups().isEmpty());
        assertTrue(response.getMisconfiguredGroups().isEmpty());
        assertTrue(response.getProperGroups().isEmpty());
        assertTrue(response.isAsExpected());
    }

    @Test
    public void validateGroupsProperGroups() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        Bucket bucket = Bucket.builder()
                .watchGroup(WatchGroup.ANY)
                .watchPort(WatchPort.ANY)
                .writeActions(Sets.newHashSet(buildGroupActions(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, PORT_NUMBER,
                        VLAN_ID, VNI)))
                .build();

        List<GroupSpeakerData> groupSpeakerData = new ArrayList<>();
        groupSpeakerData.add(buildFullGroupSpeakerCommandData(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, bucket));

        ValidateGroupsResultV2 response = validationService.validateGroups(SWITCH_ID_A, groupSpeakerData,
                groupSpeakerData);

        assertTrue(response.getExcessGroups().isEmpty());
        assertTrue(response.getMissingGroups().isEmpty());
        assertTrue(response.getMisconfiguredGroups().isEmpty());
        assertFalse(response.getProperGroups().isEmpty());
        assertTrue(response.isAsExpected());

        assertGroups(response.getProperGroups().get(0), groupSpeakerData.get(0).getGroupId().intValue(),
                PORT_NUMBER.getPortNumber(), VLAN_ID, VNI);
    }

    @Test
    public void validateGroupsMissingAndExcessGroups() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        Bucket bucket = Bucket.builder()
                .watchGroup(WatchGroup.ALL)
                .watchPort(WatchPort.ANY)
                .writeActions(Sets.newHashSet(buildGroupActions(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, PORT_NUMBER,
                        VLAN_ID, VNI)))
                .build();

        List<GroupSpeakerData> expectedGroupData = new ArrayList<>();
        expectedGroupData.add(buildFullGroupSpeakerCommandData(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, bucket));

        List<GroupSpeakerData> actualGroupData = new ArrayList<>();
        actualGroupData.add(buildFullGroupSpeakerCommandData(GroupId.MIN_FLOW_GROUP_ID, bucket));

        ValidateGroupsResultV2 response = validationService.validateGroups(SWITCH_ID_A, actualGroupData,
                expectedGroupData);

        assertFalse(response.getExcessGroups().isEmpty());
        assertFalse(response.getMissingGroups().isEmpty());
        assertTrue(response.getMisconfiguredGroups().isEmpty());
        assertTrue(response.getProperGroups().isEmpty());
        assertFalse(response.isAsExpected());

        assertGroups(response.getMissingGroups().get(0), expectedGroupData.get(0).getGroupId().intValue(),
                PORT_NUMBER.getPortNumber(), VLAN_ID, VNI);
        assertGroups(response.getExcessGroups().get(0), actualGroupData.get(0).getGroupId().intValue(),
                PORT_NUMBER.getPortNumber(), VLAN_ID, VNI);
    }

    @Test
    public void validateGroupsMisconfiguredGroups() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        Bucket expectedBucket = Bucket.builder()
                .watchGroup(WatchGroup.ALL)
                .watchPort(WatchPort.ANY)
                .writeActions(Sets.newHashSet(buildGroupActions(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, PORT_NUMBER,
                        VLAN_ID, VNI)))
                .build();
        List<GroupSpeakerData> expectedGroupData = new ArrayList<>();
        expectedGroupData.add(buildFullGroupSpeakerCommandData(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, expectedBucket));

        Bucket actualBucket = Bucket.builder()
                .watchGroup(WatchGroup.ALL)
                .watchPort(WatchPort.ANY)
                .writeActions(Sets.newHashSet(buildGroupActions(GroupId.ROUND_TRIP_LATENCY_GROUP_ID,
                        new PortNumber(PORT_NUMBER.getPortNumber() + 1), VLAN_ID, VNI)))
                .build();
        List<GroupSpeakerData> actualGroupData = new ArrayList<>();
        actualGroupData.add(buildFullGroupSpeakerCommandData(GroupId.ROUND_TRIP_LATENCY_GROUP_ID, actualBucket));

        ValidateGroupsResultV2 response = validationService.validateGroups(SWITCH_ID_A, actualGroupData,
                expectedGroupData);

        assertTrue(response.getExcessGroups().isEmpty());
        assertTrue(response.getMissingGroups().isEmpty());
        assertFalse(response.getMisconfiguredGroups().isEmpty());
        assertTrue(response.getProperGroups().isEmpty());
        assertFalse(response.isAsExpected());

        assertEquals(String.valueOf(expectedGroupData.get(0).getGroupId().getValue()),
                response.getMisconfiguredGroups().get(0).getId());

        assertGroups(response.getMisconfiguredGroups().get(0).getExpected(), expectedGroupData.get(0).getGroupId()
                .intValue(), PORT_NUMBER.getPortNumber(), VLAN_ID, VNI);

        //discrepancies
        GroupInfoEntryV2 discrepancies = response.getMisconfiguredGroups().get(0).getDiscrepancies();

        assertEquals(VLAN_ID, discrepancies.getBuckets().get(0).getVlan().intValue());
        assertEquals(PORT_NUMBER.getPortNumber() + 1, discrepancies.getBuckets().get(0).getPort().intValue());
        assertEquals(VNI, discrepancies.getBuckets().get(0).getVni().intValue());
    }

    @Test
    public void validateMetersEmpty() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        ValidateMetersResultV2 response = validationService.validateMeters(SWITCH_ID_A, emptyList(), emptyList());

        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
        assertTrue(response.isAsExpected());
    }

    @Test
    public void validateMetersProperMeters() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        MeterSpeakerData meter = buildFullMeterSpeakerCommandData(32, 1000, 10500,
                Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS));

        ValidateMetersResultV2 response = validationService.validateMeters(SWITCH_ID_B,
                singletonList(meter),
                singletonList(meter));

        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertFalse(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
        assertTrue(response.isAsExpected());

        assertEquals(meter.getMeterId().getValue(), response.getProperMeters().get(0).getMeterId().longValue());
        assertMeters(response.getProperMeters().get(0), meter.getMeterId().getValue(), meter.getRate(),
                meter.getBurst(), meter.getFlags().stream().map(MeterFlag::name).collect(Collectors.toSet()));
    }

    @Test
    public void validateMetersMissingAndExcessMeters() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        MeterSpeakerData actualMeter = buildFullMeterSpeakerCommandData(33, 10000, 10500,
                Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS));
        MeterSpeakerData expectedMeter = buildFullMeterSpeakerCommandData(32, 10000, 10500,
                Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS));

        ValidateMetersResultV2 response = validationService.validateMeters(SWITCH_ID_B,
                singletonList(actualMeter),
                singletonList(expectedMeter));

        assertFalse(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertTrue(response.getProperMeters().isEmpty());
        assertFalse(response.getExcessMeters().isEmpty());
        assertFalse(response.isAsExpected());

        assertMeters(response.getExcessMeters().get(0), actualMeter.getMeterId().getValue(), actualMeter.getRate(),
                actualMeter.getBurst(), actualMeter.getFlags().stream().map(MeterFlag::name)
                        .collect(Collectors.toSet()));
        assertMeters(response.getMissingMeters().get(0), expectedMeter.getMeterId().getValue(), expectedMeter.getRate(),
                expectedMeter.getBurst(), expectedMeter.getFlags().stream().map(MeterFlag::name)
                        .collect(Collectors.toSet()));
    }

    @Test
    public void validateMetersMisconfiguredMeters() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        MeterSpeakerData actualMeter = buildFullMeterSpeakerCommandData(32, 10002, 10498,
                Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS));
        MeterSpeakerData expectedMeter = buildFullMeterSpeakerCommandData(32, 10000, 10500,
                Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS));

        ValidateMetersResultV2 response = validationService.validateMeters(SWITCH_ID_B,
                singletonList(actualMeter),
                singletonList(expectedMeter));

        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
        assertFalse(response.getMisconfiguredMeters().isEmpty());
        assertFalse(response.isAsExpected());

        assertEquals(String.valueOf(expectedMeter.getMeterId().getValue()),
                response.getMisconfiguredMeters().get(0).getId());
        assertEquals(10002, (long) response.getMisconfiguredMeters().get(0).getDiscrepancies().getRate());
        assertEquals(10498, (long) response.getMisconfiguredMeters().get(0).getDiscrepancies().getBurstSize());
        assertEquals(actualMeter.getFlags().stream().map(MeterFlag::name).collect(Collectors.toList()),
                response.getMisconfiguredMeters().get(0).getDiscrepancies().getFlags());

        assertMeters(response.getMisconfiguredMeters().get(0).getExpected(), expectedMeter.getMeterId().getValue(),
                expectedMeter.getRate(), expectedMeter.getBurst(), expectedMeter.getFlags().stream()
                        .map(MeterFlag::name).collect(Collectors.toSet()));
    }

    @Test
    public void validateMetersProperMetersESwitch() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        long rateESwitch = FLOW_E_BANDWIDTH + (long) (FLOW_E_BANDWIDTH * 0.01) - 1;
        long burstSize = (long) (FLOW_E_BANDWIDTH * 1.05);
        long burstSizeESwitch = burstSize + (long) (burstSize * 0.01) - 1;

        MeterSpeakerData meter = buildFullMeterSpeakerCommandData(32, rateESwitch, burstSizeESwitch,
                Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS));

        ValidateMetersResultV2 response = validationService.validateMeters(SWITCH_ID_E,
                singletonList(meter),
                singletonList(meter));

        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertFalse(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
        assertTrue(response.isAsExpected());

        assertEquals(32L, (long) response.getProperMeters().get(0).getMeterId());
        assertMeters(response.getProperMeters().get(0), meter.getMeterId().getValue(), meter.getRate(),
                meter.getBurst(), meter.getFlags().stream().map(MeterFlag::name).collect(Collectors.toSet()));
    }

    @Test
    public void validateMetersMisconfiguredMetersESwitch() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        long rateESwitch = FLOW_E_BANDWIDTH + (long) (FLOW_E_BANDWIDTH * 0.01) + 1;
        long burstSize = (long) (FLOW_E_BANDWIDTH * 1.05);
        long burstSizeESwitch = burstSize + (long) (burstSize * 0.01) + 1;

        MeterSpeakerData actualMeter = buildFullMeterSpeakerCommandData(32, rateESwitch, burstSizeESwitch,
                Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS));
        MeterSpeakerData expectedMeter = buildFullMeterSpeakerCommandData(32, rateESwitch, burstSize,
                Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS));

        ValidateMetersResultV2 response = validationService.validateMeters(SWITCH_ID_E,
                singletonList(actualMeter),
                singletonList(expectedMeter));

        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
        assertFalse(response.getMisconfiguredMeters().isEmpty());
        assertFalse(response.isAsExpected());

        //discrepancies
        MeterInfoEntryV2 discrepancies = response.getMisconfiguredMeters().get(0).getDiscrepancies();

        assertEquals(String.valueOf(expectedMeter.getMeterId().getValue()),
                response.getMisconfiguredMeters().get(0).getId());
        assertEquals(actualMeter.getBurst(), (long) discrepancies.getBurstSize());
        assertNull(discrepancies.getRate());
        assertNull(discrepancies.getFlags());

        assertMeters(response.getMisconfiguredMeters().get(0).getExpected(), expectedMeter.getMeterId().getValue(),
                expectedMeter.getRate(), expectedMeter.getBurst(), expectedMeter.getFlags().stream()
                        .map(MeterFlag::name).collect(Collectors.toSet()));
    }

    @Test
    public void validateLogicalPorts() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        LogicalPort proper = buildLogicalPort(LOGICAL_PORT_NUMBER_1, PHYSICAL_PORT_2, PHYSICAL_PORT_1);
        LogicalPort misconfigured = buildLogicalPort(LOGICAL_PORT_NUMBER_2, LogicalPortType.BFD, PHYSICAL_PORT_3);
        LogicalPort excess = buildLogicalPort(LOGICAL_PORT_NUMBER_4, PHYSICAL_PORT_6);
        LogicalPort bfdExcess = buildLogicalPort(LOGICAL_PORT_NUMBER_5, LogicalPortType.BFD, PHYSICAL_PORT_7);

        ValidateLogicalPortsResultV2 result = validationService.validateLogicalPorts(SWITCH_ID_A, Lists.newArrayList(
                proper, misconfigured, excess, bfdExcess));
        assertEquals(1, result.getProperLogicalPorts().size());
        assertEquals(1, result.getExcessLogicalPorts().size()); // bfdExcess port shouldn't be in this list
        assertEquals(1, result.getMissingLogicalPorts().size());
        assertEquals(1, result.getMisconfiguredLogicalPorts().size());
        assertFalse(result.isAsExpected());

        assertEqualLogicalPort(proper, result.getProperLogicalPorts().get(0));
        assertEqualLogicalPort(excess, result.getExcessLogicalPorts().get(0));

        LogicalPortInfoEntryV2 missing = LogicalPortInfoEntryV2.builder()
                .type(org.openkilda.messaging.info.switches.LogicalPortType.LAG)
                .logicalPortNumber(LOGICAL_PORT_NUMBER_3)
                .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_5, PHYSICAL_PORT_6))
                .build();
        assertEquals(missing, result.getMissingLogicalPorts().get(0));

        MisconfiguredInfo<LogicalPortInfoEntryV2> misconfiguredEntry = MisconfiguredInfo
                .<LogicalPortInfoEntryV2>builder()
                .id(String.valueOf(LOGICAL_PORT_NUMBER_2))
                .expected(LogicalPortInfoEntryV2.builder()
                        .logicalPortNumber(misconfigured.getLogicalPortNumber())
                        .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_3, PHYSICAL_PORT_4))
                        .type(org.openkilda.messaging.info.switches.LogicalPortType.LAG)
                        .build())
                .discrepancies(LogicalPortInfoEntryV2.builder()
                        .physicalPorts(misconfigured.getPortNumbers())
                        .type(org.openkilda.messaging.info.switches.LogicalPortType.of(misconfigured.getType().name()))
                        .build())
                .build();

        assertEquals(misconfiguredEntry, result.getMisconfiguredLogicalPorts().get(0));
    }

    @Test
    public void calculateMisconfiguredLogicalPortDifferentPortOrderTest() {
        ValidationServiceImpl validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        LogicalPortInfoEntryV2 actual = LogicalPortInfoEntryV2.builder()
                .type(org.openkilda.messaging.info.switches.LogicalPortType.LAG)
                .logicalPortNumber(LOGICAL_PORT_NUMBER_1)
                .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2, PHYSICAL_PORT_3))
                .build();

        LogicalPortInfoEntryV2 expected = LogicalPortInfoEntryV2.builder()
                .type(org.openkilda.messaging.info.switches.LogicalPortType.LAG)
                .logicalPortNumber(LOGICAL_PORT_NUMBER_1)
                .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_3, PHYSICAL_PORT_2, PHYSICAL_PORT_1))
                .build();

        MisconfiguredInfo<LogicalPortInfoEntryV2> difference =
                validationService.calculateMisconfiguredLogicalPort(expected, actual);
        // physical ports are equal. Only order is different. So port difference must be null
        assertNull(difference.getDiscrepancies().getPhysicalPorts());
    }

    @Test
    public void validateRulesEmpty() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        ValidateRulesResultV2 response = validationService.validateRules(SWITCH_ID_A, emptyList(), emptyList());

        assertTrue(response.isAsExpected());
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
        assertTrue(response.getMisconfiguredRules().isEmpty());
    }

    @Test
    public void validateRulesProperRules() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        Set<FieldMatch> match = Arrays.stream(Field.values())
                .map(f -> FieldMatch.builder().field(f).value(f.ordinal()).mask(f.ordinal() + 1L).build())
                .collect(Collectors.toSet());

        List<FlowSpeakerData> flowSpeakerData = Lists.newArrayList(buildFullFlowSpeakerCommandData(OfTable.INPUT,
                PRIORITY, match));

        ValidateRulesResultV2 response = validationService.validateRules(SWITCH_ID_A, flowSpeakerData, flowSpeakerData);

        assertTrue(response.getExcessRules().isEmpty());
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getMisconfiguredRules().isEmpty());
        assertFalse(response.getProperRules().isEmpty());
        assertTrue(response.isAsExpected());

        assertRules(flowSpeakerData.get(0), response.getProperRules().stream().findFirst().get());
    }

    @Test
    public void validateRulesMissingExcessRules() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        Set<FieldMatch> match = Arrays.stream(Field.values())
                .map(f -> FieldMatch.builder().field(f).value(f.ordinal()).mask(f.ordinal() + 1L).build())
                .collect(Collectors.toSet());

        //1st case: different tableIds
        List<FlowSpeakerData> expected = Lists.newArrayList(buildFullFlowSpeakerCommandData(
                OfTable.INPUT, PRIORITY, match));
        List<FlowSpeakerData> actual = Lists.newArrayList(buildFullFlowSpeakerCommandData(
                OfTable.EGRESS, PRIORITY, match));

        ValidateRulesResultV2 response = validationService.validateRules(SWITCH_ID_A, actual, expected);

        assertFalse(response.getExcessRules().isEmpty());
        assertFalse(response.getMissingRules().isEmpty());
        assertTrue(response.getMisconfiguredRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertFalse(response.isAsExpected());

        assertRules(expected.get(0), response.getMissingRules().stream().findFirst().get());
        assertRules(actual.get(0), response.getExcessRules().stream().findFirst().get());

        //2d case: different priorities
        expected = Lists.newArrayList(buildFullFlowSpeakerCommandData(OfTable.INPUT, PRIORITY, match));
        actual = Lists.newArrayList(buildFullFlowSpeakerCommandData(OfTable.INPUT, PRIORITY + 100, match));

        response = validationService.validateRules(SWITCH_ID_A, actual, expected);

        assertFalse(response.getExcessRules().isEmpty());
        assertFalse(response.getMissingRules().isEmpty());
        assertTrue(response.getMisconfiguredRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertFalse(response.isAsExpected());

        assertRules(expected.get(0), response.getMissingRules().stream().findFirst().get());
        assertRules(actual.get(0), response.getExcessRules().stream().findFirst().get());

        //3d case: different matches
        Set<FieldMatch> newMatch = Arrays.stream(Field.values())
                .map(f -> FieldMatch.builder().field(f).value(f.ordinal()).mask(f.ordinal() + 5L).build())
                .collect(Collectors.toSet());

        expected = Lists.newArrayList(buildFullFlowSpeakerCommandData(OfTable.INPUT, PRIORITY, match));
        actual = Lists.newArrayList(buildFullFlowSpeakerCommandData(OfTable.INPUT, PRIORITY, newMatch));

        response = validationService.validateRules(SWITCH_ID_A, actual, expected);

        assertFalse(response.getExcessRules().isEmpty());
        assertFalse(response.getMissingRules().isEmpty());
        assertTrue(response.getMisconfiguredRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertFalse(response.isAsExpected());

        assertRules(expected.get(0), response.getMissingRules().stream().findFirst().get());
        assertRules(actual.get(0), response.getExcessRules().stream().findFirst().get());
    }

    @Test
    public void validateRulesMisconfiguredRules() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        Set<FieldMatch> match = Arrays.stream(Field.values())
                .map(f -> FieldMatch.builder().field(f).value(f.ordinal()).mask(f.ordinal() + 1L).build())
                .collect(Collectors.toSet());

        List<FlowSpeakerData> expected = Lists.newArrayList(buildFullFlowSpeakerCommandData(
                OfTable.INPUT, PRIORITY, match));

        List<Action> applyActions = Lists.newArrayList(buildAllActions().get(0));
        applyActions.addAll(buildAllActions());

        Instructions instructions = Instructions.builder()
                .goToMeter(new MeterId(1))
                .goToTable(OfTable.INGRESS)
                .writeMetadata(new OfMetadata(22, 0xf3))
                .writeActions(Sets.newHashSet(buildAllActions().get(0)))
                .applyActions(applyActions)
                .build();

        FlowSpeakerData misconfigured = FlowSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .cookie(new Cookie(4322))
                .priority(expected.get(0).getPriority())
                .table(expected.get(0).getTable())
                .match(match)
                .instructions(instructions)
                .switchId(SWITCH_ID)
                .ofVersion(OfVersion.OF_13)
                .build();

        List<FlowSpeakerData> actual = Lists.newArrayList(misconfigured);

        ValidateRulesResultV2 response = validationService.validateRules(SWITCH_ID_A, actual, expected);

        assertTrue(response.getExcessRules().isEmpty());
        assertTrue(response.getMissingRules().isEmpty());
        assertFalse(response.getMisconfiguredRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertFalse(response.isAsExpected());

        StringBuilder id = new StringBuilder(format("tableId=%s,priority=%s,", expected.get(0).getTable().getTableId(),
                expected.get(0).getPriority()));

        TreeMap<String, RuleInfoEntryV2.FieldMatch> matches = new TreeMap<>();

        for (FieldMatch fieldMatch : expected.get(0).getMatch()) {
            RuleInfoEntryV2.FieldMatch info = convertFieldMatch(fieldMatch);

            String fieldName = Optional.ofNullable(fieldMatch.getField())
                    .map(Field::name)
                    .orElse(null);
            matches.put(fieldName, info);

        }

        for (String speakerDataMatch : matches.keySet()) {
            RuleInfoEntryV2.FieldMatch value = matches.get(speakerDataMatch);
            id.append(format("%s:value=%s,mask=%s,", speakerDataMatch, value.getValue(), value.getMask()));
        }

        assertEquals(id.toString(), new ArrayList<>(response.getMisconfiguredRules()).get(0).getId());
        assertRules(expected.get(0), new ArrayList<>(response.getMisconfiguredRules()).get(0).getExpected());

        //discrepancies
        RuleInfoEntryV2 discrepancies = Lists.newArrayList(response.getMisconfiguredRules()).get(0).getDiscrepancies();

        assertEquals(actual.get(0).getCookie().getValue(), discrepancies.getCookie().intValue());
        assertEquals(actual.get(0).getFlags().stream().map(OfFlowFlag::name).collect(Collectors.toList()),
                discrepancies.getFlags());
        assertInstructions(actual.get(0).getInstructions(), discrepancies.getInstructions());

        assertNull(discrepancies.getMatch());
        assertNull(discrepancies.getTableId());
        assertNull(discrepancies.getPriority());
    }

    private RuleInfoEntryV2.FieldMatch convertFieldMatch(FieldMatch fieldMatch) {
        Long mask = fieldMatch.getMask() == null || fieldMatch.getMask() == -1 ? null : fieldMatch.getMask();

        return RuleInfoEntryV2.FieldMatch.builder()
                .mask(mask)
                .value(Optional.of(fieldMatch.getValue())
                        .orElse(null))
                .build();
    }

    //assert
    private void assertRules(FlowSpeakerData speakerData, RuleInfoEntryV2 ruleInfo) {
        assertEquals(speakerData.getCookie().getValue(), ruleInfo.getCookie().intValue());
        assertEquals(speakerData.getTable().getTableId(), ruleInfo.getTableId().intValue());
        assertEquals(speakerData.getPriority(), ruleInfo.getPriority().intValue());
        assertEquals(speakerData.getFlags().stream().map(OfFlowFlag::name).collect(Collectors.toList()),
                ruleInfo.getFlags());
        assertFieldMatch(speakerData.getMatch(), ruleInfo.getMatch());
        assertInstructions(speakerData.getInstructions(), ruleInfo.getInstructions());
    }

    private void assertFieldMatch(Set<FieldMatch> expected, Map<String, RuleInfoEntryV2.FieldMatch> actual) {
        for (FieldMatch fieldMatch : expected) {
            RuleInfoEntryV2.FieldMatch response = actual.get(fieldMatch.getField().name());

            assertNotNull(response);
            assertEquals(fieldMatch.getValue(), response.getValue().longValue());
            assertEquals(fieldMatch.getMask(), response.getMask());
        }
    }

    private void assertInstructions(Instructions expected, RuleInfoEntryV2.Instructions actual) {
        assertEquals(expected.getGoToMeter().getValue(), actual.getGoToMeter().longValue());
        assertEquals(expected.getGoToTable().getTableId(), actual.getGoToTable().intValue());
        assertEquals(expected.getWriteMetadata().getValue(), actual.getWriteMetadata().getValue().longValue());
        assertEquals(expected.getWriteMetadata().getMask(), actual.getWriteMetadata().getMask().longValue());

        assertEquals(expected.getApplyActions().stream().map(Action::getType).map(ActionType::name)
                        .collect(Collectors.toList()),
                actual.getApplyActions().stream().map(BaseAction::getType).collect(Collectors.toList()));
        assertEquals(expected.getWriteActions().stream().map(Action::getType).map(ActionType::name)
                        .collect(Collectors.toList()),
                actual.getWriteActions().stream().map(BaseAction::getType).collect(Collectors.toList()));
    }

    private void assertMeters(MeterInfoEntryV2 meterInfoEntry, long expectedId, long expectedRate,
                              long expectedBurstSize, Set<String> expectedFlags) {
        assertEquals(expectedId, (long) meterInfoEntry.getMeterId());
        assertEquals(expectedRate, (long) meterInfoEntry.getRate());
        assertEquals(expectedBurstSize, (long) meterInfoEntry.getBurstSize());
        assertEquals(Sets.newHashSet(expectedFlags), Sets.newHashSet(meterInfoEntry.getFlags()));
    }

    private void assertGroups(GroupInfoEntryV2 groupInfoEntry, int groupId, int port, int vlan, int vni) {
        assertEquals(groupInfoEntry.getGroupId().intValue(), groupId);
        assertEquals(groupInfoEntry.getBuckets().get(0).getPort().intValue(), port);
        assertEquals(groupInfoEntry.getBuckets().get(0).getVlan().intValue(), vlan);
        assertEquals(groupInfoEntry.getBuckets().get(0).getVni().intValue(), vni);
    }

    private void assertEqualLogicalPort(LogicalPort expected, LogicalPortInfoEntryV2 actual) {
        LogicalPortInfoEntryV2 expectedPortInfo = LogicalPortMapper.INSTANCE.map(expected);
        Collections.sort(expectedPortInfo.getPhysicalPorts());
        Collections.sort(actual.getPhysicalPorts());
        assertEquals(expectedPortInfo, actual);
    }

    //build
    private GroupSpeakerData buildFullGroupSpeakerCommandData(GroupId groupId, Bucket bucket) {
        return GroupSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .groupId(groupId)
                .type(GroupType.ALL)
                .buckets(newArrayList(bucket))
                .switchId(SWITCH_ID)
                .ofVersion(OfVersion.OF_13)
                .dependsOn(newArrayList(UUID.randomUUID()))
                .build();
    }

    private MeterSpeakerData buildFullMeterSpeakerCommandData(int meterId, long rate, long burst,
                                                              Set<MeterFlag> flags) {
        return MeterSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .meterId(new MeterId(meterId))
                .rate(rate)
                .burst(burst)
                .flags(flags)
                .switchId(SWITCH_ID)
                .ofVersion(OfVersion.OF_13)
                .dependsOn(newArrayList(UUID.randomUUID()))
                .build();
    }

    private static LogicalPort buildLogicalPort(int lagPort, Integer... physicalPorts) {
        return buildLogicalPort(lagPort, LogicalPortType.LAG, physicalPorts);
    }

    private static LogicalPort buildLogicalPort(int lagPort, LogicalPortType type, Integer... physicalPorts) {
        return LogicalPort.builder()
                .type(type)
                .name("port_" + lagPort)
                .logicalPortNumber(lagPort)
                .portNumbers(Arrays.asList(physicalPorts))
                .build();
    }

    private FlowSpeakerData buildFullFlowSpeakerCommandData(OfTable tableId, int priority, Set<FieldMatch> match) {
        Set<OfFlowFlag> flags = Sets.newHashSet(OfFlowFlag.values());
        List<Action> applyActions = new ArrayList<>(buildAllActions());
        applyActions.addAll(buildAllActions());

        Instructions instructions = Instructions.builder()
                .goToMeter(new MeterId(1))
                .goToTable(OfTable.INPUT)
                .writeMetadata(new OfMetadata(15, 0xff))
                .writeActions(new HashSet<>(buildAllActions()))
                .applyActions(applyActions)
                .build();

        return FlowSpeakerData.builder()
                .uuid(UUID.randomUUID())
                .cookie(new Cookie(123))
                .priority(priority)
                .table(tableId)
                .match(match)
                .instructions(instructions)
                .flags(flags)
                .switchId(SWITCH_ID)
                .ofVersion(OfVersion.OF_13)
                .build();
    }

    private List<Action> buildGroupActions(GroupId groupId, PortNumber port, int vlan, int vni) {
        List<Action> actions = new ArrayList<>();
        actions.add(new GroupAction(groupId));
        actions.add(new PortOutAction(port));
        actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(vlan).build());
        actions.add(PushVxlanAction.builder()
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(vni)
                .srcMacAddress(SRC_MAC_ADDRESS)
                .dstMacAddress(DST_MAC_ADDRESS)
                .srcIpv4Address(SRC_IPV4_ADDRESS)
                .dstIpv4Address(DST_IPV4_ADDRESS)
                .udpSrc(UDP_SRC)
                .build());
        return actions;
    }

    private List<Action> buildAllActions() {
        List<Action> actions = new ArrayList<>();
        actions.add(new GroupAction(GROUP_ID_1));
        actions.add(new MeterAction(METER_ID_1));
        actions.add(new PopVlanAction());
        actions.add(new PopVxlanAction(ActionType.POP_VXLAN_OVS));
        actions.add(new PortOutAction(PORT_NUMBER));
        actions.add(new PushVlanAction());
        actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(VLAN_ID).build());
        actions.add(CopyFieldAction.builder()
                .srcOffset(0)
                .dstOffset(0)
                .numberOfBits(NUMBER_OF_BITS)
                .oxmSrcHeader(OpenFlowOxms.NOVIFLOW_TX_TIMESTAMP)
                .oxmDstHeader(OpenFlowOxms.NOVIFLOW_PACKET_OFFSET)
                .build());
        actions.add(PushVxlanAction.builder()
                .type(ActionType.PUSH_VXLAN_NOVIFLOW)
                .vni(VNI)
                .srcMacAddress(SRC_MAC_ADDRESS)
                .dstMacAddress(DST_MAC_ADDRESS)
                .srcIpv4Address(SRC_IPV4_ADDRESS)
                .dstIpv4Address(DST_IPV4_ADDRESS)
                .udpSrc(UDP_SRC)
                .build());
        return actions;
    }

    private PersistenceManagerBuilder persistenceManager() {
        return new PersistenceManagerBuilder();
    }

    private static class PersistenceManagerBuilder {
        private final SwitchRepository switchRepository = mock(SwitchRepository.class);
        private final LagLogicalPortRepository lagLogicalPortRepository = mock(LagLogicalPortRepository.class);
        private final FlowPathRepository flowPathRepository = mock(FlowPathRepository.class);
        private final FlowMeterRepository flowMeterRepository = mock(FlowMeterRepository.class);

        private PersistenceManager build() {
            Switch switchE = Switch.builder()
                    .switchId(SWITCH_ID_E)
                    .description("Nicira, Inc. OF_13 2.5.5")
                    .build();
            switchE.setOfDescriptionManufacturer("E");

            RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);

            when(switchRepository.findById(SWITCH_ID_A)).thenReturn(Optional.of(switchA));
            when(switchRepository.findById(SWITCH_ID_B)).thenReturn(Optional.of(switchB));
            when(switchRepository.findById(SWITCH_ID_E)).thenReturn(Optional.of(switchE));
            when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);

            LagLogicalPort lagLogicalPortA = new LagLogicalPort(SWITCH_ID_A, LOGICAL_PORT_NUMBER_1,
                    Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2));
            LagLogicalPort lagLogicalPortB = new LagLogicalPort(SWITCH_ID_A, LOGICAL_PORT_NUMBER_2,
                    Lists.newArrayList(PHYSICAL_PORT_3, PHYSICAL_PORT_4));
            LagLogicalPort lagLogicalPortC = new LagLogicalPort(SWITCH_ID_A, LOGICAL_PORT_NUMBER_3,
                    Lists.newArrayList(PHYSICAL_PORT_5, PHYSICAL_PORT_6));

            when(lagLogicalPortRepository.findBySwitchId(SWITCH_ID_A)).thenReturn(Lists.newArrayList(
                    lagLogicalPortA, lagLogicalPortB, lagLogicalPortC));
            when(repositoryFactory.createLagLogicalPortRepository()).thenReturn(lagLogicalPortRepository);

            when(flowMeterRepository.findById(any(), any())).thenReturn(Optional.empty());
            when(repositoryFactory.createFlowMeterRepository()).thenReturn(flowMeterRepository);

            when(flowPathRepository.findById(any())).thenReturn(Optional.empty());
            when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);

            PersistenceManager persistenceManager = mock(PersistenceManager.class);
            when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
            return persistenceManager;
        }
    }
}
