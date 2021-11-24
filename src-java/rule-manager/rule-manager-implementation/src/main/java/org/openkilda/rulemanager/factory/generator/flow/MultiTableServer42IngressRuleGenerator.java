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
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_PRE_INGRESS_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.getOutPort;
import static org.openkilda.rulemanager.utils.Utils.makeVlanReplaceActions;
import static org.openkilda.rulemanager.utils.Utils.mapMetadata;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie;
import org.openkilda.model.cookie.FlowSharedSegmentCookie.SharedSegmentType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Builder.Default;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class MultiTableServer42IngressRuleGenerator extends Server42IngressRuleGenerator {

    /*
     * This set must contain FlowSideAdapters with multiTable=true which have same SwitchId as ingress endpoint of
     * target flowPath.
     */
    @Default
    protected final Set<FlowSideAdapter> overlappingIngressAdapters = new HashSet<>();

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        List<SpeakerCommandData> result = new ArrayList<>();
        if (switchProperties == null || !switchProperties.isMultiTable() || !switchProperties.isServer42FlowRtt()
                || flowPath.isOneSwitchFlow()) {
            return result;
        }

        FlowEndpoint ingressEndpoint = getIngressEndpoint(sw.getSwitchId());
        result.add(buildServer42IngressCommand(sw, ingressEndpoint));
        if (needToBuildServer42PreIngressRule(ingressEndpoint)) {
            result.add(buildServer42PreIngressCommand(sw, ingressEndpoint));
        }
        if (needToBuildServer42InputRule(ingressEndpoint)) {
            result.add(buildServer42InputCommand(sw, ingressEndpoint.getPortNumber()));
        }
        return result;
    }

    @VisibleForTesting
    boolean needToBuildServer42PreIngressRule(FlowEndpoint ingressEndpoint) {
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
    boolean needToBuildServer42InputRule(FlowEndpoint ingressEndpoint) {
        for (FlowSideAdapter overlappingIngressAdapter : overlappingIngressAdapters) {
            if (overlappingIngressAdapter.getEndpoint().getPortNumber().equals(ingressEndpoint.getPortNumber())
                    && !overlappingIngressAdapter.isOneSwitchFlow()) {
                // some other flow already has shared input rule, so current flow don't need it
                return false;
            }
        }
        return true;
    }

    private FlowSpeakerCommandData buildServer42PreIngressCommand(Switch sw, FlowEndpoint endpoint) {
        FlowSharedSegmentCookie cookie = FlowSharedSegmentCookie.builder(SharedSegmentType.SERVER42_QINQ_OUTER_VLAN)
                .portNumber(switchProperties.getServer42Port())
                .vlanId(endpoint.getOuterVlanId())
                .build();

        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(endpoint.getOuterVlanId()).build();
        Instructions instructions = Instructions.builder()
                .applyActions(Lists.newArrayList(new PopVlanAction()))
                .writeMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()))
                .goToTable(OfTable.INGRESS)
                .build();

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
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

    private FlowSpeakerCommandData buildServer42IngressDoubleVlanCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        RoutingMetadata metadata = RoutingMetadata.builder()
                .inputPort(ingressEndpoint.getPortNumber())
                .outerVlanId(ingressEndpoint.getOuterVlanId())
                .build();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(ingressEndpoint.getInnerVlanId()).build());
        return buildServer42IngressCommand(sw, ingressEndpoint, match, SERVER_42_INGRESS_DOUBLE_VLAN_FLOW_PRIORITY);
    }

    private FlowSpeakerCommandData buildServer42IngressSingleVlanCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        RoutingMetadata metadata = RoutingMetadata.builder()
                .inputPort(ingressEndpoint.getPortNumber())
                .outerVlanId(ingressEndpoint.getOuterVlanId())
                .build();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
        return buildServer42IngressCommand(sw, ingressEndpoint, match, SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY);
    }

    private FlowSpeakerCommandData buildServer42IngressFullPortCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        RoutingMetadata metadata = RoutingMetadata.builder()
                .inputPort(ingressEndpoint.getPortNumber())
                .build();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
        return buildServer42IngressCommand(sw, ingressEndpoint, match, SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY);
    }

    private FlowSpeakerCommandData buildServer42IngressCommand(
            Switch sw, FlowEndpoint ingressEndpoint, Set<FieldMatch> match, int priority) {
        FlowSegmentCookie cookie = new FlowSegmentCookie(flowPath.getCookie().getValue()).toBuilder()
                .type(CookieType.SERVER_42_FLOW_RTT_INGRESS)
                .build();
        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.INGRESS)
                .priority(priority)
                .match(match)
                .instructions(buildIngressInstructions(sw, ingressEndpoint.getInnerVlanId()));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private FlowSpeakerCommandData buildServer42IngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        if (FlowEndpoint.isVlanIdSet(ingressEndpoint.getInnerVlanId())) {
            return buildServer42IngressDoubleVlanCommand(sw, ingressEndpoint);
        } else if (FlowEndpoint.isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            return buildServer42IngressSingleVlanCommand(sw, ingressEndpoint);
        } else {
            return buildServer42IngressFullPortCommand(sw, ingressEndpoint);
        }
    }

    private Instructions buildIngressInstructions(Switch sw, int innerVlan) {
        List<Action> applyActions = new ArrayList<>(buildTransformActions(innerVlan, sw.getFeatures()));
        applyActions.add(new PortOutAction(new PortNumber(getOutPort(flowPath, flow))));
        return Instructions.builder()
                .applyActions(applyActions)
                .build();
    }

    @VisibleForTesting
    List<Action> buildTransformActions(int innerVlan, Set<SwitchFeature> features) {
        List<Action> actions = new ArrayList<>();
        List<Integer> currentStack = makeVlanStack(innerVlan);
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                actions.add(SetFieldAction.builder().field(Field.ETH_SRC)
                        .value(flowPath.getSrcSwitchId().toMacAddressAsLong()).build());
                actions.add(SetFieldAction.builder().field(Field.ETH_DST)
                        .value(flowPath.getDestSwitchId().toMacAddressAsLong()).build());
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

    private FlowSpeakerCommandData buildServer42InputCommand(Switch sw, int inPort) {
        int udpSrcPort = inPort + config.getServer42FlowRttUdpPortOffset();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(switchProperties.getServer42MacAddress().toLong())
                        .build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(udpSrcPort).build());

        PortColourCookie cookie = new PortColourCookie(CookieType.SERVER_42_FLOW_RTT_INPUT, inPort);

        List<Action> applyActions = Lists.newArrayList(
                SetFieldAction.builder().field(Field.UDP_SRC).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build(),
                SetFieldAction.builder().field(Field.UDP_DST).value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build());
        if (sw.getFeatures().contains(NOVIFLOW_COPY_FIELD)) {
            applyActions.add(buildServer42CopyFirstTimestamp());
        }

        Instructions instructions = Instructions.builder()
                .applyActions(applyActions)
                .goToTable(OfTable.PRE_INGRESS)
                .writeMetadata(mapMetadata(RoutingMetadata.builder().inputPort(inPort).build()))
                .build();

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.INPUT)
                .priority(Priority.SERVER_42_FLOW_RTT_INPUT_PRIORITY)
                .match(match)
                .instructions(instructions);

        return builder.build();
    }
}
