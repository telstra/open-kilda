package org.openkilda.functionaltests.model.stats

import org.openkilda.testing.service.tsdb.model.StatsMetric

enum SystemStatsMetric implements StatsMetric {
    FLOW_SYSTEM_METER_PACKETS("flow.system.meter.packets"),
    FLOW_SYSTEM_METER_BYTES("flow.system.meter.bytes"),
    FLOW_SYSTEM_METER_BITS("flow.system.meter.bits"),

    private final String metric;
    private final static String prefix = "switch."

    SystemStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + metric
    }

    String getPrefix() {
        return prefix
    }
}
