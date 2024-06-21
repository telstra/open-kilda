package org.openkilda.functionaltests.helpers.model

enum FlowActionType {
    CREATE("Flow creating", "Flow was created successfully"),
    DELETE("Flow deleting", "Flow was deleted successfully"),
    UPDATE("Flow updating", "Flow was updated successfully"),
    PARTIAL_UPDATE("Flow partial updating", "Flow was updated successfully"),
    PARTIAL_UPDATE_ONLY_IN_DB("Flow partial updating", "Flow PATCH operation has been executed without the consecutive update."),
    REROUTE("Flow rerouting", "Flow was rerouted successfully"),
    REROUTE_FAILED("Flow rerouting", "Failed to reroute the flow"),
    CREATE_MIRROR("Flow mirror point creating", "Flow mirror point was created successfully"),
    DELETE_MIRROR("Flow mirror point deleting", "Flow mirror point was deleted successfully"),
    PATH_SWAP("Flow paths swap", "Flow was updated successfully")

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
