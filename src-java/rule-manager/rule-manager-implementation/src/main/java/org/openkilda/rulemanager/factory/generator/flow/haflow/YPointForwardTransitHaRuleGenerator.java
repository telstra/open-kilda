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
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.utils.Utils.buildPopVxlan;
import static org.openkilda.rulemanager.utils.Utils.getShortestSubPath;
import static org.openkilda.rulemanager.utils.Utils.makeVlanReplaceActions;

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
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
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.factory.generator.flow.NotIngressRuleGenerator;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;
import org.openkilda.rulemanager.utils.Utils;

import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuperBuilder
public class YPointForwardTransitHaRuleGenerator extends NotIngressRuleGenerator {

    private final List<FlowPath> subPaths;
    private final int inPort;
    private final Map<PathId, Integer> outPorts;
    private final HaFlowPath haFlowPath;
    private final FlowTransitEncapsulation encapsulation;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        validateSubPaths(sw);

        List<SpeakerData> result = new ArrayList<>();

        FlowSpeakerData transitCommand = buildTransitCommand(sw);
        result.add(transitCommand);

        SpeakerData groupCommand = buildGroup(sw);
        result.add(groupCommand);
        transitCommand.getDependsOn().add(groupCommand.getUuid());

        return result;
    }

    private FlowSpeakerData buildTransitCommand(Switch sw) {
        List<Action> actions = new ArrayList<>(buildPreGroupTransformActions(sw));
        actions.add(new GroupAction(haFlowPath.getYPointGroupId()));

        return FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(haFlowPath.getCookie())
                .table(OfTable.TRANSIT)
                .priority(Priority.FLOW_PRIORITY)
                .flags(Utils.buildRuleFlags(sw.getFeatures()))
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(Instructions.builder().applyActions(actions).build())
                .build();
    }

    private List<Action> buildPreGroupTransformActions(Switch sw) {
        FlowPath shortestSubPath = getShortestSubPath(subPaths);
        if (!shortestSubPath.getDestSwitchId().equals(sw.getSwitchId())) {
            return new ArrayList<>();
        }

        List<Action> actions = new ArrayList<>();
        List<Integer> currenVlanStack;

        if (TRANSIT_VLAN.equals(encapsulation.getType())) {
            currenVlanStack = makeVlanStack(encapsulation.getId());
        } else if (VXLAN.equals(encapsulation.getType())) {
            currenVlanStack = new ArrayList<>();
            if (shortestSubPath.getHaSubFlow().getEndpointInnerVlan() != 0) {
                actions.add(Utils.buildPopVxlan(sw.getSwitchId(), sw.getFeatures()));
            }
        } else {
            throw new IllegalArgumentException(format("Unknown encapsulation type %s", encapsulation.getType()));
        }

        actions.addAll(makeVlanReplaceActions(currenVlanStack, getTargetPreGroupVlanStack(sw.getSwitchId())));
        return actions;

    }

    private Bucket buildBucket(FlowPath subPath, List<Integer> currentVlanStack, Switch sw) {
        Set<Action> actions = new HashSet<>();
        if (encapsulation.getType() == VXLAN) {
            if (subPath.getDestSwitchId().equals(sw.getSwitchId())) {
                if (subPath.getHaSubFlow().getEndpointInnerVlan() == 0) {
                    actions.add(buildPopVxlan(sw.getSwitchId(), sw.getFeatures()));
                }
            } else {
                FlowPath otherSubPath = getOtherSubPath(subPath.getPathId());
                if (otherSubPath.getDestSwitchId().equals(sw.getSwitchId())
                        && otherSubPath.getHaSubFlow().getEndpointInnerVlan() != 0) {
                    actions.add(Utils.buildPushVxlan(encapsulation.getId(), subPath.getSrcSwitchId(),
                            subPath.getDestSwitchId(), VXLAN_UDP_SRC, sw.getFeatures()));
                } else {
                    actions.add(SetFieldAction.builder()
                            .field(Field.ETH_DST).value(subPath.getDestSwitchId().toMacAddressAsLong()).build());
                }
            }
        }

        List<Integer> targetStack;
        if (subPath.getDestSwitchId().equals(sw.getSwitchId())) {
            targetStack = makeVlanStack(
                    subPath.getHaSubFlow().getEndpointInnerVlan(), subPath.getHaSubFlow().getEndpointVlan());
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            targetStack = makeVlanStack(encapsulation.getId());
        } else {
            targetStack = new ArrayList<>();
        }

        actions.addAll(makeVlanReplaceActions(currentVlanStack, targetStack));
        actions.add(new PortOutAction(getOutPort(subPath.getPathId())));
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
            buckets.add(buildBucket(subPath, preGroupVlanStack, sw));
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
        FlowPath shortestSubPath = getShortestSubPath(subPaths);
        if (shortestSubPath.getDestSwitchId().equals(switchId)
                && shortestSubPath.getHaSubFlow().getEndpointInnerVlan() != 0) {
            return makeVlanStack(shortestSubPath.getHaSubFlow().getEndpointInnerVlan());
        }
        if (encapsulation.getType() == TRANSIT_VLAN) {
            return makeVlanStack(encapsulation.getId());
        } else {
            return new ArrayList<>();
        }
    }

    private PortNumber getOutPort(PathId subPathId) {
        Integer outPort = outPorts.get(subPathId);
        if (outPort == null) {
            throw new IllegalArgumentException(format("Couldn't find out port for ha-sub path %s", subPathId));
        }
        return new PortNumber(outPort);
    }

    private void validateSubPaths(Switch sw) {
        if (subPaths == null) {
            throw new IllegalArgumentException("Sub paths for y-point forward egress rule can't be null");
        }
        if (subPaths.isEmpty()) {
            throw new IllegalArgumentException("Sub paths for y-point forward egress rule can't be empty");
        }
        if (subPaths.stream().allMatch(subPath -> subPath.getDestSwitchId().equals(sw.getSwitchId()))) {
            throw new IllegalArgumentException(format("Ha-sub paths %s have equal destination switch ids %s. "
                            + "To build rules you need to use generator for Y point forward egress rules.", subPaths,
                    subPaths.stream().map(FlowPath::getDestSwitchId).collect(Collectors.toList())));
        }
        if (!subPaths.stream().allMatch(FlowPath::isForward)) {
            throw new IllegalArgumentException(format("All sub paths %s must have forward direction", subPaths));
        }
        if (subPaths.stream().anyMatch(FlowPath::isOneSwitchPath)) {
            throw new IllegalArgumentException(format("All sub paths %s must be multi switch paths", subPaths));
        }
    }
    
    private FlowPath getOtherSubPath(PathId pathId) {
        for (FlowPath subPath : subPaths) {
            if (!subPath.getPathId().equals(pathId)) {
                return subPath;
            }
        }
        throw new IllegalArgumentException(format("Path %s not found", pathId));
    }
}
