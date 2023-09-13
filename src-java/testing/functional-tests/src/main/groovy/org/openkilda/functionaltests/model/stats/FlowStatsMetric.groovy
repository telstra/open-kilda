package org.openkilda.functionaltests.model.stats

import org.openkilda.testing.service.tsdb.model.StatsMetric

enum FlowStatsMetric implements StatsMetric {
    FLOW_INGRESS_PACKETS("ingress.packets"),
    FLOW_EGRESS_PACKETS("packets"),
    FLOW_INGRESS_BYTES("ingress.bytes"),
    FLOW_EGRESS_BYTES("bytes"),
    FLOW_INGRESS_BITS("ingress.bits"),
    FLOW_EGRESS_BITS("bits"),
    FLOW_RAW_BITS("raw.bits"),
    FLOW_RAW_BYTES("raw.bytes"),
    FLOW_RAW_PACKETS("raw.packets"),
    FLOW_RTT("rtt"),
    LATENCY("latency")

    private final String metric;
    private final static String prefix = "flow."

    FlowStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + metric
    }

    String getPrefix() {
        return prefix
    }
}
