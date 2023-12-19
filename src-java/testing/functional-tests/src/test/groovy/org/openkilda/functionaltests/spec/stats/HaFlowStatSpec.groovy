package org.openkilda.functionaltests.spec.stats

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.functionaltests.model.stats.HaFlowStats
import org.openkilda.functionaltests.model.stats.HaFlowStatsMetric
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.testing.Constants
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RTT
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_EGRESS_BITS
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_INGRESS_BITS
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_RAW_BITS
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Tags(LOW_PRIORITY)
@Narrative("Verify that statistic is collected for different type of Ha-Flow")
class HaFlowStatSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Shared
    int traffgenRunDuration = 5 //seconds
    @Shared
    HaFlowStats stats
    @Shared
    HaFlow haFlow
    @Autowired
    @Shared
    HaFlowStats haFlowStats
    @Shared
    SwitchTriplet switchTriplet
    @Autowired
    @Shared
    FlowStats flowStats

    def setupSpec() {
        switchTriplet = topologyHelper.getSwitchTriplets(true, false).find {
            it.ep1 != it.ep2 && it.ep1 != it.shared && it.ep2 != it.shared &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens }
                    && it.ep2.getTraffGens().size() > 1 // needed for update flow test
        } ?: assumeTrue(false, "No suiting switches found")
        // Flow with low maxBandwidth to make meters to drop packets when traffgens can't generate high load
        haFlow = haFlowHelper.addHaFlow(
                haFlowHelper.randomHaFlow(switchTriplet).tap {maximumBandwidth = 10})
        def exam = haFlowHelper.getTraffExam(haFlow, haFlow.getMaximumBandwidth() * 10, traffgenRunDuration)
        wait(statsRouterRequestInterval * 3 + WAIT_OFFSET) {
            exam.run()
            statsHelper."force kilda to collect stats"()
            stats = haFlowStats.of(haFlow.getHaFlowId())
            stats.get(HA_FLOW_EGRESS_BITS, REVERSE).getDataPoints().size() > 2
            stats.get(HA_FLOW_INGRESS_BITS, REVERSE).getDataPoints().size() > 2
        }

    }

    @Unroll
    def "System is able to collect #stat meter stats"() {
        expect: "#stat stats is available"
        assert stats.get(stat).hasNonZeroValues()

        where:
        stat << HaFlowStatsMetric.values().findAll { it.getValue().contains("meter.") }
    }

    @Unroll
    def "System is able to collect #stat stats and they grow monotonically"() {
        expect: "#stat stats is available"
        assert stats.get(stat, direction).isGrowingMonotonically()

        where:
        [stat, direction] << [HaFlowStatsMetric.values().findAll { !it.getValue().contains("meter.") },
                              [FORWARD, REVERSE]].combinations()
    }

    @Unroll
    def "System is able to collect latency stats for subflows"() {
        expect: "#stat stats is available"
        wait(statsRouterRequestInterval) {
            assert flowStats.of(subFlow).get(FLOW_RTT, direction).hasNonZeroValues()
        }

        where:
        [subFlow, direction] << [haFlow.subFlows*.flowId,
                              [FORWARD, REVERSE]].combinations()
    }

    def cleanupSpec() {
        haFlow && haFlowHelper.deleteHaFlow(haFlow.getHaFlowId())
    }
}

@Narrative("Verify that statistic is collected after various Ha-Flow updates")
class HaFlowUpdateStatSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Autowired
    @Shared
    HaFlowStats haFlowStats

    @Tags(LOW_PRIORITY)
    def "Stats are collected after #data.descr of Ha-Flow are updated"() {
        given: "Ha-Flow"
        def swT = topologyHelper.getSwitchTriplets(true, false)
                .findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT)
                .findAll(SwitchTriplet.TRAFFGEN_CAPABLE).shuffled().first()
        def haFlowRequest = haFlowHelper.randomHaFlow(swT, false)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        when: "Update the ha-flow"
        haFlow.tap(data.updateClosure)
        def update = haFlowHelper.convertToUpdate(haFlow)
        haFlowHelper.updateHaFlow(haFlow.haFlowId, update)

        then: "Traffic passes through Ha-Flow"
        def exam = haFlowHelper.getTraffExam(haFlow)
        exam.run().hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats are collected"
        wait(STATS_LOGGING_TIMEOUT) {
            haFlowStats.of(haFlow.getHaFlowId()).get(HA_FLOW_RAW_BITS, REVERSE,
                    haFlow.getSubFlows().shuffled().first().getEndpoint())
                    .hasNonZeroValues()
            haFlowStats.of(haFlow.getHaFlowId()).get(HA_FLOW_RAW_BITS, FORWARD,
                    haFlow.getSharedEndpoint())
                    .hasNonZeroValues()
        }
        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)

        where:
        data << [
                [
                        descr        : "shared port and subflow ports",
                        updateClosure: { HaFlow payload ->
                            payload.sharedEndpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(
                                    payload.getSharedEndpoint().getSwitchId())
                            payload.subFlows.each {
                                it.endpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(
                                        it.getEndpoint().getSwitchId())
                            }
                        }
                ],
                [
                        descr        : "shared switch and subflow switches",
                        updateClosure: { HaFlow payload ->
                            def newSharedSwitchId = payload.getSubFlows().get(0).getEndpoint().getSwitchId()
                            def newEp1SwitchId = payload.getSubFlows().get(1).getEndpoint().getSwitchId()
                            def newEp2SwitchId = payload.getSharedEndpoint().getSwitchId()
                            payload.subFlows[0].endpoint.switchId = newEp1SwitchId
                            payload.subFlows[0].endpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(newEp1SwitchId)
                            payload.subFlows[1].endpoint.switchId = newEp2SwitchId
                            payload.subFlows[1].endpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(newEp2SwitchId)
                            payload.sharedEndpoint.switchId = newSharedSwitchId
                            payload.sharedEndpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(newSharedSwitchId)
                        }
                ]
        ]
    }

    @Tags(LOW_PRIORITY)
    def "Stats are collected after partial update (shared endpoint VLAN id) of Ha-Flow"() {
        given: "Ha-Flow"
        def swT = topologyHelper.getSwitchTriplets(true, false)
                .findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT)
                .findAll(SwitchTriplet.TRAFFGEN_CAPABLE).shuffled().first()
        def haFlowRequest = haFlowHelper.randomHaFlow(swT, true)
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        when: "Partially update Ha-Flow"
        def newVlanId = haFlow.getSharedEndpoint().getVlanId() - 1
        haFlowHelper.partialUpdateHaFlow(haFlow.haFlowId, HaFlowPatchPayload.builder()
                .sharedEndpoint(HaFlowPatchEndpoint.builder().vlanId(newVlanId).build()
                ).build())
        haFlow.sharedEndpoint.tap { vlanId = newVlanId }
        def timeAfterUpdate = new Date().getTime()

        then: "traffic passes through flow"
        def exam = haFlowHelper.getTraffExam(haFlow)
        exam.run().hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats are collected"
        wait(STATS_LOGGING_TIMEOUT) {
            haFlowStats.of(haFlow.getHaFlowId()).get(HA_FLOW_RAW_BITS,
                    REVERSE,
                    haFlow.getSubFlows().shuffled().first().getEndpoint()).hasNonZeroValuesAfter(timeAfterUpdate)
            haFlowStats.of(haFlow.getHaFlowId()).get(HA_FLOW_RAW_BITS,
                    FORWARD,
                    haFlow.getSharedEndpoint()).hasNonZeroValuesAfter(timeAfterUpdate)
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }
}
