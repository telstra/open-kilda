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

import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.getOutPort;
import static org.openkilda.rulemanager.utils.Utils.isFullPortEndpoint;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants;
import org.openkilda.rulemanager.FlowSpeakerCommandData;
import org.openkilda.rulemanager.FlowSpeakerCommandData.FlowSpeakerCommandDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfFlowFlag;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.SpeakerCommandData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
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
        FlowEndpoint ingressEndpoint = FlowSideAdapter.makeIngressAdapter(flow, flowPath).getEndpoint();
        FlowSpeakerCommandData command = buildFlowIngressCommand(sw, ingressEndpoint);
        if (command == null) {
            return Collections.emptyList();
        }
        result.add(command);

        SpeakerCommandData meterCommand = buildMeter(flowPath, config, flowPath.getMeterId(), sw);
        if (meterCommand != null) {
            addMeterToInstructions(flowPath.getMeterId(), sw, command.getInstructions());
            result.add(meterCommand);
            command.getDependsOn().add(meterCommand.getUuid());
        }

        return result;
    }

    private FlowSpeakerCommandData buildFlowIngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        List<Action> actions = new ArrayList<>();
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .build();
        // TODO should we check if switch supports encapsulation?
        actions.addAll(buildTransformActions(ingressEndpoint.getOuterVlanId(), sw.getFeatures()));
        actions.add(new PortOutAction(new PortNumber(getOutPort(flowPath, flow))));
        addMeterToInstructions(flowPath.getMeterId(), sw, instructions);

        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
                .table(OfTable.INPUT)
                .priority(isFullPortEndpoint(ingressEndpoint) ? Constants.Priority.DEFAULT_FLOW_PRIORITY
                        : Constants.Priority.FLOW_PRIORITY)
                .match(buildMatch(ingressEndpoint, sw.getFeatures()))
                .instructions(instructions);

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(Sets.newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    @VisibleForTesting
    Set<FieldMatch> buildMatch(FlowEndpoint ingressEndpoint, Set<SwitchFeature> switchFeatures) {
        return Utils.makeIngressMatch(ingressEndpoint, false, switchFeatures);
    }

    @VisibleForTesting
    List<Action> buildTransformActions(int outerVlan, Set<SwitchFeature> features) {
        List<Integer> currentStack = makeVlanStack(outerVlan);
        List<Integer> targetStack;
        if (flowPath.isOneSwitchFlow()) {
            FlowEndpoint egressEndpoint = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint();
            targetStack = makeVlanStack(egressEndpoint.getOuterVlanId());
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            targetStack = makeVlanStack(encapsulation.getId());
        } else {
            targetStack = new ArrayList<>();
        }
        // TODO do something with groups

        List<Action> transformActions = new ArrayList<>(Utils.makeVlanReplaceActions(currentStack, targetStack));

        if (encapsulation.getType() == VXLAN && !flowPath.isOneSwitchFlow()) {
            transformActions.add(buildPushVxlan(
                    encapsulation.getId(), flowPath.getSrcSwitchId(), flowPath.getDestSwitchId(), VXLAN_UDP_SRC,
                    features));
        }
        return transformActions;
    }
}
