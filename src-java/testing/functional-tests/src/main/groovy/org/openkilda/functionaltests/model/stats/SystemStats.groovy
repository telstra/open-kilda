package org.openkilda.functionaltests.model.stats

import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SystemStats {
    private static StatsHelper statsHelper
    private List<StatsResult> stats
    private String metricPrefix

    @Autowired
    SystemStats(StatsHelper statsHelper){
        SystemStats.statsHelper = statsHelper
    }

    static SystemStats of(SystemStatsMetric metric){
        return new SystemStats(metric)
    }

    SystemStats(SystemStatsMetric metric) {
        this.metricPrefix = statsHelper.getMetricPrefix()
        this.stats = statsHelper.getTsdb().queryDataPointsForLastMinutes([metric], "switchid", "*", 10)
    }

    StatsResult get(String ruleCookieIdInHex) {
        return stats.find {it.tags.get("cookieHex").equals(ruleCookieIdInHex)}
    }
}
