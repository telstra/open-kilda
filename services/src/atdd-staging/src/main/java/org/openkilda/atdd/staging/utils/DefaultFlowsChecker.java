package org.openkilda.atdd.staging.utils;

import org.apache.commons.lang3.StringUtils;
import org.openkilda.atdd.staging.model.floodlight.FlowApplyActions;
import org.openkilda.atdd.staging.model.floodlight.FlowEntriesMap;
import org.openkilda.atdd.staging.model.floodlight.FlowEntry;
import org.openkilda.atdd.staging.model.floodlight.FlowInstructionOutput;
import org.openkilda.atdd.staging.model.floodlight.FlowInstructions;
import org.openkilda.atdd.staging.model.floodlight.FlowMatchField;
import org.openkilda.atdd.staging.model.floodlight.SwitchEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class DefaultFlowsChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFlowsChecker.class);

    private static final String VERSION_12 = "OF_12";
    private static final String COMMON_FLOW = "flow-0x8000000000000002";
    private static final String DROP_FLOW = "flow-0x8000000000000001";
    private static final String OF13_FLOW = "flow-0x8000000000000003";

    private static final String CONTROLLER_OUTPUT = "controller";
    private static final String DROP_INSTRUCTION = "drop";

    public static boolean validateDefaultRules(SwitchEntry sw, FlowEntriesMap map) {
        FlowEntry flow = map.get(COMMON_FLOW);
        boolean result = isValidDefaultFlow(flow, sw.getSwitchId());

        if (!VERSION_12.equals(sw.getOFVersion())) {
            result = result & isValidDropRule(map.get(DROP_FLOW), sw.getSwitchId());
            result = result & isValidDefaultFlow(map.get(OF13_FLOW), sw.getSwitchId());
        }

        return result;
    }

    private static boolean isValidDropRule(FlowEntry flow, String switchId) {
        boolean valid = true;
        if (Objects.isNull(flow)) {
            LOGGER.error("Switch {} doesn't contain {} flow", switchId, DROP_FLOW);
            return false;
        }

        if (flow.getPriority() != 1) {
            LOGGER.error("Switch {} has incorrect priority for flow {}", switchId, flow.getCookie());
            valid = false;
        }

        FlowInstructions instructions = flow.getInstructions();
        if (!DROP_INSTRUCTION.equals(instructions.getNone())) {
            LOGGER.error("Switch {} has incorrect drop instruction for flow {}", switchId, flow.getCookie());
            valid = false;
        }

        return valid;
    }

    private static boolean isValidDefaultFlow(FlowEntry flow, String switchId) {
        boolean valid = true;
        if (Objects.isNull(flow)) {
            LOGGER.error("Switch {} doesn't contain {} flow", switchId, DROP_FLOW);
            return false;
        }

        FlowInstructions instructions = flow.getInstructions();
        FlowApplyActions flowActions = instructions.getApplyActions();
        if (!isValidSetFieldProperty(flowActions.getField(), switchId)) {
            LOGGER.error("Switch {} has incorrect set field action for flow {}", switchId, flow.getCookie());
            valid = false;
        }

        FlowInstructionOutput flowOutput = flowActions.getFlowOutput();
        if (!CONTROLLER_OUTPUT.equals(flowOutput.getName())) {
            LOGGER.error("Switch {} has incorrect output action name for flow {}", switchId, flow.getCookie());
            valid = false;
        }

        //todo: add flow match verifications
        FlowMatchField flowMatch = flow.getMatch();
        return valid;
    }

    private DefaultFlowsChecker() {
    }

    private static boolean isValidSetFieldProperty(String value, String switchId) {
        //todo: would be better to rewrite using regexp
        return StringUtils.startsWith(value, switchId);
    }

}
