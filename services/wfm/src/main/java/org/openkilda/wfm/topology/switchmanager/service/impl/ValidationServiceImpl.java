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

import static com.google.common.collect.Lists.newArrayList;
import static org.openkilda.model.SwitchFeature.ACCURATE_SET_OF_METER_RATE_AND_BURST_SIZE;

import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerTopologyConfig;
import org.openkilda.wfm.topology.switchmanager.model.SimpleMeterEntry;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ValidationServiceImpl implements ValidationService {
    private static final int METER_BURST_SIZE_EQUALS_DELTA = 1;
    private static final double E_SWITCH_METER_RATE_EQUALS_DELTA_COEFFICIENT = 0.01;
    private static final double E_SWITCH_METER_BURST_SIZE_EQUALS_DELTA_COEFFICIENT = 0.01;

    private FlowPathRepository flowPathRepository;
    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;

    public ValidationServiceImpl(PersistenceManager persistenceManager, SwitchManagerTopologyConfig topologyConfig) {
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.flowMeterMinBurstSizeInKbits = topologyConfig.getFlowMeterMinBurstSizeInKbits();
        this.flowMeterBurstCoefficient = topologyConfig.getFlowMeterBurstCoefficient();
    }

    @Override
    public ValidateRulesResult validateRules(SwitchId switchId, List<FlowEntry> presentRules,
                                             List<FlowEntry> expectedDefaultRules) {
        log.debug("Validating rules on switch {}", switchId);

        Set<Long> expectedCookies = flowPathRepository.findBySegmentDestSwitch(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .collect(Collectors.toSet());

        flowPathRepository.findByEndpointSwitch(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .forEach(expectedCookies::add);

        return makeRulesResponse(expectedCookies, presentRules, expectedDefaultRules, switchId);
    }

    private ValidateRulesResult makeRulesResponse(Set<Long> expectedCookies, List<FlowEntry> presentRules,
                                                  List<FlowEntry> expectedDefaultRules, SwitchId switchId) {
        Set<Long> presentCookies = presentRules.stream()
                .map(FlowEntry::getCookie)
                .filter(cookie -> !Cookie.isDefaultRule(cookie))
                .collect(Collectors.toSet());

        Set<Long> missingRules = new HashSet<>(expectedCookies);
        missingRules.removeAll(presentCookies);
        if (!missingRules.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following rules are missed: {}", switchId,
                    cookiesIntoLogRepresentation(missingRules));
        }

        Set<Long> properRules = new HashSet<>(expectedCookies);
        properRules.retainAll(presentCookies);

        Set<Long> excessRules = new HashSet<>(presentCookies);
        excessRules.removeAll(expectedCookies);
        if (!excessRules.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following rules are excessive: {}", switchId,
                    cookiesIntoLogRepresentation(excessRules));
        }

        Set<Long> misconfiguredRules = new HashSet<>();

        validateDefaultRules(presentRules, expectedDefaultRules, missingRules, properRules, excessRules,
                misconfiguredRules);

        return new ValidateRulesResult(
                ImmutableList.copyOf(missingRules),
                ImmutableList.copyOf(properRules),
                ImmutableList.copyOf(excessRules),
                ImmutableList.copyOf(misconfiguredRules));
    }

    private void validateDefaultRules(List<FlowEntry> presentRules, List<FlowEntry> expectedDefaultRules,
                                      Set<Long> missingRules, Set<Long> properRules, Set<Long> excessRules,
                                      Set<Long> misconfiguredRules) {
        List<FlowEntry> presentDefaultRules = presentRules.stream()
                .filter(rule -> Cookie.isDefaultRule(rule.getCookie()))
                .collect(Collectors.toList());

        expectedDefaultRules.forEach(expectedDefaultRule -> {
            List<FlowEntry> defaultRule = presentDefaultRules.stream()
                    .filter(rule -> rule.getCookie() == expectedDefaultRule.getCookie())
                    .collect(Collectors.toList());

            if (defaultRule.isEmpty()) {
                missingRules.add(expectedDefaultRule.getCookie());
            } else {
                if (defaultRule.contains(expectedDefaultRule)) {
                    properRules.add(expectedDefaultRule.getCookie());
                } else {
                    misconfiguredRules.add(expectedDefaultRule.getCookie());
                }

                if (defaultRule.size() > 1) {
                    misconfiguredRules.add(expectedDefaultRule.getCookie());
                }
            }
        });

        presentDefaultRules.forEach(presentDefaultRule -> {
            List<FlowEntry> defaultRule = expectedDefaultRules.stream()
                    .filter(rule -> rule.getCookie() == presentDefaultRule.getCookie())
                    .collect(Collectors.toList());

            if (defaultRule.isEmpty()) {
                excessRules.add(presentDefaultRule.getCookie());
            }
        });
    }

    private static String cookiesIntoLogRepresentation(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    @Override
    public ValidateMetersResult validateMeters(SwitchId switchId, List<MeterEntry> presentMeters) {
        log.debug("Validating meters on switch {}", switchId);

        presentMeters.removeIf(meterEntry -> MeterId.isMeterIdOfDefaultRule(meterEntry.getMeterId()));

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchId);

        if (paths.isEmpty()) {
            // we do not expect any flow meter on this switch
            List<MeterInfoEntry> excessMeters = getExcessMeters(presentMeters, newArrayList());
            return new ValidateMetersResult(newArrayList(), newArrayList(), newArrayList(), excessMeters);
        }

        Switch sw = paths.iterator().next().getSrcSwitch();
        boolean accurateRateAndBurstSize = sw.getFeatures().contains(ACCURATE_SET_OF_METER_RATE_AND_BURST_SIZE);

        List<SimpleMeterEntry> expectedMeters = getExpectedFlowMeters(paths);

        return comparePresentedAndExpectedMeters(accurateRateAndBurstSize, presentMeters, expectedMeters);
    }

    private ValidateMetersResult comparePresentedAndExpectedMeters(
            boolean accurateRateAndBurstSize, List<MeterEntry> presentMeters, List<SimpleMeterEntry> expectedMeters) {
        Map<Long, MeterEntry> presentMeterMap = presentMeters.stream()
                .collect(Collectors.toMap(MeterEntry::getMeterId, Function.identity()));

        List<MeterInfoEntry> missingMeters = new ArrayList<>();
        List<MeterInfoEntry> misconfiguredMeters = new ArrayList<>();
        List<MeterInfoEntry> properMeters = new ArrayList<>();

        for (SimpleMeterEntry expectedMeter : expectedMeters) {
            MeterEntry presentedMeter = presentMeterMap.get(expectedMeter.getMeterId());

            if (presentedMeter == null) {
                missingMeters.add(makeMissingMeterEntry(expectedMeter));
                continue;
            }

            if (equalsRate(presentedMeter.getRate(), expectedMeter.getRate(), accurateRateAndBurstSize)
                    && equalsBurstSize(
                            presentedMeter.getBurstSize(), expectedMeter.getBurstSize(), accurateRateAndBurstSize)
                    && Arrays.equals(presentedMeter.getFlags(), expectedMeter.getFlags())) {

                properMeters.add(makeProperMeterEntry(
                        expectedMeter.getFlowId(), expectedMeter.getCookie(), presentedMeter));
            } else {
                misconfiguredMeters.add(
                        makeMisconfiguredMeterEntry(presentedMeter, expectedMeter, accurateRateAndBurstSize));
            }
        }

        List<MeterInfoEntry> excessMeters = getExcessMeters(presentMeters, expectedMeters);
        return new ValidateMetersResult(missingMeters, misconfiguredMeters, properMeters, excessMeters);
    }

    private List<MeterInfoEntry> getExcessMeters(List<MeterEntry> presented, List<SimpleMeterEntry> expected) {
        List<MeterInfoEntry> excessMeters = new ArrayList<>();

        Set<Long> expectedMeterIds = expected.stream()
                .map(SimpleMeterEntry::getMeterId)
                .collect(Collectors.toSet());

        for (MeterEntry meterEntry : presented) {
            if (!expectedMeterIds.contains(meterEntry.getMeterId())) {
                excessMeters.add(makeExcessMeterEntry(meterEntry));
            }
        }
        return excessMeters;
    }

    private List<SimpleMeterEntry> getExpectedFlowMeters(Collection<FlowPath> unfilteredPaths) {
        List<SimpleMeterEntry> expectedMeters = new ArrayList<>();

        Collection<FlowPath> paths = unfilteredPaths.stream()
                .filter(path -> path.getMeterId() != null)
                .collect(Collectors.toList());

        for (FlowPath path : paths) {
            long calculatedBurstSize = Meter.calculateBurstSize(path.getBandwidth(), flowMeterMinBurstSizeInKbits,
                    flowMeterBurstCoefficient, path.getSrcSwitch().getFeatures());

            SimpleMeterEntry expectedMeter = new SimpleMeterEntry(path.getFlow().getFlowId(),
                    path.getMeterId().getValue(), path.getCookie().getValue(), path.getBandwidth(), calculatedBurstSize,
                    Meter.getMeterFlags());
            expectedMeters.add(expectedMeter);
        }
        return expectedMeters;
    }

    private MeterInfoEntry makeMissingMeterEntry(SimpleMeterEntry meter) {
        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .cookie(meter.getCookie())
                .flowId(meter.getFlowId())
                .rate(meter.getRate())
                .burstSize(meter.getBurstSize())
                .flags(meter.getFlags())
                .build();
    }

    private MeterInfoEntry makeProperMeterEntry(String flowId, Long cookie, MeterEntry meter) {
        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .cookie(cookie)
                .flowId(flowId)
                .rate(meter.getRate())
                .burstSize(meter.getBurstSize())
                .flags(meter.getFlags())
                .build();
    }

    private MeterInfoEntry makeExcessMeterEntry(MeterEntry meter) {
        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .rate(meter.getRate())
                .burstSize(meter.getBurstSize())
                .flags(meter.getFlags())
                .build();
    }

    private MeterInfoEntry makeMisconfiguredMeterEntry(MeterEntry actualMeter, SimpleMeterEntry expectedMeter,
                                                       boolean isESwitch) {
        MeterMisconfiguredInfoEntry actual = new MeterMisconfiguredInfoEntry();
        MeterMisconfiguredInfoEntry expected = new MeterMisconfiguredInfoEntry();

        if (!equalsRate(actualMeter.getRate(), expectedMeter.getRate(), isESwitch)) {
            actual.setRate(actualMeter.getRate());
            expected.setRate(expectedMeter.getRate());
        }
        if (!equalsBurstSize(actualMeter.getBurstSize(), expectedMeter.getBurstSize(), isESwitch)) {
            actual.setBurstSize(actualMeter.getBurstSize());
            expected.setBurstSize(expectedMeter.getBurstSize());
        }
        if (!Arrays.equals(actualMeter.getFlags(), expectedMeter.getFlags())) {
            actual.setFlags(actualMeter.getFlags());
            expected.setFlags(expectedMeter.getFlags());
        }

        return MeterInfoEntry.builder()
                .meterId(actualMeter.getMeterId())
                .cookie(expectedMeter.getCookie())
                .flowId(expectedMeter.getFlowId())
                .rate(actualMeter.getRate())
                .burstSize(actualMeter.getBurstSize())
                .flags(actualMeter.getFlags())
                .actual(actual)
                .expected(expected)
                .build();
    }

    private boolean equalsRate(long actual, long expected, boolean accurateRateAndBurstSize) {
        // E-switches have a bug when installing the rate and burst size.
        // Such switch sets the rate different from the rate that was sent to it.
        // Therefore, we compare actual and expected values ​​using the delta coefficient.
        if (!accurateRateAndBurstSize) {
            return Math.abs(actual - expected) <= expected * E_SWITCH_METER_RATE_EQUALS_DELTA_COEFFICIENT;
        }
        return actual == expected;
    }

    private boolean equalsBurstSize(long actual, long expected, boolean accurateRateAndBurstSize) {
        // E-switches have a bug when installing the rate and burst size.
        // Such switch sets the burst size different from the burst size that was sent to it.
        // Therefore, we compare actual and expected values ​​using the delta coefficient.
        if (!accurateRateAndBurstSize) {
            return Math.abs(actual - expected) <= expected * E_SWITCH_METER_BURST_SIZE_EQUALS_DELTA_COEFFICIENT;
        }
        return Math.abs(actual - expected) <= METER_BURST_SIZE_EQUALS_DELTA;
    }
}
