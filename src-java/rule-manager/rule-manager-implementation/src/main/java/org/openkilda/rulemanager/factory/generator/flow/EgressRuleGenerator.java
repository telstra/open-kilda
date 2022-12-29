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

import static org.openkilda.rulemanager.utils.Utils.checkAndBuildEgressEndpoint;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerData;

import com.google.common.collect.Lists;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@Slf4j
public class EgressRuleGenerator extends EgressBaseRuleGenerator {

    protected final FlowPath flowPath;
    protected final Flow flow;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (flowPath.isOneSwitchFlow() || flowPath.getSegments().isEmpty()) {
            return new ArrayList<>();
        }

        PathSegment lastSegment = flowPath.getSegments().get(flowPath.getSegments().size() - 1);
        FlowEndpoint endpoint = checkAndBuildEgressEndpoint(flow, flowPath, sw.getSwitchId());
        return Lists.newArrayList(buildEgressCommand(sw, lastSegment.getDestPort(), endpoint));
    }

    private SpeakerData buildEgressCommand(Switch sw, int inPort, FlowEndpoint egressEndpoint) {
        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(flowPath.getDestSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
                .table(flowPath.isDestWithMultiTable() ? OfTable.EGRESS : OfTable.INPUT)
                .priority(Priority.FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(Instructions.builder()
                        .applyActions(buildApplyActions(egressEndpoint, sw))
                        .build());

        // todo add RESET COUNTERS flag
        return builder.build();
    }
}
