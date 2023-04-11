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

package org.openkilda.rulemanager.factory.generator.flow.haflow;

import static java.lang.String.format;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.rulemanager.utils.Utils.buildPopVxlan;
import static org.openkilda.rulemanager.utils.Utils.buildRuleFlags;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.HaFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.cookie.FlowSegmentCookie.FlowSubType;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.factory.MeteredRuleGenerator;
import org.openkilda.rulemanager.factory.generator.flow.NotIngressRuleGenerator;
import org.openkilda.rulemanager.utils.Utils;

import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuperBuilder
public class EgressHaRuleGenerator extends NotIngressRuleGenerator implements MeteredRuleGenerator {

    private final HaFlow haFlow;
    private final FlowPath subPath;
    private final FlowTransitEncapsulation encapsulation;
    private final boolean isSharedPath;
    private final MeterId sharedMeterId;
    private final UUID externalMeterCommandUuid;
    private final boolean generateCreateMeterCommand;
    private final RuleManagerConfig config;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        if (subPath.isOneSwitchPath() || subPath.getSegments().isEmpty()) {
            return new ArrayList<>();
        }

        PathSegment lastSegment = subPath.getSegments().get(subPath.getSegments().size() - 1);
        FlowEndpoint endpoint = checkAndBuildEgressEndpoint(haFlow, subPath, sw.getSwitchId());

        List<SpeakerData> result = new ArrayList<>();
        SpeakerData egressCommand = buildEgressCommand(sw, lastSegment.getDestPort(), endpoint);
        result.add(egressCommand);

        buildMeterCommandAndAddDependency(sharedMeterId, subPath.getBandwidth(), egressCommand,
                externalMeterCommandUuid, config, generateCreateMeterCommand, sw)
                .ifPresent(result::add);
        return result;
    }

    private SpeakerData buildEgressCommand(Switch sw, int inPort, FlowEndpoint egressEndpoint) {
        return FlowSpeakerData.builder()
                .switchId(sw.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(getCookie())
                .table(OfTable.EGRESS)
                .priority(Priority.FLOW_PRIORITY)
                .flags(buildRuleFlags(sw.getFeatures()))
                .match(makeTransitMatch(sw, inPort, encapsulation))
                .instructions(buildInstructions(egressEndpoint, sw))
                .build();
    }

    private Instructions buildInstructions(FlowEndpoint egressEndpoint, Switch sw) {
        Instructions instructions = Instructions.builder()
                .applyActions(buildApplyActions(egressEndpoint, sw))
                .build();
        addMeterToInstructions(sharedMeterId, sw, instructions);
        return instructions;
    }

    private FlowSegmentCookie getCookie() {
        if (isSharedPath) {
            return subPath.getCookie().toBuilder().subType(FlowSubType.SHARED).build();
        } else {
            return subPath.getCookie();
        }
    }

    protected List<Action> buildApplyActions(FlowEndpoint egressEndpoint, Switch sw) {
        List<Action> result = buildTransformActions(egressEndpoint, sw);
        result.add(new PortOutAction(new PortNumber(egressEndpoint.getPortNumber())));
        return result;
    }

    protected List<Action> buildTransformActions(FlowEndpoint egressEndpoint, Switch sw) {
        List<Action> result = new ArrayList<>();
        if (VXLAN.equals(encapsulation.getType())) {
            result.add(buildPopVxlan(sw.getSwitchId(), sw.getFeatures()));
        }

        List<Integer> targetVlanStack = egressEndpoint.getVlanStack();
        List<Integer> currentVlanStack = new ArrayList<>();
        if (TRANSIT_VLAN.equals(encapsulation.getType())) {
            currentVlanStack.add(encapsulation.getId());
        }
        result.addAll(Utils.makeVlanReplaceActions(currentVlanStack, targetVlanStack));
        return result;
    }

    private static FlowEndpoint checkAndBuildEgressEndpoint(HaFlow haFlow, FlowPath subPath, SwitchId switchId) {
        FlowEndpoint egressEndpoint = FlowSideAdapter.makeEgressAdapter(haFlow, subPath).getEndpoint();
        if (!egressEndpoint.getSwitchId().equals(switchId)) {
            throw new IllegalArgumentException(format("Ha-sub path %s has egress endpoint %s with switchId %s. "
                            + "But switchId must be equal to target switchId %s",
                    subPath.getPathId(), egressEndpoint, egressEndpoint.getSwitchId(), switchId));
        }
        return egressEndpoint;
    }
}
