/* Copyright 2023 Telstra Open Source
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

package org.openkilda.rulemanager.factory.generator.flow.haflow;

import static java.lang.String.format;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.buildRuleFlags;
import static org.openkilda.rulemanager.utils.Utils.makeIngressMatch;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSubType;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
import lombok.Builder.Default;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuperBuilder
public class IngressHaRuleGenerator implements MeteredRuleGenerator {

    /*
     * This set must contain FlowSideAdapters with src multiTable=true which have same SwitchId and inPort as ingress
     * endpoint of target subPath.
     */
    @Default
    private final Set<FlowSideAdapter> overlappingIngressAdapters = new HashSet<>();
    private final FlowPath subPath;
    private final HaFlow haFlow;
    private final boolean isSharedPath;
    private final MeterId meterId;
    private final FlowTransitEncapsulation encapsulation;
    private final UUID externalMeterCommandUuid;
    private final boolean generateCreateMeterCommand;
    private final RuleManagerConfig config;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (subPath.isForward() && subPath.isOneSwitchPath()) {
            throw new IllegalArgumentException(format("Ha-sub path %s is one switch path and has forward direction. "
                            + "To build rules you need to use generator for Y point forward ingress rules.",
                    subPath.getPathId()));
        }
        List<SpeakerData> result = new ArrayList<>();
        FlowEndpoint ingressEndpoint = checkAndBuildIngressEndpoint(haFlow, subPath, sw.getSwitchId());
        FlowSpeakerData ingressCommand = buildFlowIngressCommand(sw, ingressEndpoint);
        result.add(ingressCommand);
        if (needToBuildFlowPreIngressRule(ingressEndpoint)) {
            result.add(Utils.buildSharedFlowPreIngressCommand(sw, ingressEndpoint));
        }
        if (overlappingIngressAdapters.isEmpty()) {
            result.add(Utils.buildCustomerPortSharedCatchCommand(sw, ingressEndpoint));
        }

        buildMeterCommandAndAddDependency(meterId, subPath.getBandwidth(), ingressCommand, externalMeterCommandUuid,
                config, generateCreateMeterCommand, sw)
                .ifPresent(result::add);
        return result;
    }

    private boolean needToBuildFlowPreIngressRule(FlowEndpoint ingressEndpoint) {
        if (!isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            // Full port flows do not need pre ingress shared rule
            return false;
        }
        for (FlowSideAdapter overlappingIngressAdapters : overlappingIngressAdapters) {
            if (overlappingIngressAdapters.getEndpoint().getOuterVlanId() == ingressEndpoint.getOuterVlanId()) {
                // some other flow already has shared rule, so current flow don't need it
                return false;
            }
        }
        return true;
    }

    private FlowSpeakerData buildFlowIngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        List<Action> actions = new ArrayList<>(buildTransformActions(
                ingressEndpoint.getInnerVlanId(), sw.getFeatures()));
        actions.add(new PortOutAction(getOutPort(subPath, haFlow)));

        return FlowSpeakerData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(getCookie())
                .table(OfTable.INGRESS)
                .priority(getPriority(ingressEndpoint))
                .flags(buildRuleFlags(sw.getFeatures()))
                .match(makeIngressMatch(ingressEndpoint, true, sw.getFeatures()))
                .instructions(buildInstructions(sw, actions))
                .build();
    }

    private int getPriority(FlowEndpoint ingressEndpoint) {
        if (isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            if (isVlanIdSet(ingressEndpoint.getInnerVlanId())) {
                return Priority.DOUBLE_VLAN_FLOW_PRIORITY;
            } else {
                return Priority.FLOW_PRIORITY;
            }
        } else {
            return Priority.DEFAULT_FLOW_PRIORITY;
        }
    }

    private Instructions buildInstructions(Switch sw, List<Action> actions) {
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        addMeterToInstructions(meterId, sw, instructions);
        return instructions;
    }

    @VisibleForTesting
    List<Action> buildTransformActions(int innerVlan, Set<SwitchFeature> features) {
        List<Integer> currentStack = makeVlanStack(innerVlan);
        List<Integer> targetStack;
        if (subPath.isOneSwitchPath()) {
            targetStack = FlowSideAdapter.makeEgressAdapter(haFlow, subPath).getEndpoint().getVlanStack();
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            targetStack = makeVlanStack(encapsulation.getId());
        } else {
            targetStack = new ArrayList<>();
        }

        List<Action> transformActions = new ArrayList<>(Utils.makeVlanReplaceActions(currentStack, targetStack));

        if (encapsulation.getType() == VXLAN && !subPath.isOneSwitchPath()) {
            transformActions.add(buildPushVxlan(encapsulation.getId(), subPath.getSrcSwitchId(),
                    subPath.getDestSwitchId(), VXLAN_UDP_SRC, features));
        }
        return transformActions;
    }

    private static FlowEndpoint checkAndBuildIngressEndpoint(
            HaFlow haFlow, FlowPath subPath, SwitchId switchId) {
        FlowEndpoint ingressEndpoint = FlowSideAdapter.makeIngressAdapter(haFlow, subPath).getEndpoint();

        if (!switchId.equals(ingressEndpoint.getSwitchId())) {
            throw new IllegalArgumentException(format("Ha-sub path path %s of ha-flow %s has ingress endpoint %s with "
                            + "switchId %s. But switchId must be equal to target switchId %s",
                    subPath.getPathId(), haFlow.getHaFlowId(), ingressEndpoint, ingressEndpoint.getSwitchId(),
                    switchId));
        }
        return ingressEndpoint;
    }

    private static PortNumber getOutPort(FlowPath path, HaFlow haFlow) {
        if (path.isOneSwitchPath()) {
            if (path.getHaSubFlow().getEndpointPort() == haFlow.getSharedPort()) {
                // the case of a single switch & same port flow.
                return new PortNumber(SpecialPortType.IN_PORT);
            } else {
                return new PortNumber(haFlow.getSharedPort());
            }
        } else {
            if (path.getSegments().isEmpty()) {
                throw new IllegalStateException(format("Multi switch flow path %s has no segments", path.getPathId()));
            }
            return new PortNumber(path.getSegments().get(0).getSrcPort());
        }
    }

    private FlowSegmentCookie getCookie() {
        if (isSharedPath) {
            return subPath.getCookie().toBuilder().subType(FlowSubType.SHARED).build();
        } else {
            return subPath.getCookie();
        }
    }
}
