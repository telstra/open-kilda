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

import static java.util.stream.Collectors.toList;

import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.error.SwitchNotFoundException;
import org.openkilda.wfm.share.mappers.MeterEntryMapper;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerTopologyConfig;
import org.openkilda.wfm.topology.switchmanager.model.SimpleMeterEntry;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
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
    private FlowPathRepository flowPathRepository;
    private SwitchRepository switchRepository;
    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;
    private final int lldpRateLimit;
    private final int lldpPacketSize;
    private final long lldpMeterBurstSizeInPackets;

    public ValidationServiceImpl(PersistenceManager persistenceManager, SwitchManagerTopologyConfig topologyConfig) {
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.flowMeterMinBurstSizeInKbits = topologyConfig.getFlowMeterMinBurstSizeInKbits();
        this.flowMeterBurstCoefficient = topologyConfig.getFlowMeterBurstCoefficient();
        this.lldpRateLimit = topologyConfig.getLldpRateLimit();
        this.lldpPacketSize = topologyConfig.getLldpPacketSize();
        this.lldpMeterBurstSizeInPackets = topologyConfig.getLldpMeterBurstSizeInPackets();
    }

    @Override
    public ValidateRulesResult validateRules(SwitchId switchId, List<FlowEntry> presentRules,
                                             List<FlowEntry> expectedDefaultRules) {
        log.debug("Validating rules on switch {}", switchId);

        Set<Long> expectedCookies = flowPathRepository.findBySegmentDestSwitch(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .collect(Collectors.toSet());

        Collection<FlowPath> paths = flowPathRepository.findByEndpointSwitch(switchId);

        paths.stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .forEach(expectedCookies::add);

        return makeRulesResponse(
                expectedCookies, presentRules, expectedDefaultRules, switchId);
    }

    @VisibleForTesting
    boolean mustHaveLldpRule(SwitchId switchId, FlowPath path) {
        Flow flow = path.getFlow();

        if (flow.getDetectConnectedDevices().isSrcLldp() && flow.getSrcSwitch().getSwitchId().equals(switchId)
                && flow.isForward(path)) {
            return true;
        }

        if (flow.getDetectConnectedDevices().isDstLldp() && flow.getDestSwitch().getSwitchId().equals(switchId)
                && flow.isReverse(path)) {
            return true;
        }

        return false;
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
                .collect(toList());

        expectedDefaultRules.forEach(expectedDefaultRule -> {
            List<FlowEntry> defaultRule = presentDefaultRules.stream()
                    .filter(rule -> rule.getCookie() == expectedDefaultRule.getCookie())
                    .collect(toList());

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
                    .collect(toList());

            if (defaultRule.isEmpty()) {
                excessRules.add(presentDefaultRule.getCookie());
            }
        });
    }

    private static String cookiesIntoLogRepresentation(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    @Override
    public ValidateMetersResult validateMeters(SwitchId switchId, List<MeterEntry> presentMeters,
                                               List<MeterEntry> expectedDefaultMeters) throws SwitchNotFoundException {
        log.debug("Validating meters on switch {}", switchId);

        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        boolean isESwitch = Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

        List<SimpleMeterEntry> expectedMeters = expectedDefaultMeters.stream()
                .map(MeterEntryMapper.INSTANCE::map)
                .collect(toList());

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchId);
        if (!paths.isEmpty()) {
            expectedMeters.addAll(getExpectedFlowMeters(paths));
        }

        return comparePresentedAndExpectedMeters(isESwitch, presentMeters, expectedMeters);
    }

    private ValidateMetersResult comparePresentedAndExpectedMeters(
            boolean isESwitch, List<MeterEntry> presentMeters, List<SimpleMeterEntry> expectedMeters) {
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

            if (Meter.equalsRate(presentedMeter.getRate(), expectedMeter.getRate(), isESwitch)
                    && Meter.equalsBurstSize(presentedMeter.getBurstSize(), expectedMeter.getBurstSize(), isESwitch)
                    && flagsAreEqual(presentedMeter.getFlags(), expectedMeter.getFlags())) {

                properMeters.add(makeProperMeterEntry(
                        expectedMeter.getFlowId(), expectedMeter.getCookie(), presentedMeter));
            } else {
                misconfiguredMeters.add(makeMisconfiguredMeterEntry(presentedMeter, expectedMeter, isESwitch));
            }
        }

        List<MeterInfoEntry> excessMeters = getExcessMeters(presentMeters, expectedMeters);
        return new ValidateMetersResult(missingMeters, misconfiguredMeters, properMeters, excessMeters);
    }

    private boolean flagsAreEqual(String[] present, String[] expected) {
        Set<String> left = Sets.newHashSet(present);
        Set<String> right = Sets.newHashSet(expected);

        return left.size() == right.size() && left.containsAll(right);
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
                    flowMeterBurstCoefficient, path.getSrcSwitch().getDescription());

            SimpleMeterEntry expectedMeter = SimpleMeterEntry.builder()
                    .flowId(path.getFlow().getFlowId())
                    .meterId(path.getMeterId().getValue())
                    .cookie(path.getCookie().getValue())
                    .rate(path.getBandwidth())
                    .burstSize(calculatedBurstSize)
                    .flags(Meter.getMeterKbpsFlags())
                    .build();
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

        if (!Meter.equalsRate(actualMeter.getRate(), expectedMeter.getRate(), isESwitch)) {
            actual.setRate(actualMeter.getRate());
            expected.setRate(expectedMeter.getRate());
        }
        if (!Meter.equalsBurstSize(actualMeter.getBurstSize(), expectedMeter.getBurstSize(), isESwitch)) {
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
}
