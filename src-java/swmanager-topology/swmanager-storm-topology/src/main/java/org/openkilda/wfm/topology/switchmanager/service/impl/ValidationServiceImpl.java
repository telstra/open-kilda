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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.messaging.info.switches.GroupInfoEntry.BucketEntry;
import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortMisconfiguredInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortType;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.model.FlowMeter;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.mappers.GroupEntryConverter;
import org.openkilda.wfm.topology.switchmanager.mappers.LogicalPortMapper;
import org.openkilda.wfm.topology.switchmanager.mappers.MeterEntryConverter;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ValidationServiceImpl implements ValidationService {
    private final PersistenceManager persistenceManager;
    private final SwitchRepository switchRepository;
    private final LagLogicalPortRepository lagLogicalPortRepository;
    private final FlowMeterRepository flowMeterRepository;
    private final FlowPathRepository flowPathRepository;
    private final RuleManager ruleManager;

    public ValidationServiceImpl(PersistenceManager persistenceManager, RuleManager ruleManager) {
        this.persistenceManager = persistenceManager;
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.lagLogicalPortRepository = persistenceManager.getRepositoryFactory().createLagLogicalPortRepository();
        this.flowMeterRepository = persistenceManager.getRepositoryFactory().createFlowMeterRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.ruleManager = ruleManager;
    }

    @Override
    public List<SpeakerData> buildExpectedEntities(SwitchId switchId) {
        Set<PathId> flowPathIds = flowPathRepository.findBySegmentSwitch(switchId).stream()
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet());
        flowPathIds.addAll(flowPathRepository.findByEndpointSwitch(switchId).stream()
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet()));
        PersistenceDataAdapter dataAdapter = PersistenceDataAdapter.builder()
                .persistenceManager(persistenceManager)
                .switchIds(Collections.singleton(switchId))
                .pathIds(flowPathIds)
                .keepMultitableForFlow(true)
                .build();
        return ruleManager.buildRulesForSwitch(switchId, dataAdapter);
    }

    @Override
    public ValidateRulesResult validateRules(SwitchId switchId, List<FlowSpeakerData> presentRules,
                                             List<FlowSpeakerData> expectedRules) {
        log.debug("Validating rules on switch {}", switchId);

        Set<Long> missingRules = new HashSet<>();
        Set<Long> properRules = new HashSet<>();
        Set<Long> excessRules = new HashSet<>();
        Set<Long> misconfiguredRules = new HashSet<>();

        processRulesValidation(presentRules, expectedRules, missingRules, properRules, excessRules,
                misconfiguredRules);

        if (!missingRules.isEmpty() && log.isErrorEnabled()) {
            log.warn("On switch {} the following rules are missed: {}", switchId,
                    cookiesIntoLogRepresentation(missingRules));
        }

        if (!excessRules.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following rules are excessive: {}", switchId,
                    cookiesIntoLogRepresentation(excessRules));
        }

        return new ValidateRulesResult(
                ImmutableSet.copyOf(missingRules),
                ImmutableSet.copyOf(properRules),
                ImmutableSet.copyOf(excessRules),
                ImmutableSet.copyOf(misconfiguredRules));
    }

    @Override
    public ValidateGroupsResult validateGroups(SwitchId switchId, List<GroupSpeakerData> groupEntries,
                                               List<GroupSpeakerData> expectedGroupSpeakerData) {
        Set<GroupInfoEntry> expectedGroups = expectedGroupSpeakerData.stream()
                .map(GroupEntryConverter.INSTANCE::toGroupEntry)
                .collect(Collectors.toSet());

        Set<GroupInfoEntry> presentGroups = groupEntries.stream()
                .map(GroupEntryConverter.INSTANCE::toGroupEntry)
                .collect(Collectors.toSet());

        Set<Integer> presentGroupsIds = presentGroups.stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toSet());

        Set<GroupInfoEntry> missingGroups = new HashSet<>();
        expectedGroups.stream()
                .filter(entry -> !presentGroupsIds.contains(entry.getGroupId()))
                .forEach(missingGroups::add);
        if (!missingGroups.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following groups are missed: {}", switchId,
                    missingGroups.stream().map(x -> Integer.toString(x.getGroupId()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }
        Set<GroupInfoEntry> properGroups = new HashSet<>(expectedGroups);
        properGroups.retainAll(presentGroups);

        Set<Integer> expectedGroupsIds = expectedGroups.stream()
                .map(GroupInfoEntry::getGroupId)
                .collect(Collectors.toSet());

        Set<GroupInfoEntry> excessGroups = new HashSet<>();
        presentGroups.stream()
                .filter(entry -> !expectedGroupsIds.contains(entry.getGroupId()))
                .forEach(excessGroups::add);
        if (!excessGroups.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following groups are excessive: {}", switchId,
                    excessGroups.stream().map(x -> Integer.toString(x.getGroupId()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        Set<GroupInfoEntry> misconfiguredGroups = calculateMisconfiguredGroups(expectedGroups, presentGroups);

        return new ValidateGroupsResult(
                ImmutableList.copyOf(missingGroups),
                ImmutableList.copyOf(properGroups),
                ImmutableList.copyOf(excessGroups),
                ImmutableList.copyOf(misconfiguredGroups));
    }

    @Override
    public ValidateLogicalPortsResult validateLogicalPorts(SwitchId switchId, List<LogicalPort> presentLogicalPorts) {
        Map<Integer, LogicalPortInfoEntry> expectedPorts = lagLogicalPortRepository.findBySwitchId(switchId).stream()
                .map(LogicalPortMapper.INSTANCE::map)
                .peek(port -> Collections.sort(port.getPhysicalPorts()))
                .collect(Collectors.toMap(LogicalPortInfoEntry::getLogicalPortNumber, Function.identity()));

        Map<Integer, LogicalPortInfoEntry> actualPorts = presentLogicalPorts.stream()
                .map(LogicalPortMapper.INSTANCE::map)
                .peek(port -> Collections.sort(port.getPhysicalPorts()))
                .collect(Collectors.toMap(LogicalPortInfoEntry::getLogicalPortNumber, Function.identity()));

        List<LogicalPortInfoEntry> properPorts = new ArrayList<>();
        List<LogicalPortInfoEntry> missingPorts = new ArrayList<>();
        List<LogicalPortInfoEntry> excessPorts = new ArrayList<>();
        List<LogicalPortInfoEntry> misconfiguredPorts = new ArrayList<>();

        for (Entry<Integer, LogicalPortInfoEntry> entry : expectedPorts.entrySet()) {
            int portNumber = entry.getKey();
            LogicalPortInfoEntry expected = entry.getValue();

            if (actualPorts.containsKey(portNumber)) {
                LogicalPortInfoEntry actual = actualPorts.get(portNumber);
                if (actual.equals(expected)) {
                    properPorts.add(actual);
                } else {
                    misconfiguredPorts.add(calculateMisconfiguredLogicalPort(expected, actual));
                }
            } else {
                missingPorts.add(expected);
            }
        }

        for (Entry<Integer, LogicalPortInfoEntry> entry : actualPorts.entrySet()) {
            if (LogicalPortType.BFD.equals(entry.getValue().getType())) {
                // At this moment we do not validate BFD ports, so Kilda wouldn't include BFD ports into excess list
                continue;
            }
            if (!expectedPorts.containsKey(entry.getKey())) {
                excessPorts.add(entry.getValue());
            }
        }

        return new ValidateLogicalPortsResult(
                ImmutableList.copyOf(properPorts),
                ImmutableList.copyOf(missingPorts),
                ImmutableList.copyOf(excessPorts),
                ImmutableList.copyOf(misconfiguredPorts));
    }

    private Set<GroupInfoEntry> calculateMisconfiguredGroups(Set<GroupInfoEntry> expected, Set<GroupInfoEntry> actual) {
        Set<GroupInfoEntry> misconfiguredGroups = new HashSet<>();

        Map<Integer, GroupInfoEntry> actualEntries = actual.stream()
                .collect(Collectors.toMap(GroupInfoEntry::getGroupId, entry -> entry));
        for (GroupInfoEntry expectedEntry : expected) {
            GroupInfoEntry actualEntry = actualEntries.get(expectedEntry.getGroupId());

            if (actualEntry == null || actualEntry.equals(expectedEntry)) {
                continue;
            }

            List<BucketEntry> missingData = new ArrayList<>(expectedEntry.getGroupBuckets());
            missingData.removeAll(actualEntry.getGroupBuckets());

            List<BucketEntry> excessData = new ArrayList<>(actualEntry.getGroupBuckets());
            excessData.removeAll(expectedEntry.getGroupBuckets());

            misconfiguredGroups.add(actualEntry.toBuilder()
                    .missingGroupBuckets(missingData)
                    .excessGroupBuckets(excessData)
                    .build());
        }

        return misconfiguredGroups;
    }

    @VisibleForTesting
    LogicalPortInfoEntry calculateMisconfiguredLogicalPort(
            LogicalPortInfoEntry expectedPort, LogicalPortInfoEntry actualPort) {
        LogicalPortMisconfiguredInfoEntry expected = new LogicalPortMisconfiguredInfoEntry();
        LogicalPortMisconfiguredInfoEntry actual = new LogicalPortMisconfiguredInfoEntry();

        if (!Objects.equals(expectedPort.getType(), actualPort.getType())) {
            expected.setType(expectedPort.getType());
            actual.setType(actualPort.getType());
        }

        // compare, ignoring order
        if (!CollectionUtils.isEqualCollection(expectedPort.getPhysicalPorts(), actualPort.getPhysicalPorts())) {
            expected.setPhysicalPorts(expectedPort.getPhysicalPorts());
            actual.setPhysicalPorts(actualPort.getPhysicalPorts());
        }

        return LogicalPortInfoEntry.builder()
                .logicalPortNumber(actualPort.getLogicalPortNumber())
                .type(actualPort.getType())
                .physicalPorts(actualPort.getPhysicalPorts())
                .expected(expected)
                .actual(actual)
                .build();
    }

    private void processRulesValidation(List<FlowSpeakerData> presentRules, List<FlowSpeakerData> expectedRules,
                                        Set<Long> missingRules, Set<Long> properRules, Set<Long> excessRules,
                                        Set<Long> misconfiguredRules) {
        expectedRules.forEach(expectedRule -> {
            List<FlowSpeakerData> actualRule = presentRules.stream()
                    .filter(rule -> rule.getCookie().equals(expectedRule.getCookie()))
                    .collect(toList());

            if (actualRule.isEmpty()) {
                missingRules.add(expectedRule.getCookie().getValue());
            } else {
                if (actualRule.contains(expectedRule)) {
                    properRules.add(expectedRule.getCookie().getValue());
                } else {
                    log.info("Misconfigured rule: {} : expected : {}", actualRule, expectedRule);
                    misconfiguredRules.add(expectedRule.getCookie().getValue());
                }

                if (actualRule.size() > 1) {
                    log.info("Misconfigured rule: {} : expected : {}", actualRule, expectedRule);
                    // todo remove skipping misconfigured flow rules
                    if (expectedRule.getCookie().getServiceFlag()) {
                        misconfiguredRules.add(expectedRule.getCookie().getValue());
                    } else {
                        log.warn("Skipping misconfigured flow rules");
                    }
                }
            }
        });

        presentRules.forEach(presentRule -> {
            List<FlowSpeakerData> expectedRule = expectedRules.stream()
                    .filter(rule -> rule.getCookie().equals(presentRule.getCookie()))
                    .collect(toList());

            if (expectedRule.isEmpty()) {
                excessRules.add(presentRule.getCookie().getValue());
            }
        });
    }

    private static String cookiesIntoLogRepresentation(Collection<Long> rules) {
        return rules.stream().map(Cookie::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String metersIntoLogRepresentation(Collection<MeterInfoEntry> meters) {
        return meters.stream().map(MeterInfoEntry::getMeterId).map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String getMisconfiguredMeterDifferenceAsString(
            MeterMisconfiguredInfoEntry expected, MeterMisconfiguredInfoEntry actual) {
        List<String> difference = new ArrayList<>();
        // All non-null fields in MeterMisconfiguredInfoEntry are misconfigured.
        if (expected.getRate() != null || actual.getRate() != null) {
            difference.add(format("expected rate=%d, actual rate=%d", expected.getRate(), actual.getRate()));
        }
        if (expected.getBurstSize() != null || actual.getBurstSize() != null) {
            difference.add(format("expected burst size=%d, actual burst size=%d",
                    expected.getBurstSize(), actual.getBurstSize()));
        }
        if (expected.getFlags() != null || actual.getFlags() != null) {
            difference.add(format("expected flags=%s, actual flags=%s",
                    Arrays.toString(expected.getFlags()), Arrays.toString(actual.getFlags())));
        }
        return String.join(", ", difference);
    }

    @Override
    public ValidateMetersResult validateMeters(SwitchId switchId, List<MeterSpeakerData> presentMeters,
                                               List<MeterSpeakerData> expectedMeterSpeakerData) {
        log.debug("Validating meters on switch {}", switchId);

        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        boolean isESwitch = Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

        List<MeterInfoEntry> actualMeters = presentMeters.stream()
                .map(meter -> convertMeter(switchId, meter))
                .collect(toList());

        List<MeterInfoEntry> expectedMeters = expectedMeterSpeakerData.stream()
                .map(meter -> convertMeter(switchId, meter))
                .collect(toList());

        ValidateMetersResult result = comparePresentedAndExpectedMeters(isESwitch, actualMeters, expectedMeters);

        if (!result.getMissingMeters().isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following meters are missed: {}", switchId,
                    metersIntoLogRepresentation(result.getMissingMeters()));
        }

        if (!result.getExcessMeters().isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following meters are excessive: {}", switchId,
                    metersIntoLogRepresentation(result.getExcessMeters()));
        }

        if (!result.getMisconfiguredMeters().isEmpty() && log.isWarnEnabled()) {
            for (MeterInfoEntry meter : result.getMisconfiguredMeters()) {
                log.warn("On switch {} meter {} is misconfigured: {}", switchId, meter.getMeterId(),
                        getMisconfiguredMeterDifferenceAsString(meter.getExpected(), meter.getActual()));
            }
        }
        return result;
    }

    private MeterInfoEntry convertMeter(SwitchId switchId, MeterSpeakerData meterSpeakerData) {
        MeterInfoEntry meterInfoEntry = MeterEntryConverter.INSTANCE.toMeterEntry(meterSpeakerData);
        Optional<FlowMeter> flowMeter = flowMeterRepository.findById(switchId, meterSpeakerData.getMeterId());
        if (flowMeter.isPresent()) {
            meterInfoEntry.setFlowId(flowMeter.get().getFlowId());
            Optional<FlowPath> flowPath = flowPathRepository.findById(flowMeter.get().getPathId());
            if (flowPath.isPresent()) {
                meterInfoEntry.setCookie(flowPath.get().getCookie().getValue());
            }
        }
        return meterInfoEntry;
    }

    private ValidateMetersResult comparePresentedAndExpectedMeters(
            boolean isESwitch, List<MeterInfoEntry> presentMeters, List<MeterInfoEntry> expectedMeters) {
        Map<Long, MeterInfoEntry> presentMeterMap = presentMeters.stream()
                .collect(Collectors.toMap(MeterInfoEntry::getMeterId, Function.identity()));

        List<MeterInfoEntry> missingMeters = new ArrayList<>();
        List<MeterInfoEntry> misconfiguredMeters = new ArrayList<>();
        List<MeterInfoEntry> properMeters = new ArrayList<>();

        for (MeterInfoEntry expectedMeter : expectedMeters) {
            MeterInfoEntry presentedMeter = presentMeterMap.get(expectedMeter.getMeterId());

            if (presentedMeter == null) {
                missingMeters.add(expectedMeter);
                continue;
            }

            if (Meter.equalsRate(presentedMeter.getRate(), expectedMeter.getRate(), isESwitch)
                    && Meter.equalsBurstSize(presentedMeter.getBurstSize(), expectedMeter.getBurstSize(), isESwitch)
                    && flagsAreEqual(presentedMeter.getFlags(), expectedMeter.getFlags())) {

                properMeters.add(presentedMeter);
            } else {
                misconfiguredMeters.add(makeMisconfiguredMeterEntry(presentedMeter, expectedMeter, isESwitch));
            }
        }

        List<MeterInfoEntry> excessMeters = getExcessMeters(presentMeters, expectedMeters);
        excessMeters.sort(Comparator.comparing(MeterInfoEntry::getMeterId));
        missingMeters.sort(Comparator.comparing(MeterInfoEntry::getMeterId));
        misconfiguredMeters.sort(Comparator.comparing(MeterInfoEntry::getMeterId));
        properMeters.sort(Comparator.comparing(MeterInfoEntry::getMeterId));
        return new ValidateMetersResult(missingMeters, misconfiguredMeters, properMeters, excessMeters);
    }

    private boolean flagsAreEqual(String[] present, String[] expected) {
        Set<String> left = Sets.newHashSet(present);
        Set<String> right = Sets.newHashSet(expected);

        return left.size() == right.size() && left.containsAll(right);
    }

    private List<MeterInfoEntry> getExcessMeters(List<MeterInfoEntry> presented, List<MeterInfoEntry> expected) {
        List<MeterInfoEntry> excessMeters = new ArrayList<>();

        Set<Long> expectedMeterIds = expected.stream()
                .map(MeterInfoEntry::getMeterId)
                .collect(Collectors.toSet());

        for (MeterInfoEntry meterEntry : presented) {
            if (!expectedMeterIds.contains(meterEntry.getMeterId())) {
                excessMeters.add(meterEntry);
            }
        }
        return excessMeters;
    }

    private MeterInfoEntry makeMisconfiguredMeterEntry(MeterInfoEntry actualMeter, MeterInfoEntry expectedMeter,
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
        if (!Sets.newHashSet(actualMeter.getFlags()).equals(Sets.newHashSet(expectedMeter.getFlags()))) {
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
