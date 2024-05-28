package org.openkilda.functionaltests.helpers.model

enum PathComputationStrategy {
    COST,
    LATENCY,
    MAX_LATENCY,
    COST_AND_AVAILABLE_BANDWIDTH
}
