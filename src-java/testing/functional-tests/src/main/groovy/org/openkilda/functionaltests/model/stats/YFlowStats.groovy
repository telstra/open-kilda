package org.openkilda.functionaltests.model.stats


import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.testing.service.otsdb.model.StatsResult

import static org.openkilda.functionaltests.model.stats.StatsMetric.Y_FLOW_Y_POINT_PACKETS
import static org.openkilda.functionaltests.model.stats.StatsMetric.Y_FLOW_SHARED_PACKETS


class YFlowStats {
    StatsHelper statsHelper
    StatsResult sharedPackets
    StatsResult yPointPackets
    SubFlowStats subFlow1Stats
    SubFlowStats subFlow2Stats

    YFlowStats(YFlow yFlow, Date from, StatsHelper statsHelper) {
        this.statsHelper = statsHelper
        def yFlowId = yFlow.getYFlowId()
        subFlow1Stats = new SubFlowStats(yFlow.getSubFlows().get(0), from)
        subFlow2Stats = new SubFlowStats(yFlow.getSubFlows().get(1), from)
        List<StatsQueryFilter> statsQueryFilters = [
                new StatsQueryFilter(Y_FLOW_Y_POINT_PACKETS, ["y_flow_id": yFlowId]),
                new StatsQueryFilter(Y_FLOW_SHARED_PACKETS, ["y_flow_id": yFlowId])
        ]
        statsQueryFilters.addAll(subFlow1Stats.getStatsQueryFilters())
        statsQueryFilters.addAll(subFlow2Stats.getStatsQueryFilters())
        List<StatsResult> statsList = statsHelper."get multiple stats results"(from, statsQueryFilters)
        sharedPackets = statsList.find(Y_FLOW_SHARED_PACKETS."result filter for metric"())
        yPointPackets = statsList.find(Y_FLOW_Y_POINT_PACKETS."result filter for metric"())
        subFlow1Stats."parse stats"(statsList)
        subFlow2Stats."parse stats"(statsList)
    }
}
