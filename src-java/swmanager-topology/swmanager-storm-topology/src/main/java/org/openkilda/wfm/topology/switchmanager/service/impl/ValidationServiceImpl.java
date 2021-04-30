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

import static java.util.stream.Collectors.toList;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.GroupBucket;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.messaging.info.switches.GroupInfoEntry;
import org.openkilda.messaging.info.switches.GroupInfoEntry.PortVlanEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.Meter;
import org.openkilda.model.MirrorGroup;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.CookieBase;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSegmentCookieBuilder;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.MirrorGroupRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerTopologyConfig;
import org.openkilda.wfm.topology.switchmanager.error.InconsistentDataException;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.mappers.MeterEntryMapper;
import org.openkilda.wfm.topology.switchmanager.model.SimpleMeterEntry;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ValidationServiceImpl implements ValidationService {
    private static final String VLAN_VID_SET_ACTION = "vlan_vid";

    private final FlowPathRepository flowPathRepository;
    private final SwitchRepository switchRepository;
    private final SwitchPropertiesRepository switchPropertiesRepository;
    private final FeatureTogglesRepository featureTogglesRepository;
    private final MirrorGroupRepository mirrorGroupRepository;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;

    public ValidationServiceImpl(PersistenceManager persistenceManager, SwitchManagerTopologyConfig topologyConfig) {
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.switchPropertiesRepository = persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository();
        this.featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        this.mirrorGroupRepository = persistenceManager.getRepositoryFactory().createMirrorGroupRepository();
        this.flowMeterMinBurstSizeInKbits = topologyConfig.getFlowMeterMinBurstSizeInKbits();
        this.flowMeterBurstCoefficient = topologyConfig.getFlowMeterBurstCoefficient();
        this.flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
    }

    @Override
    public ValidateRulesResult validateRules(SwitchId switchId, List<FlowEntry> presentRules,
                                             List<FlowEntry> expectedDefaultRules) {
        log.debug("Validating rules on switch {}", switchId);

        Set<Long> expectedCookies = getExpectedFlowRules(switchId);
        return makeRulesResponse(expectedCookies, presentRules, expectedDefaultRules, switchId);
    }

    private Set<Long> getExpectedServer42IngressCookies(SwitchId switchId, Collection<FlowPath> paths) {
        SwitchProperties switchProperties = switchPropertiesRepository.findBySwitchId(switchId)
                .orElseThrow(() -> new InconsistentDataException(switchId, "switch properties not found"));

        if (switchProperties.isServer42FlowRtt()
                && featureTogglesRepository.find().map(FeatureToggles::getServer42FlowRtt).orElse(false)) {
            return paths.stream()
                    .filter(path -> switchId.equals(path.getSrcSwitchId()))
                    .filter(path -> !path.isOneSwitchFlow())
                    .map(FlowPath::getCookie)
                    .map(FlowSegmentCookie::toBuilder)
                    .map(builder -> builder.type(CookieType.SERVER_42_INGRESS))
                    .map(FlowSegmentCookieBuilder::build)
                    .map(CookieBase::getValue)
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    private Set<Long> getExpectedMirrorPointsCookies(Collection<FlowPath> paths) {
        Set<Long> cookies = new HashSet<>();
        paths.forEach(path -> {
            path.getFlowMirrorPointsSet().forEach(mirrorPoints -> {
                Collection<FlowMirrorPath> flowMirrorPaths = mirrorPoints.getMirrorPaths();
                if (!flowMirrorPaths.isEmpty()) {
                    cookies.add(path.getCookie().toBuilder().mirror(true).build().getValue());
                }
            });
        });
        return cookies;
    }

    @Override
    public ValidateGroupsResult validateGroups(SwitchId switchId, List<GroupEntry> groupEntries) {
        Collection<MirrorGroup> expected = mirrorGroupRepository.findBySwitchId(switchId);
        Set<GroupInfoEntry> expectedGroups = expected
                .stream()
                .map(group -> buildGroupInfoEntryFromDatabase(switchId, group.getGroupId()))
                .collect(Collectors.toSet());

        Set<GroupInfoEntry> presentGroups = groupEntries.stream()
                .filter(group -> group.getGroupId() >= GroupId.MIN_FLOW_GROUP_ID.getValue()) // exclude default group
                .map(this::buildGroupInfoEntryFromSpeaker)
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

    private Set<GroupInfoEntry> calculateMisconfiguredGroups(Set<GroupInfoEntry> expected, Set<GroupInfoEntry> actual) {
        Set<GroupInfoEntry> misconfiguredGroups = new HashSet<>();

        Map<Integer, GroupInfoEntry> actualEntries = actual.stream()
                .collect(Collectors.toMap(GroupInfoEntry::getGroupId, entry -> entry));
        for (GroupInfoEntry expectedEntry : expected) {
            GroupInfoEntry actualEntry = actualEntries.get(expectedEntry.getGroupId());

            if (actualEntry == null || actualEntry.equals(expectedEntry)) {
                continue;
            }

            List<PortVlanEntry> missingData = new ArrayList<>(expectedEntry.getGroupBuckets());
            missingData.removeAll(actualEntry.getGroupBuckets());

            List<PortVlanEntry> excessData = new ArrayList<>(actualEntry.getGroupBuckets());
            excessData.removeAll(expectedEntry.getGroupBuckets());

            misconfiguredGroups.add(actualEntry.toBuilder()
                    .missingGroupBuckets(missingData)
                    .excessGroupBuckets(excessData)
                    .build());
        }

        return misconfiguredGroups;
    }

    private GroupInfoEntry buildGroupInfoEntryFromDatabase(SwitchId switchId, GroupId groupId) {
        GroupInfoEntry groupInfoEntry = null;
        FlowMirrorPoints flowMirrorPoints = flowMirrorPointsRepository.findByGroupId(groupId).orElse(null);

        if (flowMirrorPoints != null) {
            List<PortVlanEntry> portVlanEntries = new ArrayList<>();
            portVlanEntries.add(new PortVlanEntry(getMainMirrorPort(switchId, flowMirrorPoints.getFlowPath()), null));
            portVlanEntries.addAll(flowMirrorPoints.getMirrorPaths().stream()
                    .map(mirrorPath -> new PortVlanEntry(mirrorPath.getEgressPort(), mirrorPath.getEgressOuterVlan()))
                    .collect(Collectors.toList()));

            portVlanEntries.sort(this::comparePortVlanEntry);
            groupInfoEntry = GroupInfoEntry.builder()
                    .groupId(flowMirrorPoints.getMirrorGroupId().intValue())
                    .groupBuckets(portVlanEntries)
                    .build();
        } else {
            log.error("Excess database mirror group resource with group id: {}", groupId);
        }

        return groupInfoEntry;
    }

    private int getMainMirrorPort(SwitchId switchId, FlowPath flowPath) {
        Flow flow = flowPath.getFlow();
        if (flow.isOneSwitchFlow()) {
            if (flowPath.isForward()) {
                return flow.getDestPort();
            }
            return flow.getSrcPort();
        }

        if (flowPath.isForward()) {
            if (switchId.equals(flowPath.getSrcSwitchId())) {
                return flowPath.getSegments().get(0).getSrcPort();
            } else {
                return flow.getDestPort();
            }
        } else {
            if (switchId.equals(flowPath.getSrcSwitchId())) {
                return flowPath.getSegments().get(0).getSrcPort();
            } else {
                return flow.getSrcPort();
            }
        }
    }

    private GroupInfoEntry buildGroupInfoEntryFromSpeaker(GroupEntry group) {
        List<PortVlanEntry> portVlanEntries = new ArrayList<>();
        for (GroupBucket bucket : group.getBuckets()) {
            FlowApplyActions actions = bucket.getApplyActions();
            if (actions == null || !NumberUtils.isParsable(actions.getFlowOutput())) {
                continue;
            }
            int bucketPort = NumberUtils.toInt(actions.getFlowOutput());
            Integer bucketVlan = null;

            if (actions.getSetFieldActions() != null
                    && actions.getSetFieldActions().size() == 1) {
                FlowSetFieldAction setFieldAction = actions.getSetFieldActions().get(0);
                if (VLAN_VID_SET_ACTION.equals(setFieldAction.getFieldName())) {
                    bucketVlan = NumberUtils.toInt(setFieldAction.getFieldValue());
                }
            }
            portVlanEntries.add(new PortVlanEntry(bucketPort, bucketVlan));
        }
        portVlanEntries.sort(this::comparePortVlanEntry);

        return GroupInfoEntry.builder()
                .groupId(group.getGroupId())
                .groupBuckets(portVlanEntries)
                .build();
    }

    private int comparePortVlanEntry(PortVlanEntry portVlanEntryA, PortVlanEntry portVlanEntryB) {
        if (Objects.equals(portVlanEntryA.getPort(), portVlanEntryB.getPort())) {
            return compareInteger(portVlanEntryA.getVlan(), portVlanEntryB.getVlan());
        }
        return compareInteger(portVlanEntryA.getPort(), portVlanEntryB.getPort());
    }

    private int compareInteger(Integer value1, Integer value2) {
        if (value1 == null && value2 == null) {
            return 0;
        }
        if (value1 == null) {
            return -1;
        }
        if (value2 == null) {
            return 1;
        }
        return Integer.compare(value1, value2);
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
                    log.info("Misconfigured rule: {} : expected : {}", defaultRule, expectedDefaultRule);
                    misconfiguredRules.add(expectedDefaultRule.getCookie());
                }

                if (defaultRule.size() > 1) {
                    log.info("Misconfigured rule: {} : expected : {}", defaultRule, expectedDefaultRule);
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
                                               List<MeterEntry> expectedDefaultMeters) {
        log.debug("Validating meters on switch {}", switchId);

        Switch sw = switchRepository.findById(switchId)
                .orElseThrow(() -> new SwitchNotFoundException(switchId));
        boolean isESwitch = Switch.isNoviflowESwitch(sw.getOfDescriptionManufacturer(), sw.getOfDescriptionHardware());

        List<SimpleMeterEntry> expectedMeters = expectedDefaultMeters.stream()
                .map(MeterEntryMapper.INSTANCE::map)
                .collect(toList());

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchId).stream()
                .filter(flowPath -> flowPath.getStatus() != FlowPathStatus.IN_PROGRESS)
                .filter(flowPath -> flowPath.getFlow().isActualPathId(flowPath.getPathId()))
                .collect(Collectors.toList());

        if (!paths.isEmpty()) {
            expectedMeters.addAll(getExpectedFlowMeters(paths));
        }

        return comparePresentedAndExpectedMeters(isESwitch, presentMeters, expectedMeters);
    }

    private Set<Long> getExpectedFlowRules(SwitchId switchId) {
        Set<Long> result = new HashSet<>();

        // collect transit segments
        flowPathRepository.findBySegmentDestSwitch(switchId).stream()
                .filter(flowPath -> flowPath.getStatus() != FlowPathStatus.IN_PROGRESS)
                .filter(flowPath -> flowPath.getFlow().isActualPathId(flowPath.getPathId()))
                .map(FlowPath::getCookie)
                .map(Cookie::getValue)
                .forEach(result::add);

        // collect termination segments
        Collection<FlowPath> affectedPaths = flowPathRepository.findByEndpointSwitch(switchId).stream()
                .filter(flowPath -> flowPath.getStatus() != FlowPathStatus.IN_PROGRESS)
                .filter(path -> path.getFlow().isActualPathId(path.getPathId()))
                .collect(Collectors.toList());
        for (FlowPath path : affectedPaths) {
            Flow flow = path.getFlow();

            result.add(path.getCookie().getValue());

            // shared outer vlan match rule
            FlowSideAdapter ingress = FlowSideAdapter.makeIngressAdapter(flow, path);
            FlowEndpoint endpoint = ingress.getEndpoint();
            if (path.isSrcWithMultiTable()
                    && switchId.equals(endpoint.getSwitchId())
                    && FlowEndpoint.isVlanIdSet(endpoint.getOuterVlanId())
                    && ingress.isPrimaryEgressPath(path.getPathId())) {
                result.add(FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                        .portNumber(endpoint.getPortNumber())
                        .vlanId(endpoint.getOuterVlanId())
                        .build().getValue());
            }
            if (switchId.equals(flow.getLoopSwitchId()) && !path.isProtected()) {
                result.add(path.getCookie().toBuilder().looped(true).build().getValue());
            }
        }

        result.addAll(getExpectedServer42IngressCookies(switchId, affectedPaths));
        result.addAll(getExpectedMirrorPointsCookies(affectedPaths));
        return result;
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

    private boolean flagsAreEqual(String[] present, Set<String> expected) {
        Set<String> actual = Sets.newHashSet(present);

        return actual.size() == expected.size() && actual.containsAll(expected);
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
                    .flags(Sets.newHashSet(Meter.getMeterKbpsFlags()))
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
                .flags(meter.getFlags().toArray(new String[0]))
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
        if (!Sets.newHashSet(actualMeter.getFlags()).equals(expectedMeter.getFlags())) {
            actual.setFlags(actualMeter.getFlags());
            expected.setFlags(expectedMeter.getFlags().toArray(new String[0]));
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
