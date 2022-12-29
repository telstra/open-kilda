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

package org.openkilda.rulemanager.factory.generator.flow;

import static java.lang.String.format;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.SwitchFeature.KILDA_OVS_PUSH_POP_MATCH_VXLAN;
import static org.openkilda.model.SwitchFeature.NOVIFLOW_PUSH_POP_VXLAN;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.Switch;
import org.openkilda.rulemanager.ProtoConstants.PortNumber;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.ActionType;
import org.openkilda.rulemanager.action.PopVxlanAction;
import org.openkilda.rulemanager.action.PortOutAction;
import org.openkilda.rulemanager.utils.Utils;

import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@Slf4j
public abstract class EgressBaseRuleGenerator extends NotIngressRuleGenerator {
    protected final FlowTransitEncapsulation encapsulation;

    protected List<Action> buildApplyActions(FlowEndpoint egressEndpoint, Switch sw) {
        List<Action> result = buildTransformActions(egressEndpoint, sw);
        result.add(new PortOutAction(new PortNumber(egressEndpoint.getPortNumber())));
        return result;
    }

    protected List<Action> buildTransformActions(FlowEndpoint egressEndpoint, Switch sw) {
        List<Action> result = new ArrayList<>();
        if (VXLAN.equals(encapsulation.getType())) {
            if (sw.getFeatures().contains(NOVIFLOW_PUSH_POP_VXLAN)) {
                result.add(new PopVxlanAction(ActionType.POP_VXLAN_NOVIFLOW));
            } else if (sw.getFeatures().contains(KILDA_OVS_PUSH_POP_MATCH_VXLAN)) {
                result.add(new PopVxlanAction(ActionType.POP_VXLAN_OVS));
            } else {
                throw new IllegalArgumentException(format("Switch %s must support one of following features to pop "
                        + "VXLAN: [%s, %s]", sw.getSwitchId(), NOVIFLOW_PUSH_POP_VXLAN,
                        KILDA_OVS_PUSH_POP_MATCH_VXLAN));
            }
        }

        List<Integer> targetVlanStack = egressEndpoint.getVlanStack();
        List<Integer> currentVlanStack = new ArrayList<>();
        if (TRANSIT_VLAN.equals(encapsulation.getType())) {
            currentVlanStack.add(encapsulation.getId());
        }
        result.addAll(Utils.makeVlanReplaceActions(currentVlanStack, targetVlanStack));
        return result;
    }
}
