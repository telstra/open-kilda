package org.openkilda.functionaltests.model.stats

enum IslStatsMetric {
    ISL_RTT("isl.rtt")



    final String metric;

    IslStatsMetric(String metric) {
        this.metric = metric
    }
}
