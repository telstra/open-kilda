package org.openkilda.functionaltests.model.stats

enum StatsMetric {
    FLOW_RAW_PACKETS("flow.raw.packets"),
    Y_FLOW_SHARED_BYTES("yFlow.meter.shared.bytes"),
    Y_FLOW_Y_POINT_BYTES("yFlow.meter.yPoint.bytes"),
    FLOW_INGRESS_PACKETS("flow.ingress.packets"),
    FLOW_PACKETS("flow.packets"),
    FLOW_RAW_BYTES("flow.raw.bytes")

    final String metric;

    StatsMetric(String metric) {
        this.metric = metric
    }
}