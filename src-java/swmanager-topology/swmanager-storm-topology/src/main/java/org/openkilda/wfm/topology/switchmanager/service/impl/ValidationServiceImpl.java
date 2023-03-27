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
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVER_42_FLOW_RTT_INGRESS;
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT;

import org.openkilda.messaging.info.switches.LogicalPortType;
import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.GroupInfoEntryV2.GroupInfoEntryV2Builder;
import org.openkilda.messaging.info.switches.v2.LogicalPortInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MeterInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.MisconfiguredInfo;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2;
import org.openkilda.messaging.info.switches.v2.RuleInfoEntryV2.FieldMatch;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMeter;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;
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
    private final FlowRepository flowRepository;
    private final YFlowRepository yFlowRepository;

    private final MirrorGroupRepository mirrorGroupRepository;
    private final RuleManager ruleManager;

    public ValidationServiceImpl(PersistenceManager persistenceManager, RuleManager ruleManager) {
        this.persistenceManager = persistenceManager;
        this.ruleManager = ruleManager;

        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        this.flowMeterRepository = repositoryFactory.createFlowMeterRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.yFlowRepository = repositoryFactory.createYFlowRepository();
        this.mirrorGroupRepository = repositoryFactory.createMirrorGroupRepository();
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
                                               List<FlowSpeakerData> expectedRules, boolean includeFlowInfo) {
        log.debug("Validating rules on switch {}", switchId);

        Set<RuleInfoEntryV2> missingRules = new HashSet<>();
        Set<RuleInfoEntryV2> properRules = new HashSet<>();
        Set<RuleInfoEntryV2> excessRules = new HashSet<>();
        Set<MisconfiguredInfo<RuleInfoEntryV2>> misconfiguredRules = new HashSet<>();

        Map<RuleKey, RuleInfoEntryV2> expectedRulesMap = convertRules(expectedRules);
        Map<RuleKey, RuleInfoEntryV2> actualRulesMap = convertRules(presentRules);

        processRulesValidation(switchId, missingRules, properRules, excessRules, misconfiguredRules, expectedRulesMap,
                actualRulesMap, includeFlowInfo);

        if (!missingRules.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following rules are missed: {}", switchId,
                    cookiesIntoLogRepresentation(missingRules));
        }

        if (!excessRules.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following rules are excessive: {}", switchId,
                    cookiesIntoLogRepresentation(excessRules));
        }

        return new ValidateRulesResultV2(
                missingRules.isEmpty() && misconfiguredRules.isEmpty() && excessRules.isEmpty(),
                ImmutableList.copyOf(missingRules),
                ImmutableList.copyOf(properRules),
                ImmutableList.copyOf(excessRules),
                ImmutableList.copyOf(misconfiguredRules));
    }

    @Override
    public ValidateGroupsResultV2 validateGroups(SwitchId switchId, List<GroupSpeakerData> groupEntries,
                                                 List<GroupSpeakerData> expectedGroupSpeakerData,
                                                 boolean includeFlowInfo) {
        log.debug("Validating groups on a switch {}", switchId);

        List<GroupInfoEntryV2> expectedGroups = convertGroups(expectedGroupSpeakerData);
        List<GroupInfoEntryV2> presentGroups = convertGroups(groupEntries);

        List<GroupInfoEntryV2> missingGroups = new ArrayList<>();
        List<GroupInfoEntryV2> properGroups = new ArrayList<>();
        List<GroupInfoEntryV2> excessGroups = new ArrayList<>();
        List<MisconfiguredInfo<GroupInfoEntryV2>> misconfiguredGroups = new ArrayList<>();

        processGroupsValidation(switchId, presentGroups, expectedGroups, missingGroups, properGroups, excessGroups,
                misconfiguredGroups, includeFlowInfo);

        if (!excessGroups.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following groups are excessive: {}", switchId,
                    excessGroups.stream().map(x -> Integer.toString(x.getGroupId()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        if (!missingGroups.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following groups are missed: {}", switchId,
                    missingGroups.stream().map(x -> Integer.toString(x.getGroupId()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        if (!misconfiguredGroups.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following groups are misconfigured: {}", switchId,
                    misconfiguredGroups.stream().map(MisconfiguredInfo::getId)
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
        log.debug("Validating logical ports on a switch {}", switchId);

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

        if (!excessPorts.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following logical ports are excessive: {}", switchId,
                    excessPorts.stream().map(x -> Integer.toString(x.getLogicalPortNumber()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        if (!missingPorts.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following logical ports are missed: {}", switchId,
                    missingPorts.stream().map(x -> Integer.toString(x.getLogicalPortNumber()))
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        if (!misconfiguredPorts.isEmpty() && log.isWarnEnabled()) {
            log.warn("On switch {} the following logical ports are misconfigured: {}", switchId,
                    misconfiguredPorts.stream().map(MisconfiguredInfo::getId)
                            .collect(Collectors.joining(", ", "[", "]")));
        }

        return new ValidateLogicalPortsResultV2(
                missingPorts.isEmpty() && excessPorts.isEmpty() && misconfiguredPorts.isEmpty(),
                ImmutableList.copyOf(missingPorts),
                ImmutableList.copyOf(properPorts),
                ImmutableList.copyOf(excessPorts),
                ImmutableList.copyOf(misconfiguredPorts),
                "");
    }

    @Override
    public ValidateMetersResultV2 validateMeters(
            SwitchId switchId, List<MeterSpeakerData> presentMeters, List<MeterSpeakerData> expectedMeterSpeakerData,
            boolean includeAllFlowInfo, boolean includeMeterFlowInfo) {
        log.debug("Validating meters on a switch {}", switchId);

        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        boolean isESwitch = Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

        List<MeterInfoEntryV2> actualMeters = convertMeters(presentMeters);
        List<MeterInfoEntryV2> expectedMeters = convertMeters(expectedMeterSpeakerData);

        List<MeterInfoEntryV2> missingMeters = new ArrayList<>();
        List<MeterInfoEntryV2> properMeters = new ArrayList<>();
        List<MeterInfoEntryV2> excessMeters = new ArrayList<>();
        List<MisconfiguredInfo<MeterInfoEntryV2>> misconfiguredMeters = new ArrayList<>();

        processMeterValidation(switchId, isESwitch, actualMeters, expectedMeters, missingMeters, properMeters,
                excessMeters, misconfiguredMeters, includeAllFlowInfo, includeMeterFlowInfo);

        if (!missingMeters.isEmpty() && log.isErrorEnabled()) {
            log.error("On switch {} the following meters are missed: {}", switchId,
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

    private void processGroupsValidation(SwitchId switchId, List<GroupInfoEntryV2> presentGroups,
                                         List<GroupInfoEntryV2> expectedGroups, List<GroupInfoEntryV2> missingGroups,
                                         List<GroupInfoEntryV2> properGroups, List<GroupInfoEntryV2> excessGroups,
                                         List<MisconfiguredInfo<GroupInfoEntryV2>> misconfiguredGroups,
                                         boolean includeFlowInfo) {
        Map<Integer, GroupInfoEntryV2> presentGroupMap = presentGroups.stream()
                .collect(Collectors.toMap(GroupInfoEntryV2::getGroupId, Function.identity()));

        for (GroupInfoEntryV2 expectedGroup : expectedGroups) {
            GroupInfoEntryV2 presentedGroup = presentGroupMap.get(expectedGroup.getGroupId());

            if (presentedGroup == null) {
                missingGroups.add(expectedGroup);
                continue;
            }

            if (CollectionUtils.isEqualCollection(expectedGroup.getBuckets(), presentedGroup.getBuckets())) {
                properGroups.add(presentedGroup);
            } else {
                misconfiguredGroups.add(calculateMisconfiguredGroup(expectedGroup, presentedGroup));
            }
        }

        Set<Integer> expectedGroupIds = expectedGroups.stream()
                .map(GroupInfoEntryV2::getGroupId)
                .collect(Collectors.toSet());

        for (GroupInfoEntryV2 groupEntry : presentGroups) {
            if (!expectedGroupIds.contains(groupEntry.getGroupId())) {
                excessGroups.add(groupEntry);
            }
        }

        excessGroups.sort(Comparator.comparing(GroupInfoEntryV2::getGroupId));
        missingGroups.sort(Comparator.comparing(GroupInfoEntryV2::getGroupId));
        misconfiguredGroups.sort(Comparator.comparing(MisconfiguredInfo::getId));
        properGroups.sort(Comparator.comparing(GroupInfoEntryV2::getGroupId));

        if (includeFlowInfo) {
            concatStreams(missingGroups.stream(), properGroups.stream(),
                    misconfiguredGroups.stream().map(MisconfiguredInfo::getExpected))
                    .forEach(group -> addGroupFlowInfo(switchId, group));
        }
    }

    private void processRulesValidation(SwitchId switchId, Set<RuleInfoEntryV2> missingRules,
                                        Set<RuleInfoEntryV2> properRules, Set<RuleInfoEntryV2> excessRules,
                                        Set<MisconfiguredInfo<RuleInfoEntryV2>> misconfiguredRules,
                                        Map<RuleKey, RuleInfoEntryV2> expectedRules,
                                        Map<RuleKey, RuleInfoEntryV2> actualRules,
                                        boolean includeFlowInfo) {

        expectedRules.keySet().forEach(expectedRuleKey -> {

            RuleInfoEntryV2 expectedRuleValue = expectedRules.get(expectedRuleKey);
            RuleInfoEntryV2 actualRuleValue = actualRules.get(expectedRuleKey);

            if (actualRuleValue == null) {
                missingRules.add(expectedRuleValue);
            } else {
                if (expectedRuleValue.equals(actualRuleValue)) {
                    properRules.add(expectedRuleValue);
                } else {
                    log.info("On switch {} rule {} is misconfigured. Actual: {} : expected : {}",
                            switchId, actualRuleValue.getCookie(), actualRuleValue, expectedRuleValue);
                    misconfiguredRules.add(calculateMisconfiguredRule(expectedRuleValue, actualRuleValue));
                }
            }
        });

        actualRules.keySet().forEach(actualRuleKey -> {
            if (!expectedRules.containsKey(actualRuleKey)) {
                excessRules.add(actualRules.get(actualRuleKey));
            }
        });

        if (includeFlowInfo) {
            concatStreams(missingRules.stream(), properRules.stream(),
                    misconfiguredRules.stream().map(MisconfiguredInfo::getExpected))
                    .forEach(this::addFlowInfo);
        }
    }

    private void processMeterValidation(
            SwitchId switchId, boolean isESwitch, List<MeterInfoEntryV2> presentMeters,
            List<MeterInfoEntryV2> expectedMeters, List<MeterInfoEntryV2> missingMeters,
            List<MeterInfoEntryV2> properMeters, List<MeterInfoEntryV2> excessMeters,
            List<MisconfiguredInfo<MeterInfoEntryV2>> misconfiguredMeters, boolean includeAllFlowInfo,
            boolean includeMeterFlowInfo) {

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
                misconfiguredMeters.add(calculateMisconfiguredMeter(isESwitch, expectedMeter, presentedMeter));
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

        if (includeAllFlowInfo || includeMeterFlowInfo) {
            concatStreams(missingMeters.stream(), properMeters.stream(),
                    misconfiguredMeters.stream().map(MisconfiguredInfo::getExpected))
                    .forEach(meter -> addMeterFlowInfo(switchId, meter, includeAllFlowInfo));
        }
    }

    private void processLogicalPortValidation(Map<Integer, LogicalPortInfoEntryV2> actualPorts,
                                              Map<Integer, LogicalPortInfoEntryV2> expectedPorts,
                                              List<LogicalPortInfoEntryV2> missingPorts,
                                              List<LogicalPortInfoEntryV2> properPorts,
                                              List<LogicalPortInfoEntryV2> excessPorts,
                                              List<MisconfiguredInfo<LogicalPortInfoEntryV2>> misconfiguredPorts) {

        for (Entry<Integer, LogicalPortInfoEntryV2> entry : expectedPorts.entrySet()) {
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

    private List<GroupInfoEntryV2> convertGroups(List<GroupSpeakerData> groupEntries) {
        return groupEntries.stream()
                .map(GroupEntryConverter.INSTANCE::toGroupEntry)
                .collect(Collectors.toList());
    }

    private List<MeterInfoEntryV2> convertMeters(List<MeterSpeakerData> meterSpeakerData) {
        return meterSpeakerData.stream()
                .map(MeterEntryConverter.INSTANCE::toMeterEntry)
                .collect(Collectors.toList());
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

    private void addFlowInfo(RuleInfoEntryV2 rule) {
        long longCookie = rule.getCookie();
        FlowSegmentCookie segmentCookie = new FlowSegmentCookie(longCookie);
        CookieBase.CookieType type = segmentCookie.getType();

        if ((type == SERVICE_OR_FLOW_SEGMENT || type == SERVER_42_FLOW_RTT_INGRESS)
                && segmentCookie.getDirection() != FlowPathDirection.UNDEFINED) {
            FlowSegmentCookie pureCookie = FlowSegmentCookie.builder()
                    .direction(segmentCookie.getDirection())
                    .flowEffectiveId(segmentCookie.getFlowEffectiveId())
                    .type(SERVICE_OR_FLOW_SEGMENT)
                    .build();

            flowPathRepository.findByCookie(pureCookie).ifPresent(flowPath -> {
                rule.setFlowPathId(flowPath.getPathId().getId());

                Flow flow = flowPath.getFlow();
                rule.setYFlowId(flow.getYFlowId());
                rule.setFlowId(flow.getFlowId());
            });
        }
    }

    private void addMeterFlowInfo(SwitchId switchId, MeterInfoEntryV2 meterEntry, boolean includeAllFlowInfo) {
        Optional<FlowMeter> flowMeter = flowMeterRepository.findById(switchId, new MeterId(meterEntry.getMeterId()));
        if (!flowMeter.isPresent()) {
            return;
        }
        String id = flowMeter.get().getFlowId(); // Can be flow ID or Y-flow ID
        // TODO(nrydanov): Probably, it will slow down performance.
        // Maybe we should cache data from this repositories.
        if (flowRepository.exists(id)) {
            meterEntry.setFlowId(id);
        }
        if (includeAllFlowInfo && yFlowRepository.exists(id)) {
            meterEntry.setYFlowId(id);
        }
        if (flowMeter.get().getPathId() != null) {
            flowPathRepository.findById(flowMeter.get().getPathId())
                    .ifPresent(path -> {
                        meterEntry.setCookie(path.getCookie().getValue());
                        if (includeAllFlowInfo) {
                            meterEntry.setFlowPathId(path.getPathId().getId());
                        }
                    });
        }
    }

    private void addGroupFlowInfo(SwitchId switchId, GroupInfoEntryV2 groupEntry) {
        mirrorGroupRepository.findByGroupIdAndSwitchId(new GroupId(groupEntry.getGroupId()), switchId)
                .ifPresent(mirrorGroup -> {
                    String id = mirrorGroup.getFlowId();
                    groupEntry.setFlowId(id);
                    String pathId = mirrorGroup.getPathId().getId();
                    groupEntry.setFlowPathId(pathId);
                });
    }

    private MisconfiguredInfo<GroupInfoEntryV2> calculateMisconfiguredGroup(
            GroupInfoEntryV2 expected, GroupInfoEntryV2 actual) {

        GroupInfoEntryV2Builder discrepancies = GroupInfoEntryV2.builder();

        if (!CollectionUtils.isEqualCollection(expected.getBuckets(), actual.getBuckets())) {
            discrepancies.buckets(actual.getBuckets());
        }

        return MisconfiguredInfo.<GroupInfoEntryV2>builder()
                .id(expected.getGroupId().toString())
                .expected(expected)
                .discrepancies(discrepancies.build())
                .build();
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

        return MisconfiguredInfo.<LogicalPortInfoEntryV2>builder()
                .id(expectedPort.getLogicalPortNumber().toString())
                .expected(expectedPort)
                .discrepancies(discrepancies.build())
                .build();
    }

    private MisconfiguredInfo<MeterInfoEntryV2> calculateMisconfiguredMeter(
            boolean isESwitch, MeterInfoEntryV2 expectedMeter, MeterInfoEntryV2 actualMeter) {
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

        return MisconfiguredInfo.<RuleInfoEntryV2>builder()
                .id(buildRuleId(expected)) // tableId + priority + match
                .expected(expected)
                .discrepancies(discrepancies.build())
                .build();
    }

    private String buildRuleId(RuleInfoEntryV2 expected) {
        StringBuilder id = new StringBuilder(format("tableId=%s,priority=%s", expected.getTableId(),
                expected.getPriority()));
        List<String> sortedMatchFields = expected.getMatch().keySet().stream()
                .sorted(String::compareTo)
                .collect(Collectors.toList());

        for (String field : sortedMatchFields) {
            FieldMatch match = expected.getMatch().get(field);
            id.append(format(",%s:value=%s", field, match.getValue()));
            if (match.getMask() != null) {
                id.append(format(",mask=%s", match.getMask()));
            }
        }

        return id.toString();
    }

    private static String cookiesIntoLogRepresentation(Collection<RuleInfoEntryV2> rules) {
        return rules.stream().map(r -> Cookie.toString(r.getCookie())).collect(Collectors.joining(", ", "[", "]"));
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

    private static boolean flagsAreEqual(List<String> present, List<String> expected) {
        Set<String> left = Sets.newHashSet(present);
        Set<String> right = Sets.newHashSet(expected);

        return left.size() == right.size() && left.containsAll(right);
    }

    private static <T> Stream<T> concatStreams(Stream<T> stream1, Stream<T> stream2, Stream<T> stream3) {
        return Stream.concat(Stream.concat(stream1, stream2), stream3);
    }
}
