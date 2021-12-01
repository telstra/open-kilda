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

package org.openkilda.rulemanager.factory.generator.flow.loop;

import static java.lang.String.format;
import static org.openkilda.rulemanager.Field.ETH_DST;
import static org.openkilda.rulemanager.Field.ETH_SRC;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.ProtoConstants.PortNumber.SpecialPortType;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.action.SetFieldAction;
import org.openkilda.rulemanager.factory.generator.flow.NotIngressRuleGenerator;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
public class FlowLoopTransitRuleGenerator extends NotIngressRuleGenerator {

    private final Flow flow;
    private final FlowPath flowPath;
    private final int inPort;
    private final boolean multiTable;
    private final FlowTransitEncapsulation encapsulation;

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        if (flowPath.isOneSwitchFlow() || !flow.isLooped()) {
            return new ArrayList<>();
        }
        if (!sw.getSwitchId().equals(flow.getLoopSwitchId())) {
            throw new IllegalArgumentException(format("Flow %s has loop switchId %s. But this switchId must be equal "
                    + "to target switchId %s", flow.getFlowId(), flow.getLoopSwitchId(), sw.getSwitchId()));
        }

        return Lists.newArrayList(buildTransitCommand(sw, inPort));
    }

    private SpeakerCommandData buildTransitCommand(Switch sw, int inPort) {
        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie().toBuilder().looped(true).build())
                .table(multiTable ? OfTable.TRANSIT : OfTable.INPUT)
                .priority(Priority.LOOP_FLOW_PRIORITY)
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(buildInstructions(sw.getSwitchId()));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private Instructions buildInstructions(SwitchId switchId) {
        List<Action> applyActions = new ArrayList<>();

        if (FlowEncapsulationType.VXLAN.equals(encapsulation.getType())) {
            // After turning of VXLAN packet we must update eth_dst header because egress rule on the last switch
            // will match the packet by this field.
            applyActions.add(SetFieldAction.builder().field(ETH_SRC).value(switchId.toMacAddressAsLong()).build());
            applyActions.add(SetFieldAction.builder().field(ETH_DST)
                    .value(flowPath.getSrcSwitchId().toMacAddressAsLong()).build());
        }

        applyActions.add(new PortOutAction(new PortNumber(SpecialPortType.IN_PORT)));
        return Instructions.builder().applyActions(applyActions).build();
    }
}
