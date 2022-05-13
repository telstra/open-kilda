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

import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.rulemanager.utils.Utils.checkAndBuildEgressEndpoint;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;

import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuperBuilder
@Slf4j
public class EgressYRuleGenerator extends EgressRuleGenerator implements MeteredRuleGenerator {
    @NonNull
    protected final MeterId sharedMeterId;
    @NonNull
    protected final RuleManagerConfig config;
    @NonNull
    protected final UUID externalMeterCommandUuid;
    protected final boolean generateMeterCommand;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (flowPath.isOneSwitchFlow() || flowPath.getSegments().isEmpty()) {
            throw new IllegalStateException("Y-Flow rules can't be created for one switch flow");
        }
        List<SpeakerData> result = new ArrayList<>();
        PathSegment lastSegment = flowPath.getSegments().get(flowPath.getSegments().size() - 1);
        FlowEndpoint endpoint = checkAndBuildEgressEndpoint(flow, flowPath, sw.getSwitchId());
        SpeakerData command = buildEgressCommand(sw, lastSegment.getDestPort(), endpoint);
        result.add(command);

        if (generateMeterCommand) {
            SpeakerData meterCommand = buildMeter(externalMeterCommandUuid, flowPath, config, sharedMeterId, sw);
            if (meterCommand != null) {
                result.add(meterCommand);
                command.getDependsOn().add(externalMeterCommandUuid);
            }
        } else if (sw.getFeatures().contains(METERS) && sharedMeterId != null) {
            command.getDependsOn().add(externalMeterCommandUuid);
        }
        return result;
    }

    private SpeakerData buildEgressCommand(Switch sw, int inPort, FlowEndpoint egressEndpoint) {
        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(flowPath.getDestSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie().toBuilder().yFlow(true).build())
                .table(flowPath.isDestWithMultiTable() ? OfTable.EGRESS : OfTable.INPUT)
                .priority(Priority.Y_FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(buildInstructions(sw, buildApplyActions(egressEndpoint, sw)));


        // todo add RESET COUNTERS flag
        return builder.build();
    }

    private Instructions buildInstructions(Switch sw, List<Action> actions) {
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        addMeterToInstructions(sharedMeterId, sw, instructions);
        return instructions;
    }
}
