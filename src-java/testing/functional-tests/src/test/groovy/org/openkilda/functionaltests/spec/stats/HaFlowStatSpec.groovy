package org.openkilda.functionaltests.spec.stats

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
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

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.functionaltests.model.stats.HaFlowStats
import org.openkilda.functionaltests.model.stats.HaFlowStatsMetric
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import javax.inject.Provider

@Tags([LOW_PRIORITY, HA_FLOW])
@Narrative("Verify that statistic is collected for different type of HA-Flow")
class HaFlowStatSpec extends HealthCheckSpecification {
    @Shared
    int traffgenRunDuration = 5 //seconds
    @Shared
    HaFlowStats stats
    @Shared
    HaFlowExtended haFlow
    @Autowired
    @Shared
    HaFlowStats haFlowStats
    @Shared
    SwitchTriplet switchTriplet
    @Autowired
    @Shared
    FlowStats flowStats
    @Shared
    @Autowired
    Provider<TraffExamService> traffExamProvider

    def setupSpec() {
        switchTriplet = topologyHelper.getSwitchTriplets(true, false).find {
            it.ep1 != it.ep2 && it.ep1 != it.shared && it.ep2 != it.shared &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens }
                    && it.ep2.getTraffGens().size() > 1 // needed for update flow test
        } ?: assumeTrue(false, "No suiting switches found")
        // Flow with low maxBandwidth to make meters to drop packets when traffgens can't generate high load
        haFlow = HaFlowExtended.build(switchTriplet, northboundV2, topology).withBandwidth(10).create()
        def exam = haFlow.traffExam(traffExamProvider, haFlow.getMaximumBandwidth() * 10, traffgenRunDuration)
        wait(statsRouterRequestInterval * 3 + WAIT_OFFSET) {
            exam.run()
            statsHelper."force kilda to collect stats"()
            stats = haFlowStats.of(haFlow.haFlowId)
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
        haFlow && haFlow.delete()
    }
}

@Narrative("Verify that statistic is collected after various HA-Flow updates")
@Tags([HA_FLOW])
class HaFlowUpdateStatSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowStats haFlowStats
    @Shared
    @Autowired
    Provider<TraffExamService> traffExamProvider

    @Tags(LOW_PRIORITY)
    def "Stats are collected after #data.descr of HA-Flow are updated"() {
        given: "HA-Flow"
        def swT = topologyHelper.getSwitchTriplets(true, false)
                .findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT)
                .findAll(SwitchTriplet.TRAFFGEN_CAPABLE).shuffled().first()

        def haFlow = HaFlowExtended.build(swT, northboundV2, topology, false).create()

        when: "Update the ha-flow"
        haFlow.tap(data.updateClosure)
        def updateRequest = haFlow.convertToUpdateRequest()
        haFlow.update(updateRequest)

        then: "Traffic passes through HA-Flow"
        haFlow.traffExam(traffExamProvider).run().hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats are collected"
        wait(STATS_LOGGING_TIMEOUT) {
            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS, REVERSE,
                    haFlow.subFlows.shuffled().first().endpoint).hasNonZeroValues()

            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS, FORWARD,
                    haFlow.sharedEndpoint).hasNonZeroValues()
        }
        cleanup:
        haFlow && haFlow.delete()

        where:
        data << [
                [
                        descr        : "shared port and subflow ports",
                        updateClosure: { HaFlowExtended payload ->
                            payload.sharedEndpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(
                                    payload.sharedEndpoint.switchId)
                            payload.subFlows.each {
                                it.endpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(
                                        it.endpoint.switchId)
                            }
                        }
                ],
                [
                        descr        : "shared switch and subflow switches",
                        updateClosure: { HaFlowExtended payload ->
                            def newSharedSwitchId = payload.subFlows.first().endpoint.switchId
                            def newEp1SwitchId = payload.subFlows.last().endpoint.switchId
                            def newEp2SwitchId = payload.sharedEndpoint.switchId
                            payload.subFlows.first().endpoint.switchId = newEp1SwitchId
                            payload.subFlows.first().endpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(newEp1SwitchId)
                            payload.subFlows.last().endpoint.switchId = newEp2SwitchId
                            payload.subFlows.last().endpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(newEp2SwitchId)
                            payload.sharedEndpoint.switchId = newSharedSwitchId
                            payload.sharedEndpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(newSharedSwitchId)
                        }
                ]
        ]
    }

    @Tags(LOW_PRIORITY)
    def "Stats are collected after partial update (shared endpoint VLAN id) of HA-Flow"() {
        given: "HA-Flow"
        def swT = topologyHelper.getSwitchTriplets(true, false)
                .findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT)
                .findAll(SwitchTriplet.TRAFFGEN_CAPABLE).shuffled().first()
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()

        when: "Partially update HA-Flow"
        def newVlanId = haFlow.sharedEndpoint.vlanId - 1
        haFlow.partialUpdate(HaFlowPatchPayload.builder().sharedEndpoint(HaFlowPatchEndpoint.builder().vlanId(newVlanId).build()).build())

        haFlow.sharedEndpoint.tap { vlanId = newVlanId }
        def timeAfterUpdate = new Date().getTime()

        then: "traffic passes through flow"
        haFlow.traffExam(traffExamProvider).run().hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats are collected"
        wait(STATS_LOGGING_TIMEOUT) {
            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                    REVERSE,
                    haFlow.subFlows.shuffled().first().endpoint).hasNonZeroValuesAfter(timeAfterUpdate)
            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                    FORWARD,
                    haFlow.sharedEndpoint).hasNonZeroValuesAfter(timeAfterUpdate)
        }

        cleanup:
        haFlow && haFlow.delete()
    }
}
