package org.openkilda.functionaltests.model.stats

import org.openkilda.functionaltests.helpers.StatsHelper
import org.openkilda.northbound.dto.v2.yflows.SubFlow
import org.openkilda.testing.service.otsdb.model.StatsResult

import static org.openkilda.functionaltests.model.stats.StatsMetric.FLOW_EGRESS_PACKETS
import static org.openkilda.functionaltests.model.stats.StatsMetric.FLOW_INGRESS_PACKETS

class SubFlowStats {
    private final static Closure<Boolean> FORWARD = {StatsResult result -> result.tags.containsValue("forward")}
    private final static Closure<Boolean> REVERSE = {StatsResult result -> result.tags.containsValue("reverse")}
    Date from
    List <StatsQueryFilter> statsQueryFilters
    StatsResult packetsForwardIngress
    StatsResult packetsForwardEgress
    StatsResult packetsReverseIngress
    StatsResult packetsReverseEgress
    SubFlow subFlow

    SubFlowStats(SubFlow flow, Date from) {
        def flowIdTag = ["flowid":  flow.getFlowId()]
        def forwardTag = ["direction": "forward"]
        def reverseTag = ["direction": "reverse"]
        this.statsQueryFilters = [
                new StatsQueryFilter(FLOW_EGRESS_PACKETS, flowIdTag + forwardTag),
                new StatsQueryFilter(FLOW_EGRESS_PACKETS, flowIdTag + reverseTag),
                new StatsQueryFilter(FLOW_INGRESS_PACKETS, flowIdTag + forwardTag),
                new StatsQueryFilter(FLOW_INGRESS_PACKETS, flowIdTag + reverseTag)
        ]
        this.from = from
        this.subFlow = flow
    }

    void "parse stats" (List<StatsResult> statsList) {
        def filteredStats = statsList.findAll {it.tags.containsValue(this.subFlow.getFlowId())}
        this.packetsForwardIngress = filteredStats.findAll(FLOW_INGRESS_PACKETS."result filter for metric"())
                .find(FORWARD)
        this.packetsReverseIngress = filteredStats.findAll(FLOW_INGRESS_PACKETS."result filter for metric"())
                .find(REVERSE)
        this.packetsForwardEgress = filteredStats.findAll(FLOW_EGRESS_PACKETS."result filter for metric"())
                .find(FORWARD)
        this.packetsReverseEgress = filteredStats.findAll(FLOW_EGRESS_PACKETS."result filter for metric"())
                .find(REVERSE)
    }

    void "receive stats" (StatsHelper statsHelper) {
        List<StatsResult> statsList = statsHelper."get multiple stats results"(from, this.statsQueryFilters)
        "parse stats"(statsList)
    }
}
