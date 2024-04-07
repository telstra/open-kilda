package org.openkilda.functionaltests.model.stats

import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SystemStats {
    private static TsdbQueryService tsdbQueryService
    private List<StatsResult> stats

    @Autowired
    SystemStats(TsdbQueryService tsdbQueryService) {
        SystemStats.tsdbQueryService = tsdbQueryService
    }

    static SystemStats of(SystemStatsMetric metric){
        return new SystemStats(metric)
    }

    SystemStats(SystemStatsMetric metric) {
        this.stats = tsdbQueryService.queryDataPointsForLastMinutes(/__name__="%s${metric.getValue()}"/, 10)
    }

    StatsResult get(String ruleCookieIdInHex) {
        return stats.find {it.tags.get("cookieHex").equals(ruleCookieIdInHex)}
    }
}
