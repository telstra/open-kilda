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
import java.util.Set;
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
                    excessRules.add(expectedDefaultRule.getCookie());
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

        List<Long> presentMeterIds = presentMeters.stream()
                .map(MeterEntry::getMeterId)
                .collect(Collectors.toList());

        List<MeterInfoEntry> missingMeters = new ArrayList<>();
        List<MeterInfoEntry> misconfiguredMeters = new ArrayList<>();
        List<MeterInfoEntry> properMeters = new ArrayList<>();
        List<MeterInfoEntry> excessMeters = new ArrayList<>();

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchId).stream()
                .filter(path -> path.getMeterId() != null)
                .collect(Collectors.toList());

        for (FlowPath path : paths) {
            Switch sw = path.getSrcSwitch();
            boolean isESwitch =
                    Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

            long calculatedBurstSize = Meter.calculateBurstSize(path.getBandwidth(), flowMeterMinBurstSizeInKbits,
                    flowMeterBurstCoefficient, path.getSrcSwitch().getDescription());

            if (!presentMeterIds.contains(path.getMeterId().getValue())) {
                missingMeters.add(makeMissingMeterEntry(path, calculatedBurstSize));
            }

            for (MeterEntry meter : presentMeters) {
                if (meter.getMeterId() == path.getMeterId().getValue()) {
                    if (equalsRate(meter.getRate(), path.getBandwidth(), isESwitch)
                            && equalsBurstSize(meter.getBurstSize(), calculatedBurstSize, isESwitch)
                            && Arrays.equals(meter.getFlags(), Meter.getMeterFlags())) {

                        properMeters.add(makeProperMeterEntry(path, meter));
                    } else {
                        misconfiguredMeters.add(
                                makeMisconfiguredMeterEntry(path, meter, calculatedBurstSize, isESwitch));
                    }
                }
            }
        }

        List<Long> expectedMeterIds = paths.stream()
                .map(FlowPath::getMeterId)
                .map(MeterId::getValue)
                .collect(Collectors.toList());

        for (MeterEntry meterEntry : presentMeters) {
            if (!expectedMeterIds.contains(meterEntry.getMeterId())) {
                excessMeters.add(makeExcessMeterEntry(meterEntry));
            }
        }

        return new ValidateMetersResult(missingMeters, misconfiguredMeters, properMeters, excessMeters);
    }

    private MeterInfoEntry makeMissingMeterEntry(FlowPath path, Long burstSize) {
        return MeterInfoEntry.builder()
                .meterId(path.getMeterId().getValue())
                .cookie(path.getCookie().getValue())
                .flowId(path.getFlow().getFlowId())
                .rate(path.getBandwidth())
                .burstSize(burstSize)
                .flags(Meter.getMeterFlags())
                .build();
    }

    private MeterInfoEntry makeProperMeterEntry(FlowPath path, MeterEntry meter) {
        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .cookie(path.getCookie().getValue())
                .flowId(path.getFlow().getFlowId())
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

    private MeterInfoEntry makeMisconfiguredMeterEntry(FlowPath path, MeterEntry meter, long burstSize,
                                                       boolean isESwitch) {
        MeterMisconfiguredInfoEntry actual = new MeterMisconfiguredInfoEntry();
        MeterMisconfiguredInfoEntry expected = new MeterMisconfiguredInfoEntry();

        if (!equalsRate(meter.getRate(), path.getBandwidth(), isESwitch)) {
            actual.setRate(meter.getRate());
            expected.setRate(path.getBandwidth());
        }
        if (!equalsBurstSize(meter.getBurstSize(), burstSize, isESwitch)) {
            actual.setBurstSize(meter.getBurstSize());
            expected.setBurstSize(burstSize);
        }
        if (!Arrays.equals(meter.getFlags(), Meter.getMeterFlags())) {
            actual.setFlags(meter.getFlags());
            expected.setFlags(Meter.getMeterFlags());
        }

        return MeterInfoEntry.builder()
                .meterId(meter.getMeterId())
                .cookie(path.getCookie().getValue())
                .flowId(path.getFlow().getFlowId())
                .rate(meter.getRate())
                .burstSize(meter.getBurstSize())
                .flags(meter.getFlags())
                .actual(actual)
                .expected(expected)
                .build();
    }

    private boolean equalsRate(long actual, long expected, boolean isESwitch) {
        // E-switches have a bug when installing the rate and burst size.
        // Such switch sets the rate different from the rate that was sent to it.
        // Therefore, we compare actual and expected values ​​using the delta coefficient.
        if (isESwitch) {
            return Math.abs(actual - expected) <= expected * E_SWITCH_METER_RATE_EQUALS_DELTA_COEFFICIENT;
        }
        return actual == expected;
    }

    private boolean equalsBurstSize(long actual, long expected, boolean isESwitch) {
        // E-switches have a bug when installing the rate and burst size.
        // Such switch sets the burst size different from the burst size that was sent to it.
        // Therefore, we compare actual and expected values ​​using the delta coefficient.
        if (isESwitch) {
            return Math.abs(actual - expected) <= expected * E_SWITCH_METER_BURST_SIZE_EQUALS_DELTA_COEFFICIENT;
        }
        return Math.abs(actual - expected) <= METER_BURST_SIZE_EQUALS_DELTA;
    }
}
