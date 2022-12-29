/* Copyright 2022 Telstra Open Source
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

import static org.openkilda.rulemanager.utils.Utils.checkAndBuildEgressEndpoint;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.generator.flow.EgressBaseRuleGenerator;

import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
public class EgressSinkRuleGenerator extends EgressBaseRuleGenerator {
    FlowMirror flowMirror;
    FlowMirrorPath mirrorPath;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();
        if (mirrorPath.isSingleSwitchPath() || mirrorPath.getSegments().isEmpty() || mirrorPath.isDummy()) {
            return result;
        }

        if (!mirrorPath.isForward()) {
            throw new IllegalArgumentException(String.format("Invalid direction of mirror path %s. Only forward mirror "
                    + "paths can have egress sink rules.", mirrorPath.getMirrorPathId()));
        }

        FlowEndpoint egressEndpoint = checkAndBuildEgressEndpoint(flowMirror, mirrorPath, sw.getSwitchId());
        PathSegment lastSegment = mirrorPath.getSegments().get(mirrorPath.getSegments().size() - 1);

        result.add(buildEgressCommand(sw, lastSegment.getDestPort(), egressEndpoint));
        return result;
    }

    private SpeakerData buildEgressCommand(Switch sw, int inPort, FlowEndpoint egressEndpoint) {
        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(mirrorPath.getCookie())
                .table(mirrorPath.isEgressWithMultiTable() ? OfTable.EGRESS : OfTable.INPUT)
                .priority(Priority.MIRROR_FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(Instructions.builder()
                        .applyActions(buildApplyActions(egressEndpoint, sw))
                        .build());

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }
}
