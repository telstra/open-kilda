package org.openkilda.functionaltests.model.stats

import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.openkilda.testing.service.tsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Component
class HaFlowStats extends AbstractStats {

    @Autowired
    HaFlowStats(TsdbQueryService tsdbQueryService) {
        AbstractStats.tsdbQueryService = tsdbQueryService
    }

    static HaFlowStats of(String haFlowId) {
        return new HaFlowStats(haFlowId)
    }

    HaFlowStats(String haFlowId) {
        stats = tsdbQueryService.queryDataPointsForLastFiveMinutes(
                /__name__=~"%s${HaFlowStatsMetric.prefix}*", ha_flow_id="${haFlowId}"/)
    }

    StatsResult get(HaFlowStatsMetric metric) {
        return getStats(metric, { it.metric.endsWith(metric.getValue()) })
    }

    StatsResult get(HaFlowStatsMetric metric, Direction direction) {
        return getStats(metric, {
            it.metric.endsWith(metric.getValue())
                    && it.tags.get("direction").equals(direction.getValue())
        })
    }

    StatsResult get(HaFlowStatsMetric metric, Direction direction, BaseFlowEndpointV2 endpoint) {
        return this.getBySwitchAndPort(metric,
                direction,
                endpoint.getSwitchId(),
                endpoint.getPortNumber())
    }

    StatsResult get(HaFlowStatsMetric metric, Direction direction, HaFlowSharedEndpoint endpoint) {
        return this.getBySwitchAndPort(metric,
                direction,
                endpoint.getSwitchId(),
                endpoint.getPortNumber())
    }

    private StatsResult getBySwitchAndPort(HaFlowStatsMetric metric, Direction direction, SwitchId switchId, int portNumber) {
        return getStats(metric, {
            it.metric.endsWith(metric.getValue())
                    && it.tags.get("direction").equals(direction.getValue())
                    && it.tags.get("switchid").equals(switchId.toOtsdFormat())
                    && it.tags.get("inPort").equals(portNumber.toString())
        })
    }
}
