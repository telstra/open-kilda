package org.openkilda.functionaltests.model.stats


import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Component
class YFlowStats {
    private static StatsHelper statsHelper
    private List<StatsResult> stats
    private String metricPrefix

    @Autowired
    YFlowStats(StatsHelper statsHelper){
        YFlowStats.statsHelper = statsHelper
    }

    static YFlowStats of(String yFlowId){
        return new YFlowStats(yFlowId)
    }

    YFlowStats(String yFlowId) {
        this.metricPrefix = statsHelper.getMetricPrefix()
        this.stats = statsHelper.getTsdb().queryDataPointsForLastFiveMinutes(
                /__name__=~"${metricPrefix}${YFlowStatsMetric.prefix}*", y_flow_id="${yFlowId}"/)
    }

    StatsResult get(YFlowStatsMetric metric) {
        return this.stats.find {it.metric.equals(metricPrefix + metric.getValue())}
    }
}
