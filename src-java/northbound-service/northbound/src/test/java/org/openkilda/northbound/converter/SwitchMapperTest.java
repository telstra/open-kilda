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

package org.openkilda.northbound.converter;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.openkilda.messaging.info.switches.LogicalPortType.BFD;
import static org.openkilda.messaging.info.switches.LogicalPortType.LAG;

import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortsValidationEntry;
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
import org.openkilda.messaging.info.switches.v2.action.BaseAction;
import org.openkilda.messaging.info.switches.v2.action.CopyFieldActionEntry;
import org.openkilda.messaging.model.SwitchPatch;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.northbound.dto.v1.switches.GroupInfoDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortInfoDto;
import org.openkilda.northbound.dto.v1.switches.LogicalPortsValidationDto;
import org.openkilda.northbound.dto.v1.switches.MeterInfoDto;
import org.openkilda.northbound.dto.v1.switches.RulesValidationDto;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.northbound.dto.v1.switches.SwitchPropertiesDto;
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult;
import org.openkilda.northbound.dto.v2.action.CopyFieldActionDto;
import org.openkilda.northbound.dto.v2.switches.GroupInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.GroupsValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.LogicalPortInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.LogicalPortsValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.MeterInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.MetersValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.RuleInfoDtoV2;
import org.openkilda.northbound.dto.v2.switches.RulesValidationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchLocationDtoV2;
import org.openkilda.northbound.dto.v2.switches.SwitchPatchDto;

import com.google.common.collect.Lists;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(SpringRunner.class)
public class SwitchMapperTest {
    public static final int LOGICAL_PORT_NUMBER_1 = 1;
    public static final int LOGICAL_PORT_NUMBER_2 = 2;
    public static final int LOGICAL_PORT_NUMBER_3 = 3;
    public static final int LOGICAL_PORT_NUMBER_4 = 4;
    public static final int PHYSICAL_PORT_1 = 1;
    public static final int PHYSICAL_PORT_2 = 2;
    public static final int PHYSICAL_PORT_3 = 3;
    public static final int PHYSICAL_PORT_4 = 4;
    public static final int PHYSICAL_PORT_5 = 5;
    public static final int PHYSICAL_PORT_6 = 6;

    public static final int MISSING_BASE = 10;
    public static final int PROPER_BASE = 11;
    public static final int MISCONFIG_BASE = 12;
    public static final int EXCESS_BASE = 13;


    @Autowired
    private SwitchMapper switchMapper;

    @Test
    public void testSwitchPropertiesDto() {
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setMultiTable(true);
        properties.setSwitchArp(true);
        properties.setSwitchLldp(true);
        properties.setServer42FlowRtt(true);
        properties.setServer42Port(42);
        properties.setServer42MacAddress("42:42:42:42:42:42");
        properties.setSupportedTransitEncapsulation(newArrayList(
                FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()));

        org.openkilda.messaging.model.SwitchPropertiesDto messagingProperties = switchMapper.map(properties);
        SwitchPropertiesDto apiProperties = switchMapper.map(messagingProperties);
        assertEquals(properties, apiProperties);
    }

    @Test
    public void testNullServer42SwitchProperties() {
        SwitchPropertiesDto properties = new SwitchPropertiesDto();
        properties.setServer42FlowRtt(false);
        properties.setServer42Port(null);
        properties.setServer42MacAddress(null);
        properties.setSupportedTransitEncapsulation(newArrayList(
                FlowEncapsulationType.TRANSIT_VLAN.toString().toLowerCase()));

        org.openkilda.messaging.model.SwitchPropertiesDto messagingProperties = switchMapper.map(properties);
        SwitchPropertiesDto apiProperties = switchMapper.map(messagingProperties);
        assertEquals(properties, apiProperties);
    }

    @Test
    public void testSwitchToSwitchDto() {
        Switch sw = getSwitch();

        SwitchDto switchDto = switchMapper.toSwitchDto(sw);
        assertEquals(sw.getSwitchId(), switchDto.getSwitchId());
        assertEquals("127.0.0.1", switchDto.getAddress());
        assertEquals(sw.getSocketAddress().getPort(), switchDto.getPort());
        assertEquals(sw.getHostname(), switchDto.getHostname());
        assertEquals(sw.getDescription(), switchDto.getDescription());
        assertEquals(switchMapper.convertStatus(sw.getStatus()), switchDto.getState());
        assertEquals(sw.isUnderMaintenance(), switchDto.isUnderMaintenance());
        assertEquals(sw.getOfVersion(), switchDto.getOfVersion());
        assertEquals(sw.getOfDescriptionManufacturer(), switchDto.getManufacturer());
        assertEquals(sw.getOfDescriptionHardware(), switchDto.getHardware());
        assertEquals(sw.getOfDescriptionSoftware(), switchDto.getSoftware());
        assertEquals(sw.getOfDescriptionSerialNumber(), switchDto.getSerialNumber());
        assertEquals(sw.getPop(), switchDto.getPop());
        assertEquals((Double) sw.getLatitude(), switchDto.getLocation().getLatitude());
        assertEquals((Double) sw.getLongitude(), switchDto.getLocation().getLongitude());
        assertEquals(sw.getStreet(), switchDto.getLocation().getStreet());
        assertEquals(sw.getCity(), switchDto.getLocation().getCity());
        assertEquals(sw.getCountry(), switchDto.getLocation().getCountry());
    }

    @Test
    public void testSwitchToSwitchDtoV2() {
        Switch sw = getSwitch();

        SwitchDtoV2 switchDto = switchMapper.map(sw);
        assertEquals(sw.getSwitchId(), switchDto.getSwitchId());
        assertEquals("127.0.0.1", switchDto.getAddress());
        assertEquals(sw.getSocketAddress().getPort(), switchDto.getPort());
        assertEquals(sw.getHostname(), switchDto.getHostname());
        assertEquals(sw.getDescription(), switchDto.getDescription());
        assertEquals(switchMapper.convertStatus(sw.getStatus()), switchDto.getState());
        assertEquals(sw.isUnderMaintenance(), switchDto.isUnderMaintenance());
        assertEquals(sw.getOfVersion(), switchDto.getOfVersion());
        assertEquals(sw.getOfDescriptionManufacturer(), switchDto.getManufacturer());
        assertEquals(sw.getOfDescriptionHardware(), switchDto.getHardware());
        assertEquals(sw.getOfDescriptionSoftware(), switchDto.getSoftware());
        assertEquals(sw.getOfDescriptionSerialNumber(), switchDto.getSerialNumber());
        assertEquals(sw.getPop(), switchDto.getPop());
        assertEquals((Double) sw.getLatitude(), switchDto.getLocation().getLatitude());
        assertEquals((Double) sw.getLongitude(), switchDto.getLocation().getLongitude());
        assertEquals(sw.getStreet(), switchDto.getLocation().getStreet());
        assertEquals(sw.getCity(), switchDto.getLocation().getCity());
        assertEquals(sw.getCountry(), switchDto.getLocation().getCountry());
    }

    @Test
    public void testSwitchPatchDtoToSwitchPatch() {
        SwitchPatchDto switchPatchDto = new SwitchPatchDto("pop",
                new SwitchLocationDtoV2(48.860611, 2.337633, "street", "city", "country"));

        SwitchPatch switchPatch = switchMapper.map(switchPatchDto);
        assertEquals(switchPatchDto.getPop(), switchPatch.getPop());
        assertEquals(switchPatchDto.getLocation().getLatitude(), switchPatch.getLocation().getLatitude());
        assertEquals(switchPatchDto.getLocation().getLongitude(), switchPatch.getLocation().getLongitude());
        assertEquals(switchPatchDto.getLocation().getStreet(), switchPatch.getLocation().getStreet());
        assertEquals(switchPatchDto.getLocation().getCity(), switchPatch.getLocation().getCity());
        assertEquals(switchPatchDto.getLocation().getCountry(), switchPatch.getLocation().getCountry());
    }

    @Test
    public void testToLogicalPortsValidationDto() {
        LogicalPortInfoEntry missing = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_1)
                .type(LAG)
                .physicalPorts(newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2))
                .build();
        LogicalPortInfoEntry excess = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_2)
                .type(LAG)
                .physicalPorts(newArrayList(PHYSICAL_PORT_3))
                .build();
        LogicalPortInfoEntry misconfigured = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_3)
                .type(BFD)
                .physicalPorts(newArrayList(PHYSICAL_PORT_4))
                .actual(new LogicalPortMisconfiguredInfoEntry(BFD, newArrayList(PHYSICAL_PORT_4)))
                .expected(new LogicalPortMisconfiguredInfoEntry(LAG, newArrayList(
                        PHYSICAL_PORT_4, PHYSICAL_PORT_5)))
                .build();
        LogicalPortInfoEntry proper = LogicalPortInfoEntry.builder()
                .logicalPortNumber(LOGICAL_PORT_NUMBER_4)
                .type(LAG)
                .physicalPorts(newArrayList(PHYSICAL_PORT_6))
                .build();

        LogicalPortsValidationEntry validationEntry = LogicalPortsValidationEntry.builder()
                .missing(newArrayList(missing))
                .misconfigured(newArrayList(misconfigured))
                .proper(newArrayList(proper))
                .excess(newArrayList(excess))
                .build();

        LogicalPortsValidationDto validationDto = switchMapper.toLogicalPortsValidationDto(validationEntry);
        assertEquals(1, validationDto.getProper().size());
        assertEquals(1, validationDto.getMissing().size());
        assertEquals(1, validationDto.getMisconfigured().size());
        assertEquals(1, validationDto.getExcess().size());

        assertEqualsLogicalPortInfoDto(validationDto.getMissing().get(0), LOGICAL_PORT_NUMBER_1, LAG.toString(),
                PHYSICAL_PORT_1, PHYSICAL_PORT_2);
        assertEqualsLogicalPortInfoDto(validationDto.getExcess().get(0), LOGICAL_PORT_NUMBER_2, LAG.toString(),
                PHYSICAL_PORT_3);
        assertEqualsLogicalPortInfoDto(validationDto.getMisconfigured().get(0), LOGICAL_PORT_NUMBER_3, BFD.toString(),
                PHYSICAL_PORT_4);
        assertEqualsLogicalPortInfoDto(validationDto.getProper().get(0), LOGICAL_PORT_NUMBER_4, LAG.toString(),
                PHYSICAL_PORT_6);

        assertEquals(BFD.toString(), validationDto.getMisconfigured().get(0).getActual().getType());
        assertEquals(newArrayList(PHYSICAL_PORT_4),
                validationDto.getMisconfigured().get(0).getActual().getPhysicalPorts());
        assertEquals(LAG.toString(), validationDto.getMisconfigured().get(0).getExpected().getType());
        assertEquals(newArrayList(PHYSICAL_PORT_4, PHYSICAL_PORT_5),
                validationDto.getMisconfigured().get(0).getExpected().getPhysicalPorts());
    }

    @Test
    public void testToGroupInfoDtoV2() {
        GroupInfoEntryV2 expected = buildGroupInfoEntryV2(PROPER_BASE);
        GroupInfoDtoV2 actual = switchMapper.toGroupInfoDtoV2(expected);

        assertGroups(expected, actual);
    }

    @Test
    public void testToMetersInfoDtoV2() {
        MeterInfoEntryV2 expected = buildMeterInfoEntryV2(PROPER_BASE);
        MeterInfoDtoV2 actual = switchMapper.toMeterInfoDtoV2(expected);

        assertMeters(expected, actual);
    }

    @Test
    public void testToLogicalPortInfoDtoV2() {
        LogicalPortInfoEntryV2 logicalPortInfoEntryV2 = buildLogicalPortInfoEntryV2(PROPER_BASE);
        LogicalPortInfoDtoV2 logicalPortInfoDtoV2 = switchMapper.toLogicalPortInfoDtoV2(logicalPortInfoEntryV2);

        assertEquals(logicalPortInfoEntryV2.getLogicalPortNumber(), logicalPortInfoDtoV2.getLogicalPortNumber());
        assertEquals(logicalPortInfoEntryV2.getType().toString(), logicalPortInfoDtoV2.getType());
        assertEquals(logicalPortInfoEntryV2.getPhysicalPorts(), logicalPortInfoDtoV2.getPhysicalPorts());
    }

    @Test
    public void testToRulesInfoDtoV2() {
        RuleInfoEntryV2 ruleInfoEntryV2 = buildRuleInfoEntryV2(PROPER_BASE);
        RuleInfoDtoV2 ruleInfoDtoV2 = switchMapper.toRuleInfoDtoV2(ruleInfoEntryV2);

        assertRules(ruleInfoEntryV2, ruleInfoDtoV2);
    }

    @Test
    public void testToRulesValidationDtoV2() {
        RulesValidationEntryV2 rulesValidationEntryV2 = buildRulesValidationEntryV2();
        RulesValidationDtoV2 ruleInfoDtoV2 = switchMapper.toRulesValidationDtoV2(rulesValidationEntryV2);

        assertFalse(ruleInfoDtoV2.isAsExpected());

        assertRules(Lists.newArrayList(rulesValidationEntryV2.getExcess()).get(0), ruleInfoDtoV2.getExcess().get(0));
        assertRules(Lists.newArrayList(rulesValidationEntryV2.getProper()).get(0), ruleInfoDtoV2.getProper().get(0));
        assertRules(Lists.newArrayList(rulesValidationEntryV2.getMissing()).get(0), ruleInfoDtoV2.getMissing().get(0));

        assertRules(Lists.newArrayList(rulesValidationEntryV2.getMisconfigured()).get(0).getExpected(),
                ruleInfoDtoV2.getMisconfigured().get(0).getExpected());
        assertRules(Lists.newArrayList(rulesValidationEntryV2.getMisconfigured()).get(0).getDiscrepancies(),
                ruleInfoDtoV2.getMisconfigured().get(0).getDiscrepancies());
        assertEquals(Lists.newArrayList(rulesValidationEntryV2.getMisconfigured()).get(0).getId(),
                ruleInfoDtoV2.getMisconfigured().get(0).getId());
    }

    @Test
    public void testToMetersValidationDtoV2() {
        MetersValidationEntryV2 expected = buildMetersValidationEntryV2();
        MetersValidationDtoV2 actual = switchMapper.toMetersValidationDtoV2(expected);

        assertEquals(expected.isAsExpected(), actual.isAsExpected());

        assertMeters(expected.getExcess().get(0), actual.getExcess().get(0));
        assertMeters(expected.getProper().get(0), actual.getProper().get(0));
        assertMeters(expected.getMissing().get(0), actual.getMissing().get(0));

        assertMeters(expected.getMisconfigured().get(0).getExpected(), actual.getMisconfigured().get(0).getExpected());
        assertMeters(expected.getMisconfigured().get(0).getDiscrepancies(),
                actual.getMisconfigured().get(0).getDiscrepancies());
        assertEquals(expected.getMisconfigured().get(0).getId(), actual.getMisconfigured().get(0).getId());
    }

    @Test
    public void testToGroupsValidationDtoV2() {
        GroupsValidationEntryV2 expected = buildGroupsValidationEntryV2();
        GroupsValidationDtoV2 actual = switchMapper.toGroupsValidationDtoV2(expected);

        assertEquals(expected.isAsExpected(), actual.isAsExpected());

        assertGroups(expected.getExcess().get(0), actual.getExcess().get(0));
        assertGroups(expected.getProper().get(0), actual.getProper().get(0));
        assertGroups(expected.getMissing().get(0), actual.getMissing().get(0));

        assertGroups(expected.getMisconfigured().get(0).getExpected(), actual.getMisconfigured().get(0).getExpected());
        assertGroups(expected.getMisconfigured().get(0).getDiscrepancies(),
                actual.getMisconfigured().get(0).getDiscrepancies());
        assertEquals(expected.getMisconfigured().get(0).getId(), actual.getMisconfigured().get(0).getId());
    }

    @Test
    public void testToLoLogicalPortsValidationDtoV2() {
        LogicalPortsValidationEntryV2 expected = buildLogicalPortsValidationEntryV2();
        LogicalPortsValidationDtoV2 actual = switchMapper.toLogicalPortsValidationDtoV2(expected);

        assertEquals(expected.isAsExpected(), actual.isAsExpected());

        assertLogicalPorts(expected.getExcess().get(0), actual.getExcess().get(0));
        assertLogicalPorts(expected.getProper().get(0), actual.getProper().get(0));
        assertLogicalPorts(expected.getMissing().get(0), actual.getMissing().get(0));

        assertLogicalPorts(expected.getMisconfigured().get(0).getExpected(),
                actual.getMisconfigured().get(0).getExpected());
        assertLogicalPorts(expected.getMisconfigured().get(0).getDiscrepancies(),
                actual.getMisconfigured().get(0).getDiscrepancies());
        assertEquals(expected.getMisconfigured().get(0).getId(), actual.getMisconfigured().get(0).getId());
    }

    @Test
    public void testToRulesValidationDtoV1() {
        RulesValidationEntryV2 expected = RulesValidationEntryV2.builder()
                .asExpected(false)
                .excess(newHashSet(buildRuleInfoEntryV2(EXCESS_BASE)))
                .missing(newHashSet(buildRuleInfoEntryV2(MISSING_BASE)))
                .proper(newHashSet(buildRuleInfoEntryV2(PROPER_BASE)))
                .misconfigured(newHashSet(MisconfiguredInfo.<RuleInfoEntryV2>builder()
                        .id(String.valueOf(MISCONFIG_BASE))
                        .expected(buildRuleInfoEntryV2(MISCONFIG_BASE))
                        .discrepancies(buildRuleInfoEntryV2(MISCONFIG_BASE))
                        .build()))
                .build();
        RulesValidationDto actual = switchMapper.toRulesValidationDtoV1(expected);

        assertEquals(Lists.newArrayList(expected.getExcess()).get(0).getCookie(), actual.getExcess().get(0));
        assertEquals(Lists.newArrayList(expected.getMissing()).get(0).getCookie(), actual.getMissing().get(0));
        assertEquals(Lists.newArrayList(expected.getProper()).get(0).getCookie(), actual.getProper().get(0));
        assertEquals(Lists.newArrayList(expected.getMisconfigured()).get(0).getExpected().getCookie(),
                actual.getMisconfigured().get(0));
    }

    @Test
    @Ignore
    public void testMisconfigMeterV2ToMeterInfoDtoV1() {
        MisconfiguredInfo<MeterInfoEntryV2> expected = MisconfiguredInfo.<MeterInfoEntryV2>builder()
                .id(String.valueOf(MISCONFIG_BASE))
                .expected(buildMeterInfoEntryV2(MISCONFIG_BASE))
                .discrepancies(buildMeterInfoEntryV2(MISCONFIG_BASE))
                .build();

        MeterInfoDto actual = switchMapper.toMeterInfoDtoV1(expected);

        assertMeters(expected.getExpected(), actual);

        assertEquals(expected.getExpected().getRate(), actual.getExpected().getRate());
        assertEquals(expected.getExpected().getBurstSize(), actual.getExpected().getBurstSize());
        assertEquals(expected.getExpected().getFlags(), Lists.newArrayList(actual.getExpected().getFlags()));

        assertEquals(expected.getDiscrepancies().getRate(), actual.getActual().getRate());
        assertEquals(expected.getDiscrepancies().getBurstSize(), actual.getActual().getBurstSize());
        assertEquals(expected.getDiscrepancies().getFlags(), Lists.newArrayList(actual.getActual().getFlags()));
    }

    @Test
    public void testToMeterInfoDtoV1() {
        MeterInfoEntryV2 expected = buildMeterInfoEntryV2(PROPER_BASE);
        MeterInfoDto actual = switchMapper.toMeterInfoDtoV1(expected);

        assertMeters(expected, actual);
    }

    @Test
    public void testMisconfigLogicalPortV2ToLogicalPortInfoV1() {
        MisconfiguredInfo<LogicalPortInfoEntryV2> expected = MisconfiguredInfo.<LogicalPortInfoEntryV2>builder()
                .id(String.valueOf(MISCONFIG_BASE))
                .expected(buildLogicalPortInfoEntryV2(MISCONFIG_BASE))
                .discrepancies(buildLogicalPortInfoEntryV2(MISCONFIG_BASE))
                .build();
        LogicalPortInfoDto actual = switchMapper.toLogicalPortInfoDtoV1(expected);

        assertLogicalPorts(expected.getExpected(), actual);

        assertEquals(expected.getExpected().getType().toString(), actual.getExpected().getType());
        assertEquals(expected.getExpected().getPhysicalPorts(), actual.getExpected().getPhysicalPorts());

        assertEquals(expected.getDiscrepancies().getType().toString(), actual.getActual().getType());
        assertEquals(expected.getExpected().getPhysicalPorts(), actual.getPhysicalPorts());
    }

    @Test
    public void testToLogicalPortInfoDtoV1() {
        LogicalPortInfoEntryV2 expected = buildLogicalPortInfoEntryV2(PROPER_BASE);
        LogicalPortInfoDto actual = switchMapper.toLogicalPortInfoDtoV1(expected);

        assertLogicalPorts(expected, actual);
        assertNull(actual.getActual());
        assertNull(actual.getExpected());
    }

    @Test
    public void testMisconfigGroupV2ToGroupInfoDtoV1() {
        MisconfiguredInfo<GroupInfoEntryV2> expected = MisconfiguredInfo.<GroupInfoEntryV2>builder()
                .id(String.valueOf(MISCONFIG_BASE))
                .expected(buildGroupInfoEntryV2(MISCONFIG_BASE))
                .discrepancies(buildGroupInfoEntryV2(MISCONFIG_BASE))
                .build();

        GroupInfoDto actual = switchMapper.toGroupInfoDtoV1(expected);

        assertGroups(expected.getDiscrepancies(), actual);
    }

    @Test
    public void testToGroupInfoDtoV1() {
        GroupInfoEntryV2 expected = buildGroupInfoEntryV2(PROPER_BASE);
        GroupInfoDto actual = switchMapper.toGroupInfoDtoV1(expected);

        assertGroups(expected, actual);
    }

    @Test
    public void testSwitchValidationResponseV2ToSwitchValidationResultV1() {
        LogicalPortsValidationEntryV2 logicalPortsEntry = buildLogicalPortsValidationEntryV2();
        MetersValidationEntryV2 metersEntry = buildMetersValidationEntryV2();
        RulesValidationEntryV2 rulesEntry = buildRulesValidationEntryV2();
        GroupsValidationEntryV2 groupsEntry = buildGroupsValidationEntryV2();

        SwitchValidationResponseV2 expected = SwitchValidationResponseV2.builder()
                .logicalPorts(logicalPortsEntry)
                .meters(metersEntry)
                .groups(groupsEntry)
                .rules(rulesEntry)
                .asExpected(false)
                .build();

        SwitchValidationResult actual = switchMapper.toSwitchValidationResultV1(expected);

        assertGroups(expected.getGroups().getExcess().get(0), actual.getGroups().getExcess().get(0));
        assertGroups(expected.getGroups().getProper().get(0), actual.getGroups().getProper().get(0));
        assertGroups(expected.getGroups().getMissing().get(0), actual.getGroups().getMissing().get(0));
        assertGroups(expected.getGroups().getMisconfigured().get(0).getDiscrepancies(),
                actual.getGroups().getMisconfigured().get(0));

        assertMeters(expected.getMeters().getExcess().get(0), actual.getMeters().getExcess().get(0));
        assertMeters(expected.getMeters().getProper().get(0), actual.getMeters().getProper().get(0));
        assertMeters(expected.getMeters().getMissing().get(0), actual.getMeters().getMissing().get(0));
        assertMeters(expected.getMeters().getMisconfigured().get(0).getDiscrepancies(),
                actual.getMeters().getMisconfigured().get(0));

        assertLogicalPorts(expected.getLogicalPorts().getExcess().get(0), actual.getLogicalPorts().getExcess().get(0));
        assertLogicalPorts(expected.getLogicalPorts().getProper().get(0), actual.getLogicalPorts().getProper().get(0));
        assertLogicalPorts(expected.getLogicalPorts().getMissing().get(0),
                actual.getLogicalPorts().getMissing().get(0));
        assertLogicalPorts(expected.getLogicalPorts().getMisconfigured().get(0).getDiscrepancies(),
                actual.getLogicalPorts().getMisconfigured().get(0));

        assertEquals(Lists.newArrayList(expected.getRules().getExcess()).get(0).getCookie(),
                actual.getRules().getExcess().get(0));
        assertEquals(Lists.newArrayList(expected.getRules().getMissing()).get(0).getCookie(),
                actual.getRules().getMissing().get(0));
        assertEquals(Lists.newArrayList(expected.getRules().getProper()).get(0).getCookie(),
                actual.getRules().getProper().get(0));
        assertEquals(Lists.newArrayList(expected.getRules().getMisconfigured()).get(0).getExpected().getCookie(),
                actual.getRules().getMisconfigured().get(0));
    }

    //assert
    private void assertRules(RuleInfoEntryV2 ruleInfo, RuleInfoDtoV2 ruleDto) {
        assertEquals(ruleInfo.getCookie(), ruleDto.getCookie());
        assertEquals(ruleInfo.getFlowPath(), ruleDto.getFlowPath());
        assertEquals(ruleInfo.getFlowId(), ruleDto.getFlowId());
        assertEquals(ruleInfo.getYFlowId(), ruleDto.getYFlowId());
        assertEquals(ruleInfo.getTableId(), ruleDto.getTableId());
        assertEquals(ruleInfo.getPriority(), ruleDto.getPriority());
        assertEquals(ruleInfo.getFlags(), ruleDto.getFlags());
        assertFieldMatch(ruleInfo.getMatch(), ruleDto.getMatch());
        assertInstructions(ruleInfo.getInstructions(), ruleDto.getInstructions());
    }

    private void assertFieldMatch(Map<String, RuleInfoEntryV2.FieldMatch> expected,
                                  Map<String, RuleInfoDtoV2.FieldMatch> actual) {
        assertEquals(expected.size(), actual.size());

        expected.keySet().forEach(key -> {
                    RuleInfoDtoV2.FieldMatch actualFieldMatch = actual.get(key);

                    assertNotNull(actualFieldMatch);
                    assertEquals(expected.get(key).getValue(), actualFieldMatch.getValue());
                    assertEquals(expected.get(key).getMask(), actualFieldMatch.getMask());
                }
        );
    }

    private void assertInstructions(RuleInfoEntryV2.Instructions expected, RuleInfoDtoV2.Instructions actual) {
        assertEquals(expected.getGoToMeter(), actual.getGoToMeter());
        assertEquals(expected.getGoToTable(), actual.getGoToTable());

        assertEquals(expected.getWriteMetadata().getValue(), actual.getWriteMetadata().getValue());
        assertEquals(expected.getWriteMetadata().getMask(), actual.getWriteMetadata().getMask());

        //actions
        CopyFieldActionDto actualAction = (CopyFieldActionDto) actual.getApplyActions().get(0);
        CopyFieldActionEntry expectedAction = (CopyFieldActionEntry) expected.getApplyActions().get(0);
        assertActions(expectedAction, actualAction);

        actualAction = (CopyFieldActionDto) actual.getWriteActions().get(0);
        expectedAction = (CopyFieldActionEntry) expected.getWriteActions().get(0);
        assertActions(expectedAction, actualAction);
    }

    private void assertActions(CopyFieldActionEntry expectedAction, CopyFieldActionDto actualAction) {
        assertEquals(expectedAction.getActionType(), actualAction.getActionType());
        assertEquals(expectedAction.getDstOffset(), actualAction.getDstOffset());
        assertEquals(expectedAction.getSrcOffset(), actualAction.getSrcOffset());
        assertEquals(expectedAction.getNumberOfBits(), actualAction.getNumberOfBits());
        assertEquals(expectedAction.getOxmDstHeader(), actualAction.getOxmDstHeader());
        assertEquals(expectedAction.getOxmSrcHeader(), actualAction.getOxmSrcHeader());
    }

    private void assertMeters(MeterInfoEntryV2 expected, MeterInfoDtoV2 actual) {
        assertEquals(expected.getMeterId(), actual.getMeterId());
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getFlowPath(), actual.getFlowPath());
        assertEquals(expected.getFlags(), actual.getFlags());
        assertEquals(expected.getBurstSize(), actual.getBurstSize());
        assertEquals(expected.getCookie(), actual.getCookie());
        assertEquals(expected.getYFlowId(), actual.getYFlowId());
        assertEquals(expected.getRate(), actual.getRate());
    }

    private void assertMeters(MeterInfoEntryV2 expected, MeterInfoDto actual) {
        assertEquals(expected.getMeterId(), actual.getMeterId());
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getFlags(), Lists.newArrayList(actual.getFlags()));
        assertEquals(expected.getBurstSize(), actual.getBurstSize());
        assertEquals(expected.getCookie(), actual.getCookie());
        assertEquals(expected.getRate(), actual.getRate());
    }

    private void assertLogicalPorts(LogicalPortInfoEntryV2 expected, LogicalPortInfoDtoV2 actual) {
        assertEquals(expected.getLogicalPortNumber(), actual.getLogicalPortNumber());
        assertEquals(expected.getType().toString(), actual.getType());
        assertEquals(expected.getPhysicalPorts(), actual.getPhysicalPorts());
    }

    private void assertLogicalPorts(LogicalPortInfoEntryV2 expected, LogicalPortInfoDto actual) {
        assertEquals(expected.getLogicalPortNumber(), actual.getLogicalPortNumber());
        assertEquals(expected.getType().toString(), actual.getType());
        assertEquals(expected.getPhysicalPorts(), actual.getPhysicalPorts());
    }

    private void assertEqualsLogicalPortInfoDto(LogicalPortInfoDto port, int logicalPortNumber, String type,
                                                Integer... physicalPorts) {
        assertEquals(logicalPortNumber, port.getLogicalPortNumber().intValue());
        assertEquals(type, port.getType());
        assertEquals(Arrays.asList(physicalPorts), port.getPhysicalPorts());
    }

    private void assertGroups(GroupInfoEntryV2 expected, GroupInfoDtoV2 actual) {
        assertEquals(expected.getGroupId(), actual.getGroupId());
        assertEquals(expected.getFlowId(), actual.getFlowId());
        assertEquals(expected.getFlowPath(), actual.getFlowPath());

        assertEquals(expected.getBuckets().get(0).getVni(), actual.getBuckets().get(0).getVni());
        assertEquals(expected.getBuckets().get(0).getVlan(), actual.getBuckets().get(0).getVlan());
        assertEquals(expected.getBuckets().get(0).getPort(), actual.getBuckets().get(0).getPort());
    }

    private void assertGroups(GroupInfoEntryV2 expected, GroupInfoDto actual) {
        assertEquals(expected.getGroupId(), actual.getGroupId());

        assertEquals(expected.getBuckets().get(0).getVni(), actual.getGroupBuckets().get(0).getVni());
        assertEquals(expected.getBuckets().get(0).getVlan(), actual.getGroupBuckets().get(0).getVlan());
        assertEquals(expected.getBuckets().get(0).getPort(), actual.getGroupBuckets().get(0).getPort());
    }

    //build test entity
    private Switch getSwitch() {
        return Switch.builder()
                .switchId(new SwitchId(1))
                .socketAddress(new IpSocketAddress("127.0.0.1", 5050))
                .hostname("hostname")
                .description("description")
                .status(SwitchStatus.ACTIVE)
                .underMaintenance(true)
                .ofVersion("OF_13")
                .ofDescriptionManufacturer("manufacturer")
                .ofDescriptionHardware("hardware")
                .ofDescriptionSoftware("software")
                .ofDescriptionSerialNumber("serialNumber")
                .pop("pop")
                .latitude(48.860611)
                .longitude(2.337633)
                .street("Rue de Rivoli")
                .city("Paris")
                .country("France")
                .build();
    }

    private GroupInfoEntryV2 buildGroupInfoEntryV2(int base) {
        return GroupInfoEntryV2.builder()
                .groupId(base + 1)
                .flowId(String.format("flow_id_%s", base))
                .flowPath(String.format("flow_path_%s", base))
                .buckets(Lists.newArrayList(GroupInfoEntryV2.BucketEntry.builder()
                        .port(base + 2)
                        .vlan(base + 3)
                        .vni(base + 4)
                        .build()))
                .build();
    }

    private RuleInfoEntryV2.FieldMatch buildFieldMatchV2(int base) {
        return RuleInfoEntryV2.FieldMatch.builder()
                .value(base + 1L)
                .mask(base + 2L)
                .build();
    }

    private MeterInfoEntryV2 buildMeterInfoEntryV2(int base) {
        return MeterInfoEntryV2.builder()
                .burstSize(base + 1L)
                .rate(base + 2L)
                .cookie(base + 3L)
                .yFlowId(String.format("y_flow_id_%s", base))
                .flowPath(String.format("y_flow_path_%s", base))
                .meterId(base + 4L)
                .flags(Lists.newArrayList((String.format("FLAG_%s", base))))
                .build();
    }

    private LogicalPortInfoEntryV2 buildLogicalPortInfoEntryV2(int base) {
        return LogicalPortInfoEntryV2.builder()
                .logicalPortNumber(base + 1)
                .physicalPorts(Lists.newArrayList(base + 1, base + 2, base + 3))
                .type(LAG)
                .build();
    }

    private RuleInfoEntryV2 buildRuleInfoEntryV2(int base) {
        RuleInfoEntryV2.Instructions instructions = RuleInfoEntryV2.Instructions.builder()
                .goToMeter(base + 1L)
                .goToTable(base + 2)
                .applyActions(buildActions())
                .writeMetadata(RuleInfoEntryV2.WriteMetadata.builder().mask(base + 3L).value(base + 4L).build())
                .writeActions(buildActions())
                .build();

        return RuleInfoEntryV2.builder()
                .cookie(base + 5L)
                .priority(base + 6)
                .tableId(base + 7)
                .cookieHex(String.format("cookie_hex_%s", base))
                .cookieKind(String.format("cookie_kind_%s", base))
                .flowId(String.format("flow_id_%s", base))
                .flowPath(String.format("flow_path_%s", base))
                .yFlowId(String.format("y_flow_id_%s", base))
                .match(Stream.of(new SimpleEntry<>("key", buildFieldMatchV2(base)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (oldValue, newValue) -> newValue, TreeMap::new)))
                .instructions(instructions)
                .flags(Lists.newArrayList(String.format("FLAG_%s", base)))
                .build();
    }

    private List<BaseAction> buildActions() {
        List<BaseAction> baseActions = new ArrayList<>();
        baseActions.add(CopyFieldActionEntry.builder()
                .dstOffset(100)
                .numberOfBits(200)
                .oxmDstHeader("oxmDstHeader")
                .oxmSrcHeader("oxmSrcHeader")
                .build());
        return baseActions;
    }

    private LogicalPortsValidationEntryV2 buildLogicalPortsValidationEntryV2() {
        return LogicalPortsValidationEntryV2.builder()
                .asExpected(false)
                .excess(Lists.newArrayList(buildLogicalPortInfoEntryV2(EXCESS_BASE)))
                .proper(Lists.newArrayList(buildLogicalPortInfoEntryV2(PROPER_BASE)))
                .missing(Lists.newArrayList(buildLogicalPortInfoEntryV2(MISSING_BASE)))
                .misconfigured(Lists.newArrayList(MisconfiguredInfo.<LogicalPortInfoEntryV2>builder()
                        .id(String.valueOf(MISCONFIG_BASE))
                        .expected(buildLogicalPortInfoEntryV2(MISCONFIG_BASE))
                        .discrepancies(buildLogicalPortInfoEntryV2(MISCONFIG_BASE))
                        .build()))
                .build();
    }

    private MetersValidationEntryV2 buildMetersValidationEntryV2() {
        return MetersValidationEntryV2.builder()
                .asExpected(false)
                .excess(Lists.newArrayList(buildMeterInfoEntryV2(EXCESS_BASE)))
                .missing(Lists.newArrayList(buildMeterInfoEntryV2(MISSING_BASE)))
                .proper(Lists.newArrayList(buildMeterInfoEntryV2(PROPER_BASE)))
                .misconfigured(Lists.newArrayList(MisconfiguredInfo.<MeterInfoEntryV2>builder()
                        .expected(buildMeterInfoEntryV2(MISCONFIG_BASE))
                        .discrepancies(buildMeterInfoEntryV2(MISCONFIG_BASE))
                        .id(String.valueOf(MISCONFIG_BASE))
                        .build()))
                .build();
    }

    private GroupsValidationEntryV2 buildGroupsValidationEntryV2() {
        return GroupsValidationEntryV2.builder()
                .asExpected(false)
                .excess(Lists.newArrayList(buildGroupInfoEntryV2(EXCESS_BASE)))
                .proper(Lists.newArrayList(buildGroupInfoEntryV2(PROPER_BASE)))
                .missing(Lists.newArrayList(buildGroupInfoEntryV2(MISSING_BASE)))
                .misconfigured(Lists.newArrayList(MisconfiguredInfo.<GroupInfoEntryV2>builder()
                        .id(String.valueOf(MISCONFIG_BASE))
                        .expected(buildGroupInfoEntryV2(MISCONFIG_BASE))
                        .discrepancies(buildGroupInfoEntryV2(MISCONFIG_BASE))
                        .build()))
                .build();
    }

    private RulesValidationEntryV2 buildRulesValidationEntryV2() {
        return RulesValidationEntryV2.builder()
                .asExpected(false)
                .excess(newHashSet(buildRuleInfoEntryV2(EXCESS_BASE)))
                .missing(newHashSet(buildRuleInfoEntryV2(MISSING_BASE)))
                .proper(newHashSet(buildRuleInfoEntryV2(PROPER_BASE)))
                .misconfigured(newHashSet(MisconfiguredInfo.<RuleInfoEntryV2>builder()
                        .expected(buildRuleInfoEntryV2(MISCONFIG_BASE))
                        .discrepancies(buildRuleInfoEntryV2(MISCONFIG_BASE))
                        .id(String.valueOf(MISCONFIG_BASE))
                        .build()))
                .build();
    }

    @TestConfiguration
    @ComponentScan({"org.openkilda.northbound.converter"})
    static class Config {
        // nothing to define here
    }
}
