package org.openkilda.functionaltests.model.stats


import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.model.SwitchId
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException

import static org.openkilda.functionaltests.model.stats.Direction.FORWARD


@Component
class FlowStats {
    private static StatsHelper statsHelper
    private List<StatsResult> stats
    private String metricPrefix
    private static List<FlowStatsMetric> defaultMetricsList =
            FlowStatsMetric.values() as List - [FlowStatsMetric.LATENCY, FlowStatsMetric.FLOW_RTT]

    @Autowired
    FlowStats(StatsHelper statsHelper) {
        FlowStats.statsHelper = statsHelper
    }

    static FlowStats of(String flowId) {
        return new FlowStats(flowId)
    }

    static FlowStats latencyOf(String flowId) {
        return new FlowStats(flowId, [FlowStatsMetric.LATENCY])
    }

    static FlowStats rttOf(String flowId) {
        return new FlowStats(flowId, [FlowStatsMetric.FLOW_RTT])
    }

    FlowStats(String flowId, List<FlowStatsMetric> metrics = defaultMetricsList) {
        this.metricPrefix = statsHelper.getMetricPrefix()
        // OpenTSDB returns error instead of empty response if no metric or tag from filter exists
        try {
            this.stats = statsHelper.getTsdb().queryDataPointsForLastFiveMinutes(metrics,
                    "flowid", flowId)
        }
        catch (HttpClientErrorException exception) {
            if (exception.getMessage().toString().contains(flowId)) {
                this.stats = null
            } else {
                throw exception
            }
        }
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
