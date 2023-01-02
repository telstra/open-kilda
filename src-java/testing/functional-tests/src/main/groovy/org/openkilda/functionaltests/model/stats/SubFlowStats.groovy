package org.openkilda.functionaltests.model.stats

import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.northbound.dto.v2.yflows.SubFlow
import org.openkilda.testing.service.otsdb.model.StatsResult

class SubFlowStats {
    Date from
    List <StatsQueryFilter> statsQueryFilters
    StatsResult packetsIngress
    StatsResult packetsEgress
    StatsResult packetsRaw
    StatsResult packets
    SubFlow subFlow

    SubFlowStats(SubFlow flow, Date from) {
        def flowTags = ["flowid":  flow.getFlowId()]
        this.statsQueryFilters = [
                new StatsQueryFilter(StatsMetric.FLOW_RAW_PACKETS, flowTags),
                new StatsQueryFilter(StatsMetric.FLOW_PACKETS, flowTags),
                new StatsQueryFilter(StatsMetric.FLOW_INGRESS_PACKETS, flowTags),
                new StatsQueryFilter(StatsMetric.FLOW_EGRESS_PACKETS, flowTags)
        ]
        this.from = from
        this.subFlow = flow
    }

    void "parse stats" (List<StatsResult> statsList) {
        def filteredStats = statsList.findAll() {it.tags.containsValue(this.subFlow.getFlowId())}
        this.packetsIngress = filteredStats.find {it.metric.endsWith(StatsMetric.FLOW_INGRESS_PACKETS.getMetric())}
        this.packetsEgress= filteredStats.find {it.metric.endsWith(StatsMetric.FLOW_EGRESS_PACKETS.getMetric())}
        this.packetsRaw = filteredStats.find {it.metric.endsWith(StatsMetric.FLOW_RAW_PACKETS.getMetric())}
        this.packets = filteredStats.find {it.metric.endsWith(StatsMetric.FLOW_PACKETS.getMetric())}
    }

    void "receive stats" (StatsHelper statsHelper) {
        List<StatsResult> statsList = statsHelper."get multiple stats results"(from, this.statsQueryFilters)
        "parse stats"(statsList)
    }
}
