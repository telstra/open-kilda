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

import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.model.SwitchFeature.METERS;
import static org.openkilda.rulemanager.utils.Utils.checkAndBuildIngressEndpoint;
import static org.openkilda.rulemanager.utils.Utils.getOutPort;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuperBuilder
public class MultiTableIngressYRuleGenerator extends MultiTableIngressRuleGenerator {
    @NonNull
    protected final MeterId sharedMeterId;
    @NonNull
    protected final UUID externalMeterCommandUuid;
    protected final boolean generateMeterCommand;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();
        FlowEndpoint ingressEndpoint = checkAndBuildIngressEndpoint(flow, flowPath, sw.getSwitchId());
        FlowSpeakerData command = buildFlowIngressCommand(sw, ingressEndpoint);
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

    private FlowSpeakerData buildFlowIngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        List<Action> actions =
                new ArrayList<>(buildTransformActions(ingressEndpoint.getInnerVlanId(), sw.getFeatures()));
        actions.add(new PortOutAction(getOutPort(flowPath, flow)));

        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie().toBuilder().yFlow(true).build())
                .table(OfTable.INGRESS)
                .priority(getPriority(ingressEndpoint))
                .match(buildIngressMatch(ingressEndpoint, sw.getFeatures()))
                .instructions(buildInstructions(sw, actions));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private int getPriority(FlowEndpoint ingressEndpoint) {
        if (isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            if (isVlanIdSet(ingressEndpoint.getInnerVlanId())) {
                return Priority.Y_FLOW_DOUBLE_VLAN_PRIORITY;
            } else {
                return Priority.Y_FLOW_PRIORITY;
            }
        } else {
            return Priority.Y_DEFAULT_FLOW_PRIORITY;
        }
    }

    private Instructions buildInstructions(Switch sw, List<Action> actions) {
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .goToTable(OfTable.POST_INGRESS)
                .build();
        addMeterToInstructions(sharedMeterId, sw, instructions);
        if (flowPath.isOneSwitchFlow()) {
            RoutingMetadata metadata = RoutingMetadata.builder().oneSwitchFlowFlag(true).build(sw.getFeatures());
            instructions.setWriteMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()));
        }
        return instructions;
    }
}
