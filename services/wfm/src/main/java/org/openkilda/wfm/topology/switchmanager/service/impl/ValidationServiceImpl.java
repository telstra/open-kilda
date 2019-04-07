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
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
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
    private static final int METER_BURST_SIZE_EQUALS_EPS = 1;

    private FlowPathRepository flowPathRepository;

    public ValidationServiceImpl(PersistenceManager persistenceManager) {
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    public ValidateRulesResult validateRules(SwitchId switchId, Set<Long> presentCookies) {
        log.debug("Validating rules on switch {}", switchId);

        Set<Long> expectedCookies = flowPathRepository.findBySegmentDestSwitch(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .collect(Collectors.toSet());

        flowPathRepository.findByEndpointSwitch(switchId).stream()
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .forEach(expectedCookies::add);

        presentCookies.removeIf(Cookie::isDefaultRule);

        return makeRulesResponse(expectedCookies, presentCookies, switchId);
    }

    private ValidateRulesResult makeRulesResponse(Set<Long> expectedCookies,
                                                  Set<Long> presentCookies,
                                                  SwitchId switchId) {
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

        return new ValidateRulesResult(
                ImmutableList.copyOf(missingRules),
                ImmutableList.copyOf(properRules),
                ImmutableList.copyOf(excessRules)
        );
    }

    private static String cookiesIntoLogRepresentation(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    @Override
    public ValidateMetersResult validateMeters(SwitchId switchId, List<MeterEntry> presentMeters,
                                               long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        presentMeters.removeIf(meterEntry -> MeterId.isMeterIdOfDefaultRule(meterEntry.getMeterId()));
        log.debug("Validating meters on switch {}", switchId);
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
            long calculatedBurstSize = Meter.calculateBurstSize(path.getBandwidth(), flowMeterMinBurstSizeInKbits,
                    flowMeterBurstCoefficient, path.getSrcSwitch().getDescription());

            if (!presentMeterIds.contains(path.getMeterId().getValue())) {
                missingMeters.add(MeterInfoEntry.builder()
                        .meterId(path.getMeterId().getValue())
                        .cookie(path.getCookie().getValue())
                        .flowId(path.getFlowId())
                        .rate(path.getBandwidth())
                        .burstSize(calculatedBurstSize)
                        .flags(Meter.getMeterFlags())
                        .build());
            }

            for (MeterEntry meter : presentMeters) {
                if (meter.getMeterId() == path.getMeterId().getValue()) {
                    if (meter.getRate() == path.getBandwidth()
                            && equalsBurstSize(meter.getBurstSize(), calculatedBurstSize)
                            && Arrays.equals(meter.getFlags(), Meter.getMeterFlags())) {
                        properMeters.add(MeterInfoEntry.builder()
                                .meterId(meter.getMeterId())
                                .cookie(path.getCookie().getValue())
                                .flowId(path.getFlowId())
                                .rate(meter.getRate())
                                .burstSize(meter.getBurstSize())
                                .flags(meter.getFlags())
                                .build());
                    } else {
                        MeterMisconfiguredInfoEntry actual = new MeterMisconfiguredInfoEntry();
                        MeterMisconfiguredInfoEntry expected = new MeterMisconfiguredInfoEntry();

                        if (meter.getRate() != path.getBandwidth()) {
                            actual.setRate(meter.getRate());
                            expected.setRate(path.getBandwidth());
                        }
                        if (!equalsBurstSize(meter.getBurstSize(), calculatedBurstSize)) {
                            actual.setBurstSize(meter.getBurstSize());
                            expected.setBurstSize(calculatedBurstSize);
                        }
                        if (!Arrays.equals(meter.getFlags(), Meter.getMeterFlags())) {
                            actual.setFlags(meter.getFlags());
                            expected.setFlags(Meter.getMeterFlags());
                        }

                        misconfiguredMeters.add(MeterInfoEntry.builder()
                                .meterId(meter.getMeterId())
                                .cookie(path.getCookie().getValue())
                                .flowId(path.getFlowId())
                                .rate(meter.getRate())
                                .burstSize(meter.getBurstSize())
                                .flags(meter.getFlags())
                                .actual(actual)
                                .expected(expected)
                                .build());
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
                excessMeters.add(MeterInfoEntry.builder()
                        .meterId(meterEntry.getMeterId())
                        .rate(meterEntry.getRate())
                        .burstSize(meterEntry.getBurstSize())
                        .flags(meterEntry.getFlags())
                        .build());
            }
        }

        return new ValidateMetersResult(missingMeters, misconfiguredMeters, properMeters, excessMeters);
    }

    private boolean equalsBurstSize(long actual, long expected) {
        return Math.abs(actual - expected) <= METER_BURST_SIZE_EQUALS_EPS;
    }
}
