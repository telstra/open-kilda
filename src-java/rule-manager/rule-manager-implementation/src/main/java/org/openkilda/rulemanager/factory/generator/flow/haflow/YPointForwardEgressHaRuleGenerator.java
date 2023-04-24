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
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.rulemanager.utils.Utils.buildRuleFlags;
import static org.openkilda.rulemanager.utils.Utils.makeVlanReplaceActions;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.generator.flow.NotIngressRuleGenerator;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.collect.Lists;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
public class YPointForwardEgressHaRuleGenerator extends NotIngressRuleGenerator {

    private final List<FlowPath> subPaths;
    private final int inPort;
    private final HaFlowPath haFlowPath;
    private final FlowTransitEncapsulation encapsulation;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        validateSubPaths(sw);

        List<SpeakerData> result = new ArrayList<>();

        FlowSpeakerData egressCommand = buildEgressCommand(sw);
        result.add(egressCommand);

        SpeakerData groupCommand = buildGroup(sw);
        result.add(groupCommand);
        egressCommand.getDependsOn().add(groupCommand.getUuid());

        return result;
    }

    private FlowSpeakerData buildEgressCommand(Switch sw) {
        List<Action> actions = new ArrayList<>(buildPreGroupTransformActions(sw));
        actions.add(new GroupAction(haFlowPath.getYPointGroupId()));

        return FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(haFlowPath.getCookie())
                .table(OfTable.EGRESS)
                .priority(Priority.FLOW_PRIORITY)
                .flags(buildRuleFlags(sw.getFeatures()))
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(Instructions.builder().applyActions(actions).build())
                .build();
    }

    private List<Action> buildPreGroupTransformActions(Switch sw) {
        List<Action> actions = new ArrayList<>();
        List<Integer> currenVlanStack;

        if (TRANSIT_VLAN.equals(encapsulation.getType())) {
            currenVlanStack = makeVlanStack(encapsulation.getId());
        } else if (VXLAN.equals(encapsulation.getType())) {
            currenVlanStack = new ArrayList<>();
            actions.add(Utils.buildPopVxlan(sw.getSwitchId(), sw.getFeatures()));
        } else {
            throw new IllegalArgumentException(format("Unknown encapsulation type %s", encapsulation.getType()));
        }

        actions.addAll(makeVlanReplaceActions(currenVlanStack, getTargetPreGroupVlanStack(sw.getSwitchId())));
        return actions;
    }

    private Bucket buildBucket(FlowPath subPath, List<Integer> currentVlanStack) {
        List<Integer> targetStack = makeVlanStack(
                subPath.getHaSubFlow().getEndpointInnerVlan(), subPath.getHaSubFlow().getEndpointVlan());
        Set<Action> actions = new HashSet<>(makeVlanReplaceActions(currentVlanStack, targetStack));
        actions.add(new PortOutAction(new PortNumber(subPath.getHaSubFlow().getEndpointPort())));
        return Bucket.builder()
                .watchGroup(WatchGroup.ANY)
                .watchPort(WatchPort.ANY)
                .writeActions(actions)
                .build();
    }

    private GroupSpeakerData buildGroup(Switch sw) {
        List<Integer> preGroupVlanStack = getTargetPreGroupVlanStack(sw.getSwitchId());

        List<Bucket> buckets = new ArrayList<>();
        for (FlowPath subPath : subPaths) {
            buckets.add(buildBucket(subPath, preGroupVlanStack));
        }

        return GroupSpeakerData.builder()
                .groupId(haFlowPath.getYPointGroupId())
                .buckets(buckets)
                .type(GroupType.ALL)
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .build();
    }

    private List<Integer> getTargetPreGroupVlanStack(SwitchId switchId) {
        List<Integer> endpointVlanStack1 = makeDstVlanStack(subPaths.get(0));
        List<Integer> endpointVlanStack2 = makeDstVlanStack(subPaths.get(1));

        if (endpointVlanStack1.size() == 2 && endpointVlanStack2.size() == 2
                && !Objects.equals(endpointVlanStack1.get(0), endpointVlanStack2.get(0))) {
            throw new IllegalArgumentException(format("To have ability to use double vlan tagging for both sub flow "
                            + "destination endpoints which are placed on one switch %s you must set equal inner vlan "
                            + "for both endpoints. Current inner vlans: SubFlow %s, inner vlan %d. SubFlow %s, inner "
                            + "vlan %d", switchId,
                    subPaths.get(0).getHaSubFlowId(), endpointVlanStack1.get(0),
                    subPaths.get(1).getHaSubFlowId(), endpointVlanStack2.get(0)));
        }

        List<Integer> targetVlanStack = new ArrayList<>();

        for (List<Integer> endpointStack : Lists.newArrayList(endpointVlanStack1, endpointVlanStack2)) {
            if (endpointStack.size() > 1) {
                targetVlanStack.add(endpointStack.get(0));
                break;
            }
        }
        if (targetVlanStack.isEmpty() && encapsulation.getType() == TRANSIT_VLAN) {
            for (List<Integer> endpointStack : Lists.newArrayList(endpointVlanStack1, endpointVlanStack2)) {
                if (endpointStack.size() > 0) {
                    targetVlanStack.add(encapsulation.getId());
                    break;
                }
            }
        }
        return targetVlanStack;
    }

    private List<Integer> makeDstVlanStack(FlowPath subPath) {
        return makeVlanStack(subPath.getHaSubFlow().getEndpointInnerVlan(), subPath.getHaSubFlow().getEndpointVlan());
    }

    private void validateSubPaths(Switch sw) {
        if (subPaths == null) {
            throw new IllegalArgumentException("Sub paths for y-point forward egress rule can't be null");
        }
        if (subPaths.size() != 2) {
            throw new IllegalArgumentException(format("Y point forward egress rule require 2 sub paths but only %d "
                    + "were provided %s", subPaths.size(), subPaths));
        }
        if (!subPaths.stream().allMatch(subPath -> subPath.getDestSwitchId().equals(sw.getSwitchId()))) {
            throw new IllegalArgumentException(format("Ha-sub paths %s have different destination switch ids %s. "
                            + "To build rules you need to use generator for Y point forward transit rules.", subPaths,
                    subPaths.stream().map(FlowPath::getDestSwitchId).collect(Collectors.toList())));
        }
        if (!subPaths.stream().allMatch(FlowPath::isForward)) {
            throw new IllegalArgumentException(format("All sub paths %s must have forward direction", subPaths));
        }
        if (subPaths.stream().anyMatch(FlowPath::isOneSwitchPath)) {
            throw new IllegalArgumentException(format("All sub paths %s must be multi switch paths", subPaths));
        }
    }
}
