package org.openkilda.functionaltests.model.stats

class StatsQueryFilter {
    StatsMetric metric;
    Map<String, Object> tags;

    StatsQueryFilter(StatsMetric meter, Map<String, Object> tags) {
        this.metric = meter
        this.tags = tags
    }
}
