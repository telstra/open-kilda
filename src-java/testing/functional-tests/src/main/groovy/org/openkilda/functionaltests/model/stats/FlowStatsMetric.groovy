package org.openkilda.functionaltests.model.stats

enum FlowStatsMetric {
    FLOW_INGRESS_PACKETS("ingress.packets"),
    FLOW_EGRESS_PACKETS("packets"),
    FLOW_INGRESS_BYTES("ingress.bytes"),
    FLOW_EGRESS_BYTES("bytes"),
    FLOW_INGRESS_BITS("ingress.bits"),
    FLOW_EGRESS_BITS("bits"),
    FLOW_METER_BITS("meter.bits"),
    FLOW_METER_BYTES("meter.bytes"),
    FLOW_METER_PACKETS("meter.packets"),
    FLOW_RAW_BITS("raw.bits"),
    FLOW_RAW_BYTES("raw.bytes"),
    FLOW_RAW_PACKETS("raw.packets"),
    FLOW_RTT("rtt"),
    LATENCY("latency")

    final String metric;
    public final static String prefix = "flow."

    FlowStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + metric
    }
}
