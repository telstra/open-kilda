package org.openkilda.functionaltests.helpers.model

enum YFlowActionType {
    CREATE("Flow creating", "The y-flow was created successfully"),
    DELETE("Flow deleting", "The y-flow was deleted successfully"),
    REROUTE("Flow rerouting", "Y-flow was rerouted successfully")

    final String value
    final String payloadLastAction

    String getValue() {
        return value
    }

    String getPayloadLastAction() {
        return payloadLastAction
    }

    YFlowActionType(String value, String payloadLastAction) {
        this.value = value
        this.payloadLastAction = payloadLastAction
    }
}
