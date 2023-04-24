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
import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.model.FlowEndpoint.makeVlanStack;
import static org.openkilda.rulemanager.Constants.VXLAN_UDP_SRC;
import static org.openkilda.rulemanager.utils.Utils.buildPushVxlan;
import static org.openkilda.rulemanager.utils.Utils.checkAndBuildIngressEndpoint;
import static org.openkilda.rulemanager.utils.Utils.getOutPort;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
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
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import lombok.Builder.Default;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuperBuilder
@Slf4j
public class MultiTableIngressRuleGenerator extends IngressRuleGenerator {

    /*
     * This set must contain FlowSideAdapters with src multiTable=true which have same SwitchId and inPort as ingress
     * endpoint of target flowPath.
     */
    @Default
    private final Set<FlowSideAdapter> overlappingIngressAdapters = new HashSet<>();

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();
        FlowEndpoint ingressEndpoint = checkAndBuildIngressEndpoint(flow, flowPath, sw.getSwitchId());
        FlowSpeakerData command = buildFlowIngressCommand(sw, ingressEndpoint);
        if (command == null) {
            return Collections.emptyList();
        }
        result.add(command);
        if (needToBuildFlowPreIngressRule(ingressEndpoint)) {
            result.add(Utils.buildSharedFlowPreIngressCommand(sw, ingressEndpoint));
        }
        if (overlappingIngressAdapters.isEmpty()) {
            result.add(Utils.buildCustomerPortSharedCatchCommand(sw, ingressEndpoint));
        }

        SpeakerData meterCommand = buildMeter(flowPath, config, flowPath.getMeterId(), sw);
        if (meterCommand != null) {
            result.add(meterCommand);
            command.getDependsOn().add(meterCommand.getUuid());
        }

        return result;
    }

    private boolean needToBuildFlowPreIngressRule(FlowEndpoint ingressEndpoint) {
        if (!isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            // Full port flows do not need pre ingress shared rule
            return false;
        }
        for (FlowSideAdapter overlappingIngressAdapter : overlappingIngressAdapters) {
            if (overlappingIngressAdapter.getEndpoint().getOuterVlanId() == ingressEndpoint.getOuterVlanId()) {
                // some other flow already has shared rule, so current flow don't need it
                return false;
            }
        }
        return true;
    }

    private FlowSpeakerData buildFlowIngressCommand(Switch sw, FlowEndpoint ingressEndpoint) {
        // TODO should we check if switch supports encapsulation?
        List<Action> actions = new ArrayList<>(buildTransformActions(
                ingressEndpoint.getInnerVlanId(), sw.getFeatures()));
        actions.add(new PortOutAction(getOutPort(flowPath, flow)));

        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(ingressEndpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie())
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
                return Priority.DOUBLE_VLAN_FLOW_PRIORITY;
            } else {
                return Priority.FLOW_PRIORITY;
            }
        } else {
            return Priority.DEFAULT_FLOW_PRIORITY;
        }
    }

    private Instructions buildInstructions(Switch sw, List<Action> actions) {
        Instructions instructions = Instructions.builder()
                .applyActions(actions)
                .goToTable(OfTable.POST_INGRESS)
                .build();
        addMeterToInstructions(flowPath.getMeterId(), sw, instructions);
        if (flowPath.isOneSwitchPath()) {
            RoutingMetadata metadata = RoutingMetadata.builder().oneSwitchFlowFlag(true).build(sw.getFeatures());
            instructions.setWriteMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()));
        }
        return instructions;
    }

    @VisibleForTesting
    Set<FieldMatch> buildIngressMatch(FlowEndpoint endpoint, Set<SwitchFeature> switchFeatures) {
        return Utils.makeIngressMatch(endpoint, true, switchFeatures);
    }

    @VisibleForTesting
    List<Action> buildTransformActions(int innerVlan, Set<SwitchFeature> features) {
        List<Integer> currentStack = makeVlanStack(innerVlan);
        List<Integer> targetStack;
        if (flowPath.isOneSwitchPath()) {
            targetStack = FlowSideAdapter.makeEgressAdapter(flow, flowPath).getEndpoint().getVlanStack();
        } else if (encapsulation.getType() == TRANSIT_VLAN) {
            targetStack = makeVlanStack(encapsulation.getId());
        } else {
            targetStack = new ArrayList<>();
        }

        List<Action> transformActions = new ArrayList<>(Utils.makeVlanReplaceActions(currentStack, targetStack));

        if (encapsulation != null && encapsulation.getType() == VXLAN && !flowPath.isOneSwitchPath()) {
            transformActions.add(buildPushVxlan(encapsulation.getId(), flowPath.getSrcSwitchId(),
                    flowPath.getDestSwitchId(), VXLAN_UDP_SRC, features));
        }
        return transformActions;
    }
}
