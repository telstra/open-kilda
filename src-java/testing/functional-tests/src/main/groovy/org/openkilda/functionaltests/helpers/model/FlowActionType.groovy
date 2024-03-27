package org.openkilda.functionaltests.helpers.model

enum FlowActionType {
    CREATE("Flow creating", "Flow was created successfully"),
    DELETE("Flow deleting", "Flow was deleted successfully"),
    UPDATE_ACTION("Flow updating", "Flow was updated successfully"),
    REROUTE("Flow rerouting", "Flow was rerouted successfully")

    final String value
    final String payloadLastAction

    String getValue() {
        return value
    }

    String getPayloadLastAction() {
        return payloadLastAction
    }

    FlowActionType(String value, String payloadLastAction) {
        this.value = value
        this.payloadLastAction = payloadLastAction
    }
}
