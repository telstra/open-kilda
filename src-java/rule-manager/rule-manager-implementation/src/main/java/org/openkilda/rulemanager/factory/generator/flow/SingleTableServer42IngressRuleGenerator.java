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
import static org.openkilda.rulemanager.Constants.Priority.SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY;
import static org.openkilda.rulemanager.Constants.SERVER_42_FLOW_RTT_FORWARD_UDP_PORT;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.getOutPort;
import static org.openkilda.rulemanager.utils.Utils.makeVlanReplaceActions;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.ProtoConstants.IpProto;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class SingleTableServer42IngressRuleGenerator extends Server42IngressRuleGenerator {

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        if (switchProperties == null || switchProperties.isMultiTable() || !switchProperties.isServer42FlowRtt()
                || flowPath.isOneSwitchFlow()) {
            return new ArrayList<>();
        }

        FlowEndpoint ingressEndpoint = getIngressEndpoint(sw.getSwitchId());
        return Lists.newArrayList(buildServer42IngressCommand(sw, ingressEndpoint));
    }

    private FlowSpeakerCommandData buildServer42IngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        int priority = isVlanIdSet(ingressEndpoint.getOuterVlanId()) ? SERVER_42_INGRESS_SINGLE_VLAN_FLOW_PRIORITY
                : SERVER_42_INGRESS_DEFAULT_FLOW_PRIORITY;

        FlowSegmentCookie cookie = new FlowSegmentCookie(flowPath.getCookie().getValue()).toBuilder()
                .type(CookieType.SERVER_42_FLOW_RTT_INGRESS)
                .build();
        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(cookie)
                .table(OfTable.INPUT)
                .priority(priority)
                .match(buildMatch(ingressEndpoint))
                .instructions(buildIngressInstructions(sw, ingressEndpoint.getOuterVlanId()));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private Set<FieldMatch> buildMatch(FlowEndpoint ingressEndpoint) {
        int udpSrcPort = ingressEndpoint.getPortNumber() + config.getServer42FlowRttUdpPortOffset();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(switchProperties.getServer42Port()).build(),
                FieldMatch.builder().field(Field.ETH_SRC).value(switchProperties.getServer42MacAddress().toLong())
                        .build(),
                FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.IPv4).build(),
                FieldMatch.builder().field(Field.IP_PROTO).value(IpProto.UDP).build(),
                FieldMatch.builder().field(Field.UDP_SRC).value(udpSrcPort).build());

        if (isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            match.add(FieldMatch.builder().field(Field.VLAN_VID).value(ingressEndpoint.getOuterVlanId()).build());
        }
        return match;
    }

    private Instructions buildIngressInstructions(Switch sw, int outerVlan) {
        List<Action> applyActions = new ArrayList<>(buildTransformActions(outerVlan, sw.getFeatures()));
        applyActions.add(new PortOutAction(new PortNumber(getOutPort(flowPath, flow))));
        return Instructions.builder()
                .applyActions(applyActions)
                .build();
    }

    @VisibleForTesting
    List<Action> buildTransformActions(int outerVlan, Set<SwitchFeature> features) {
        List<Action> actions = new ArrayList<>();
        List<Integer> currentStack = makeVlanStack(outerVlan);
        switch (encapsulation.getType()) {
            case TRANSIT_VLAN:
                actions.add(SetFieldAction.builder().field(Field.ETH_SRC)
                        .value(flowPath.getSrcSwitchId().toMacAddressAsLong()).build());
                actions.add(SetFieldAction.builder().field(Field.ETH_DST)
                        .value(flowPath.getDestSwitchId().toMacAddressAsLong()).build());
                actions.add(SetFieldAction.builder().field(Field.UDP_SRC)
                        .value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build());
                actions.add(SetFieldAction.builder().field(Field.UDP_DST)
                        .value(SERVER_42_FLOW_RTT_FORWARD_UDP_PORT).build());
                if (features.contains(NOVIFLOW_COPY_FIELD)) {
                    actions.add(buildServer42CopyFirstTimestamp());
                }
                actions.addAll(makeVlanReplaceActions(currentStack, makeVlanStack(encapsulation.getId())));
                break;
            case VXLAN:
                actions.addAll(makeVlanReplaceActions(currentStack, new ArrayList<>()));
                if (features.contains(NOVIFLOW_COPY_FIELD)) {
                    actions.add(buildServer42CopyFirstTimestamp());
                }
                actions.add(buildPushVxlan(encapsulation.getId(), flowPath.getSrcSwitchId(),
                        flowPath.getDestSwitchId(), SERVER_42_FLOW_RTT_FORWARD_UDP_PORT, features));
                break;
            default:
                throw new IllegalArgumentException(format("Unknown transit encapsulation %s", encapsulation.getType()));
        }
        return actions;
    }
}
