package org.openkilda.functionaltests.model.stats

import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class IslStats {
    private static StatsHelper statsHelper
    private List<StatsResult> stats
    private String metricPrefix
    private Isl isl

    @Autowired
    IslStats(StatsHelper statsHelper){
        IslStats.statsHelper = statsHelper
    }

    static IslStats of(Isl isl){
        return new IslStats(isl)
    }

    IslStats(Isl isl) {
        this.metricPrefix = statsHelper.getMetricPrefix()
        this.isl = isl
        this.stats = statsHelper.getTsdb().queryDataPointsForLastFiveMinutes(IslStatsMetric.values() as List,
        "src_switch", isl.srcSwitch.dpId.toOtsdFormat())
    }

    StatsResult get(IslStatsMetric metric, Origin origin) {
        return stats.find {it.metric.equals(metricPrefix + metric.getValue())
        && it.tags.get("origin").equals(origin.getValue())
        && it.tags.get("src_port") == String.valueOf(isl.srcPort)
        && it.tags.get("dst_switch") == isl.dstSwitch.dpId.toOtsdFormat()
        && it.tags.get("dst_port") == String.valueOf(isl.dstPort)}
    }
}
