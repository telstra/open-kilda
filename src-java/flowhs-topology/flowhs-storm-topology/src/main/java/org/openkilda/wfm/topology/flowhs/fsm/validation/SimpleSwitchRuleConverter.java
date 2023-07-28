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

import static org.openkilda.model.cookie.CookieBase.CookieType.SERVICE_OR_FLOW_SEGMENT;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.EncapsulationId;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Meter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.Cookie;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule.SimpleGroupBucket;
import org.openkilda.wfm.topology.flowhs.fsm.validation.SimpleSwitchRule.SimpleSwitchRuleBuilder;

import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleSwitchRuleConverter {

    /**
     * Build ingress rules ({@link SimpleSwitchRule}) for provided {@link FlowPath}.
     */
    public List<SimpleSwitchRule> buildIngressSimpleSwitchRules(Flow flow, FlowPath flowPath,
                                                                EncapsulationId encapsulationId,
                                                                long flowMeterMinBurstSizeInKbits,
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
        // actual ingress rule is match port+inner_vlan+metadata(outer_vlan)
        if (FlowEndpoint.isVlanIdSet(endpoint.getInnerVlanId())) {
            rule.setInVlan(endpoint.getInnerVlanId());
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
                            String.format("PathSegment was not found for ingress flow rule, flowId: %s",
                                    flow.getFlowId())));

            outPort = ingressSegment.getSrcPort();
            rule.setOutPort(outPort);
            if (flow.getEncapsulationType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
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
                    .groupBuckets(mapGroupBuckets(flowMirrorPoints.getMirrorPaths(), outPort, transitVlan, vni))
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
        if (ingress.getEndpoint().getOuterVlanId() != 0) {
            builder.outVlan(Collections.singletonList(ingress.getEndpoint().getOuterVlanId()));
        }
        return builder.build();
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
        if (flow.getEncapsulationType().equals(FlowEncapsulationType.TRANSIT_VLAN)) {
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

    /**
     * Convert {@link SpeakerData list} to the Map of {@link SimpleSwitchRule}.
     */
    public Map<SwitchId, List<SimpleSwitchRule>> convertSpeakerDataToSimpleSwitchRulesAndGroupBySwitchId(
            List<SpeakerData> speakerDataList) {

        Map<SwitchId, List<FlowSpeakerData>> rules = filterSpeakerDataAndGroupBySwitchId(speakerDataList,
                FlowSpeakerData.class);
        Map<SwitchId, List<MeterSpeakerData>> meters = filterSpeakerDataAndGroupBySwitchId(speakerDataList,
                MeterSpeakerData.class);
        Map<SwitchId, List<GroupSpeakerData>> groups = filterSpeakerDataAndGroupBySwitchId(speakerDataList,
                GroupSpeakerData.class);

        Map<SwitchId, List<SimpleSwitchRule>> result = new HashMap<>();

        rules.forEach((switchId, flowSpeakerData) ->
                result.put(switchId, convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(
                        flowSpeakerData, meters.getOrDefault(switchId, Collections.emptyList()),
                        groups.getOrDefault(switchId, Collections.emptyList()))));
        return result;
    }

    /**
     * Convert {@link SpeakerData lists} to the Map of {@link SimpleSwitchRule}.
     */
    public List<SimpleSwitchRule> convertSpeakerDataToSimpleSwitchRulesForOneSwitchId(List<FlowSpeakerData> rules,
                                                                                      List<MeterSpeakerData> meters,
                                                                                      List<GroupSpeakerData> groups) {
        if (CollectionUtils.isEmpty(rules)) {
            return Collections.emptyList();
        }

        Map<Long, List<MeterSpeakerData>> meterMap = Optional.ofNullable(meters)
                .orElse(Collections.emptyList()).stream()
                .collect(Collectors.groupingBy(e -> e.getMeterId().getValue()));

        Map<Integer, List<GroupSpeakerData>> groupMap = Optional.ofNullable(groups)
                .orElse(Collections.emptyList()).stream().filter(e -> Objects.nonNull(e.getGroupId()))
                .collect(Collectors.groupingBy(e -> e.getGroupId().intValue()));


        List<SimpleSwitchRule> simpleRules = new ArrayList<>();
        for (FlowSpeakerData flowSpeakerData : rules) {
            simpleRules.add(buildSimpleSwitchRule(flowSpeakerData, meterMap, groupMap));
        }
        return simpleRules;
    }

    private <T extends SpeakerData> Map<SwitchId, List<T>> filterSpeakerDataAndGroupBySwitchId(
            List<SpeakerData> speakerData, Class<T> clazz) {

        return speakerData.stream()
                .filter(Objects::nonNull)
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(Collectors.groupingBy(SpeakerData::getSwitchId));
    }

    private SimpleSwitchRule buildSimpleSwitchRule(
            FlowSpeakerData flowEntry, Map<Long, List<MeterSpeakerData>> meterMap,
            Map<Integer, List<GroupSpeakerData>> groupMap) {

        SimpleSwitchRule rule = SimpleSwitchRule.builder()
                .switchId(flowEntry.getSwitchId())
                .cookie(flowEntry.getCookie().getValue())
                .pktCount(flowEntry.getPacketCount())
                .byteCount(flowEntry.getByteCount())
                .version(flowEntry.getOfVersion().toString())
                .build();

        if (flowEntry.getMatch() != null) {
            rule.setInPort(flowEntry.getMatch().stream().filter(e -> e.getField().equals(Field.IN_PORT))
                    .map(FieldMatch::getValue).findFirst().orElse(NumberUtils.LONG_ZERO).intValue());

            rule.setInVlan(flowEntry.getMatch().stream()
                    .filter(fieldMatch -> fieldMatch.getField().equals(Field.VLAN_VID))
                    .map(FieldMatch::getValue).findFirst().orElse(NumberUtils.LONG_ZERO).intValue());

            rule.setTunnelId(flowEntry.getMatch().stream().filter(e -> e.getField().equals(Field.OVS_VXLAN_VNI)
                            || e.getField().equals(Field.NOVIFLOW_TUNNEL_ID)).map(e -> String.valueOf(e.getValue()))
                    .findFirst().map(Integer::decode).orElse(NumberUtils.INTEGER_ZERO));
        }

        if (flowEntry.getInstructions() != null) {
            if (CollectionUtils.isNotEmpty(flowEntry.getInstructions().getApplyActions())) {
                List<Action> applyActions = flowEntry.getInstructions().getApplyActions();
                List<SetFieldAction> setFields = applyActions.stream()
                        .filter(action -> action.getType().equals(ActionType.SET_FIELD))
                        .map(action -> (SetFieldAction) action)
                        .collect(Collectors.toList());

                rule.setOutVlan(setFields.stream()
                        .filter(action -> Field.VLAN_VID.equals(action.getField()))
                        .map(action -> Long.valueOf(action.getValue()).intValue())
                        .collect(Collectors.toList()));

                PortNumber outPortNumber = applyActions.stream()
                        .filter(action -> action.getType().equals(ActionType.PORT_OUT))
                        .findFirst().map(action -> ((PortOutAction) action).getPortNumber())
                        .orElse(null);
                int outPort;
                if (outPortNumber != null && SpecialPortType.IN_PORT.equals(outPortNumber.getPortType())
                        && rule.getInPort() != 0) {
                    outPort = rule.getInPort();
                } else {
                    outPort = Optional.ofNullable(outPortNumber).map(PortNumber::getPortNumber).orElse(0);
                }
                rule.setOutPort(outPort);

                if (rule.getTunnelId() == NumberUtils.INTEGER_ZERO) {
                    rule.setTunnelId(applyActions.stream()
                            .filter(action -> action.getType().equals(ActionType.PUSH_VXLAN_OVS)
                                    || action.getType().equals(ActionType.PUSH_VXLAN_NOVIFLOW))
                            .findFirst().map(action -> ((PushVxlanAction) action).getVni()).orElse(0));
                }
                Long groupId = applyActions.stream().filter(action -> action.getType().equals(ActionType.GROUP))
                        .map(action -> ((GroupAction) action).getGroupId().getValue())
                        .findFirst().orElse(null);

                if (groupId != null) {
                    List<GroupSpeakerData> buckets = groupMap.getOrDefault(groupId.intValue(), new ArrayList<>());
                    rule.setGroupId(groupId.intValue());
                    rule.setGroupBuckets(buildSimpleGroupBucketsFromSpeakerGroups(buckets));
                }
            }
            Optional.ofNullable(flowEntry.getInstructions().getGoToMeter())
                    .ifPresent(meterId -> {
                        rule.setMeterId(meterId.getValue());
                        List<MeterSpeakerData> meterEntry = meterMap.get(meterId.getValue());
                        if (meterEntry != null && meterEntry.size() != 0) {
                            rule.setMeterRate(meterEntry.get(0).getRate());
                            rule.setMeterBurstSize(meterEntry.get(0).getBurst());
                            rule.setMeterFlags(meterEntry.get(0).getFlags().stream()
                                    .map(Enum::name).sorted().toArray(String[]::new));
                        }
                    });
        }
        return rule;
    }

    private List<SimpleGroupBucket> buildSimpleGroupBucketsFromSpeakerGroups(List<GroupSpeakerData> buckets) {
        List<SimpleGroupBucket> simpleGroupBuckets = new ArrayList<>();
        for (Bucket bucket : buckets.stream().flatMap(e -> e.getBuckets().stream()).collect(Collectors.toList())) {
            Set<Action> actions = bucket.getWriteActions();
            if (actions == null) {
                continue;
            }
            PortNumber portNumber = actions.stream().filter(action -> action.getType().equals(ActionType.PORT_OUT))
                    .findFirst().map(e -> ((PortOutAction) e).getPortNumber()).orElse(null);

            if (portNumber == null || portNumber.getPortNumber() == 0) {
                continue;
            }

            int bucketPort = portNumber.getPortNumber();

            int bucketVlan = 0;
            List<SetFieldAction> setFieldActions = actions.stream()
                    .filter(action -> action.getType().equals(ActionType.SET_FIELD))
                    .map(action -> (SetFieldAction) action)
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(setFieldActions)
                    && setFieldActions.size() == 1) {
                SetFieldAction setFieldAction = setFieldActions.get(0);
                if (Field.VLAN_VID.equals(setFieldAction.getField())) {
                    bucketVlan = (int) setFieldAction.getValue();
                }
            }

            int bucketVni = actions.stream()
                    .filter(action -> action.getType().equals(ActionType.PUSH_VXLAN_OVS)
                            || action.getType().equals(ActionType.PUSH_VXLAN_NOVIFLOW))
                    .findFirst().map(action -> ((PushVxlanAction) action).getVni()).orElse(0);

            simpleGroupBuckets.add(new SimpleGroupBucket(bucketPort, bucketVlan, bucketVni));
        }
        simpleGroupBuckets.sort(this::compareSimpleGroupBucket);
        return simpleGroupBuckets;
    }

    private List<SimpleGroupBucket> mapGroupBuckets(Collection<FlowMirrorPath> flowMirrorPaths, int mainMirrorPort,
                                                    int mainMirrorVlan, int mainMirrorVni) {
        List<SimpleGroupBucket> buckets = Lists.newArrayList(
                new SimpleGroupBucket(mainMirrorPort, mainMirrorVlan, mainMirrorVni));
        flowMirrorPaths.forEach(flowMirrorPath ->
                buckets.add(new SimpleGroupBucket(flowMirrorPath.getEgressPort(),
                        flowMirrorPath.getEgressOuterVlan(), 0)));
        buckets.sort(this::compareSimpleGroupBucket);
        return buckets;
    }

    private int compareSimpleGroupBucket(SimpleGroupBucket simpleGroupBucketA, SimpleGroupBucket simpleGroupBucketB) {
        if (simpleGroupBucketA.getOutPort() == simpleGroupBucketB.getOutPort()) {
            return Integer.compare(simpleGroupBucketA.getOutVlan(), simpleGroupBucketB.getOutVlan());
        }
        return Integer.compare(simpleGroupBucketA.getOutPort(), simpleGroupBucketB.getOutPort());
    }

    private static List<Integer> calcVlanSetSequence(FlowSideAdapter ingress, FlowPath flowPath,
                                                     List<Integer> desiredVlanStack) {
        // outer vlan is removed by first shared rule
        List<Integer> current = FlowEndpoint.makeVlanStack(ingress.getEndpoint().getInnerVlanId());
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
