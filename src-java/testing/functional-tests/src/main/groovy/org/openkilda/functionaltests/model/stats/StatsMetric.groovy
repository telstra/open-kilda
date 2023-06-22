package org.openkilda.functionaltests.model.stats

import org.openkilda.testing.service.otsdb.model.StatsResult

enum StatsMetric {
    Y_FLOW_SHARED_PACKETS("yflow.meter.shared.packets"),
    Y_FLOW_Y_POINT_PACKETS("yflow.meter.ypoint.packets"),
    FLOW_INGRESS_PACKETS("flow.ingress.packets"),
    FLOW_EGRESS_PACKETS("flow.packets")

    final String metric;

    StatsMetric(String metric) {
        this.metric = metric
    }

    def "result filter for metric"() {
        return {StatsResult result -> result.metric.endsWith(this.getMetric())}
    }
}