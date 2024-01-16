package org.openkilda.functionaltests.model.stats


import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class YFlowStats extends AbstractStats {

    @Autowired
    YFlowStats(TsdbQueryService tsdbQueryService) {
        AbstractStats.tsdbQueryService = tsdbQueryService
    }

    static YFlowStats of(String yFlowId){
        return new YFlowStats(yFlowId)
    }

    YFlowStats(String yFlowId) {
        stats = tsdbQueryService.queryDataPointsForLastFiveMinutes(
                /__name__=~"%s${YFlowStatsMetric.prefix}*", y_flow_id="${yFlowId}"/)
    }

    StatsResult get(YFlowStatsMetric metric) {
        return getStats(metric)
    }
}
