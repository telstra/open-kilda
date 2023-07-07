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

package org.openkilda.rulemanager.factory.generator.flow;

import static java.lang.String.format;
import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_COPY_FIELD;
import static org.openkilda.rulemanager.Constants.NOVIFLOW_TIMESTAMP_SIZE_IN_BITS;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_PRE_INGRESS_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.makeVlanReplaceActions;
import static org.openkilda.rulemanager.utils.Utils.mapMetadata;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSegmentCookieBuilder;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.action.noviflow.CopyFieldAction;
import org.openkilda.rulemanager.action.noviflow.OpenFlowOxms;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;
import org.openkilda.rulemanager.utils.RoutingMetadata.HaSubFlowType;
import org.openkilda.rulemanager.utils.RoutingMetadata.RoutingMetadataBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Builder.Default;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuperBuilder
@Slf4j
public class Server42IngressRuleGenerator implements RuleGenerator {

    /*
     * This set must contain FlowSideAdapters which have same SwitchId as ingress endpoint of target flowPath.
     */
    @Default
    protected final Set<FlowSideAdapter> overlappingIngressAdapters = new HashSet<>();
    protected RuleManagerConfig config;
    protected final FlowPath flowPath;
    protected final Flow flow;
    protected final FlowTransitEncapsulation encapsulation;
    protected final SwitchProperties switchProperties;
    protected final HaFlow haFlow;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();
        if (switchProperties == null || !switchProperties.isServer42FlowRtt()) {
            return result;
        }
        if (flowPath != null && flowPath.isOneSwitchPath()) {
            return result;
        }
        if (flow == null && haFlow == null) {
            throw new IllegalArgumentException("Flow and HaFlow are null");
        }

        FlowEndpoint ingressEndpoint = getIngressEndpoint(sw.getSwitchId());
        result.add(buildServer42IngressCommand(sw, ingressEndpoint, flowPath, null));
        if (needToBuildServer42PreIngressRule(ingressEndpoint)) {
            result.add(buildServer42PreIngressCommand(sw, ingressEndpoint));
        }
        if (needToBuildServer42InputRule(ingressEndpoint)) {
            result.add(buildServer42InputCommand(sw, ingressEndpoint.getPortNumber()));
        }
        return result;
    }

    @VisibleForTesting
    protected boolean needToBuildServer42PreIngressRule(FlowEndpoint ingressEndpoint) {
        if (!isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            // Full port flows do not need pre ingress shared rule
            return false;
        }
        for (FlowSideAdapter overlappingIngressAdapter : overlappingIngressAdapters) {
            if (overlappingIngressAdapter.getEndpoint().getOuterVlanId() == ingressEndpoint.getOuterVlanId()
                    && !overlappingIngressAdapter.isOneSwitchFlow()) {
                // some other flow already has shared pre ingress rule, so current flow don't need it
                return false;
            }
        }
        return true;
    }

    @VisibleForTesting
    protected boolean needToBuildServer42InputRule(FlowEndpoint ingressEndpoint) {
        for (FlowSideAdapter overlappingIngressAdapter : overlappingIngressAdapters) {
            if (overlappingIngressAdapter.getEndpoint().getPortNumber().equals(ingressEndpoint.getPortNumber())
                    && !overlappingIngressAdapter.isOneSwitchFlow()) {
                // some other flow already has shared input rule, so current flow don't need it
                return false;
            }
        }
        return true;
    }

    protected FlowSpeakerData buildServer42PreIngressCommand(Switch sw, FlowEndpoint endpoint) {
        FlowSharedSegmentCookie cookie = FlowSharedSegmentCookie.builder(SharedSegmentType.SERVER42_QINQ_OUTER_VLAN)
                .portNumber(switchProperties.getServer42Port())
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
                .priority(SERVER_42_PRE_INGRESS_FLOW_PRIORITY)
                .match(Sets.newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                        FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getOuterVlanId()).build()))
                .instructions(instructions);

        return builder.build();
    }

    private FlowSpeakerData buildServer42IngressDoubleVlanCommand(Switch sw, FlowEndpoint ingressEndpoint,
                                                                  FlowPath flowPath,
                                                                  HaSubFlowType haSubFlowType) {
        RoutingMetadata metadata = getRoutingMetadataBuilderBase(ingressEndpoint, haSubFlowType)
                .outerVlanId(ingressEndpoint.getOuterVlanId())
                .build(sw.getFeatures());

        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(ingressEndpoint.getInnerVlanId()).build());
        return buildServer42IngressCommand(sw, ingressEndpoint, match,
                SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY, flowPath);
    }

    private FlowSpeakerData buildServer42IngressSingleVlanCommand(Switch sw, FlowEndpoint ingressEndpoint,
                                                                  FlowPath flowPath,
                                                                  HaSubFlowType haSubFlowType) {
        RoutingMetadata metadata = getRoutingMetadataBuilderBase(ingressEndpoint, haSubFlowType)
                .outerVlanId(ingressEndpoint.getOuterVlanId())
                .build(sw.getFeatures());
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
        return buildServer42IngressCommand(sw, ingressEndpoint, match,
                SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY, flowPath);
    }

    private FlowSpeakerData buildServer42IngressFullPortCommand(Switch sw, FlowEndpoint ingressEndpoint,
                                                                FlowPath flowPath,
                                                                HaSubFlowType haSubFlowType) {
        RoutingMetadata metadata = getRoutingMetadataBuilderBase(ingressEndpoint, haSubFlowType)
                .build(sw.getFeatures());

        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
        return buildServer42IngressCommand(sw, ingressEndpoint, match,
                SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY, flowPath);
    }

    private FlowSpeakerData buildServer42IngressCommand(Switch sw, FlowEndpoint ingressEndpoint, Set<FieldMatch> match,
                                                        int priority, FlowPath flowPath) {
        FlowSegmentCookieBuilder cookieBuilder = new FlowSegmentCookie(flowPath.getCookie().getValue()).toBuilder()
                .type(CookieType.SERVER_42_FLOW_RTT_INGRESS);

        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookieBuilder.build())
                .table(OfTable.INGRESS)
                .priority(priority)
                .match(match)
                .instructions(buildIngressInstructions(sw, ingressEndpoint.getInnerVlanId(), flowPath));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    protected FlowSpeakerData buildServer42IngressCommand(Switch sw, FlowEndpoint ingressEndpoint, FlowPath flowPath,
                                                          HaSubFlowType haSubFlowType) {
        if (FlowEndpoint.isVlanIdSet(ingressEndpoint.getInnerVlanId())) {
            return buildServer42IngressDoubleVlanCommand(sw, ingressEndpoint, flowPath, haSubFlowType);
        } else if (FlowEndpoint.isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            return buildServer42IngressSingleVlanCommand(sw, ingressEndpoint, flowPath, haSubFlowType);
        } else {
            return buildServer42IngressFullPortCommand(sw, ingressEndpoint, flowPath, haSubFlowType);
        }
    }

    protected Instructions buildIngressInstructions(Switch sw, int vlan, FlowPath flowPath) {
        List<Action> applyActions = new ArrayList<>(buildTransformActions(vlan, sw.getFeatures(), flowPath));
        applyActions.add(new PortOutAction(getOutPort(flowPath)));
        return Instructions.builder()
                .applyActions(applyActions)
                .build();
    }

    @VisibleForTesting
    List<Action> buildTransformActions(int innerVlan, Set<SwitchFeature> features, FlowPath flowPath) {
        List<Action> actions = new ArrayList<>();
        List<Integer> currentStack = makeVlanStack(innerVlan);
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                actions.add(SetFieldAction.builder().field(Field.ETH_SRC)
                        .value(flowPath.getSrcSwitchId().toMacAddressAsLong()).build());
                actions.addAll(makeVlanReplaceActions(currentStack, makeVlanStack(encapsulation.getId())));
                break;
            case VXLAN:
                actions.addAll(makeVlanReplaceActions(currentStack, new ArrayList<>()));
                actions.add(buildPushVxlan(encapsulation.getId(), flowPath.getSrcSwitchId(),
                        flowPath.getDestSwitchId(), SERVER_42_FLOW_RTT_FORWARD_UDP_PORT, features));
                break;
            default:
                throw new IllegalArgumentException(format("Unknown transit encapsulation %s", encapsulation.getType()));
        }
        return actions;
    }

    private FlowSpeakerData buildServer42InputCommand(Switch sw, int inPort) {
        return buildServer42InputCommand(sw, inPort, null, null);
    }

    protected FlowSpeakerData buildServer42InputCommand(Switch sw, int inPort, FlowPath flowPath,
                                                        HaSubFlowType haSubFlowType) {

        int udpSrcPort = inPort + config.getServer42FlowRttUdpPortOffset();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(switchProperties.getServer42MacAddress().toLong())
                        .build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(udpSrcPort).build());


        List<Action> applyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build());
        if (sw.getFeatures().contains(NOVIFLOW_COPY_FIELD)) {
            applyActions.add(buildServer42CopyFirstTimestamp());
        }

        RoutingMetadataBuilder routingMetadataBuilder = RoutingMetadata.builder()
                .inputPort(inPort)
                .haSubFlowType(haSubFlowType);

        Instructions instructions = Instructions.builder()
                .applyActions(applyActions)
                .goToTable(OfTable.PRE_INGRESS)
                .writeMetadata(mapMetadata(routingMetadataBuilder.build(sw.getFeatures())))
                .build();

        PortColourCookie.PortColourCookieBuilder cookieBuilder = PortColourCookie.builder()
                .type(CookieType.SERVER_42_FLOW_RTT_INPUT)
                .portNumber(inPort);

        if (flowPath != null) {
            match.add(FieldMatch.builder().field(Field.ETH_DST).value(flowPath.getDestSwitchId().toMacAddressAsLong())
                    .build());
            cookieBuilder.subType(flowPath.getCookie().getFlowSubType());
        }

        return FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookieBuilder.build())
                .table(OfTable.INPUT)
                .priority(Priority.SERVER_42_FLOW_RTT_INPUT_PRIORITY)
                .match(match)
                .instructions(instructions)
                .build();
    }

    private CopyFieldAction buildServer42CopyFirstTimestamp() {
        return CopyFieldAction.builder()
                .numberOfBits(NOVIFLOW_TIMESTAMP_SIZE_IN_BITS)
                .srcOffset(0)
                .dstOffset(0)
                .oxmSrcHeader(OpenFlowOxms.NOVIFLOW_TX_TIMESTAMP)
                .oxmDstHeader(OpenFlowOxms.NOVIFLOW_UDP_PAYLOAD_OFFSET)
                .build();
    }

    protected FlowEndpoint getIngressEndpoint(SwitchId switchId) {
        FlowEndpoint ingressEndpoint;
        if (haFlow != null) {
            ingressEndpoint = FlowSideAdapter.makeIngressAdapter(haFlow, flowPath).getEndpoint();
        } else {
            ingressEndpoint = FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint();
        }
        if (!ingressEndpoint.getSwitchId().equals(switchId)) {
            throw new IllegalArgumentException(format("Path %s has ingress endpoint %s with switchId %s. But switchId "
                            + "must be equal to target switchId %s", flowPath.getPathId(), ingressEndpoint,
                    ingressEndpoint.getSwitchId(), switchId));
        }
        return ingressEndpoint;
    }

    private PortNumber getOutPort(FlowPath flowPath) {
        if (flowPath.getSegments().isEmpty()) {
            throw new IllegalStateException(format("Multi-switch flow path %s has no segments", flowPath.getPathId()));
        }
        return new PortNumber(flowPath.getSegments().get(0).getSrcPort());
    }

    private RoutingMetadataBuilder getRoutingMetadataBuilderBase(FlowEndpoint ingressEndpoint,
                                                                 HaSubFlowType haSubFlowType) {
        return RoutingMetadata.builder()
                .inputPort(ingressEndpoint.getPortNumber())
                .haSubFlowType(haSubFlowType);
    }

}
