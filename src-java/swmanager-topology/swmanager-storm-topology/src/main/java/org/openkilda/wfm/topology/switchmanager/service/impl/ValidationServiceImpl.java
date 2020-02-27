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

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.messaging.info.switches.MeterMisconfiguredInfoEntry;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SharedOfFlow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.model.FlowPathReference;
import org.openkilda.wfm.share.model.FlowPathSnapshot;
import org.openkilda.wfm.share.model.SharedOfFlowStatus;
import org.openkilda.wfm.share.service.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilder;
import org.openkilda.wfm.topology.switchmanager.SwitchManagerTopologyConfig;
import org.openkilda.wfm.topology.switchmanager.model.SimpleMeterEntry;
import org.openkilda.wfm.topology.switchmanager.model.SwitchValidationContext;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.service.SpeakerFlowSegmentRequestSwitchFilter;
import org.openkilda.wfm.topology.switchmanager.service.ValidationService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ValidationServiceImpl implements ValidationService {
    private final SwitchRepository switchRepository;
    private final FlowPathRepository flowPathRepository;

    private final FlowResourcesManager flowResourcesManager;
    private final FlowCommandBuilderFactory commandBuilderFactory = new FlowCommandBuilderFactory();

    private final long flowMeterMinBurstSizeInKbits;
    private final double flowMeterBurstCoefficient;

    public ValidationServiceImpl(
            PersistenceManager persistenceManager, SwitchManagerTopologyConfig topologyConfig,
            FlowResourcesManager flowResourcesManager) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        this.switchRepository = repositoryFactory.createSwitchRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();

        this.flowResourcesManager = flowResourcesManager;

        this.flowMeterMinBurstSizeInKbits = topologyConfig.getFlowMeterMinBurstSizeInKbits();
        this.flowMeterBurstCoefficient = topologyConfig.getFlowMeterBurstCoefficient();
    }

    @Override
    public SwitchValidationContext validateRules(
            CommandContext commandContext, SwitchValidationContext validationContext) {
        SwitchId switchId = validationContext.getSwitchId();

        log.debug("Validating rules on switch {}", switchId);

        List<FlowSegmentRequestFactory> expectedFlowSegments = makeFlowSegmentRequestFactories(
                commandContext, switchId);

        Set<Long> expectedOfFlowCookies = expectedFlowSegments.stream()
                .map(entry -> entry.getCookie().getValue())
                .collect(Collectors.toSet());
        ValidateRulesResult ofFlowsReport = makeRulesResponse(
                expectedOfFlowCookies,
                validationContext.getActualOfFlows(), validationContext.getExpectedDefaultOfFlows(), switchId);

        return validationContext.toBuilder()
                .expectedFlowSegments(expectedFlowSegments)
                .ofFlowsValidationReport(ofFlowsReport)
                .build();
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
    public SwitchValidationContext validateMeters(SwitchValidationContext validationContext) {
        SwitchId switchId = validationContext.getSwitchId();
        log.debug("Validating meters on switch {}", switchId);

        List<MeterEntry> originMeters = validationContext.getActualMeters();
        if (originMeters == null) {
            throw new IllegalArgumentException(String.format(
                    "Validation context does not contain actual meters list (%s)", validationContext));
        }

        Switch targetSwitch = switchRepository.findById(switchId)
                .orElseThrow(() -> new IllegalStateException(
                        String.format("Switch %s is missing in persistent storage", switchId)));

        List<MeterEntry> actualMeters = new ArrayList<>(originMeters);
        actualMeters.removeIf(meterEntry -> MeterId.isMeterIdOfDefaultRule(meterEntry.getMeterId()));

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchId);

        List<SimpleMeterEntry> expectedMeters = getExpectedFlowMeters(paths);
        ValidateMetersResult metersReport = comparePresentedAndExpectedMeters(
                targetSwitch.isNoviflowESwitch(), actualMeters, expectedMeters);

        return validationContext.toBuilder()
                .metersValidationReport(metersReport)
                .build();
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

    private List<FlowSegmentRequestFactory> makeFlowSegmentRequestFactories(
            CommandContext commandcontext, SwitchId switchId) {
        List<FlowSegmentRequestFactory> results = new ArrayList<>();
        Map<FlowEncapsulationType, FlowCommandBuilder> speakerRequestBuilderCache = new HashMap<>();
        for (FlowPathReference reference : makePathReferences(switchId)) {
            FlowResources resources = flowResourcesManager.fetchResources(reference);
            Optional<FlowPathSnapshot> possiblePathSnapshot = makePathSnapshot(
                    reference.getPath(), resources.getForward());
            if (! possiblePathSnapshot.isPresent()) {
                log.error("Failed to build forward path snapshot for {}", reference);
                continue;
            }

            FlowPathSnapshot path = possiblePathSnapshot.get();
            FlowPathSnapshot oppositePath = makePathSnapshot(reference.getOppositePath(), resources.getReverse())
                    .orElse(null);

            Flow flow = reference.getFlow();
            FlowCommandBuilder requestFactoryBuilder = speakerRequestBuilderCache.computeIfAbsent(
                    flow.getEncapsulationType(), encapsulation -> {
                        FlowCommandBuilder builder = commandBuilderFactory.getBuilder(encapsulation);
                        return new SpeakerFlowSegmentRequestSwitchFilter(switchId, builder);
                    });

            Boolean isProtectedPath = reference.getProtectedPath();
            if (isProtectedPath == null || !isProtectedPath) {
                results.addAll(requestFactoryBuilder.buildAll(commandcontext, flow, path, oppositePath));
            } else {
                results.addAll(requestFactoryBuilder.buildAllExceptIngress(commandcontext, flow, path, oppositePath));
            }
        }
        return results;
    }

    private List<FlowPathReference> makePathReferences(SwitchId switchId) {
        List<FlowPath> pathUngrouped = loadAffectedPaths(switchId);
        List<FlowPathGroup> pathGroups = groupFlowPathByEffectiveId(pathUngrouped);
        Map<String, Flow> flowById = extractAffectedFlows(pathUngrouped);

        List<FlowPathReference> pathReferences = new ArrayList<>();
        for (FlowPathGroup group : pathGroups) {
            final Flow flow = flowById.get(group.getFlowId());
            if (flow == null) {
                log.error("Orphaned path(s) found: \"{}\"", formatPathIdList(group.getPaths()));
                continue;
            }

            try {
                pathReferences.add(makePathReferenceForPathGroup(switchId, flow, group));
            } catch (IllegalArgumentException e) {
                log.error("Unable to make path reference for flow={} and paths={}: {}",
                          e.getMessage(), flow.getFlowId(), formatPathIdList(group.getPaths()));
            }
        }
        return pathReferences;
    }

    private Optional<FlowPathSnapshot> makePathSnapshot(FlowPath path, PathResources resources) {
        if (path == null || resources == null) {
            return Optional.empty();
        }

        FlowPathSnapshot.FlowPathSnapshotBuilder snapshot = FlowPathSnapshot.builder(path)
                .resources(resources);
        for (SharedOfFlow shareOfFlow : path.getSharedOfFlows()) {
            // force generation of install/remove requests
            SharedOfFlowStatus status = new SharedOfFlowStatus(shareOfFlow, 0);
            snapshot.sharedOfReference(shareOfFlow.getType(), status);
        }
        return Optional.of(snapshot.build());
    }

    private List<FlowPath> loadAffectedPaths(SwitchId switchId) {
        Map<PathId, FlowPath> affectedPaths = new HashMap<>();

        for (FlowPath path : flowPathRepository.findBySegmentSwitch(switchId)) {
            affectedPaths.put(path.getPathId(), path);
        }
        // one-switch-flow have no path segments, so they must be fetched separately
        for (FlowPath path : flowPathRepository.findByEndpointSwitchIncludeProtected(switchId)) {
            affectedPaths.put(path.getPathId(), path);
        }

        return new ArrayList<>(affectedPaths.values());
    }

    private List<FlowPathGroup> groupFlowPathByEffectiveId(List<FlowPath> ungrouped) {
        Map<FlowPathGroupKey, List<FlowPath>> grouped = new HashMap<>();
        for (FlowPath path : ungrouped) {
            long effectiveId = path.getCookie().getFlowEffectiveId();
            String flowId = null;
            if (path.getFlow() != null) {
                flowId = path.getFlow().getFlowId();
            }
            FlowPathGroupKey key = new FlowPathGroupKey(flowId, effectiveId);
            grouped.computeIfAbsent(key, ignore -> new ArrayList<>()).add(path);
        }

        return grouped.entrySet().stream()
                .map(entry -> new FlowPathGroup(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private Map<String, Flow> extractAffectedFlows(Collection<FlowPath> affectedPaths) {
        Map<String, Flow> affectedFlows = new HashMap<>();

        for (FlowPath path : affectedPaths) {
            Flow flow = path.getFlow();
            if (flow != null) {
                affectedFlows.put(flow.getFlowId(), flow);
            }
        }
        return affectedFlows;
    }

    private FlowPathReference makePathReferenceForPathGroup(SwitchId switchId, Flow flow, FlowPathGroup group) {
        FlowPath forward = null;
        FlowPath reverse = null;

        for (FlowPath path : group.getPaths()) {
            FlowPathDirection direction = path.getCookie().getFlowPathDirection();
            switch (direction) {
                case FORWARD:
                    forward = detectPathCookieCollision(forward, path);
                    break;
                case REVERSE:
                    reverse = detectPathCookieCollision(reverse, path);
                    break;
                default:
                    log.error(
                            "Unable to use path {} with direction {} to make desired list of rules for {}",
                            path.getPathId(), direction, switchId);
            }
        }

        return new FlowPathReference(flow, forward, reverse);
    }

    private FlowPath detectPathCookieCollision(FlowPath first, @NonNull FlowPath second) {
        if (first != null) {
            log.error(
                    "Cookie collision detected path {} and {} are using same cookie {}",
                    first.getPathId(), second.getPathId(), first.getCookie());
            return first;
        }
        return second;
    }

    private static String formatPathIdList(List<FlowPath> paths) {
        return paths.stream()
                .map(path -> path.getPathId().toString())
                .collect(Collectors.joining("\", \""));
    }

    @Value
    private static class FlowPathGroupKey {
        private String flowId;
        private long effectiveId;
    }

    @Value
    private static class FlowPathGroup {
        @NonNull
        private FlowPathGroupKey key;

        @NonNull
        private List<FlowPath> paths;

        public String getFlowId() {
            return key.getFlowId();
        }
    }
}
