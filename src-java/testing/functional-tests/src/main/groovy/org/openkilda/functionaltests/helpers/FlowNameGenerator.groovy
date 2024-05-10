package org.openkilda.functionaltests.helpers

import static org.openkilda.functionaltests.helpers.StringGenerator.generateFlowId

enum FlowNameGenerator {
    FLOW("flow"),
    Y_FLOW("y-flow"),
    HA_FLOW("ha-flow")

    private String flowType

    FlowNameGenerator(String flowType) {
        this.flowType = flowType
    }

    String generateId() {
        generateFlowId() + "_" + flowType
    }
}
