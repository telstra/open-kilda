package org.openkilda.functionaltests.model.stats


import org.openkilda.model.SwitchId
import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SwitchStats extends AbstractStats {

    @Autowired
    SwitchStats(TsdbQueryService tsdbQueryService) {
        AbstractStats.tsdbQueryService = tsdbQueryService
    }

    static SwitchStats of(SwitchId switchId, int minutes = 5) {
        return new SwitchStats(switchId, minutes)
    }

    SwitchStats(SwitchId switchId, int minutes = 5) {
        stats = tsdbQueryService.queryDataPointsForLastMinutes(
                /__name__=~"%sswitch.*", switchid="${switchId.toOtsdFormat()}"/, minutes)
    }

    StatsResult get(SwitchStatsMetric metric, String ruleCookieIdInHex) {
        return getStats(metric, { it.tags.get("cookieHex").equals(ruleCookieIdInHex) })
    }

    StatsResult get(SwitchStatsMetric metric) {
        return getStats(metric)
    }

    StatsResult get(SwitchStatsMetric metric, int port) {
        return getStats(metric, { it.tags.get("port").equals(port.toString()) })
    }
}
