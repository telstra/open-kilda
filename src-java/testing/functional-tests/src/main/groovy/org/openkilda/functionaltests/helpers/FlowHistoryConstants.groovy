package org.openkilda.functionaltests.helpers

class FlowHistoryConstants {
    public static String CREATE_ACTION = "Flow creating"
    public static String UPDATE_ACTION = "Flow updating"
    public static String PARTIAL_UPDATE_ACTION = "Flow partial updating"
    public static String DELETE_ACTION = "Flow deleting"
    public static String REROUTE_ACTION = "Flow rerouting"
    public static String SYNC_ACTION = "Flow paths sync"
    public static String UPDATE_SUCCESS = "Flow was updated successfully"
    public static String PARTIAL_UPDATE_ONLY_IN_DB = "Flow PATCH operation has been executed without the consecutive update."
    public static String CREATE_SUCCESS = "Flow was created successfully"
    public static String CREATE_SUCCESS_Y = "The y-flow was created successfully"
    public static String REROUTE_SUCCESS = "Flow was rerouted successfully"
    public static String REROUTE_SUCCESS_Y = "Y-flow was rerouted successfully"
    public static String REROUTE_FAIL = "Failed to reroute the flow"
    public static String REROUTE_COMPLETE = "Flow reroute completed"
    public static String DELETE_SUCCESS = "Flow was deleted successfully"
    public static String DELETE_SUCCESS_Y = "The y-flow was deleted successfully"
    public static String PATH_SWAP_ACTION = "Flow paths swap"

    public static String CREATE_MIRROR_ACTION = "Flow mirror point creating"
    public static String DELETE_MIRROR_ACTION = "Flow mirror point deleting"

    public static String CREATE_MIRROR_SUCCESS = "Flow mirror point was created successfully"
    public static String DELETE_MIRROR_SUCCESS = "Flow mirror point was deleted successfully"
}
