package org.openkilda.functionaltests.model.stats

import org.openkilda.testing.service.tsdb.model.StatsMetric

enum IslStatsMetric implements StatsMetric{
    ISL_RTT("isl.rtt")


    private final static String prefix = ""
    private final String metric;

    IslStatsMetric(String metric) {
        this.metric = metric
    }

    String getValue() {
        return prefix + metric
    }

    String getPrefix() {
        return prefix
    }
}
