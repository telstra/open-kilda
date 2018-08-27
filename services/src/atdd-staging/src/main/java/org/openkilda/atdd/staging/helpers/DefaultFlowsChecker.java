/* Copyright 2018 Telstra Open Source
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

package org.openkilda.atdd.staging.helpers;

import org.openkilda.messaging.model.SwitchId;
import org.openkilda.testing.service.floodlight.model.FlowApplyActions;
import org.openkilda.testing.service.floodlight.model.FlowEntriesMap;
import org.openkilda.testing.service.floodlight.model.FlowEntry;
import org.openkilda.testing.service.floodlight.model.FlowInstructions;
import org.openkilda.testing.service.floodlight.model.FlowMatchField;
import org.openkilda.testing.service.floodlight.model.SwitchEntry;

import cucumber.api.Scenario;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public final class DefaultFlowsChecker {
    private static final String VERSION_12 = "OF_12";
    private static final String BROADCAST_FLOW = "flow-0x8000000000000002";
    private static final String DROP_FLOW = "flow-0x8000000000000001";
    private static final String NON_BROADCAST_FLOW = "flow-0x8000000000000003";
    private static final String VERIFICATION_DST = "08:ed:02:ef:ff:ff";

    private static final String CONTROLLER_OUTPUT = "controller";
    private static final int DPID_LENGTH = 23;

    /**
     * Validates whether all default rules are correctly installed on a switch.
     */
    public static boolean validateDefaultRules(SwitchEntry sw, FlowEntriesMap map, Scenario scenario) {
        FlowEntry flow = map.get(BROADCAST_FLOW);
        boolean result = isValidDefaultFlow(BROADCAST_FLOW, flow, sw.getSwitchId(), scenario);

        if (!VERSION_12.equals(sw.getOfVersion())) {
            result = result & isValidDefaultFlow(NON_BROADCAST_FLOW,
                    map.get(NON_BROADCAST_FLOW), sw.getSwitchId(), scenario);
            result = result & isValidDropRule(map.get(DROP_FLOW), sw.getSwitchId(), scenario);
        }

        return result;
    }

    private static boolean isValidDropRule(FlowEntry flow, SwitchId switchId, Scenario scenario) {
        boolean valid = true;
        if (Objects.isNull(flow)) {
            scenario.write(String.format("Switch %s doesn't contain %s flow", switchId, DROP_FLOW));
            return false;
        }

        if (flow.getPriority() != 1) {
            scenario.write(String.format("Switch %s has incorrect priority for flow %s", switchId, flow.getCookie()));
            valid = false;
        }

        FlowInstructions instructions = flow.getInstructions();
        if (Objects.nonNull(instructions.getApplyActions())) {
            scenario.write(String.format("Switch %s has incorrect instructions for flow %s",
                    switchId, flow.getCookie()));
            valid = false;
        }

        return valid;
    }

    private static boolean isValidDefaultFlow(String flowId, FlowEntry flow, SwitchId switchId, Scenario scenario) {
        boolean valid = true;
        if (Objects.isNull(flow)) {
            scenario.write(String.format("Switch %s doesn't contain %s flow", switchId, flowId));
            return false;
        }

        FlowInstructions instructions = flow.getInstructions();
        FlowApplyActions flowActions = instructions.getApplyActions();
        if (!isValidSetFieldProperty(flowActions.getField(), switchId)) {
            scenario.write(String.format("Switch %s has incorrect set field action for flow %s",
                    switchId, flow.getCookie()));
            valid = false;
        }

        String flowOutput = flowActions.getFlowOutput();
        if (!CONTROLLER_OUTPUT.equals(flowOutput)) {
            scenario.write(String.format("Switch %s has incorrect output action name for flow %s", switchId,
                    flow.getCookie()));
            valid = false;
        }

        FlowMatchField flowMatch = flow.getMatch();
        if (BROADCAST_FLOW.equals(flow.getCookie())) {
            if (!VERIFICATION_DST.equals(flowMatch.getEthDst())) {
                scenario.write(String.format("Switch %s has incorrect verification broadcast packets destination",
                        switchId));
                valid = false;
            }
        } else if (NON_BROADCAST_FLOW.equals(flow.getCookie())) {
            if (!switchId.toMacAddress().equals(flowMatch.getEthDst())) {
                scenario.write(String.format("Switch %s contains incorrect eth_dst: %s",
                        switchId, flowMatch.getEthDst()));
                valid = false;
            }
        }
        return valid;
    }

    private static boolean isValidSetFieldProperty(String value, SwitchId switchId) {
        return StringUtils.startsWith(value, switchId.toMacAddress());
    }

    private DefaultFlowsChecker() {
    }

}
