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

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.MeterId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.MeterSpeakerCommandData;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.match.FieldMatch;

import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class SingleTableIngressRuleGenerator extends IngressRuleGenerator {

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        List<SpeakerCommandData> result = new ArrayList<>();
        FlowSideAdapter flowSide = FlowSideAdapter.makeIngressAdapter(flow, flowPath);
        FlowSpeakerCommandData command = buildFlowCommand(sw, flowSide);
        if (command == null) {
            return Collections.emptyList();
        }
        result.add(command);

        SpeakerCommandData meterCommand = buildMeter(flowPath.getMeterId());
        if (meterCommand != null) {
            addMeterToInstructions(flowPath.getMeterId(), sw, command.getInstructions());
            result.add(meterCommand);
            command.getDependsOn().add(meterCommand.getUuid());
        }

        return result;
    }

    private SpeakerCommandData buildMeter(MeterId meterId) {
        // todo fix meter
        return MeterSpeakerCommandData.builder().build();
    }

    private FlowSpeakerCommandData buildFlowCommand(Switch sw, FlowSideAdapter flowSide) {
        FlowEndpoint endpoint = flowSide.getEndpoint();
        Set<FieldMatch> match = Sets.newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build());
        if (isFullPortFlow(flowSide.getEndpoint())) {
            match.add(FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getOuterVlanId()).build());
        }
        List<Action> actions = new ArrayList<>();
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        if (!isFullPortFlow(flowSide.getEndpoint())) {
            // todo: check encapsulation
            actions.add(new PopVlanAction());
        }
        actions.addAll(buildTransitEncapsulationActions());

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
                .table(OfTable.INPUT)
                .priority(isFullPortFlow(flowSide.getEndpoint()) ? Constants.Priority.DEFAULT_FLOW_PRIORITY
                        : Constants.Priority.FLOW_PRIORITY)
                .match(match)
                .instructions(instructions);

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private boolean isFullPortFlow(FlowEndpoint flowEndpoint) {
        return !FlowEndpoint.isVlanIdSet(flowEndpoint.getOuterVlanId());
    }

    private List<Action> buildTransitEncapsulationActions() {
        if (flow.getEncapsulationType() == FlowEncapsulationType.TRANSIT_VLAN) {
            return Collections.singletonList(PushVlanAction.builder()
                    .vlanId(encapsulation.getId().shortValue()).build());
        } else {
            // todo: add VXLAN
            return Collections.emptyList();
        }
    }
}
