package org.openkilda.functionaltests.model.stats

enum SystemStatsMetric {
    FLOW_SYSTEM_METER_PACKETS("flow.system.meter.packets"),
    FLOW_SYSTEM_METER_BYTES("flow.system.meter.bytes"),
    FLOW_SYSTEM_METER_BITS("flow.system.meter.bits"),

    final String metric;
    public final static String prefix = "switch."

    SystemStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + this.metric
    }
}
