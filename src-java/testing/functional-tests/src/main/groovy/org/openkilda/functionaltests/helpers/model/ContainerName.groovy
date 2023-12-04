package org.openkilda.functionaltests.helpers.model

enum ContainerName {
    GRPC("grpc-speaker"),
    GRPC_STUB("grpc-stub"),
    WFM("wfm"),
    STORM("storm-ui")

    private final String id;

    ContainerName(String id) {
        this.id = id
    }

    @Override
    String toString() {
        return "/${this.id}"
    }
}