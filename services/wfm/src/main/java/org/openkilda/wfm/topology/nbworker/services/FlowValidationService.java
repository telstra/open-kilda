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

package org.openkilda.wfm.topology.nbworker.services;

import static java.lang.String.format;

import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.nbtopology.response.FlowValidationResponse;
import org.openkilda.messaging.nbtopology.response.PathDiscrepancyEntity;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.topology.nbworker.models.SimpleSwitchRule;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class FlowValidationService {
    private SwitchRepository switchRepository;
    private FlowRepository flowRepository;
    private TransitVlanRepository transitVlanRepository;

    private long flowMeterMinBurstSizeInKbits;
    private double flowMeterBurstCoefficient;

    public FlowValidationService(PersistenceManager persistenceManager,
                                 long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
        this.flowMeterMinBurstSizeInKbits = flowMeterMinBurstSizeInKbits;
        this.flowMeterBurstCoefficient = flowMeterBurstCoefficient;
    }

    /**
     * Get list of switch ids by flow id.
     */
    public List<SwitchId> getSwitchIdListByFlowId(String flowId) throws FlowNotFoundException {
        List<SwitchId> switchIds = switchRepository.findSwitchesInFlowPathByFlowId(flowId).stream()
                .map(Switch::getSwitchId)
                .collect(Collectors.toList());

        if (switchIds.isEmpty()) {
            throw new FlowNotFoundException(flowId);
        }

        return switchIds;
    }

    /**
     * Validate flow.
     */
    public List<FlowValidationResponse> validateFlow(String flowId, List<SwitchFlowEntries> switchFlowEntries,
                                                     List<SwitchMeterEntries> switchMeterEntries)
            throws FlowNotFoundException {

        Map<SwitchId, List<SimpleSwitchRule>> switchRules = new HashMap<>();
        int rulesCount = 0;
        int metersCount = 0;
        for (SwitchFlowEntries switchRulesEntries : switchFlowEntries) {
            SwitchMeterEntries switchMeters = switchMeterEntries.stream()
                    .filter(meterEntries -> switchRulesEntries.getSwitchId().equals(meterEntries.getSwitchId()))
                    .findFirst()
                    .orElse(null);

            List<SimpleSwitchRule> simpleSwitchRules = convertSwitchRules(switchRulesEntries, switchMeters);
            switchRules.put(switchRulesEntries.getSwitchId(), simpleSwitchRules);

            rulesCount += Optional.ofNullable(switchRulesEntries.getFlowEntries())
                    .map(List::size)
                    .orElse(0);
            metersCount += Optional.ofNullable(switchMeters)
                    .map(SwitchMeterEntries::getMeterEntries)
                    .map(List::size)
                    .orElse(0);
        }

        Optional<Flow> foundFlow = flowRepository.findById(flowId);
        if (!foundFlow.isPresent()) {
            throw new FlowNotFoundException(flowId);
        }
        Flow flow = foundFlow.get();

        TransitVlan forwardTransitVlan = null;
        TransitVlan reverseTransitVlan = null;
        if (!flow.isOneSwitchFlow()) {
            forwardTransitVlan = transitVlanRepository.findByPathId(flow.getForwardPath().getPathId()).stream()
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("TransitVlan was not found, pathId: %s", flow.getForwardPath().getPathId())));

            reverseTransitVlan = transitVlanRepository.findByPathId(flow.getReversePath().getPathId()).stream()
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("TransitVlan was not found, pathId: %s", flow.getReversePath().getPathId())));
        }

        List<SimpleSwitchRule> forwardRules =
                convertFlowToSimpleSwitchRules(flow, flow.getForwardPath(), forwardTransitVlan);
        List<SimpleSwitchRule> reverseRules =
                convertFlowToSimpleSwitchRules(flow, flow.getReversePath(), reverseTransitVlan);

        return Lists.newArrayList(compare(switchRules, forwardRules, flowId, rulesCount, metersCount),
                compare(switchRules, reverseRules, flowId, rulesCount, metersCount));
    }

    private FlowValidationResponse compare(Map<SwitchId, List<SimpleSwitchRule>> rulesPerSwitch,
                                           List<SimpleSwitchRule> rulesFromDb, String flowId,
                                           int totalSwitchRules, int metersCount) {

        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        List<Long> pktCounts = new ArrayList<>();
        List<Long> byteCounts = new ArrayList<>();
        rulesFromDb.forEach(simpleRule -> discrepancies.addAll(
                findDiscrepancy(simpleRule, rulesPerSwitch.get(simpleRule.getSwitchId()), pktCounts, byteCounts)));
        int flowMetersCount = (int) rulesFromDb.stream().filter(rule -> rule.getMeterId() != null).count();

        return FlowValidationResponse.builder()
                .flowId(flowId)
                .discrepancies(discrepancies)
                .asExpected(discrepancies.isEmpty())
                .pktCounts(pktCounts)
                .byteCounts(byteCounts)
                .flowRulesTotal(rulesFromDb.size())
                .switchRulesTotal(totalSwitchRules)
                .flowMetersTotal(flowMetersCount)
                .switchMetersTotal(metersCount)
                .build();
    }

    private List<SimpleSwitchRule> convertFlowToSimpleSwitchRules(Flow flow, FlowPath flowPath,
                                                                  TransitVlan transitVlan) {
        List<SimpleSwitchRule> rules = new ArrayList<>();
        rules.add(getIngressRules(flow, flowPath, transitVlan));
        rules.addAll(getTransitAndEgressRules(flow, flowPath, transitVlan));
        return rules;
    }

    private SimpleSwitchRule getIngressRules(Flow flow, FlowPath flowPath, TransitVlan transitVlan) {
        boolean forward = isForward(flow, flowPath);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();

        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(flowPath.getSrcSwitch().getSwitchId())
                .cookie(flowPath.getCookie().getValue())
                .inPort(inPort)
                .inVlan(inVlan)
                .meterId(flowPath.getMeterId() != null ? flowPath.getMeterId().getValue() : null)
                .meterRate(flow.getBandwidth())
                .meterBurstSize(Meter.calculateBurstSize(flow.getBandwidth(), flowMeterMinBurstSizeInKbits,
                        flowMeterBurstCoefficient, flow.getSrcSwitch().getDescription()))
                .meterFlags(Meter.getMeterFlags())
                .build();

        if (flow.isOneSwitchFlow()) {
            rule.setOutPort(outPort);
            rule.setOutVlan(outVlan);

        } else {
            PathSegment ingressSegment = flowPath.getSegments().stream()
                    .filter(segment -> segment.getSrcSwitch().getSwitchId()
                            .equals(flowPath.getSrcSwitch().getSwitchId()))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("PathSegment was not found for ingress flow rule, flowId: %s",
                                    flowPath.getFlowId())));

            rule.setOutPort(ingressSegment.getSrcPort());
            rule.setOutVlan(transitVlan.getVlan());
        }

        return rule;
    }

    private List<SimpleSwitchRule> getTransitAndEgressRules(Flow flow, FlowPath flowPath, TransitVlan transitVlan) {
        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }

        boolean forward = isForward(flow, flowPath);
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();

        List<PathSegment> orderedSegments = flowPath.getSegments().stream()
                .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                .collect(Collectors.toList());

        List<SimpleSwitchRule> rules = new ArrayList<>();

        for (int i = 1; i < orderedSegments.size(); i++) {
            PathSegment src = orderedSegments.get(i - 1);
            PathSegment dst = orderedSegments.get(i);

            rules.add(SimpleSwitchRule.builder()
                    .switchId(src.getDestSwitch().getSwitchId())
                    .inPort(src.getDestPort())
                    .outPort(dst.getSrcPort())
                    .cookie(flowPath.getCookie().getValue())
                    .inVlan(transitVlan.getVlan())
                    .build());
        }

        PathSegment egressSegment = orderedSegments.get(orderedSegments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    String.format("PathSegment was not found for egress flow rule, flowId: %s", flowPath.getFlowId()));
        }

        rules.add(SimpleSwitchRule.builder()
                .switchId(flowPath.getDestSwitch().getSwitchId())
                .outPort(outPort)
                .outVlan(outVlan)
                .inVlan(transitVlan.getVlan())
                .inPort(egressSegment.getDestPort())
                .cookie(flowPath.getCookie().getValue())
                .build());

        return rules;
    }

    private boolean isForward(Flow flow, FlowPath flowPath) {
        if (!flowPath.getFlowId().equals(flow.getFlowId())
                || !flowPath.getPathId().equals(flow.getForwardPathId())
                && !flowPath.getPathId().equals(flow.getReversePathId())) {
            throw new IllegalArgumentException(format(
                    "Flow path %s doesn't correspond to the given flow %s.", flowPath.getPathId(), flow.getFlowId()));
        }
        return flowPath.getPathId().equals(flow.getForwardPathId());
    }

    private List<SimpleSwitchRule> convertSwitchRules(SwitchFlowEntries rules, SwitchMeterEntries meters) {
        if (rules == null || rules.getFlowEntries() == null) {
            return Collections.emptyList();
        }

        List<SimpleSwitchRule> simpleRules = new ArrayList<>();
        for (FlowEntry switchRule : rules.getFlowEntries()) {
            log.debug("FlowEntry: {}", switchRule);
            SimpleSwitchRule rule = SimpleSwitchRule.builder()
                    .switchId(rules.getSwitchId())
                    .cookie(switchRule.getCookie())
                    .inPort(NumberUtils.toInt(switchRule.getMatch().getInPort()))
                    .inVlan(NumberUtils.toInt(switchRule.getMatch().getVlanVid()))
                    .pktCount(switchRule.getPacketCount())
                    .byteCount(switchRule.getByteCount())
                    .version(switchRule.getVersion())
                    .build();

            if (switchRule.getInstructions() != null) {
                if (switchRule.getInstructions().getApplyActions() != null) {
                    FlowApplyActions applyActions = switchRule.getInstructions().getApplyActions();
                    rule.setOutVlan(Optional.ofNullable(applyActions.getFieldAction())
                            .filter(action -> "vlan_vid".equals(action.getFieldName()))
                            .map(FlowSetFieldAction::getFieldValue)
                            .map(NumberUtils::toInt)
                            .orElse(NumberUtils.INTEGER_ZERO));
                    String outPort = applyActions.getFlowOutput();
                    if ("in_port".equals(outPort)) {
                        outPort = switchRule.getMatch().getInPort();
                    }
                    rule.setOutPort(NumberUtils.toInt(outPort));
                }

                Optional.ofNullable(switchRule.getInstructions().getGoToMeter())
                        .ifPresent(meterId -> {
                            rule.setMeterId(meterId);
                            if (meters != null && meters.getMeterEntries() != null) {
                                meters.getMeterEntries().stream()
                                        .filter(entry -> meterId == entry.getMeterId())
                                        .findFirst()
                                        .ifPresent(entry -> {
                                            rule.setMeterRate(entry.getRate());
                                            rule.setMeterBurstSize(entry.getBurstSize());
                                            rule.setMeterFlags(entry.getFlags());
                                        });
                            }
                        });
            }

            simpleRules.add(rule);
        }
        return simpleRules;
    }

    private List<PathDiscrepancyEntity> findDiscrepancy(SimpleSwitchRule expected, List<SimpleSwitchRule> actual,
                                                        List<Long> pktCounts, List<Long> byteCounts) {
        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        SimpleSwitchRule matched = findMatched(expected, actual);

        if (matched == null) {
            discrepancies.add(new PathDiscrepancyEntity(String.valueOf(expected), "all", String.valueOf(expected), ""));
            pktCounts.add(-1L);
            byteCounts.add(-1L);
        } else {
            discrepancies.addAll(getRuleDiscrepancies(expected, matched));
            pktCounts.add(matched.getPktCount());
            byteCounts.add(matched.getByteCount());
        }

        return discrepancies;
    }

    private SimpleSwitchRule findMatched(SimpleSwitchRule expected, List<SimpleSwitchRule> actual) {

        //try to match on the cookie
        SimpleSwitchRule matched = actual.stream()
                .filter(rule -> rule.getCookie() != 0 && rule.getCookie() == expected.getCookie())
                .findFirst()
                .orElse(null);

        //if no cookie match, then try to match on in_port and in_vlan
        if (matched == null) {
            matched = actual.stream()
                    .filter(rule -> rule.getInPort() == expected.getInPort()
                            && rule.getInVlan() == expected.getInVlan())
                    .findFirst()
                    .orElse(null);
        }

        //if cookie or in_port and in_vlan doesn't match, try to match on out_port and out_vlan
        if (matched == null) {
            matched = actual.stream()
                    .filter(rule -> rule.getOutPort() == expected.getOutPort()
                            && rule.getOutVlan() == expected.getOutVlan())
                    .findFirst()
                    .orElse(null);
        }
        return matched;
    }

    private List<PathDiscrepancyEntity> getRuleDiscrepancies(SimpleSwitchRule expected, SimpleSwitchRule matched) {
        List<PathDiscrepancyEntity> discrepancies = new ArrayList<>();
        if (matched.getCookie() != expected.getCookie()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "cookie",
                    String.valueOf(expected.getCookie()), String.valueOf(matched.getCookie())));
        }
        if (matched.getInPort() != expected.getInPort()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "inPort",
                    String.valueOf(expected.getInPort()), String.valueOf(matched.getInPort())));
        }
        if (matched.getInVlan() != expected.getInVlan()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "inVlan",
                    String.valueOf(expected.getInVlan()), String.valueOf(matched.getInVlan())));
        }
        if (matched.getOutPort() != expected.getOutPort()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "outPort",
                    String.valueOf(expected.getOutPort()), String.valueOf(matched.getOutPort())));
        }
        if (matched.getOutVlan() != expected.getOutVlan()) {
            discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "outVlan",
                    String.valueOf(expected.getOutVlan()), String.valueOf(matched.getOutVlan())));
        }
        //meters on OF_12 switches are not supported, so skip them.
        if ((matched.getVersion() == null || matched.getVersion().compareTo("OF_12") > 0)
                && !(matched.getMeterId() == null && expected.getMeterId() == null)) {

            if (!Objects.equals(matched.getMeterId(), expected.getMeterId())) {
                discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterId",
                        String.valueOf(expected.getMeterId()), String.valueOf(matched.getMeterId())));
            } else {
                if (!Objects.equals(matched.getMeterRate(), expected.getMeterRate())) {
                    discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterRate",
                            String.valueOf(expected.getMeterRate()), String.valueOf(matched.getMeterRate())));
                }
                if (!Objects.equals(matched.getMeterBurstSize(), expected.getMeterBurstSize())) {
                    discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterBurstSize",
                            String.valueOf(expected.getMeterBurstSize()), String.valueOf(matched.getMeterBurstSize())));
                }
                if (!Arrays.equals(matched.getMeterFlags(), expected.getMeterFlags())) {
                    discrepancies.add(new PathDiscrepancyEntity(expected.toString(), "meterFlags",
                            Arrays.toString(expected.getMeterFlags()), Arrays.toString(matched.getMeterFlags())));
                }
            }
        }
        return discrepancies;
    }
}
