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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.LogicalPortType;
import org.openkilda.model.LagLogicalPort;
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
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.MeterFlag;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.switchmanager.mappers.LogicalPortMapper;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

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

    @Mock
    private RuleManager ruleManager;

    @Test
    public void validateRulesEmpty() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        ValidateRulesResult response = validationService.validateRules(SWITCH_ID_A, emptyList(), emptyList());
        assertTrue(response.getMissingRules().isEmpty());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void validateRules() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        List<FlowSpeakerData> actualFlows = Lists.newArrayList(
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000001L)).priority(1).build(),
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000001L)).priority(2).build(),
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000002L)).priority(1).build(),
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000002L)).priority(2).build(),
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000004L)).priority(1).build());
        List<FlowSpeakerData> expectedFlows = Lists.newArrayList(
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000001L)).priority(1).build(),
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000002L)).priority(3).build(),
                FlowSpeakerData.builder().cookie(new Cookie(0x8000000000000003L)).priority(1).build());
        ValidateRulesResult response =
                validationService.validateRules(SWITCH_ID_A, actualFlows, expectedFlows);
        assertEquals(ImmutableSet.of(0x8000000000000001L), new HashSet<>(response.getProperRules()));
        assertEquals(ImmutableSet.of(0x8000000000000001L, 0x8000000000000002L),
                new HashSet<>(response.getMisconfiguredRules()));
        assertEquals(ImmutableSet.of(0x8000000000000003L), new HashSet<>(response.getMissingRules()));
        assertEquals(ImmutableSet.of(0x8000000000000004L), new HashSet<>(response.getExcessRules()));
    }

    @Test
    public void validateMetersEmpty() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        ValidateMetersResult response = validationService.validateMeters(SWITCH_ID_A, emptyList(), emptyList());
        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateMetersProperMeters() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        MeterSpeakerData meter = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(10000)
                .burst(10500)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        ValidateMetersResult response = validationService.validateMeters(SWITCH_ID_B,
                singletonList(meter),
                singletonList(meter));
        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertFalse(response.getProperMeters().isEmpty());
        assertEquals(meter.getMeterId().getValue(), response.getProperMeters().get(0).getMeterId().longValue());
        assertMeter(response.getProperMeters().get(0), 32, 10000, 10500, new String[]{"KBPS", "BURST", "STATS"});
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateMetersMisconfiguredMeters() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        MeterSpeakerData actualMeter = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(10002)
                .burst(10498)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        String[] actualFlags = new String[]{"PKTPS", "BURST", "STATS"};
        MeterSpeakerData expectedMeter = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(10000)
                .burst(10500)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        ValidateMetersResult response = validationService.validateMeters(SWITCH_ID_B,
                singletonList(actualMeter),
                singletonList(expectedMeter));
        assertTrue(response.getMissingMeters().isEmpty());
        assertFalse(response.getMisconfiguredMeters().isEmpty());
        assertEquals(10002, (long) response.getMisconfiguredMeters().get(0).getActual().getRate());
        assertEquals(10000, (long) response.getMisconfiguredMeters().get(0).getExpected().getRate());
        assertEquals(10498L, (long) response.getMisconfiguredMeters().get(0).getActual().getBurstSize());
        assertEquals(10500L, (long) response.getMisconfiguredMeters().get(0).getExpected().getBurstSize());
        assertTrue(Sets.newHashSet(actualFlags).containsAll(
                Sets.newHashSet(response.getMisconfiguredMeters().get(0).getActual().getFlags())));
        assertTrue(Sets.newHashSet("KBPS", "BURST", "STATS").containsAll(
                Sets.newHashSet(response.getMisconfiguredMeters().get(0).getExpected().getFlags())));
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateMetersMissingAndExcessMeters() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        MeterSpeakerData actualMeter = MeterSpeakerData.builder()
                .meterId(new MeterId(33))
                .rate(10000)
                .burst(10500)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        MeterSpeakerData expectedMeter = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(10000)
                .burst(10500)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        ValidateMetersResult response = validationService.validateMeters(SWITCH_ID_B,
                singletonList(actualMeter),
                singletonList(expectedMeter));
        assertFalse(response.getMissingMeters().isEmpty());
        assertMeter(response.getMissingMeters().get(0), 32, 10000, 10500, new String[]{"KBPS", "BURST", "STATS"});
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertTrue(response.getProperMeters().isEmpty());
        assertFalse(response.getExcessMeters().isEmpty());
        assertMeter(response.getExcessMeters().get(0), 33, 10000, 10500, new String[]{"KBPS", "BURST", "STATS"});
    }

    @Test
    public void validateMetersProperMetersESwitch() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        long rateESwitch = FLOW_E_BANDWIDTH + (long) (FLOW_E_BANDWIDTH * 0.01) - 1;
        long burstSize = (long) (FLOW_E_BANDWIDTH * 1.05);
        long burstSizeESwitch = burstSize + (long) (burstSize * 0.01) - 1;
        MeterSpeakerData meter = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(rateESwitch)
                .burst(burstSizeESwitch)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        ValidateMetersResult response = validationService.validateMeters(SWITCH_ID_E,
                singletonList(meter),
                singletonList(meter));
        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertFalse(response.getProperMeters().isEmpty());
        assertEquals(32L, (long) response.getProperMeters().get(0).getMeterId());
        assertMeter(response.getProperMeters().get(0), 32, rateESwitch, burstSizeESwitch,
                new String[]{"KBPS", "BURST", "STATS"});
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateMetersMisconfiguredMetersESwitch() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);
        long rateESwitch = FLOW_E_BANDWIDTH + (long) (FLOW_E_BANDWIDTH * 0.01) + 1;
        long burstSize = (long) (FLOW_E_BANDWIDTH * 1.05);
        long burstSizeESwitch = burstSize + (long) (burstSize * 0.01) + 1;
        MeterSpeakerData actualMeter = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(rateESwitch)
                .burst(burstSizeESwitch)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        MeterSpeakerData expectedMeter = MeterSpeakerData.builder()
                .meterId(new MeterId(32))
                .rate(rateESwitch)
                .burst(burstSize)
                .ofVersion(OfVersion.OF_13)
                .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                .build();
        ValidateMetersResult response = validationService.validateMeters(SWITCH_ID_E,
                singletonList(actualMeter),
                singletonList(expectedMeter));
        assertTrue(response.getMissingMeters().isEmpty());
        assertFalse(response.getMisconfiguredMeters().isEmpty());
        assertEquals(10606L, (long) response.getMisconfiguredMeters().get(0).getActual().getBurstSize());
        assertEquals(10500L, (long) response.getMisconfiguredMeters().get(0).getExpected().getBurstSize());
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateLogicalPorts() {
        ValidationService validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        LogicalPort proper = buildLogicalPort(LOGICAL_PORT_NUMBER_1, PHYSICAL_PORT_2, PHYSICAL_PORT_1);
        LogicalPort misconfigured = buildLogicalPort(LOGICAL_PORT_NUMBER_2, LogicalPortType.BFD, PHYSICAL_PORT_3);
        LogicalPort excess = buildLogicalPort(LOGICAL_PORT_NUMBER_4, PHYSICAL_PORT_6);
        LogicalPort bfdExcess = buildLogicalPort(LOGICAL_PORT_NUMBER_5, LogicalPortType.BFD, PHYSICAL_PORT_7);

        ValidateLogicalPortsResult result = validationService.validateLogicalPorts(SWITCH_ID_A, Lists.newArrayList(
                proper, misconfigured, excess, bfdExcess));
        assertEquals(1, result.getProperLogicalPorts().size());
        assertEquals(1, result.getExcessLogicalPorts().size()); // bfdExcess port shouldn't be in this list
        assertEquals(1, result.getMissingLogicalPorts().size());
        assertEquals(1, result.getMisconfiguredLogicalPorts().size());

        assertEqualLogicalPort(proper, result.getProperLogicalPorts().get(0));
        assertEqualLogicalPort(excess, result.getExcessLogicalPorts().get(0));

        LogicalPortInfoEntry missing = LogicalPortInfoEntry.builder()
                .type(org.openkilda.messaging.info.switches.LogicalPortType.LAG)
                .logicalPortNumber(LOGICAL_PORT_NUMBER_3)
                .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_5, PHYSICAL_PORT_6))
                .build();
        assertEquals(missing, result.getMissingLogicalPorts().get(0));

        LogicalPortInfoEntry misconfiguredEntry = LogicalPortInfoEntry.builder()
                .type(org.openkilda.messaging.info.switches.LogicalPortType.BFD)
                .logicalPortNumber(LOGICAL_PORT_NUMBER_2)
                .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_3))
                .actual(new LogicalPortMisconfiguredInfoEntry(
                        org.openkilda.messaging.info.switches.LogicalPortType.BFD, Lists.newArrayList(PHYSICAL_PORT_3)))
                .expected(new LogicalPortMisconfiguredInfoEntry(
                        org.openkilda.messaging.info.switches.LogicalPortType.LAG,
                        Lists.newArrayList(PHYSICAL_PORT_3, PHYSICAL_PORT_4)))
                .build();
        assertEquals(misconfiguredEntry, result.getMisconfiguredLogicalPorts().get(0));
    }

    private void assertEqualLogicalPort(LogicalPort expected, LogicalPortInfoEntry actual) {
        LogicalPortInfoEntry expectedPortInfo = LogicalPortMapper.INSTANCE.map(expected);
        Collections.sort(expectedPortInfo.getPhysicalPorts());
        Collections.sort(actual.getPhysicalPorts());
        assertEquals(expectedPortInfo, actual);
    }

    @Test
    public void calculateMisconfiguredLogicalPortDifferentPortOrderTest() {
        ValidationServiceImpl validationService = new ValidationServiceImpl(persistenceManager().build(), ruleManager);

        LogicalPortInfoEntry actual = LogicalPortInfoEntry.builder()
                .type(org.openkilda.messaging.info.switches.LogicalPortType.LAG)
                .logicalPortNumber(LOGICAL_PORT_NUMBER_1)
                .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_1, PHYSICAL_PORT_2, PHYSICAL_PORT_3))
                .build();

        LogicalPortInfoEntry expected = LogicalPortInfoEntry.builder()
                .type(org.openkilda.messaging.info.switches.LogicalPortType.LAG)
                .logicalPortNumber(LOGICAL_PORT_NUMBER_1)
                .physicalPorts(Lists.newArrayList(PHYSICAL_PORT_3, PHYSICAL_PORT_2, PHYSICAL_PORT_1))
                .build();

        LogicalPortInfoEntry difference = validationService.calculateMisconfiguredLogicalPort(expected, actual);
        // physical ports are equal. Only order is different. So port difference must be null
        assertNull(difference.getActual().getPhysicalPorts());
        assertNull(difference.getExpected().getPhysicalPorts());
    }

    private void assertMeter(MeterInfoEntry meterInfoEntry, long expectedId, long expectedRate, long expectedBurstSize,
                             String[] expectedFlags) {
        assertEquals(expectedId, (long) meterInfoEntry.getMeterId());
        assertEquals(expectedRate, (long) meterInfoEntry.getRate());
        assertEquals(expectedBurstSize, (long) meterInfoEntry.getBurstSize());
        assertEquals(Sets.newHashSet(expectedFlags), Sets.newHashSet(meterInfoEntry.getFlags()));
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

    private PersistenceManagerBuilder persistenceManager() {
        return new PersistenceManagerBuilder();
    }

    private static class PersistenceManagerBuilder {
        private SwitchRepository switchRepository = mock(SwitchRepository.class);
        private LagLogicalPortRepository lagLogicalPortRepository = mock(LagLogicalPortRepository.class);
        private FlowPathRepository flowPathRepository = mock(FlowPathRepository.class);
        private FlowMeterRepository flowMeterRepository = mock(FlowMeterRepository.class);

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
