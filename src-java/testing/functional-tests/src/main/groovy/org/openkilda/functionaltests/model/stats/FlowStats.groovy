package org.openkilda.functionaltests.model.stats


import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.model.SwitchId
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static org.openkilda.functionaltests.model.stats.Direction.FORWARD


@Component
class FlowStats {
    private static StatsHelper statsHelper
    private List<StatsResult> stats
    private String metricPrefix

    @Autowired
    FlowStats(StatsHelper statsHelper) {
        FlowStats.statsHelper = statsHelper
    }

    static FlowStats of(String flowId) {
        return new FlowStats(flowId)
    }

    FlowStats(String flowId) {
        this.metricPrefix = statsHelper.getMetricPrefix()
        this.stats = statsHelper.getTsdb().queryDataPointsForLastFiveMinutes(
                /__name__=~"${metricPrefix}${FlowStatsMetric.prefix}*", flowid="${flowId}"/)
    }

    StatsResult get(FlowStatsMetric metric, Direction direction = FORWARD) {
        def stats = stats.findAll {
            it.metric.equals(metricPrefix + metric.getValue())
                    && it.tags.get("direction").equals(direction.getValue())
        }
        return stats ? stats.get(0) : null
    }

    StatsResult get(FlowStatsMetric metric, Direction direction = FORWARD, Origin origin) {
        def stats = stats.findAll {
            it.metric.equals(metricPrefix + metric.getValue())
                    && it.tags.get("direction").equals(direction.getValue())
                    && it.tags.get("origin").equals(origin.getValue())
        }
        return stats ? stats.get(0) : null
    }

    StatsResult get(FlowStatsMetric metric, SwitchId switchId, long cookie) {
        return stats.find {
            it.metric.equals(metricPrefix + metric.getValue())
                    && it.tags.get("switchid").equals(switchId.toOtsdFormat())
                    && it.tags.get("cookie").equals(cookie.toString())
        }
    }

    StatsResult get(FlowStatsMetric metric, SwitchId switchId) {
        return stats.find {
            it.metric.equals(metricPrefix + metric.getValue())
                    && it.tags.get("switchid").equals(switchId.toOtsdFormat())
        }
    }

    StatsResult get(FlowStatsMetric metric, long cookie) {
        return stats.find {
            it.metric.equals(metricPrefix + metric.getValue())
                    && it.tags.get("cookie").equals(cookie.toString())
        }
    }

    StatsResult get(FlowStatsMetric metric, int inPort, int outPort) {
        return stats.find {
            it.metric.equals(metricPrefix + metric.getValue())
            && it.tags.get("inPort").equals(inPort.toString())
                    && it.tags.get("outPort").equals(outPort.toString())
        }
    }

    StatsResult get(FlowStatsMetric metric, Direction direction = FORWARD, Status status) {
        def stats = stats.findAll {
            it.metric.equals(metricPrefix + metric.getValue())
                    && it.tags.get("direction").equals(direction.getValue())
                    && it.tags.get("status").equals(status.getValue())
        }
        return stats ? stats.get(0) : null
    }
}
