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
import static org.openkilda.rulemanager.utils.Utils.getShortestSubPath;
import static org.openkilda.rulemanager.utils.Utils.makeVlanReplaceActions;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
import lombok.Builder.Default;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class YPointForwardIngressHaRuleGenerator implements MeteredRuleGenerator {

    /*
     * This set must contain FlowSideAdapters with src multiTable=true which have same SwitchId and inPort as ingress
     * endpoint of target subPath.
     */
    @Default
    private final Set<FlowSideAdapter> overlappingIngressAdapters = new HashSet<>();
    private RuleManagerConfig config;
    private final List<FlowPath> subPaths;
    private final HaFlow haFlow;
    private final HaFlowPath haFlowPath;
    private final FlowTransitEncapsulation encapsulation;


    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();
        FlowEndpoint ingressEndpoint = validateAndBuildIngressEndpoint(haFlow, subPaths, sw.getSwitchId());

        FlowSpeakerData ingressCommand = buildFlowIngressCommand(sw, ingressEndpoint);
        result.add(ingressCommand);
        if (needToBuildFlowPreIngressRule(ingressEndpoint)) {
            result.add(Utils.buildSharedFlowPreIngressCommand(sw, ingressEndpoint));
        }
        if (overlappingIngressAdapters.isEmpty()) {
            result.add(Utils.buildCustomerPortSharedCatchCommand(sw, ingressEndpoint));
        }

        SpeakerData meterCommand = buildMeter(subPaths.get(0), config, haFlowPath.getSharedPointMeterId(), sw);
        if (meterCommand != null) {
            result.add(meterCommand);
            ingressCommand.getDependsOn().add(meterCommand.getUuid());
        }

        SpeakerData groupCommand = buildGroup(sw);
        result.add(groupCommand);
        ingressCommand.getDependsOn().add(groupCommand.getUuid());

        return result;
    }

    private boolean needToBuildFlowPreIngressRule(FlowEndpoint ingressEndpoint) {
        if (!isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            // Full port flows do not need pre ingress shared rule
            return false;
        }
        for (FlowSideAdapter overlappingIngressAdapter : overlappingIngressAdapters) {
            if (overlappingIngressAdapter.getEndpoint().getOuterVlanId() == ingressEndpoint.getOuterVlanId()) {
                // some other flow already has shared rule, so current flow don't need it
                return false;
            }
        }
        return true;
    }

    private FlowSpeakerData buildFlowIngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        List<Action> actions = new ArrayList<>(buildPreGroupTransformActions(
                ingressEndpoint.getInnerVlanId(), sw));
        actions.add(new GroupAction(haFlowPath.getYPointGroupId()));

        return FlowSpeakerData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(haFlowPath.getCookie())
                .table(OfTable.INGRESS)
                .priority(getPriority(ingressEndpoint))
                .flags(buildRuleFlags(sw.getFeatures()))
                .match(buildIngressMatch(ingressEndpoint, sw.getFeatures()))
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
        addMeterToInstructions(haFlowPath.getSharedPointMeterId(), sw, instructions);
        return instructions;
    }

    @VisibleForTesting
    static Set<FieldMatch> buildIngressMatch(FlowEndpoint endpoint, Set<SwitchFeature> switchFeatures) {
        return Utils.makeIngressMatch(endpoint, true, switchFeatures);
    }

    @VisibleForTesting
    List<Action> buildPreGroupTransformActions(int currentInnerVLan, Switch sw) {
        List<Integer> currentStack = makeVlanStack(currentInnerVLan);
        List<Integer> targetStack = getTargetPreGroupVlanStack();
        List<Action> actions = makeVlanReplaceActions(currentStack, targetStack);

        if (encapsulation.getType() == VXLAN && !getShortestSubPath(subPaths).isOneSwitchPath()) {
            actions.add(buildPushVxlan(encapsulation.getId(), haFlow.getSharedSwitchId(),
                    subPaths.get(0).getDestSwitchId(), VXLAN_UDP_SRC, sw.getFeatures()));
        }
        return actions;
    }

    private List<Integer> getTargetPreGroupVlanStack() {
        FlowPath shortestSubPath = getShortestSubPath(subPaths);

        if (shortestSubPath.isOneSwitchPath()) {
            if (shortestSubPath.getHaSubFlow().getEndpointInnerVlan() != 0) {
                return makeVlanStack(shortestSubPath.getHaSubFlow().getEndpointInnerVlan());
            }
            if (encapsulation.getType() == VXLAN) {
                return new ArrayList<>();
            }
            if (haFlow.getSharedInnerVlan() == 0 && shortestSubPath.getHaSubFlow().getEndpointVlan() == 0) {
                return new ArrayList<>();
            }
            return makeVlanStack(encapsulation.getId());
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            return makeVlanStack(encapsulation.getId());
        } else {
            return new ArrayList<>();
        }
    }

    private Bucket buildBucket(FlowPath subPath, List<Integer> currentVlanStack, Set<SwitchFeature> features) {
        List<Integer> targetStack;
        if (subPath.isOneSwitchPath()) {
            targetStack = makeVlanStack(
                    subPath.getHaSubFlow().getEndpointInnerVlan(), subPath.getHaSubFlow().getEndpointVlan());
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            targetStack = makeVlanStack(encapsulation.getId());
        } else {
            targetStack = new ArrayList<>();
        }

        Set<Action> actions = new HashSet<>(makeVlanReplaceActions(currentVlanStack, targetStack));

        if (encapsulation.getType() == VXLAN && !subPath.isOneSwitchPath()) {
            if (getShortestSubPath(subPaths).isOneSwitchPath()) {
                actions.add(buildPushVxlan(encapsulation.getId(), subPath.getSrcSwitchId(),
                        subPath.getDestSwitchId(), VXLAN_UDP_SRC, features));
            } else {
                // VXLAN was pushed by flow apply actions
                actions.add(SetFieldAction.builder().field(Field.ETH_DST)
                        .value(subPath.getDestSwitchId().toMacAddressAsLong()).build());
            }
        }

        actions.add(new PortOutAction(getOutPort(subPath)));
        return Bucket.builder()
                .watchGroup(WatchGroup.ANY)
                .watchPort(WatchPort.ANY)
                .writeActions(actions)
                .build();
    }

    private GroupSpeakerData buildGroup(Switch sw) {
        List<Integer> preGroupVlanStack = getTargetPreGroupVlanStack();
        List<Bucket> buckets = new ArrayList<>();
        for (FlowPath subPath : subPaths) {
            buckets.add(buildBucket(subPath, preGroupVlanStack, sw.getFeatures()));
        }

        return GroupSpeakerData.builder()
                .groupId(haFlowPath.getYPointGroupId())
                .buckets(buckets)
                .type(GroupType.ALL)
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .build();

    }

    private PortNumber getOutPort(FlowPath subPath) {
        if (subPath.isOneSwitchPath()) {
            if (haFlow.getSharedPort() == subPath.getHaSubFlow().getEndpointPort()) {
                return new PortNumber(SpecialPortType.IN_PORT);
            } else {
                return new PortNumber(subPath.getHaSubFlow().getEndpointPort());
            }
        } else {
            return new PortNumber(subPath.getSegments().get(0).getSrcPort());
        }
    }

    private static FlowEndpoint validateAndBuildIngressEndpoint(
            HaFlow haFlow, List<FlowPath> subPaths, SwitchId targetSwitchId) {
        if (subPaths == null) {
            throw new IllegalArgumentException("Sub paths for y-point forward egress rule can't be null");
        }
        if (subPaths.isEmpty()) {
            throw new IllegalArgumentException("Sub paths for y-point forward egress rule can't be empty");
        }
        if (subPaths.size() != 2) {
            throw new IllegalArgumentException(
                    format("Sub path count must be equal to 2. But sub path count is %d", subPaths.size()));
        }
        if (!subPaths.stream().allMatch(FlowPath::isForward)) {
            throw new IllegalArgumentException("Sub paths must have forward direction");
        }
        for (FlowPath subPath : subPaths) {
            if (!targetSwitchId.equals(subPath.getSrcSwitchId())) {
                throw new IllegalArgumentException(
                        format("Sub path %s has source switchId %s. But switchId must be equal to target switchId %s",
                                subPath.getPathId(), subPath.getSrcSwitchId(), targetSwitchId));
            }
        }
        if (!targetSwitchId.equals(haFlow.getSharedSwitchId())) {
            throw new IllegalArgumentException(
                    format("HA flow %s has shared switchId %s. But switchId must be equal to target switchId %s",
                            haFlow.getHaFlowId(), haFlow.getSharedSwitchId(), targetSwitchId));

        }
        return new FlowEndpoint(haFlow.getSharedSwitchId(), haFlow.getSharedPort(),
                haFlow.getSharedOuterVlan(), haFlow.getSharedInnerVlan());
    }
}
