package org.openkilda.functionaltests.model.stats

enum YFlowStatsMetric {
    Y_FLOW_SHARED_PACKETS("meter.shared.packets"),
    Y_FLOW_SHARED_BYTES("meter.shared.bytes"),
    Y_FLOW_SHARED_BITS("meter.shared.bits"),
    Y_FLOW_Y_POINT_PACKETS("meter.ypoint.packets"),
    Y_FLOW_Y_POINT_BYTES("meter.ypoint.bytes"),
    Y_FLOW_Y_POINT_BITS("meter.ypoint.bits")


    final String metric;
    public final static String prefix = "yflow."

    YFlowStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + metric
    }
}
