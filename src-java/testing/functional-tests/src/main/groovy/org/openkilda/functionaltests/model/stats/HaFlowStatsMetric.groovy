package org.openkilda.functionaltests.model.stats

import org.openkilda.testing.service.tsdb.model.StatsMetric

enum HaFlowStatsMetric implements StatsMetric {
    HA_FLOW_METER_INGRESS_PACKETS("meter.packets"),
    HA_FLOW_METER_INGRESS_BYTES("meter.bytes"),
    HA_FLOW_METER_INGRESS_BITS("meter.bits"),
    HA_FLOW_METER_Y_POINT_PACKETS("meter.ypoint.packets"),
    HA_FLOW_METER_Y_POINT_BYTES("meter.ypoint.bytes"),
    HA_FLOW_METER_Y_POINT_BITS("meter.ypoint.bits"),
    HA_FLOW_Y_POINT_PACKETS("ypoint.packets"),
    HA_FLOW_Y_POINT_BYTES("ypoint.bytes"),
    HA_FLOW_Y_POINT_BITS("ypoint.bits"),
    HA_FLOW_RAW_PACKETS("raw.packets"),
    HA_FLOW_RAW_BYTES("raw.bytes"),
    HA_FLOW_RAW_BITS("raw.bits"),
    HA_FLOW_INGRESS_PACKETS("ingress.packets"),
    HA_FLOW_INGRESS_BYTES("ingress.bytes"),
    HA_FLOW_INGRESS_BITS("ingress.bits"),
    HA_FLOW_EGRESS_PACKETS("packets"),
    HA_FLOW_EGRESS_BYTES("bytes"),
    HA_FLOW_EGRESS_BITS("bits")

    private final String metric;
    private final static String prefix = "haflow."

    HaFlowStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + metric
    }

    String getPrefix() {
        return prefix
    }
}
