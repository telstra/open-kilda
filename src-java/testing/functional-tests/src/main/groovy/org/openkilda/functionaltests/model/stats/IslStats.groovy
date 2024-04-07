package org.openkilda.functionaltests.model.stats


import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class IslStats extends AbstractStats {
    private Isl isl

    @Autowired
    IslStats(TsdbQueryService tsdbQueryService) {
        AbstractStats.tsdbQueryService = tsdbQueryService
    }

    static IslStats of(Isl isl) {
        return new IslStats(isl)
    }

    IslStats(Isl isl) {
        this.isl = isl
        stats = tsdbQueryService.queryDataPointsForLastFiveMinutes(
                /__name__=~"%sisl.*", src_switch="${isl.srcSwitch.dpId.toOtsdFormat()}",\
src_port="${String.valueOf(isl.srcPort)}",\
dst_switch="${isl.dstSwitch.dpId.toOtsdFormat()}",\
dst_port="${String.valueOf(isl.dstPort)}"/)
    }

    StatsResult get(IslStatsMetric metric, Origin origin) {
        return getStats(metric, {
                    it.tags.get("origin").equals(origin.getValue())
                    && it.tags.get("src_port") == String.valueOf(isl.srcPort)
                    && it.tags.get("dst_switch") == isl.dstSwitch.dpId.toOtsdFormat()
                    && it.tags.get("dst_port") == String.valueOf(isl.dstPort)
        })
    }
}
