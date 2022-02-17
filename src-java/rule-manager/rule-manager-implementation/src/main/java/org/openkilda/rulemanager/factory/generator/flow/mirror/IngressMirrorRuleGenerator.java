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

package org.openkilda.rulemanager.factory.generator.flow.mirror;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.utils.Utils.buildMirrorBuckets;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.checkAndBuildIngressEndpoint;
import static org.openkilda.rulemanager.utils.Utils.getOutPort;
import static org.openkilda.rulemanager.utils.Utils.makeIngressMatch;
import static org.openkilda.rulemanager.utils.Utils.makeVlanReplaceActions;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.GroupId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.generator.flow.IngressRuleGenerator;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuperBuilder
public class IngressMirrorRuleGenerator extends IngressRuleGenerator {
    private boolean multiTable;
    private UUID sharedMeterCommandUuid;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();

        FlowMirrorPoints mirrorPoints = flowPath.getFlowMirrorPointsSet().stream()
                .filter(points -> sw.getSwitchId().equals(points.getMirrorSwitchId()))
                .findFirst().orElse(null);
        if (mirrorPoints == null) {
            return result;
        }

        FlowEndpoint ingressEndpoint = checkAndBuildIngressEndpoint(flow, flowPath, sw.getSwitchId());
        FlowSpeakerData ingressCommand = buildFlowIngressCommand(
                sw, ingressEndpoint, mirrorPoints.getMirrorGroupId());
        result.add(ingressCommand);

        SpeakerData groupCommand = buildGroup(sw, mirrorPoints);
        result.add(groupCommand);

        ingressCommand.getDependsOn().add(groupCommand.getUuid());
        if (sharedMeterCommandUuid != null) {
            ingressCommand.getDependsOn().add(sharedMeterCommandUuid);
        }
        return result;
    }

    private FlowSpeakerData buildFlowIngressCommand(Switch sw, FlowEndpoint ingressEndpoint, GroupId groupId) {
        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie().toBuilder().mirror(true).build())
                .table(multiTable ? OfTable.INGRESS : OfTable.INPUT)
                .priority(getPriority(ingressEndpoint))
                .match(makeIngressMatch(ingressEndpoint, multiTable, sw.getFeatures()))
                .instructions(buildIngressInstructions(sw, buildIngressActions(ingressEndpoint, groupId)));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private int getPriority(FlowEndpoint ingressEndpoint) {
        if (isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            if (isVlanIdSet(ingressEndpoint.getInnerVlanId())) {
                return Priority.MIRROR_DOUBLE_VLAN_FLOW_PRIORITY;
            } else {
                return Priority.MIRROR_FLOW_PRIORITY;
            }
        } else {
            return Priority.MIRROR_DEFAULT_FLOW_PRIORITY;
        }
    }

    private Instructions buildIngressInstructions(Switch sw, List<Action> actions) {
        Instructions instructions = Instructions.builder().applyActions(actions).build();
        addMeterToInstructions(flowPath.getMeterId(), sw, instructions);
        return instructions;
    }

    @VisibleForTesting
    List<Action> buildIngressActions(FlowEndpoint ingressEndpoint, GroupId groupId) {
        List<Integer> currentStack;
        if (multiTable) {
            currentStack = makeVlanStack(ingressEndpoint.getInnerVlanId());
        } else {
            currentStack = makeVlanStack(ingressEndpoint.getOuterVlanId());
        }

        List<Integer> targetStack;
        if (flowPath.isOneSwitchFlow()) {
            targetStack = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint().getVlanStack();
        } else {
            targetStack = new ArrayList<>();
        }

        List<Action> actions = new ArrayList<>(Utils.makeVlanReplaceActions(currentStack, targetStack));
        actions.add(new GroupAction(groupId));
        return actions;
    }

    private Bucket buildFlowBucket(Set<SwitchFeature> features) {
        Set<Action> actions = new HashSet<>();

        // for one switch flow all vlan manipulations were made by ingress rule, not group.
        if (!flowPath.isOneSwitchFlow()) {
            switch (encapsulation.getType()) {
                case TRANSIT_VLAN:
                    actions.addAll(makeVlanReplaceActions(new ArrayList<>(), makeVlanStack(encapsulation.getId())));
                    break;
                case VXLAN:
                    actions.add(buildPushVxlan(encapsulation.getId(), flowPath.getSrcSwitchId(),
                            flowPath.getDestSwitchId(), VXLAN_UDP_SRC, features));
                    break;
                default:
                    throw new IllegalArgumentException(
                            format("Unknown encapsulation type %s", encapsulation.getType()));
            }
        }

        actions.add(new PortOutAction(getOutPort(flowPath, flow)));

        return Bucket.builder()
                .watchGroup(WatchGroup.ANY)
                .watchPort(WatchPort.ANY)
                .writeActions(actions)
                .build();
    }

    private GroupSpeakerData buildGroup(Switch sw, FlowMirrorPoints flowMirrorPoints) {
        List<Bucket> buckets = newArrayList(buildFlowBucket(sw.getFeatures()));
        buckets.addAll(buildMirrorBuckets(flowMirrorPoints));
        return GroupSpeakerData.builder()
                .groupId(flowMirrorPoints.getMirrorGroupId())
                .buckets(buckets)
                .type(GroupType.ALL)
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .build();

    }
}
