package org.openkilda.functionaltests.model.stats


import org.openkilda.model.SwitchId
import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.openkilda.testing.service.tsdb.model.StatsMetric
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static org.openkilda.functionaltests.model.stats.Direction.FORWARD


@Component
class FlowStats extends AbstractStats{

    @Autowired
    FlowStats(TsdbQueryService tsdbQueryService) {
        AbstractStats.tsdbQueryService = tsdbQueryService
    }

    static FlowStats of(String flowId) {
        return new FlowStats(flowId)
    }

    FlowStats(String flowId) {
        stats = tsdbQueryService.queryDataPointsForLastFiveMinutes(/__name__=~"%sflow.*", flowid="${flowId}"/)
    }

    StatsResult get(FlowStatsMetric metric, Direction direction = FORWARD) {
        return getStats(metric, { it.tags.get("direction").equals(direction.getValue()) })
    }

    StatsResult get(FlowStatsMetric metric, Direction direction = FORWARD, Origin origin) {
        return getStats(metric, {
            it.tags.get("direction").equals(direction.getValue())
                    && it.tags.get("origin").equals(origin.getValue())
        })
    }

    StatsResult get(FlowStatsMetric metric, SwitchId switchId, long cookie) {
        return getStats(metric, {
            it.tags.get("switchid").equals(switchId.toOtsdFormat())
                    && it.tags.get("cookie").equals(cookie.toString())
        })
    }

    StatsResult get(FlowStatsMetric metric, SwitchId switchId) {
        return getStats(metric, { it.tags.get("switchid").equals(switchId.toOtsdFormat()) })
    }

    StatsResult get(FlowStatsMetric metric, long cookie) {
        return getStats(metric, { it.tags.get("cookie").equals(cookie.toString()) })
    }

    StatsResult get(FlowStatsMetric metric, int inPort, int outPort) {
        return getStats(metric, {
            it.tags.get("inPort").equals(inPort.toString())
                    && it.tags.get("outPort").equals(outPort.toString())
        })
    }

    StatsResult get(FlowStatsMetric metric, Direction direction = FORWARD, Status status) {
        return getStats(metric, {
            it.tags.get("direction").equals(direction.getValue())
                    && it.tags.get("status").equals(status.getValue())
        })
    }
}
