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

import static com.google.common.collect.Sets.newHashSet;
import static org.openkilda.model.FlowEndpoint.isVlanIdSet;
import static org.openkilda.rulemanager.utils.Utils.checkAndBuildIngressEndpoint;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
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
import org.openkilda.rulemanager.factory.generator.flow.NotIngressRuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;
import org.openkilda.rulemanager.utils.Utils;

import com.google.common.collect.Lists;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class FlowLoopIngressRuleGenerator extends NotIngressRuleGenerator {

    protected final Flow flow;
    private final FlowPath flowPath;
    private final boolean multiTable;

    @Override
    public List<SpeakerCommandData> generateCommands(Switch sw) {
        if (!flow.isLooped()) {
            return new ArrayList<>();
        }
        return Lists.newArrayList(buildIngressLoopCommand(sw));
    }

    private SpeakerCommandData buildIngressLoopCommand(Switch sw) {
        FlowEndpoint ingressEndpoint = checkAndBuildIngressEndpoint(flow, flowPath, sw.getSwitchId());
        FlowSpeakerCommandDataBuilder<?, ?> builder = FlowSpeakerCommandData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(flowPath.getCookie().toBuilder().looped(true).build())
                .table(multiTable ? OfTable.INGRESS : OfTable.INPUT)
                .priority(getPriority(ingressEndpoint))
                .match(makeLoopMatch(ingressEndpoint, sw.getFeatures()))
                .instructions(makeIngressFlowLoopInstructions(ingressEndpoint));

        if (sw.getFeatures().contains(SwitchFeature.RESET_COUNTS_FLAG)) {
            builder.flags(newHashSet(OfFlowFlag.RESET_COUNTERS));
        }
        return builder.build();
    }

    private int getPriority(FlowEndpoint ingressEndpoint) {
        if (isVlanIdSet(ingressEndpoint.getOuterVlanId())) {
            if (isVlanIdSet(ingressEndpoint.getInnerVlanId())) {
                return Priority.LOOP_DOUBLE_VLAN_FLOW_PRIORITY;
            } else {
                return Priority.LOOP_FLOW_PRIORITY;
            }
        } else {
            return Priority.LOOP_DEFAULT_FLOW_PRIORITY;
        }
    }

    private Set<FieldMatch> makeLoopMatch(FlowEndpoint endpoint, Set<SwitchFeature> features) {
        if (multiTable) {
            if (isVlanIdSet(endpoint.getOuterVlanId())) {
                if (isVlanIdSet(endpoint.getInnerVlanId())) {
                    return makeDoubleVlanLoopMatch(endpoint, features);
                } else {
                    return makeSingleVlanMultiTableLoopMatch(endpoint, features);
                }
            } else {
                return makeDefaultPortLoopMatch(endpoint);
            }
        } else {
            if (isVlanIdSet(endpoint.getOuterVlanId())) {
                return makeSingleVlanSingleTableLoopMatch(endpoint);
            } else {
                return makeDefaultPortLoopMatch(endpoint);
            }
        }
    }

    private Set<FieldMatch> makeDefaultPortLoopMatch(FlowEndpoint endpoint) {
        return newHashSet(FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build());
    }

    private Set<FieldMatch> makeDoubleVlanLoopMatch(FlowEndpoint endpoint, Set<SwitchFeature> features) {
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(endpoint.getOuterVlanId()).build(features);
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getInnerVlanId()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
    }

    private Set<FieldMatch> makeSingleVlanMultiTableLoopMatch(FlowEndpoint endpoint, Set<SwitchFeature> features) {
        RoutingMetadata metadata = RoutingMetadata.builder().outerVlanId(endpoint.getOuterVlanId()).build(features);
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.METADATA).value(metadata.getValue()).mask(metadata.getMask()).build());
    }

    private Set<FieldMatch> makeSingleVlanSingleTableLoopMatch(FlowEndpoint endpoint) {
        return newHashSet(
                FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                FieldMatch.builder().field(Field.VLAN_VID).value(endpoint.getOuterVlanId()).build());
    }

    private Instructions makeIngressFlowLoopInstructions(FlowEndpoint endpoint) {
        List<Action> applyActions = new ArrayList<>();
        if (multiTable) {
            applyActions.addAll(Utils.makeVlanReplaceActions(
                    FlowEndpoint.makeVlanStack(endpoint.getInnerVlanId()),
                    endpoint.getVlanStack()));
        }
        applyActions.add(new PortOutAction(new PortNumber(SpecialPortType.IN_PORT)));
        return Instructions.builder().applyActions(applyActions).build();
    }
}
