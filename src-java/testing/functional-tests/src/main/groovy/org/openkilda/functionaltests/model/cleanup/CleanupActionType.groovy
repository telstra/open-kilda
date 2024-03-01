package org.openkilda.functionaltests.model.cleanup

enum CleanupActionType {
    DELETE_FLOW,
    DELETE_YFLOW,
    DELETE_HAFLOW,
    RESTORE_ISL,
    PORT_UP,
    RESET_ISLS_COST,
    DELETE_ISLS_PROPERTIES,
    RESET_ISL_AVAILABLE_BANDWIDTH,
    REVIVE_SWITCH,
    RESTORE_FEATURE_TOGGLE,
    OTHER
}