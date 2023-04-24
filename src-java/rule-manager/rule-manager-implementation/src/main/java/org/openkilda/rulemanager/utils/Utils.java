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

package org.openkilda.rulemanager.utils;

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;
import static org.openkilda.rulemanager.Constants.VXLAN_DST_IPV4_ADDRESS;
import static org.openkilda.rulemanager.Constants.VXLAN_SRC_IPV4_ADDRESS;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MirrorConfig.MirrorConfigData;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.PushVxlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class Utils {

    private Utils() {
    }

    /**
     * Create series of actions required to reTAG one set of vlan tags to another.
     */
    public static List<Action> makeVlanReplaceActions(List<Integer> currentVlanStack, List<Integer> targetVlanStack) {
        Iterator<Integer> currentIter = currentVlanStack.iterator();
        Iterator<Integer> targetIter = targetVlanStack.iterator();

        final List<Action> actions = new ArrayList<>();
        while (currentIter.hasNext() && targetIter.hasNext()) {
            Integer current = currentIter.next();
            Integer target = targetIter.next();
            if (current == null || target == null) {
                throw new IllegalArgumentException(
                        "Null elements are not allowed inside currentVlanStack and targetVlanStack arguments");
            }

            if (!current.equals(target)) {
                // remove all extra VLANs
                while (currentIter.hasNext()) {
                    currentIter.next();
                    actions.add(new PopVlanAction());
                }
                // rewrite existing VLAN stack "head"
                actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(target).build());
                break;
            }
        }

        // remove all extra VLANs (if previous loops ends with lack of target VLANs)
        while (currentIter.hasNext()) {
            currentIter.next();
            actions.add(new PopVlanAction());
        }

        while (targetIter.hasNext()) {
            actions.add(new PushVlanAction());
            actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(targetIter.next()).build());
        }
        return actions;
    }

    public static boolean isFullPortEndpoint(FlowEndpoint flowEndpoint) {
        return !FlowEndpoint.isVlanIdSet(flowEndpoint.getOuterVlanId());
    }

    /**
     * Returns port to which packets must be sent by first path switch.
     */
    public static PortNumber getOutPort(FlowPath path, Flow flow) {
        if (path.isOneSwitchPath()) {
            FlowEndpoint endpoint = FlowSideAdapter.makeEgressAdapter(flow, path).getEndpoint();
            if (flow.getSrcPort() == flow.getDestPort()) {
                // the case of a single switch & same port flow.
                return new PortNumber(SpecialPortType.IN_PORT);
            } else {
                return new PortNumber(endpoint.getPortNumber());
            }
        } else {
            if (path.getSegments().isEmpty()) {
                throw new IllegalStateException(
                        format("Multi switch flow path %s has no segments", path.getPathId()));
            }
            return new PortNumber(path.getSegments().get(0).getSrcPort());
        }
    }

    /**
     * Builds push VXLAN action.
     */
    public static PushVxlanAction buildPushVxlan(
            int vni, SwitchId srcSwitchId, SwitchId dstSwitchId, int udpSrc, Set<SwitchFeature> features) {
        ActionType type;
        if (features.contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            type = ActionType.PUSH_VXLAN_NOVIFLOW;
        } else if (features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            type = ActionType.PUSH_VXLAN_OVS;
        } else {
            throw new IllegalArgumentException(format("To push VXLAN switch %s must support one of the following "
                    + "features: [%s, %s]", srcSwitchId, NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        }
        return PushVxlanAction.builder()
                .type(type)
                .vni(vni)
                .srcMacAddress(new MacAddress(srcSwitchId.toMacAddress()))
                .dstMacAddress(new MacAddress(dstSwitchId.toMacAddress()))
                .srcIpv4Address(VXLAN_SRC_IPV4_ADDRESS)
                .dstIpv4Address(VXLAN_DST_IPV4_ADDRESS)
                .udpSrc(udpSrc)
                .build();

    }

    /**
     * Builds pop VXLAN action.
     */
    public static PopVxlanAction buildPopVxlan(SwitchId switchId, Set<SwitchFeature> features) {
        ActionType type;
        if (features.contains(NOVIFLOW_PUSH_POP_VXLAN)) {
            type = ActionType.POP_VXLAN_NOVIFLOW;
        } else if (features.contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
            type = ActionType.POP_VXLAN_OVS;
        } else {
            throw new IllegalArgumentException(format("To pop VXLAN switch %s must support one of the following "
                    + "features: [%s, %s]", switchId, NOVIFLOW_PUSH_POP_VXLAN, KILDA_OVS_PUSH_POP_MATCH_VXLAN));
        }
        return new PopVxlanAction(type);
    }

    public static OfMetadata mapMetadata(RoutingMetadata metadata) {
        return new OfMetadata(metadata.getValue(), metadata.getMask());
    }

    /**
     * Builds ingress endpoint from flow and flowPath and checks if target switchId equal to path src switchId.
     */
    public static FlowEndpoint checkAndBuildIngressEndpoint(Flow flow, FlowPath flowPath, SwitchId switchId) {
        FlowEndpoint ingressEndpoint = FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint();
        if (!ingressEndpoint.getSwitchId().equals(switchId)) {
            throw new IllegalArgumentException(format("Path %s has ingress endpoint %s with switchId %s. But switchId "
                            + "must be equal to target switchId %s", flowPath.getPathId(), ingressEndpoint,
                    ingressEndpoint.getSwitchId(), switchId));
        }
        return ingressEndpoint;
    }

    /**
     * Builds group buckets for flow mirror points (only for sink endpoints. Flow bucket must be build separately).
     */
    public static List<Bucket> buildMirrorBuckets(FlowMirrorPoints flowMirrorPoints) {
        List<Bucket> buckets = new ArrayList<>();
        Set<MirrorConfigData> mirrorConfigDataSet = flowMirrorPoints.getMirrorPaths().stream()
                .map(mirrorPath -> new MirrorConfigData(mirrorPath.getEgressPort(),
                        mirrorPath.getEgressOuterVlan()))
                .collect(Collectors.toSet());

        for (MirrorConfigData mirrorConfig : mirrorConfigDataSet) {
            Set<Action> actions = new HashSet<>(makeVlanReplaceActions(
                    new ArrayList<>(), makeVlanStack(mirrorConfig.getMirrorVlan())));
            actions.add(new PortOutAction(new PortNumber(mirrorConfig.getMirrorPort())));
            buckets.add(Bucket.builder()
                    .writeActions(actions)
                    .watchGroup(WatchGroup.ANY)
                    .watchPort(WatchPort.ANY)
                    .build());
        }
        return buckets;
    }

    /**
     * Builds egress endpoint from flow and flowPath and checks if target switchId equal to path dst switchId.
     */
    public static FlowEndpoint checkAndBuildEgressEndpoint(Flow flow, FlowPath flowPath, SwitchId switchId) {
        FlowEndpoint egressEndpoint = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint();
        if (!egressEndpoint.getSwitchId().equals(switchId)) {
            throw new IllegalArgumentException(format("Path %s has egress endpoint %s with switchId %s. But switchId "
                            + "must be equal to target switchId %s", flowPath.getPathId(), egressEndpoint,
                    egressEndpoint.getSwitchId(), switchId));
        }
        return egressEndpoint;
    }

    /**
     * Builds match for ingress rule.
     */
    public static Set<FieldMatch> makeIngressMatch(
            FlowEndpoint endpoint, boolean multiTable, Set<SwitchFeature> features) {
        if (multiTable) {
            if (isVlanIdSet(endpoint.getOuterVlanId())) {
                if (isVlanIdSet(endpoint.getInnerVlanId())) {
                    return makeDoubleVlanIngressMatch(endpoint, features);
                } else {
                    return makeSingleVlanMultiTableIngressMatch(endpoint, features);
                }
            } else {
                return makeDefaultPortIngresMatch(endpoint);
            }
        } else {
            if (isVlanIdSet(endpoint.getOuterVlanId())) {
                return makeSingleVlanSingleTableIngressMatch(endpoint);
            } else {
                return makeDefaultPortIngresMatch(endpoint);
            }
        }
    }

    /**
     * Builds flags for a rule.
     */
    public static Set<OfFlowFlag> buildRuleFlags(Set<SwitchFeature> features) {
        if (features.contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            return Sets.newHashSet(OfFlowFlag.RESET_COUNTERS);
        }
        return new HashSet<>();
    }

    /**
     * Builds shared flow pre ingress rule.
     */
    public static FlowSpeakerData buildSharedFlowPreIngressCommand(Switch sw, FlowEndpoint endpoint) {
        FlowSharedSegmentCookie cookie = FlowSharedSegmentCookie.builder(SharedSegmentType.QINQ_OUTER_VLAN)
                .portNumber(endpoint.getPortNumber())
                .vlanId(endpoint.getOuterVlanId())
                .build();

        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(endpoint.getOuterVlanId())
                .build(sw.getFeatures());
        Instructions instructions = Instructions.builder()
                .applyActions(Lists.newArrayList(new PopVlanAction()))
                .writeMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()))
                .goToTable(OfTable.INGRESS)
                .build();

        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.PRE_INGRESS)
                .priority(Constants.Priority.FLOW_PRIORITY)
                .match(buildSharedPreIngressMatch(endpoint))
                .instructions(instructions);

        // TODO add RESET_COUNTERS flag
        return builder.build();
    }

    /**
     * Builds rule for catching packets from customer port.
     */
    public static FlowSpeakerData buildCustomerPortSharedCatchCommand(Switch sw, FlowEndpoint endpoint) {
        PortColourCookie cookie = new PortColourCookie(CookieType.MULTI_TABLE_INGRESS_RULES, endpoint.getPortNumber());

        Instructions instructions = Instructions.builder()
                .goToTable(OfTable.PRE_INGRESS)
                .build();

        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.INPUT)
                .priority(Priority.INGRESS_CUSTOMER_PORT_RULE_PRIORITY_MULTITABLE)
                .match(Sets.newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build()))
                .instructions(instructions);

        return builder.build();
    }

    /**
     * Finds shortest sub path.
     */
    public static FlowPath getShortestSubPath(@NonNull Collection<FlowPath> subPaths) {
        if (subPaths.isEmpty()) {
            throw new IllegalArgumentException("Sub paths collection is empty");
        }
        FlowPath shortest = subPaths.iterator().next();
        for (FlowPath subPath : subPaths) {
            if (subPath.getSegments().size() < shortest.getSegments().size()) {
                shortest = subPath;
            }
        }
        return shortest;
    }

    /**
     * Find Speaker Command Data of specific type.
     */
    public static <C extends SpeakerData> Optional<C> getCommand(
            Class<C> commandType, List<SpeakerData> commands) {
        return commands.stream()
                .filter(commandType::isInstance)
                .map(commandType::cast)
                .findFirst();
    }

    private static Set<FieldMatch> buildSharedPreIngressMatch(FlowEndpoint endpoint) {
        return Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getOuterVlanId()).build());
    }

    private static Set<FieldMatch> makeDefaultPortIngresMatch(FlowEndpoint endpoint) {
        return newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build());
    }

    private static Set<FieldMatch> makeDoubleVlanIngressMatch(FlowEndpoint endpoint, Set<SwitchFeature> features) {
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(endpoint.getOuterVlanId()).build(features);
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getInnerVlanId()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
    }

    private static Set<FieldMatch> makeSingleVlanMultiTableIngressMatch(
            FlowEndpoint endpoint, Set<SwitchFeature> features) {
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(endpoint.getOuterVlanId()).build(features);
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
    }

    private static Set<FieldMatch> makeSingleVlanSingleTableIngressMatch(FlowEndpoint endpoint) {
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getOuterVlanId()).build());
    }
}
