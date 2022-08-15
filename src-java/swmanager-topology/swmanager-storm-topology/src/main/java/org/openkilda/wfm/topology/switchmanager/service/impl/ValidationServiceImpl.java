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

import org.openkilda.messaging.info.switches.LogicalPortType;
import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2.BucketEntry;
import org.openkilda.messaging.info.switches.v2.LogicalPortInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MeterInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.FieldMatch;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.model.FlowMeter;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.Meter;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
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
import org.openkilda.wfm.topology.switchmanager.mappers.RuleEntryConverter;
import org.openkilda.wfm.topology.switchmanager.model.v2.RuleKey;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateGroupsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateLogicalPortsResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateMetersResultV2;
import org.openkilda.wfm.topology.switchmanager.model.v2.ValidateRulesResultV2;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
                .filter(fp -> fp.getStatus() != FlowPathStatus.IN_PROGRESS)
                .map(FlowPath::getPathId)
                .collect(Collectors.toSet());
        flowPathIds.addAll(flowPathRepository.findByEndpointSwitch(switchId).stream()
                .filter(fp -> fp.getStatus() != FlowPathStatus.IN_PROGRESS)
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
    public ValidateRulesResultV2 validateRules(SwitchId switchId, List<FlowSpeakerData> presentRules,
                                               List<FlowSpeakerData> expectedRules) {
        log.debug("Validating rules on switch {}", switchId);

        Set<RuleInfoEntryV2> missingRules = new HashSet<>();
        Set<RuleInfoEntryV2> properRules = new HashSet<>();
        Set<RuleInfoEntryV2> excessRules = new HashSet<>();
        Set<MisconfiguredInfo<RuleInfoEntryV2>> misconfiguredRules = new HashSet<>();

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

        return new ValidateRulesResultV2(
                missingRules.isEmpty() && misconfiguredRules.isEmpty() && excessRules.isEmpty(),
                ImmutableSet.copyOf(missingRules),
                ImmutableSet.copyOf(properRules),
                ImmutableSet.copyOf(excessRules),
                ImmutableSet.copyOf(misconfiguredRules));
    }

    @Override
    public ValidateGroupsResultV2 validateGroups(SwitchId switchId, List<GroupSpeakerData> groupEntries,
                                                 List<GroupSpeakerData> expectedGroupSpeakerData) {

        Set<GroupInfoEntryV2> expectedGroups = convertGroups(expectedGroupSpeakerData);
        Set<GroupInfoEntryV2> presentGroups = convertGroups(groupEntries);

        Set<GroupInfoEntryV2> missingGroups = new HashSet<>();
        Set<GroupInfoEntryV2> properGroups = new HashSet<>(expectedGroups);
        Set<GroupInfoEntryV2> excessGroups = new HashSet<>();
        Set<MisconfiguredInfo<GroupInfoEntryV2>> misconfiguredGroups = new HashSet<>();

        processGroupsValidation(presentGroups, expectedGroups, missingGroups, properGroups, excessGroups,
                misconfiguredGroups);

        if (!excessGroups.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following groups are excessive: {}", switchId,
                    excessGroups.stream().map(x -> Integer.toString(x.getGroupId()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        if (!missingGroups.isEmpty() && log.isErrorEnabled()) {
            log.warn("On switch {} the following groups are missed: {}", switchId,
                    missingGroups.stream().map(x -> Integer.toString(x.getGroupId()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        return new ValidateGroupsResultV2(
                missingGroups.isEmpty() && excessGroups.isEmpty() && misconfiguredGroups.isEmpty(),
                ImmutableList.copyOf(missingGroups),
                ImmutableList.copyOf(properGroups),
                ImmutableList.copyOf(excessGroups),
                ImmutableList.copyOf(misconfiguredGroups));
    }

    @Override
    public ValidateLogicalPortsResultV2 validateLogicalPorts(SwitchId switchId, List<LogicalPort> presentLogicalPorts) {
        Map<Integer, LogicalPortInfoEntryV2> expectedPorts = lagLogicalPortRepository.findBySwitchId(switchId).stream()
                .map(LogicalPortMapper.INSTANCE::map)
                .peek(port -> Collections.sort(port.getPhysicalPorts()))
                .collect(Collectors.toMap(LogicalPortInfoEntryV2::getLogicalPortNumber, Function.identity()));

        Map<Integer, LogicalPortInfoEntryV2> actualPorts = presentLogicalPorts.stream()
                .map(LogicalPortMapper.INSTANCE::map)
                .peek(port -> Collections.sort(port.getPhysicalPorts()))
                .collect(Collectors.toMap(LogicalPortInfoEntryV2::getLogicalPortNumber, Function.identity()));

        List<LogicalPortInfoEntryV2> properPorts = new ArrayList<>();
        List<LogicalPortInfoEntryV2> missingPorts = new ArrayList<>();
        List<LogicalPortInfoEntryV2> excessPorts = new ArrayList<>();
        List<MisconfiguredInfo<LogicalPortInfoEntryV2>> misconfiguredPorts = new ArrayList<>();

        processLogicalPortValidation(actualPorts, expectedPorts, missingPorts, properPorts, excessPorts,
                misconfiguredPorts);

        //TODO: add logging

        return new ValidateLogicalPortsResultV2(
                missingPorts.isEmpty() && excessPorts.isEmpty() && misconfiguredPorts.isEmpty(),
                ImmutableList.copyOf(missingPorts),
                ImmutableList.copyOf(properPorts),
                ImmutableList.copyOf(excessPorts),
                ImmutableList.copyOf(misconfiguredPorts),
                "");
    }

    @Override
    public ValidateMetersResultV2 validateMeters(SwitchId switchId, List<MeterSpeakerData> presentMeters,
                                                 List<MeterSpeakerData> expectedMeterSpeakerData) {
        log.debug("Validating meters on a switch {}", switchId);

        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        boolean isESwitch = Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

        List<MeterInfoEntryV2> actualMeters = convertMeters(switchId, presentMeters);
        List<MeterInfoEntryV2> expectedMeters = convertMeters(switchId, expectedMeterSpeakerData);

        List<MeterInfoEntryV2> missingMeters = new ArrayList<>();
        List<MeterInfoEntryV2> properMeters = new ArrayList<>();
        List<MeterInfoEntryV2> excessMeters = new ArrayList<>();
        List<MisconfiguredInfo<MeterInfoEntryV2>> misconfiguredMeters = new ArrayList<>();

        processMeterValidation(isESwitch, actualMeters, expectedMeters, missingMeters, properMeters, excessMeters,
                misconfiguredMeters);

        if (!missingMeters.isEmpty() && log.isErrorEnabled()) {
            log.warn("On switch {} the following meters are missed: {}", switchId,
                    metersIntoLogRepresentation(missingMeters));
        }

        if (!excessMeters.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following meters are excessive: {}", switchId,
                    metersIntoLogRepresentation(excessMeters));
        }

        if (!misconfiguredMeters.isEmpty() && log.isWarnEnabled()) {
            for (MisconfiguredInfo<MeterInfoEntryV2> meter : misconfiguredMeters) {
                log.warn("On switch {} meter {} is misconfigured: {}", switchId, meter.getId(),
                        getMisconfiguredMeterDifferenceAsString(meter.getExpected(), meter.getDiscrepancies()));
            }
        }

        return new ValidateMetersResultV2(
                missingMeters.isEmpty() && misconfiguredMeters.isEmpty() && excessMeters.isEmpty(),
                missingMeters, properMeters, excessMeters, misconfiguredMeters);
    }

    private void processGroupsValidation(Set<GroupInfoEntryV2> presentGroups, Set<GroupInfoEntryV2> expectedGroups,
                                         Set<GroupInfoEntryV2> missingGroups, Set<GroupInfoEntryV2> properGroups,
                                         Set<GroupInfoEntryV2> excessGroups,
                                         Set<MisconfiguredInfo<GroupInfoEntryV2>> misconfiguredGroups) {
        Set<Integer> presentGroupsIds = presentGroups.stream()
                .map(GroupInfoEntryV2::getGroupId)
                .collect(Collectors.toSet());

        expectedGroups.stream()
                .filter(entry -> !presentGroupsIds.contains(entry.getGroupId()))
                .forEach(missingGroups::add);

        properGroups.retainAll(presentGroups);

        //TODO: add flow_id and flow_path for proper groups

        Set<Integer> expectedGroupsIds = expectedGroups.stream()
                .map(GroupInfoEntryV2::getGroupId)
                .collect(Collectors.toSet());

        presentGroups.stream()
                .filter(entry -> !expectedGroupsIds.contains(entry.getGroupId()))
                .forEach(excessGroups::add);

        misconfiguredGroups.addAll(calculateMisconfiguredGroups(expectedGroups, presentGroups));
    }

    private void processRulesValidation(List<FlowSpeakerData> presentRules, List<FlowSpeakerData> existedRules,
                                        Set<RuleInfoEntryV2> missingRules, Set<RuleInfoEntryV2> properRules,
                                        Set<RuleInfoEntryV2> excessRules,
                                        Set<MisconfiguredInfo<RuleInfoEntryV2>> misconfiguredRules) {

        Map<RuleKey, RuleInfoEntryV2> expectedRules = convertRules(existedRules);
        Map<RuleKey, RuleInfoEntryV2> actualRules = convertRules(presentRules);

        expectedRules.keySet().forEach(expectedRuleKey -> {
            RuleInfoEntryV2 expectedRuleValue = expectedRules.get(expectedRuleKey);
            RuleInfoEntryV2 actualRuleValue = actualRules.get(expectedRuleKey);

            if (actualRuleValue == null) {
                missingRules.add(expectedRuleValue);
            } else {
                if (expectedRuleValue.equals(actualRuleValue)) {
                    properRules.add(expectedRuleValue);
                } else {
                    log.info("Misconfigured rule: {} : expected : {}", actualRuleValue, expectedRuleValue);
                    misconfiguredRules.add(calculateMisconfiguredRule(expectedRuleValue, actualRuleValue));
                }
            }
        });

        actualRules.keySet().forEach(actualRuleKey -> {
            if (!expectedRules.containsKey(actualRuleKey)) {
                excessRules.add(actualRules.get(actualRuleKey));
            }
        });
    }

    private void processMeterValidation(boolean isESwitch, List<MeterInfoEntryV2> presentMeters,
                                        List<MeterInfoEntryV2> expectedMeters, List<MeterInfoEntryV2> missingMeters,
                                        List<MeterInfoEntryV2> properMeters, List<MeterInfoEntryV2> excessMeters,
                                        List<MisconfiguredInfo<MeterInfoEntryV2>> misconfiguredMeters) {

        Map<Long, MeterInfoEntryV2> presentMeterMap = presentMeters.stream()
                .collect(Collectors.toMap(MeterInfoEntryV2::getMeterId, Function.identity()));

        for (MeterInfoEntryV2 expectedMeter : expectedMeters) {
            MeterInfoEntryV2 presentedMeter = presentMeterMap.get(expectedMeter.getMeterId());

            if (presentedMeter == null) {
                missingMeters.add(expectedMeter);
                continue;
            }
            if (Meter.equalsRate(presentedMeter.getRate(), expectedMeter.getRate(), isESwitch)
                    && Meter.equalsBurstSize(presentedMeter.getBurstSize(), expectedMeter.getBurstSize(), isESwitch)
                    && flagsAreEqual(presentedMeter.getFlags(), expectedMeter.getFlags())) {

                properMeters.add(presentedMeter);
            } else {
                misconfiguredMeters.add(calculateMisconfiguredMeter(isESwitch, presentedMeter, expectedMeter));
            }
        }

        Set<Long> expectedMeterIds = expectedMeters.stream()
                .map(MeterInfoEntryV2::getMeterId)
                .collect(Collectors.toSet());

        for (MeterInfoEntryV2 meterEntry : presentMeters) {
            if (!expectedMeterIds.contains(meterEntry.getMeterId())) {
                excessMeters.add(meterEntry);
            }
        }

        excessMeters.sort(Comparator.comparing(MeterInfoEntryV2::getMeterId));
        missingMeters.sort(Comparator.comparing(MeterInfoEntryV2::getMeterId));
        misconfiguredMeters.sort(Comparator.comparing(MisconfiguredInfo::getId));
        properMeters.sort(Comparator.comparing(MeterInfoEntryV2::getMeterId));
    }

    private void processLogicalPortValidation(Map<Integer, LogicalPortInfoEntryV2> actualPorts,
                                              Map<Integer, LogicalPortInfoEntryV2> expectedPorts,
                                              List<LogicalPortInfoEntryV2> missingPorts,
                                              List<LogicalPortInfoEntryV2> properPorts,
                                              List<LogicalPortInfoEntryV2> excessPorts,
                                              List<MisconfiguredInfo<LogicalPortInfoEntryV2>> misconfiguredPorts) {

        for (Entry<Integer, LogicalPortInfoEntryV2> entry : expectedPorts.entrySet()) {
            //TODO: fill out flow_id, flow_path
            int portNumber = entry.getKey();
            LogicalPortInfoEntryV2 expected = entry.getValue();

            if (actualPorts.containsKey(portNumber)) {
                LogicalPortInfoEntryV2 actual = actualPorts.get(portNumber);
                if (actual.equals(expected)) {
                    properPorts.add(actual);
                } else {
                    misconfiguredPorts.add(calculateMisconfiguredLogicalPort(expected, actual));
                }
            } else {
                missingPorts.add(expected);
            }
        }

        for (Entry<Integer, LogicalPortInfoEntryV2> entry : actualPorts.entrySet()) {
            if (LogicalPortType.BFD.equals(entry.getValue().getType())) {
                // At this moment we do not validate BFD ports, so Kilda wouldn't include BFD ports into excess list
                continue;
            }
            if (!expectedPorts.containsKey(entry.getKey())) {
                excessPorts.add(entry.getValue());
            }
        }
    }

    private Set<GroupInfoEntryV2> convertGroups(List<GroupSpeakerData> groupEntries) {
        return groupEntries.stream()
                .map(GroupEntryConverter.INSTANCE::toGroupEntry)
                .collect(Collectors.toSet());
    }

    private List<MeterInfoEntryV2> convertMeters(SwitchId switchId, List<MeterSpeakerData> meterSpeakerData) {
        List<MeterInfoEntryV2> meters = new ArrayList<>();

        for (MeterSpeakerData meterData : meterSpeakerData) {
            MeterInfoEntryV2 meterInfoEntry = MeterEntryConverter.INSTANCE.toMeterEntry(meterData);
            Optional<FlowMeter> flowMeter = flowMeterRepository.findById(switchId, meterData.getMeterId());

            if (flowMeter.isPresent()) {
                meterInfoEntry.setFlowId(flowMeter.get().getFlowId());
                Optional<FlowPath> flowPath = flowPathRepository.findById(flowMeter.get().getPathId());
                flowPath.ifPresent(path -> meterInfoEntry.setCookie(path.getCookie().getValue()));

                meterInfoEntry.setYFlowId("TBD");
                //TODO: set yFlowId from repo
            }
            meters.add(meterInfoEntry);
        }
        return meters;
    }

    private Map<RuleKey, RuleInfoEntryV2> convertRules(List<FlowSpeakerData> flowSpeakerData) {
        Map<RuleKey, RuleInfoEntryV2> rules = new HashMap<>();

        for (FlowSpeakerData rule : flowSpeakerData) {
            RuleInfoEntryV2 ruleInfo = RuleEntryConverter.INSTANCE.toRuleEntry(rule);
            RuleKey ruleKey = RuleKey.builder()
                    .priority(ruleInfo.getPriority())
                    .match(ruleInfo.getMatch())
                    .tableId(ruleInfo.getTableId())
                    .build();
            rules.put(ruleKey, ruleInfo);
        }
        return rules;
    }

    private void convertLogicalPorts() {
        //TODO: do we have the ability to generalize it for logical port?
    }

    private Set<MisconfiguredInfo<GroupInfoEntryV2>> calculateMisconfiguredGroups(Set<GroupInfoEntryV2> expected,
                                                                                  Set<GroupInfoEntryV2> actual) {
        Set<MisconfiguredInfo<GroupInfoEntryV2>> misconfiguredGroups = new HashSet<>();

        Map<Integer, GroupInfoEntryV2> actualEntries = actual.stream()
                .collect(Collectors.toMap(GroupInfoEntryV2::getGroupId, entry -> entry));
        for (GroupInfoEntryV2 expectedEntry : expected) {
            GroupInfoEntryV2 actualEntry = actualEntries.get(expectedEntry.getGroupId());

            if (actualEntry == null || actualEntry.equals(expectedEntry)) {
                continue;
            }

            List<BucketEntry> missingBuckets = new ArrayList<>(expectedEntry.getBuckets());
            missingBuckets.removeAll(actualEntry.getBuckets());

            List<BucketEntry> excessBuckets = new ArrayList<>(actualEntry.getBuckets());
            missingBuckets.removeAll(expectedEntry.getBuckets());

            GroupInfoEntryV2 discrepancies = GroupInfoEntryV2.builder()
                    .buckets(Stream.concat(missingBuckets.stream(), excessBuckets.stream())
                            .collect(Collectors.toList()))
                    .build();
            //TODO: add flow id,flow path for expected entry

            misconfiguredGroups.add(MisconfiguredInfo.<GroupInfoEntryV2>builder()
                    .id(expectedEntry.getGroupId().toString())
                    .expected(expectedEntry)
                    .discrepancies(discrepancies)
                    .build());
        }

        return misconfiguredGroups;
    }

    @VisibleForTesting
    MisconfiguredInfo<LogicalPortInfoEntryV2> calculateMisconfiguredLogicalPort(LogicalPortInfoEntryV2 expectedPort,
                                                                                LogicalPortInfoEntryV2 actualPort) {

        LogicalPortInfoEntryV2.LogicalPortInfoEntryV2Builder discrepancies = LogicalPortInfoEntryV2.builder();

        if (!Objects.equals(expectedPort.getType(), actualPort.getType())) {
            discrepancies.type(actualPort.getType());
        }

        // compare, ignoring order
        if (!CollectionUtils.isEqualCollection(expectedPort.getPhysicalPorts(), actualPort.getPhysicalPorts())) {
            discrepancies.physicalPorts(actualPort.getPhysicalPorts());
        }
        discrepancies.build();

        return MisconfiguredInfo.<LogicalPortInfoEntryV2>builder()
                .id(expectedPort.getLogicalPortNumber().toString())
                .expected(expectedPort)
                .discrepancies(discrepancies.build())
                .build();
    }

    private MisconfiguredInfo<MeterInfoEntryV2> calculateMisconfiguredMeter(boolean isESwitch,
                                                                            MeterInfoEntryV2 actualMeter,
                                                                            MeterInfoEntryV2 expectedMeter) {
        MeterInfoEntryV2.MeterInfoEntryV2Builder discrepancies = MeterInfoEntryV2.builder();

        if (!Meter.equalsRate(actualMeter.getRate(), expectedMeter.getRate(), isESwitch)) {
            discrepancies.rate(actualMeter.getRate());
        }
        if (!Meter.equalsBurstSize(actualMeter.getBurstSize(), expectedMeter.getBurstSize(), isESwitch)) {
            discrepancies.burstSize(actualMeter.getBurstSize());
        }
        if (!Sets.newHashSet(actualMeter.getFlags()).equals(Sets.newHashSet(expectedMeter.getFlags()))) {
            discrepancies.flags(actualMeter.getFlags());
        }

        return MisconfiguredInfo.<MeterInfoEntryV2>builder()
                .id(expectedMeter.getMeterId().toString())
                .expected(expectedMeter)
                .discrepancies(discrepancies.build())
                .build();
    }

    private MisconfiguredInfo<RuleInfoEntryV2> calculateMisconfiguredRule(RuleInfoEntryV2 expected,
                                                                          RuleInfoEntryV2 actual) {
        RuleInfoEntryV2.RuleInfoEntryV2Builder discrepancies = RuleInfoEntryV2.builder();

        if (!expected.getCookie().equals(actual.getCookie())) {
            discrepancies.cookie(actual.getCookie());
        }
        if (!expected.getCookieHex().equals(actual.getCookieHex())) {
            discrepancies.cookieHex(actual.getCookieHex());
        }
        if (!expected.getCookieKind().equals(actual.getCookieKind())) {
            discrepancies.cookieKind(actual.getCookieKind());
        }
        if (!expected.getPriority().equals(actual.getPriority())) {
            discrepancies.priority(actual.getPriority());
        }
        if (!expected.getTableId().equals(actual.getTableId())) {
            discrepancies.tableId(actual.getTableId());
        }
        if (!expected.getMatch().equals(actual.getMatch())) {
            discrepancies.match(actual.getMatch());
        }
        if (!flagsAreEqual(expected.getFlags(), actual.getFlags())) {
            discrepancies.flags(actual.getFlags());
        }
        if (!expected.getInstructions().equals(actual.getInstructions())) {
            discrepancies.instructions(actual.getInstructions());
        }
        StringBuilder id = new StringBuilder(format("tableId=%s,priority=%s,", expected.getTableId(),
                expected.getPriority()));

        for (String match : expected.getMatch().keySet()) {
            FieldMatch value = expected.getMatch().get(match);
            id.append(format("%s:value=%s,mask=%s,isMasked=%s,", match, value.getValue(), value.getMask(),
                    value.isMasked()));
        }

        return MisconfiguredInfo.<RuleInfoEntryV2>builder()
                .id(id.toString()) // tableId + priority + match
                .expected(expected)
                .discrepancies(discrepancies.build())
                .build();
    }

    private static String cookiesIntoLogRepresentation(Collection<RuleInfoEntryV2> rules) {
        return rules.stream().map(r -> r.getCookie().toString()).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String metersIntoLogRepresentation(Collection<MeterInfoEntryV2> meters) {
        return meters.stream().map(MeterInfoEntryV2::getMeterId).map(String::valueOf)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String getMisconfiguredMeterDifferenceAsString(
            MeterInfoEntryV2 expected, MeterInfoEntryV2 actual) {
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
                    expected.getFlags(), actual.getFlags()));
        }
        return String.join(", ", difference);
    }

    private boolean flagsAreEqual(List<String> present, List<String> expected) {
        Set<String> left = Sets.newHashSet(present);
        Set<String> right = Sets.newHashSet(expected);

        return left.size() == right.size() && left.containsAll(right);
    }

    private String getFlowId() {
        return "";
    }

    private String getFlowPath() {
        return "";
    }

    private String getYFlowId() {
        return "";
    }
}
