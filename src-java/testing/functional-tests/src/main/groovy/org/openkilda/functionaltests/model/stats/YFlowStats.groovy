package org.openkilda.functionaltests.model.stats


import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.testing.service.otsdb.model.StatsResult

class YFlowStats {
    StatsHelper statsHelper
    StatsResult sharedBytes
    StatsResult yPointBytes
    SubFlowStats subFlow1Stats
    SubFlowStats subFlow2Stats

    YFlowStats(YFlow yFlow, Date from, StatsHelper statsHelper) {
        this.statsHelper = statsHelper
        def yFlowId = yFlow.getYFlowId()
        subFlow1Stats = new SubFlowStats(yFlow.getSubFlows().get(0), from)
        subFlow2Stats = new SubFlowStats(yFlow.getSubFlows().get(1), from)
        List<StatsQueryFilter> statsQueryFilters = [
                new StatsQueryFilter(StatsMetric.Y_FLOW_Y_POINT_BYTES, ["y_flow_id": yFlowId]),
                new StatsQueryFilter(StatsMetric.Y_FLOW_SHARED_BYTES, ["y_flow_id": yFlowId])
        ]
        statsQueryFilters.addAll(subFlow1Stats.getStatsQueryFilters())
        statsQueryFilters.addAll(subFlow2Stats.getStatsQueryFilters())
        List<StatsResult> statsList = statsHelper."get multiple stats results"(from, statsQueryFilters)
        sharedBytes = statsList.find {it.getMetric().endsWith(StatsMetric.Y_FLOW_SHARED_BYTES.getMetric())}
        yPointBytes = statsList.find {it.getMetric().endsWith(StatsMetric.Y_FLOW_Y_POINT_BYTES.getMetric())}
        subFlow1Stats."parse stats"(statsList)
        subFlow2Stats."parse stats"(statsList)
    }
}
