package org.openkilda.functionaltests.model.stats

import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.model.SwitchId
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SwitchStats {
    private static StatsHelper statsHelper
    private List<StatsResult> stats
    private String metricPrefix

    @Autowired
    SwitchStats(StatsHelper statsHelper){
        SwitchStats.statsHelper = statsHelper
    }

    static SwitchStats of(SwitchId switchId, int minutes = 5){
        return new SwitchStats(switchId, minutes)
    }

    SwitchStats(SwitchId switchId, int minutes) {
        this.metricPrefix = statsHelper.getMetricPrefix()
        this.stats = statsHelper.getTsdb().queryDataPointsForLastMinutes(
                /__name__=~"${metricPrefix}${SwitchStatsMetric.prefix}*", switchid="${switchId.toOtsdFormat()}"/, minutes)
    }

    StatsResult get(SwitchStatsMetric metric, String ruleCookieIdInHex) {
        return stats.find {it.metric.equals(metricPrefix + metric.getValue())
                && it.tags.get("cookieHex").equals(ruleCookieIdInHex)}
    }

    StatsResult get(SwitchStatsMetric metric) {
        return stats.find {it.metric.equals(metricPrefix + metric.getValue())}
    }

    StatsResult get(SwitchStatsMetric metric, int port) {
        return stats.find {it.metric.equals(metricPrefix + metric.getValue())
        && it.tags.get("port").equals(port.toString())}
    }
}
