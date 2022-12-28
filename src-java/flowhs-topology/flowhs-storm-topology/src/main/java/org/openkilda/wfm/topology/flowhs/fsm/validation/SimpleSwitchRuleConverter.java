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

package org.openkilda.wfm.topology.flowhs.fsm.validation;

import static java.lang.String.format;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.messaging.info.meter.MeterEntry;
import org.openkilda.messaging.info.meter.SwitchMeterEntries;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowSetFieldAction;
import org.openkilda.messaging.info.rule.GroupBucket;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.messaging.info.rule.SwitchFlowEntries;
import org.openkilda.messaging.info.rule.SwitchGroupEntries;
import org.openkilda.model.EncapsulationId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule.SimpleGroupBucket;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule.SimpleSwitchRuleBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SimpleSwitchRuleConverter {

    private static final String VLAN_VID = "vlan_vid";
    private static final String IN_PORT = "in_port";

    /**
     * Convert {@link FlowPath} to list of {@link SimpleSwitchRule}.
     */
    public List<SimpleSwitchRule> convertFlowPathToSimpleSwitchRules(
            Flow flow, FlowPath flowPath, EncapsulationId encapsulationId,
            Map<PathId, EncapsulationResources> mirrorEncapsulationMap,
            long flowMeterMinBurstSizeInKbits, double flowMeterBurstCoefficient) {
        List<SimpleSwitchRule> rules = new ArrayList<>();
        if (!flowPath.isProtected()) {
            rules.addAll(buildIngressSimpleSwitchRules(flow, flowPath, encapsulationId, mirrorEncapsulationMap,
                    flowMeterMinBurstSizeInKbits, flowMeterBurstCoefficient));
        }
        if (!flow.isOneSwitchFlow()) {
            rules.addAll(buildTransitAndEgressSimpleSwitchRules(flow, flowPath, encapsulationId,
                    mirrorEncapsulationMap));
        }
        return rules;
    }

    /**
     * Build ingress rules ({@link SimpleSwitchRule}) for provided {@link FlowPath}.
     */
    public List<SimpleSwitchRule> buildIngressSimpleSwitchRules(
            Flow flow, FlowPath flowPath, EncapsulationId encapsulationId,
            Map<PathId, EncapsulationResources> mirrorEncapsulationMap, long flowMeterMinBurstSizeInKbits,
            double flowMeterBurstCoefficient) {
        boolean forward = flow.isForward(flowPath);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();

        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(flowPath.getSrcSwitchId())
                .cookie(flowPath.getCookie().getValue())
                .inPort(inPort)
                .meterId(flowPath.getMeterId() != null ? flowPath.getMeterId().getValue() : null)
                .meterRate(flow.getBandwidth())
                .meterBurstSize(Meter.calculateBurstSize(flow.getBandwidth(), flowMeterMinBurstSizeInKbits,
                        flowMeterBurstCoefficient, flowPath.getSrcSwitch().getDescription()))
                .meterFlags(Meter.getMeterKbpsFlags())
                .ingressRule(true)
                .build();

        FlowSideAdapter ingress = FlowSideAdapter.makeIngressAdapter(flow, flowPath);
        FlowEndpoint endpoint = ingress.getEndpoint();
        if (flowPath.isSrcWithMultiTable()) {
            // in multi-table mode actual ingress rule will match port+inner_vlan+metadata(outer_vlan)
            if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
                rule.setInVlan(endpoint.getInnerVlanId());
            }
        } else {
            rule.setInVlan(endpoint.getOuterVlanId());
        }

        int transitVlan = 0;
        int vni = 0;
        if (flow.isOneSwitchFlow()) {
            FlowEndpoint egressEndpoint = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint();
            rule.setOutPort(outPort);
            rule.setOutVlan(calcVlanSetSequence(ingress, flowPath, egressEndpoint.getVlanStack()));
        } else {
            PathSegment ingressSegment = flowPath.getSegments().stream()
                    .filter(segment -> segment.getSrcSwitchId()
                            .equals(flowPath.getSrcSwitchId()))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException(
                            format("PathSegment was not found for ingress flow rule, flowId: %s",
                                    flow.getFlowId())));

            outPort = ingressSegment.getSrcPort();
            rule.setOutPort(outPort);
            if (flow.getEncapsulationType().equals(TRANSIT_VLAN)) {
                transitVlan = encapsulationId.getEncapsulationId();
                rule.setOutVlan(calcVlanSetSequence(ingress, flowPath, Collections.singletonList(transitVlan)));
            } else if (flow.getEncapsulationType().equals(FlowEncapsulationType.VXLAN)) {
                vni = encapsulationId.getEncapsulationId();
                rule.setTunnelId(vni);
            }
        }
        List<SimpleSwitchRule> rules = Lists.newArrayList(rule);

        if (ingress.isLooped() && !flowPath.isProtected()) {
            rules.add(buildIngressLoopSimpleSwitchRule(rule, flowPath, ingress));
        }

        Optional<FlowMirrorPoints> foundFlowMirrorPoints = flowPath.getFlowMirrorPointsSet().stream()
                .filter(mirrorPoints -> mirrorPoints.getMirrorSwitchId().equals(flowPath.getSrcSwitchId()))
                .findFirst();
        if (foundFlowMirrorPoints.isPresent()) {
            FlowMirrorPoints flowMirrorPoints = foundFlowMirrorPoints.get();
            SimpleSwitchRule mirrorRule = rule.toBuilder()
                    .outPort(0)
                    .tunnelId(0)
                    .cookie(flowPath.getCookie().toBuilder().mirror(true).build().getValue())
                    .groupId(flowMirrorPoints.getMirrorGroupId().intValue())
                    .groupBuckets(mapGroupBuckets(flowMirrorPoints.getFlowMirrors(), outPort, transitVlan, vni,
                            flow.getEncapsulationType(), mirrorEncapsulationMap))
                    .build();
            if (!flow.isOneSwitchFlow()) {
                mirrorRule.setOutVlan(Collections.emptyList());
            }
            rules.add(mirrorRule);
        }

        return rules;
    }

    private SimpleSwitchRule buildIngressLoopSimpleSwitchRule(SimpleSwitchRule rule, FlowPath flowPath,
                                                              FlowSideAdapter ingress) {
        SimpleSwitchRuleBuilder builder = SimpleSwitchRule.builder()
                .switchId(rule.getSwitchId())
                .cookie(flowPath.getCookie().toBuilder().looped(true).build().getValue())
                .inPort(rule.getInPort())
                .inVlan(rule.getInVlan())
                .outPort(rule.getInPort());
        if (flowPath.isSrcWithMultiTable() && ingress.getEndpoint().getOuterVlanId() != 0) {
            builder.outVlan(Collections.singletonList(ingress.getEndpoint().getOuterVlanId()));
        }
        return builder.build();
    }

    private List<SimpleSwitchRule> buildTransitAndEgressSimpleSwitchRules(
            Flow flow, FlowPath flowPath, EncapsulationId encapsulationId,
            Map<PathId, EncapsulationResources> mirrorEncapsulationMap) {
        List<PathSegment> orderedSegments = flowPath.getSegments().stream()
                .sorted(Comparator.comparingInt(PathSegment::getSeqId))
                .collect(Collectors.toList());

        List<SimpleSwitchRule> rules = new ArrayList<>();

        for (int i = 1; i < orderedSegments.size(); i++) {
            PathSegment srcPathSegment = orderedSegments.get(i - 1);
            PathSegment dstPathSegment = orderedSegments.get(i);
            rules.add(buildTransitSimpleSwitchRule(flow, flowPath, srcPathSegment, dstPathSegment, encapsulationId));
        }

        PathSegment egressSegment = orderedSegments.get(orderedSegments.size() - 1);
        if (!egressSegment.getDestSwitchId().equals(flowPath.getDestSwitchId())) {
            throw new IllegalStateException(
                    format("PathSegment was not found for egress flow rule, flowId: %s", flow.getFlowId()));
        }
        rules.addAll(buildEgressSimpleSwitchRules(flow, flowPath, egressSegment, encapsulationId,
                mirrorEncapsulationMap));

        return rules;
    }

    /**
     * Build ingress rules ({@link SimpleSwitchRule}) for provided y-flow's {@link FlowPath}.
     */
    public List<SimpleSwitchRule> buildYFlowIngressSimpleSwitchRules(List<SimpleSwitchRule> ingressRules,
                                                                     SwitchId sharedEndpoint,
                                                                     MeterId sharedEndpointMeterId) {
        return ingressRules.stream()
                .filter(SimpleSwitchRule::isIngressRule)
                .filter(rule -> rule.getSwitchId().equals(sharedEndpoint))
                .filter(rule -> new Cookie(rule.getCookie()).getType() == SERVICE_OR_FLOW_SEGMENT)
                .map(origin -> {
                    FlowSegmentCookie cookie = new FlowSegmentCookie(origin.getCookie());
                    return origin.toBuilder()
                            .meterId(sharedEndpointMeterId.getValue())
                            .cookie(cookie.toBuilder().yFlow(true).build().getValue())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Build transit rule ({@link SimpleSwitchRule}) between the segments of provided {@link FlowPath}.
     */
    public SimpleSwitchRule buildTransitSimpleSwitchRule(Flow flow, FlowPath flowPath,
                                                         PathSegment srcPathSegment, PathSegment dstPathSegment,
                                                         EncapsulationId encapsulationId) {

        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(srcPathSegment.getDestSwitchId())
                .inPort(srcPathSegment.getDestPort())
                .outPort(dstPathSegment.getSrcPort())
                .cookie(flowPath.getCookie().getValue())
                .build();
        if (flow.getEncapsulationType().equals(TRANSIT_VLAN)) {
            rule.setInVlan(encapsulationId.getEncapsulationId());
        } else if (flow.getEncapsulationType().equals(FlowEncapsulationType.VXLAN)) {
            rule.setTunnelId(encapsulationId.getEncapsulationId());
        }

        return rule;
    }

    /**
     * Build transit rules ({@link SimpleSwitchRule}) between the segments of provided y-flow's {@link FlowPath}.
     */
    public List<SimpleSwitchRule> buildYFlowTransitSimpleSwitchRules(List<SimpleSwitchRule> transitRules,
                                                                     SwitchId yPoint,
                                                                     MeterId yPointMeterId,
                                                                     Long meterRate, Long meterMinBurstSize) {
        return transitRules.stream()
                .filter(rule -> !rule.isIngressRule() && !rule.isEgressRule())
                .filter(rule -> rule.getSwitchId().equals(yPoint))
                .filter(rule -> new Cookie(rule.getCookie()).getType() == SERVICE_OR_FLOW_SEGMENT)
                .map(origin -> {
                    FlowSegmentCookie cookie = new FlowSegmentCookie(origin.getCookie());
                    return origin.toBuilder()
                            .cookie(cookie.toBuilder().yFlow(true).build().getValue())
                            .meterId(yPointMeterId.getValue())
                            .meterRate(meterRate)
                            .meterBurstSize(meterMinBurstSize)
                            .meterFlags(Meter.getMeterKbpsFlags())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<SimpleSwitchRule> buildEgressSimpleSwitchRules(
            Flow flow, FlowPath flowPath, PathSegment egressSegment, EncapsulationId encapsulationId,
            Map<PathId, EncapsulationResources> mirrorEncapsulationMap) {
        List<SimpleSwitchRule> rules = new ArrayList<>();

        FlowSideAdapter egressAdapter = FlowSideAdapter.makeEgressAdapter(flow, flowPath);
        FlowEndpoint endpoint = egressAdapter.getEndpoint();
        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(flowPath.getDestSwitchId())
                .outPort(endpoint.getPortNumber())
                .inPort(egressSegment.getDestPort())
                .cookie(flowPath.getCookie().getValue())
                .egressRule(true)
                .build();

        if (flow.getEncapsulationType().equals(TRANSIT_VLAN)) {
            rule.setInVlan(encapsulationId.getEncapsulationId());
            rule.setOutVlan(calcVlanSetSequence(
                    Collections.singletonList(encapsulationId.getEncapsulationId()),
                    endpoint.getVlanStack()));
        } else if (flow.getEncapsulationType().equals(FlowEncapsulationType.VXLAN)) {
            rule.setTunnelId(encapsulationId.getEncapsulationId());
            rule.setOutVlan(calcVlanSetSequence(Collections.emptyList(), endpoint.getVlanStack()));
        }
        if (egressAdapter.isLooped() && !flowPath.isProtected()) {
            rules.add(buildTransitLoopRuleForEgressSwitch(rule, flowPath));
        }
        rules.add(rule);

        Optional<FlowMirrorPoints> foundFlowMirrorPoints = flowPath.getFlowMirrorPointsSet().stream()
                .filter(mirrorPoints -> mirrorPoints.getMirrorSwitchId().equals(egressSegment.getDestSwitchId()))
                .findFirst();
        if (foundFlowMirrorPoints.isPresent()) {
            FlowMirrorPoints flowMirrorPoints = foundFlowMirrorPoints.get();
            rules.add(rule.toBuilder()
                    .outPort(0)
                    .cookie(flowPath.getCookie().toBuilder().mirror(true).build().getValue())
                    .groupId(flowMirrorPoints.getMirrorGroupId().intValue())
                    .groupBuckets(mapGroupBuckets(flowMirrorPoints.getFlowMirrors(), endpoint.getPortNumber(),
                            0, 0, flow.getEncapsulationType(), mirrorEncapsulationMap))
                    .build());
        }

        return rules;
    }

    private SimpleSwitchRule buildTransitLoopRuleForEgressSwitch(SimpleSwitchRule rule, FlowPath flowPath) {
        SimpleSwitchRuleBuilder builder = SimpleSwitchRule.builder()
                .switchId(rule.getSwitchId())
                .cookie(flowPath.getCookie().toBuilder().looped(true).build().getValue())
                .inPort(rule.getInPort())
                .inVlan(rule.getInVlan())
                .tunnelId(rule.getTunnelId())
                .outPort(rule.getInPort());
        return builder.build();
    }

    /**
     * Convert {@link SwitchFlowEntries} to list of {@link SimpleSwitchRule}.
     */
    public List<SimpleSwitchRule> convertSwitchFlowEntriesToSimpleSwitchRules(SwitchFlowEntries rules,
                                                                              SwitchMeterEntries meters,
                                                                              SwitchGroupEntries groups) {
        if (rules == null || rules.getFlowEntries() == null) {
            return Collections.emptyList();
        }

        Map<Long, MeterEntry> meterMap = new HashMap<>();
        if (meters != null && meters.getMeterEntries() != null) {
            for (MeterEntry meterEntry : meters.getMeterEntries()) {
                meterMap.put(meterEntry.getMeterId(), meterEntry);
            }
        }

        Map<Integer, List<GroupBucket>> groupMap = new HashMap<>();
        if (groups != null && groups.getGroupEntries() != null) {
            for (GroupEntry groupEntry : groups.getGroupEntries()) {
                groupMap.put(groupEntry.getGroupId(), groupEntry.getBuckets());
            }
        }

        List<SimpleSwitchRule> simpleRules = new ArrayList<>();
        for (FlowEntry flowEntry : rules.getFlowEntries()) {
            simpleRules.add(buildSimpleSwitchRule(rules.getSwitchId(), flowEntry, meterMap, groupMap));
        }
        return simpleRules;
    }

    private SimpleSwitchRule buildSimpleSwitchRule(
            SwitchId switchId, FlowEntry flowEntry, Map<Long, MeterEntry> meterMap,
            Map<Integer, List<GroupBucket>> groupMap) {
        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(switchId)
                .cookie(flowEntry.getCookie())
                .pktCount(flowEntry.getPacketCount())
                .byteCount(flowEntry.getByteCount())
                .version(flowEntry.getVersion())
                .build();

        if (flowEntry.getMatch() != null) {
            rule.setInPort(NumberUtils.toInt(flowEntry.getMatch().getInPort()));
            rule.setInVlan(NumberUtils.toInt(flowEntry.getMatch().getVlanVid()));
            rule.setTunnelId(Optional.ofNullable(flowEntry.getMatch().getTunnelId())
                    .map(Integer::decode)
                    .orElse(NumberUtils.INTEGER_ZERO));
        }

        if (flowEntry.getInstructions() != null) {
            if (flowEntry.getInstructions().getApplyActions() != null) {
                FlowApplyActions applyActions = flowEntry.getInstructions().getApplyActions();
                List<FlowSetFieldAction> setFields = Optional.ofNullable(applyActions.getSetFieldActions())
                        .orElse(new ArrayList<>());
                rule.setOutVlan(setFields.stream()
                        .filter(Objects::nonNull)
                        .filter(action -> VLAN_VID.equals(action.getFieldName()))
                        .map(FlowSetFieldAction::getFieldValue)
                        .map(NumberUtils::toInt)
                        .collect(Collectors.toList()));
                String outPort = applyActions.getFlowOutput();
                if (IN_PORT.equals(outPort) && flowEntry.getMatch() != null) {
                    outPort = flowEntry.getMatch().getInPort();
                }
                rule.setOutPort(NumberUtils.toInt(outPort));

                if (rule.getTunnelId() == NumberUtils.INTEGER_ZERO) {
                    rule.setTunnelId(Optional.ofNullable(applyActions.getPushVxlan())
                            .map(Integer::parseInt)
                            .orElse(NumberUtils.INTEGER_ZERO));
                }

                if (NumberUtils.isParsable(applyActions.getGroup())) {
                    int groupId = NumberUtils.toInt(applyActions.getGroup());
                    List<GroupBucket> buckets = groupMap.getOrDefault(groupId, new ArrayList<>());
                    rule.setGroupId(groupId);
                    rule.setGroupBuckets(mapGroupBuckets(buckets));
                }
            }

            Optional.ofNullable(flowEntry.getInstructions().getGoToMeter())
                    .ifPresent(meterId -> {
                        rule.setMeterId(meterId);
                        MeterEntry meterEntry = meterMap.get(meterId);
                        if (meterEntry != null) {
                            rule.setMeterRate(meterEntry.getRate());
                            rule.setMeterBurstSize(meterEntry.getBurstSize());
                            rule.setMeterFlags(meterEntry.getFlags());
                        }
                    });
        }

        return rule;
    }

    private List<SimpleGroupBucket> mapGroupBuckets(List<GroupBucket> buckets) {
        List<SimpleGroupBucket> simpleGroupBuckets = new ArrayList<>();
        for (GroupBucket bucket : buckets) {
            FlowApplyActions actions = bucket.getApplyActions();
            if (actions == null || !NumberUtils.isParsable(actions.getFlowOutput())) {
                continue;
            }
            int bucketPort = NumberUtils.toInt(actions.getFlowOutput());

            int bucketVlan = 0;
            if (actions.getSetFieldActions() != null
                    && actions.getSetFieldActions().size() == 1) {
                FlowSetFieldAction setFieldAction = actions.getSetFieldActions().get(0);
                if (VLAN_VID.equals(setFieldAction.getFieldName())) {
                    bucketVlan = NumberUtils.toInt(setFieldAction.getFieldValue());
                }
            }

            int bucketVni = 0;
            if (actions.getPushVxlan() != null) {
                bucketVni = NumberUtils.toInt(actions.getPushVxlan());
            }
            simpleGroupBuckets.add(new SimpleGroupBucket(bucketPort, bucketVlan, bucketVni));
        }
        simpleGroupBuckets.sort(this::compareSimpleGroupBucket);
        return simpleGroupBuckets;
    }

    private List<SimpleGroupBucket> mapGroupBuckets(
            Collection<FlowMirror> flowMirrors, int mainMirrorPort, int mainMirrorVlan, int mainMirrorVni,
            FlowEncapsulationType encapsulationType, Map<PathId, EncapsulationResources> mirrorEncapsulationMap) {
        List<SimpleGroupBucket> buckets = Lists.newArrayList(
                new SimpleGroupBucket(mainMirrorPort, mainMirrorVlan, mainMirrorVni));
        for (FlowMirror flowMirror : flowMirrors) {
            if (flowMirror.isOneSwitchMirror()) {
                buckets.add(new SimpleGroupBucket(flowMirror.getEgressPort(), flowMirror.getEgressOuterVlan(), 0));
            } else {
                buckets.add(buildMirrorBucket(flowMirror, mirrorEncapsulationMap, encapsulationType));
            }
        }
        buckets.sort(this::compareSimpleGroupBucket);
        return buckets;
    }

    private static SimpleGroupBucket buildMirrorBucket(
            FlowMirror flowMirror, Map<PathId, EncapsulationResources> mirrorEncapsulationMap,
            FlowEncapsulationType encapsulationType) {
        int egressPort = flowMirror.getForwardPath().getSegments().get(0).getSrcPort();
        int encapsulation = mirrorEncapsulationMap.get(flowMirror.getForwardPathId())
                .getEncapsulation().getEncapsulationId();
        switch (encapsulationType) {
            case TRANSIT_VLAN:
                return new SimpleGroupBucket(egressPort, encapsulation, 0);
            case VXLAN:
                return new SimpleGroupBucket(egressPort, 0, encapsulation);
            default:
                throw new IllegalArgumentException(format("Unknown encapsulation type %s for flow mirror path %s",
                        encapsulationType, flowMirror.getForwardPathId()));
        }
    }

    @VisibleForTesting
    int compareSimpleGroupBucket(SimpleGroupBucket simpleGroupBucketA, SimpleGroupBucket simpleGroupBucketB) {
        if (simpleGroupBucketA.getOutPort() == simpleGroupBucketB.getOutPort()) {
            return Integer.compare(simpleGroupBucketA.getOutVlan(), simpleGroupBucketB.getOutVlan());
        }
        return Integer.compare(simpleGroupBucketA.getOutPort(), simpleGroupBucketB.getOutPort());
    }

    private static List<Integer> calcVlanSetSequence(FlowSideAdapter ingress, FlowPath flowPath,
                                                     List<Integer> desiredVlanStack) {
        List<Integer> current;
        if (flowPath.isSrcWithMultiTable()) {
            // outer vlan is removed by first shared rule
            current = FlowEndpoint.makeVlanStack(ingress.getEndpoint().getInnerVlanId());
        } else {
            current = ingress.getEndpoint().getVlanStack();
        }
        return calcVlanSetSequence(current, desiredVlanStack);
    }

    /**
     * Steal logic from {@link org.openkilda.floodlight.utils.OfAdapter#makeVlanReplaceActions}.
     */
    private static List<Integer> calcVlanSetSequence(
            List<Integer> currentVlanStack, List<Integer> desiredVlanStack) {
        Iterator<Integer> currentIter = currentVlanStack.iterator();
        Iterator<Integer> desiredIter = desiredVlanStack.iterator();

        final List<Integer> actions = new ArrayList<>();
        while (currentIter.hasNext() && desiredIter.hasNext()) {
            Integer current = currentIter.next();
            Integer desired = desiredIter.next();
            if (current == null || desired == null) {
                throw new IllegalArgumentException(
                        "Null elements are not allowed inside currentVlanStack and desiredVlanStack arguments");
            }

            if (!current.equals(desired)) {
                // rewrite existing VLAN stack "head"
                actions.add(desired);
                break;
            }
        }

        while (desiredIter.hasNext()) {
            actions.add(desiredIter.next());
        }
        return actions;
    }
}
