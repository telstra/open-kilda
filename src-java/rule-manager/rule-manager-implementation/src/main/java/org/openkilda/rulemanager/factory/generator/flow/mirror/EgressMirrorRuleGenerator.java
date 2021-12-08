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
import static org.openkilda.rulemanager.utils.Utils.buildMirrorBuckets;
import static org.openkilda.rulemanager.utils.Utils.checkAndBuildEgressEndpoint;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.GroupId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.GroupSpeakerCommandData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.GroupAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.generator.flow.EgressRuleGenerator;
import org.openkilda.rulemanager.group.Bucket;
import org.openkilda.rulemanager.group.GroupType;
import org.openkilda.rulemanager.group.WatchGroup;
import org.openkilda.rulemanager.group.WatchPort;

import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
public class EgressMirrorRuleGenerator extends EgressRuleGenerator {

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        List<SpeakerCommandData> result = new ArrayList<>();
        if (flowPath.isOneSwitchFlow() || flowPath.getSegments().isEmpty()) {
            return result;
        }

        FlowMirrorPoints mirrorPoints = flowPath.getFlowMirrorPointsSet().stream()
                .filter(points -> sw.getSwitchId().equals(points.getMirrorSwitchId()))
                .findFirst().orElse(null);
        if (mirrorPoints == null) {
            return result;
        }

        PathSegment lastSegment = flowPath.getSegments().get(flowPath.getSegments().size() - 1);
        FlowEndpoint egressEndpoint = checkAndBuildEgressEndpoint(flow, flowPath, sw.getSwitchId());
        SpeakerCommandData egressCommand = buildEgressCommand(sw, lastSegment.getDestPort(), egressEndpoint,
                mirrorPoints.getMirrorGroupId());
        result.add(egressCommand);

        SpeakerCommandData groupCommand = buildGroup(sw, mirrorPoints, egressEndpoint.getPortNumber());
        result.add(groupCommand);

        egressCommand.getDependsOn().add(groupCommand.getUuid());
        return result;
    }

    private SpeakerCommandData buildEgressCommand(Switch sw, int inPort, FlowEndpoint egressEndpoint, GroupId groupId) {
        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(flowPath.getDestSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie().toBuilder().mirror(true).build())
                .table(flowPath.isDestWithMultiTable() ? OfTable.EGRESS : OfTable.INPUT)
                .priority(Priority.MIRROR_FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(Instructions.builder()
                        .applyActions(buildApplyActions(egressEndpoint, sw, groupId))
                        .build());

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private List<Action> buildApplyActions(FlowEndpoint egressEndpoint, Switch sw, GroupId groupId) {
        List<Action> result = buildTransformActions(egressEndpoint, sw);
        result.add(new GroupAction(groupId));
        return result;
    }

    private Bucket buildFlowBucket(int flowOutPort) {
        return Bucket.builder()
                .watchGroup(WatchGroup.ANY)
                .watchPort(WatchPort.ANY)
                .writeActions(Sets.newHashSet(new PortOutAction(new PortNumber(flowOutPort))))
                .build();
    }

    private GroupSpeakerCommandData buildGroup(Switch sw, FlowMirrorPoints flowMirrorPoints, int flowOutPort) {
        List<Bucket> buckets = newArrayList(buildFlowBucket(flowOutPort));
        buckets.addAll(buildMirrorBuckets(flowMirrorPoints));

        return GroupSpeakerCommandData.builder()
                .groupId(flowMirrorPoints.getMirrorGroupId())
                .buckets(buckets)
                .type(GroupType.ALL)
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .build();

    }
}
