package org.openkilda.functionaltests.model.stats

import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class IslStats {
    private static StatsHelper statsHelper
    private List<StatsResult> stats
    private String metricPrefix

    @Autowired
    IslStats(StatsHelper statsHelper){
        IslStats.statsHelper = statsHelper
    }

    static IslStats of(TopologyDefinition.Isl isl){
        return new IslStats(isl)
    }

    IslStats(TopologyDefinition.Isl isl) {
        this.metricPrefix = statsHelper.getMetricPrefix()
        this.stats = statsHelper.getTsdb().queryDataPointsForLastFiveMinutes(
                /__name__=~"${metricPrefix}isl.*", src_switch="${isl.srcSwitch.dpId.toOtsdFormat()}",\
src_port="${String.valueOf(isl.srcPort)}",\
dst_switch="${isl.dstSwitch.dpId.toOtsdFormat()}",\
dst_port="${String.valueOf(isl.dstPort)}"/)
    }

    StatsResult get(IslStatsMetric metric, Origin origin) {
        return stats.find {it.metric.equals(metricPrefix + metric.getMetric())
        && it.tags.get("origin").equals(origin.getValue())}
    }
}
