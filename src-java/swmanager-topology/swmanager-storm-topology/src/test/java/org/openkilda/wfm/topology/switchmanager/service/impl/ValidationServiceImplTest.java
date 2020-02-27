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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.Meter;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.dummy.IslDirectionalReference;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerTopologyConfig;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class ValidationServiceImplTest extends Neo4jBasedTest {
    private static final CommandContext commandContext = new CommandContext("dummy");

    private static final SwitchId SWITCH_ID_BEFORE = new SwitchId("00:10");
    private static final SwitchId SWITCH_ID_TARGET = new SwitchId("00:20");
    private static final SwitchId SWITCH_ID_AFTER = new SwitchId("00:30");

    private final IslDirectionalReference islBeforeTarget = new IslDirectionalReference(
            new IslEndpoint(SWITCH_ID_BEFORE, 1), new IslEndpoint(SWITCH_ID_TARGET, 2));
    private final IslDirectionalReference islTargetAfter = new IslDirectionalReference(
            new IslEndpoint(SWITCH_ID_TARGET, 3), new IslEndpoint(SWITCH_ID_AFTER, 4));
    private final IslDirectionalReference islBeforeAfter = new IslDirectionalReference(
            new IslEndpoint(SWITCH_ID_BEFORE, 5), new IslEndpoint(SWITCH_ID_AFTER, 6));

    private Flow flowTargetStarts;
    private Flow flowTargetEnds;
    private Flow flowTargetTransit;
    private Flow flowTargetBypass;

    private static SwitchManagerTopologyConfig topologyConfig;

    @BeforeClass
    public static void setupOnce() {
        PropertiesBasedConfigurationProvider configurationProvider =
                new PropertiesBasedConfigurationProvider(new Properties());
        topologyConfig = configurationProvider.getConfiguration(SwitchManagerTopologyConfig.class);
    }

    @Before
    public void setUp() throws Exception {
        dummyFactory.makeSwitch(SWITCH_ID_BEFORE);
        dummyFactory.makeSwitch(SWITCH_ID_TARGET);
        Switch switchAfter = dummyFactory.makeSwitch(SWITCH_ID_AFTER);

        switchAfter.setOfDescriptionManufacturer("E");
        persistenceManager.getRepositoryFactory().createSwitchRepository()
                .createOrUpdate(switchAfter);

        for (IslDirectionalReference reference : new IslDirectionalReference[]{
                islBeforeTarget, islTargetAfter, islBeforeAfter}) {
            dummyFactory.makeIsl(reference.getSourceEndpoint(), reference.getDestEndpoint());
            dummyFactory.makeIsl(reference.getDestEndpoint(), reference.getSourceEndpoint());
        }

        flowTargetStarts = dummyFactory.makeFlow(
                new FlowEndpoint(SWITCH_ID_TARGET, 10, 100),
                new FlowEndpoint(SWITCH_ID_AFTER, 11, 101),
                islTargetAfter);
        flowTargetEnds = dummyFactory.makeFlow(
                new FlowEndpoint(SWITCH_ID_BEFORE, 12, 103),
                new FlowEndpoint(SWITCH_ID_TARGET, 13, 104),
                islBeforeTarget);
        flowTargetTransit = dummyFactory.makeFlow(
                new FlowEndpoint(SWITCH_ID_BEFORE, 14, 105),
                new FlowEndpoint(SWITCH_ID_AFTER, 15, 106),
                islBeforeTarget, islTargetAfter);
        flowTargetBypass = dummyFactory.makeFlow(
                new FlowEndpoint(SWITCH_ID_BEFORE, 16, 107),
                new FlowEndpoint(SWITCH_ID_AFTER, 17, 108),
                islBeforeAfter);
    }

    @Test
    public void validateRulesEmpty() {
        ValidationService validationService = makeService();
        ValidateRulesResult response = validationService.validateRules(
                commandContext, makeValidationContext(emptyList(), emptyList()))
                .getOfFlowsValidationReport();
        verifyCookiesSeriesMatch(extractCookies(collectFlowEntries()), response.getMissingRules());
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    // FIXME
    @Test
    public void mustHaveLldpRuleWithOutConnectedDevices() {
        Flow flow = createFlowForConnectedDevices(false, false);

        ValidationServiceImpl validationService =
                new ValidationServiceImpl(persistenceManager().withSegmentsCookies(1).build(), topologyConfig);

        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_A, flow.getForwardPath()));
        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_A, flow.getReversePath()));
        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_A, flow.getProtectedForwardPath()));
        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_A, flow.getProtectedReversePath()));

        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_B, flow.getForwardPath()));
        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_B, flow.getReversePath()));
        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_B, flow.getProtectedForwardPath()));
        assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_B, flow.getProtectedReversePath()));
    }

    @Test
    public void mustHaveLldpRuleWithConnectedDevices() {
        Flow flow = createFlowForConnectedDevices(true, true);

        ValidationServiceImpl validationService = makeService();

        Assert.assertNotNull(flow.getForwardPath());
        Assert.assertTrue(validationService.mustHaveLldpRule(SWITCH_ID_BEFORE, flow.getForwardPath()));

        Assert.assertNotNull(flow.getReversePath());
        Assert.assertTrue(validationService.mustHaveLldpRule(SWITCH_ID_AFTER, flow.getReversePath()));

        Assert.assertNotNull(flow.getProtectedForwardPath());
        Assert.assertTrue(validationService.mustHaveLldpRule(SWITCH_ID_BEFORE, flow.getProtectedForwardPath()));

        Assert.assertNotNull(flow.getProtectedReversePath());
        Assert.assertTrue(validationService.mustHaveLldpRule(SWITCH_ID_AFTER, flow.getProtectedReversePath()));

        Assert.assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_AFTER, flow.getForwardPath()));
        Assert.assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_BEFORE, flow.getReversePath()));
        Assert.assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_AFTER, flow.getProtectedForwardPath()));
        Assert.assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_BEFORE, flow.getProtectedReversePath()));
    }

    @Test
    public void mustHaveLldpRuleWithOutConnectedDevices() {
        Flow flow = createFlowForConnectedDevices(false, false);

        ValidationServiceImpl validationService = makeService();
        for (FlowPath path : flow.getPaths()) {
            Assert.assertNotNull(path);
            Assert.assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_BEFORE, path));
            Assert.assertFalse(validationService.mustHaveLldpRule(SWITCH_ID_AFTER, path));
        }
    }

    @Test
    public void validateExcessAndMissingFlowRulesDetection() {
        ValidationService validationService = makeService();
>>>>>>> 4e7dffe8e... Incorporate QinQ flows logic into flow and switch validation tools

        List<FlowEntry> properEntries = collectFlowEntries(flowTargetStarts, flowTargetTransit);
        List<FlowEntry> extraEntries = collectFlowEntries(flowTargetBypass);
        List<FlowEntry> missingEntries = collectFlowEntries(flowTargetEnds);

<<<<<<< HEAD
    @Test
||||||| parent of 4e7dffe8e... Incorporate QinQ flows logic into flow and switch validation tools
    @Test
    public void validateRulesSegmentAndIngressLldpCookiesProper() {
        PersistenceManager persistenceManager = persistenceManager()
                .withIngressCookies(1L)
                .withDetectConnectedDevices(detectConnectedDevices)
                .build();
        ValidationService validationService = new ValidationServiceImpl(persistenceManager, topologyConfig);
        List<FlowEntry> flowEntries =
                Lists.newArrayList(FlowEntry.builder().cookie(1L).build());
        ValidateRulesResult response = validationService.validateRules(SWITCH_ID_A, flowEntries, emptyList());
        assertTrue(response.getMissingRules().isEmpty());
        assertEquals(ImmutableSet.of(1L), new HashSet<>(response.getProperRules()));
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void validateRulesSegmentAndIngressLldpCookiesMissing() {
        PersistenceManager persistenceManager = persistenceManager()
                .withIngressCookies(1L)
                .withDetectConnectedDevices(new DetectConnectedDevices(true, false, true, false, false, false))
                .build();
        ValidationService validationService = new ValidationServiceImpl(persistenceManager, topologyConfig);
        ValidateRulesResult response = validationService.validateRules(SWITCH_ID_A, emptyList(), emptyList());
        assertEquals(ImmutableSet.of(1L), new HashSet<>(response.getMissingRules()));
        assertTrue(response.getProperRules().isEmpty());
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
=======
        List<FlowEntry> actualEntries = new ArrayList<>(properEntries);
        actualEntries.addAll(extraEntries);

        ValidateRulesResult response = validationService.validateRules(
                commandContext, makeValidationContext(actualEntries, emptyList()))
                .getOfFlowsValidationReport();

        verifyCookiesSeriesMatch(extractCookies(missingEntries), response.getMissingRules());
        verifyCookiesSeriesMatch(extractCookies(properEntries), response.getProperRules());
        verifyCookiesSeriesMatch(extractCookies(extraEntries), response.getExcessRules());
    }

    @Test
    public void validateFlowRulesFullMatch() {
        ValidationService validationService = makeService();
        List<FlowEntry> properEntries = collectFlowEntries();
        ValidateRulesResult response = validationService.validateRules(
                commandContext, makeValidationContext(properEntries, emptyList()))
                .getOfFlowsValidationReport();
        assertTrue(response.getMissingRules().isEmpty());
        verifyCookiesSeriesMatch(extractCookies(properEntries), response.getProperRules());
        assertTrue(response.getExcessRules().isEmpty());
    }

    @Test
    public void validateDefaultRules() {
        final ValidationService validationService = makeService();
        final List<FlowEntry> flowEntries = Lists.newArrayList(
                FlowEntry.builder().cookie(0x8000000000000001L).priority(1).byteCount(123).build(),
                FlowEntry.builder().cookie(0x8000000000000001L).priority(2).build(),
                FlowEntry.builder().cookie(0x8000000000000002L).priority(1).build(),
                FlowEntry.builder().cookie(0x8000000000000002L).priority(2).build(),
                FlowEntry.builder().cookie(0x8000000000000004L).priority(1).build());
        final List<FlowEntry> expectedDefaultFlowEntries =
                Lists.newArrayList(
                        FlowEntry.builder().cookie(0x8000000000000001L).priority(1).byteCount(321).build(),
                        FlowEntry.builder().cookie(0x8000000000000002L).priority(3).build(),
                        FlowEntry.builder().cookie(0x8000000000000003L).priority(1).build());

        ValidateRulesResult response = validationService.validateRules(
                commandContext, makeValidationContext(flowEntries, expectedDefaultFlowEntries))
                .getOfFlowsValidationReport();


        verifyCookiesSeriesMatch(Lists.newArrayList(0x8000000000000001L), response.getProperRules());
        verifyCookiesSeriesMatch(
                Lists.newArrayList(0x8000000000000001L, 0x8000000000000002L), response.getMisconfiguredRules());

        List<Long> missingCookies = extractCookies(collectFlowEntries());
        missingCookies.add(0x8000000000000003L);
        verifyCookiesSeriesMatch(missingCookies, response.getMissingRules());

        verifyCookiesSeriesMatch(Lists.newArrayList(0x8000000000000004L), response.getExcessRules());
    }

    @Test
    public void validateMissingFlowMeters() {
        SwitchValidationContext validationContext = makeValidationContext(
                Collections.emptyList(), Collections.emptyList())
                .toBuilder()
                .actualMeters(Collections.emptyList())
                .build();
        ValidateMetersResult response = makeService().validateMeters(validationContext).getMetersValidationReport();

        List<MeterInfoEntry> expectedMeters = collectMeterEntries();
        verifyMetersSeriesMatch(expectedMeters, response.getMissingMeters());

        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateProperFlowMeters() {
        List<MeterInfoEntry> expectedMeters = collectMeterEntries();
        List<MeterEntry> actualMeterEntries = expectedMeters.stream()
                .map(entry -> new MeterEntry(
                        entry.getMeterId(), entry.getRate(), entry.getBurstSize(), "OF_13",
                        new String[]{"KBPS", "BURST", "STATS"}))
                .collect(Collectors.toList());
        SwitchValidationContext validationContext = makeValidationContext(
                Collections.emptyList(), Collections.emptyList())
                .toBuilder()
                .actualMeters(actualMeterEntries)
                .build();

        ValidateMetersResult response = makeService().validateMeters(validationContext).getMetersValidationReport();

        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        verifyMetersSeriesMatch(expectedMeters, response.getProperMeters());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateMetersMisconfiguredMeters() {
        long rateOffset = 100;
        long burstOffset = 200;

        List<MeterInfoEntry> expectedMeters = collectMeterEntries();
        List<MeterEntry> actualMeterEntries = expectedMeters.stream()
                .map(entry -> new MeterEntry(
                        entry.getMeterId(), entry.getRate() + rateOffset, entry.getBurstSize() + burstOffset, "OF_13",
                        new String[]{"KBPS", "BURST", "STATS"}))
                .collect(Collectors.toList());

        SwitchValidationContext validationContext = makeValidationContext(
                Collections.emptyList(), Collections.emptyList())
                .toBuilder()
                .actualMeters(actualMeterEntries)
                .build();
        ValidateMetersResult response = makeService().validateMeters(validationContext).getMetersValidationReport();

        assertTrue(response.getMissingMeters().isEmpty());

        assertEquals(actualMeterEntries.size(), response.getMisconfiguredMeters().size());
        for (MeterInfoEntry entry : response.getMisconfiguredMeters()) {
            MeterMisconfiguredInfoEntry actual = entry.getActual();
            MeterMisconfiguredInfoEntry expected = entry.getExpected();

            assertEquals(actual.getRate() - rateOffset, (long) expected.getRate());
            assertEquals(actual.getBurstSize() - burstOffset, (long) expected.getBurstSize());
        }

        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateMetersMissingAndExcessMeters() {
        List<MeterInfoEntry> expectedMeters = collectMeterEntries();
        List<MeterInfoEntry> missingMeters = Collections.singletonList(expectedMeters.remove(0));
        MeterInfoEntry lastEntry = expectedMeters.get(expectedMeters.size() - 1);
        List<MeterInfoEntry> extraMeters = Collections.singletonList(
                MeterInfoEntry.builder()
                        .meterId(lastEntry.getMeterId() + 1)
                        .rate(lastEntry.getRate())
                        .burstSize(lastEntry.getBurstSize())
                        .build());

        List<MeterEntry> actualMeterEntries = Stream.concat(expectedMeters.stream(), extraMeters.stream())
                .map(entry -> new MeterEntry(
                        entry.getMeterId(), entry.getRate(), entry.getBurstSize(), "OF_13",
                        new String[]{"KBPS", "BURST", "STATS"}))
                .collect(Collectors.toList());

        SwitchValidationContext validationContext = makeValidationContext(
                Collections.emptyList(), Collections.emptyList())
                .toBuilder()
                .actualMeters(actualMeterEntries)
                .build();
        ValidateMetersResult response = makeService().validateMeters(validationContext).getMetersValidationReport();

        assertTrue(response.getMisconfiguredMeters().isEmpty());
        verifyMetersSeriesMatch(expectedMeters, response.getProperMeters());
        verifyMetersSeriesMatch(extraMeters, response.getExcessMeters());
    }

    @Test
    public void validateMetersIgnoreDefaultMeters() {
        List<MeterInfoEntry> expectedMeters = collectMeterEntries();
        List<MeterEntry> actualMeterEntries = Lists.newArrayList(
                new MeterEntry(2, 0, 0, null, null),
                new MeterEntry(3, 0, 0, null, null));
        SwitchValidationContext validationContext = makeValidationContext(
                Collections.emptyList(), Collections.emptyList())
                .toBuilder()
                .actualMeters(actualMeterEntries)
                .build();

        ValidateMetersResult response = makeService().validateMeters(validationContext).getMetersValidationReport();

        verifyMetersSeriesMatch(expectedMeters, response.getMissingMeters());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        assertFalse(response.getProperMeters().isEmpty());
        assertEquals(1, response.getProperMeters().size());
        assertFalse(response.getExcessMeters().isEmpty());
        assertEquals(1, response.getExcessMeters().size());
        assertMeter(response.getExcessMeters().get(0), excessMeter);
    }

    @Test
    public void validateMetersProperMetersESwitch() {
        List<MeterInfoEntry> expectedMeters = collectMeterEntries(
                SWITCH_ID_AFTER, flowTargetStarts, flowTargetTransit, flowTargetBypass);

        List<MeterEntry> actualMeterEntries = expectedMeters.stream()
                .map(entry -> {
                    long rate = entry.getRate() + (long) (entry.getRate() * 0.01) - 1;
                    long burst = (long) (entry.getRate() * 1.05);
                    burst += (long) (burst * 0.01) - 1;
                    return new MeterEntry(
                            entry.getMeterId(), rate, burst, "OF_13",
                            new String[]{"KBPS", "BURST", "STATS"});
                })
                .collect(Collectors.toList());

        SwitchValidationContext validationContext = makeValidationContext(
                Collections.emptyList(), Collections.emptyList())
                .toBuilder()
                .switchId(SWITCH_ID_AFTER)
                .actualMeters(actualMeterEntries)
                .build();
        ValidateMetersResult response = makeService().validateMeters(validationContext).getMetersValidationReport();

        assertTrue(response.getMissingMeters().isEmpty());
        assertTrue(response.getMisconfiguredMeters().isEmpty());
        verifyMetersSeriesMatch(expectedMeters, response.getProperMeters());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    @Test
    public void validateMetersMisconfiguredMetersESwitch() {
        List<MeterInfoEntry> expectedMeters = collectMeterEntries(
                SWITCH_ID_AFTER, flowTargetStarts, flowTargetTransit, flowTargetBypass);

        long rateOffset = 100;
        long burstOffset = 200;
        List<MeterEntry> actualMeterEntries = expectedMeters.stream()
                .map(entry -> {
                    long rate = entry.getRate() + (long) (entry.getRate() * 0.01) - 1;
                    long burst = (long) (entry.getRate() * 1.05);
                    burst += (long) (burst * 0.01) - 1;
                    return new MeterEntry(
                            entry.getMeterId(), rate + rateOffset, burst + burstOffset, "OF_13",
                            new String[]{"KBPS", "BURST", "STATS"});
                })
                .collect(Collectors.toList());

        SwitchValidationContext validationContext = makeValidationContext(
                Collections.emptyList(), Collections.emptyList())
                .toBuilder()
                .switchId(SWITCH_ID_AFTER)
                .actualMeters(actualMeterEntries)
                .build();
        ValidateMetersResult response = makeService().validateMeters(validationContext).getMetersValidationReport();

        assertTrue(response.getMissingMeters().isEmpty());
        verifyMetersSeriesMatch(expectedMeters, response.getMisconfiguredMeters());
        assertTrue(response.getProperMeters().isEmpty());
        assertTrue(response.getExcessMeters().isEmpty());
    }

    private void verifyCookiesSeriesMatch(List<Long> expectedSeries, List<Long> actualSeries) {
        List<Long> expected = new ArrayList<>(expectedSeries);
        expected.sort(null);

        List<Long> actual = new ArrayList<>(actualSeries);
        actual.sort(null);
        assertEquals(expected, actual);
    }

    private void verifyMetersSeriesMatch(List<MeterInfoEntry> expectedSeries, List<MeterInfoEntry> actualSeries) {
        Set<Long> expected = extractMeterIds(expectedSeries);
        Set<Long> actual = extractMeterIds(actualSeries);
        assertEquals(expected, actual);
    }

    private List<FlowEntry> collectFlowEntries() {
        return collectFlowEntries(flowTargetStarts, flowTargetEnds, flowTargetTransit);
    }

    private List<FlowEntry> collectFlowEntries(Flow... flowsSet) {
        List<FlowEntry> results = new ArrayList<>();
        for (Flow flow : flowsSet) {
            for (FlowPath path : flow.getPaths()) {
                FlowEntry entry = FlowEntry.builder()
                        .cookie(path.getCookie().getValue())
                        .build();
                results.add(entry);
            }
        }
        return results;
    }

    private List<MeterInfoEntry> collectMeterEntries() {
        return collectMeterEntries(SWITCH_ID_TARGET, flowTargetStarts, flowTargetEnds);
    }

    private List<MeterInfoEntry> collectMeterEntries(SwitchId target, Flow... flowsSet) {
        List<MeterInfoEntry> results = new ArrayList<>();
        for (Flow flow : flowsSet) {
            for (FlowPath path : new FlowPath[]{flow.getForwardPath(), flow.getReversePath()}) {
                if (path == null) {
                    continue;
                }

                FlowSideAdapter sideAdapter = FlowSideAdapter.makeIngressAdapter(flow, path);
                if (! target.equals(sideAdapter.getEndpoint().getSwitchId())) {
                    continue;
                }

                long burstSize = Meter.calculateBurstSize(
                        path.getBandwidth(), topologyConfig.getFlowMeterMinBurstSizeInKbits(),
                        topologyConfig.getFlowMeterBurstCoefficient(), path.getSrcSwitch().getDescription());

                MeterInfoEntry entry = MeterInfoEntry.builder()
                        .meterId(path.getMeterId().getValue())
                        .rate(path.getBandwidth())
                        .burstSize(burstSize)
                        .build();
                results.add(entry);
            }
        }
        return results;
    }

    private List<Long> extractCookies(List<FlowEntry> flowEntries) {
        return flowEntries.stream()
                .map(FlowEntry::getCookie)
                .collect(Collectors.toList());
    }

    private Set<Long> extractMeterIds(List<MeterInfoEntry> meterEntries) {
        return meterEntries.stream()
                .map(MeterInfoEntry::getMeterId)
                .collect(Collectors.toSet());
    }

    private ValidationServiceImpl makeService() {
        FlowResourcesConfig resourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        return new ValidationServiceImpl(
                persistenceManager, topologyConfig, new FlowResourcesManager(persistenceManager, resourcesConfig));
    }

    private SwitchValidationContext makeValidationContext(
            List<FlowEntry> actualOfFlows, List<FlowEntry> expectedDefaultOfFlows) {
        return SwitchValidationContext.builder(SWITCH_ID_TARGET)
                .actualOfFlows(actualOfFlows)
                .expectedDefaultOfFlows(expectedDefaultOfFlows)
                .build();
    }

    private Flow createFlowForConnectedDevices(boolean detectSrcLldp, boolean detectDstLldp) {
        FlowEndpoint ingress = new FlowEndpoint(SWITCH_ID_BEFORE, 21);
        FlowEndpoint egress = new FlowEndpoint(SWITCH_ID_AFTER, 22);
        Flow flow = dummyFactory.makeFlow(ingress, egress, islBeforeTarget, islTargetAfter);
        dummyFactory.injectProtectedPath(flow, islBeforeAfter);

        flow.setDetectConnectedDevices(new DetectConnectedDevices(
                detectSrcLldp, false, detectDstLldp, false, false, false, false, false));
        persistenceManager.getRepositoryFactory().createFlowRepository()
                .createOrUpdate(flow);

        return flow;
    }
}
