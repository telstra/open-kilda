package org.openkilda.functionaltests.spec.stats

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowFactory
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

import javax.inject.Provider

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.CLASS
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RTT
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_EGRESS_BITS
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_INGRESS_BITS
import static org.openkilda.functionaltests.model.stats.HaFlowStatsMetric.HA_FLOW_RAW_BITS
import static org.openkilda.messaging.payload.flow.FlowState.UP
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

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
    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    def setupSpec() {
        switchTriplet = topologyHelper.getSwitchTriplets(true, false).find {
            it.ep1 != it.ep2 && it.ep1 != it.shared && it.ep2 != it.shared &&
                    [it.shared, it.ep1, it.ep2].every { it.traffGens }
                    && it.ep2.getTraffGens().size() > 1 // needed for update flow test
        } ?: assumeTrue(false, "No suiting switches found")
        // Flow with low maxBandwidth to make meters to drop packets when traffgens can't generate high load
        haFlow = haFlowFactory.getBuilder(switchTriplet).withBandwidth(10).build().create(UP, CLASS)
        def exam = haFlow.traffExam(traffExamProvider.get(), haFlow.getMaximumBandwidth() * 10, traffgenRunDuration)
        wait(statsRouterRequestInterval * 3 + WAIT_OFFSET) {
            exam.run()
            statsHelper."force kilda to collect stats"()
            stats = haFlowStats.of(haFlow.haFlowId)
            stats.get(HA_FLOW_EGRESS_BITS, REVERSE).getDataPoints().size() > 2
            stats.get(HA_FLOW_INGRESS_BITS, REVERSE).getDataPoints().size() > 2
        }

    }

    def "System is able to collect #stat meter stats"() {
        expect: "#stat stats is available"
        assert stats.get(stat).hasNonZeroValues()

        where:
        stat << HaFlowStatsMetric.values().findAll { it.getValue().contains("meter.") }
    }

    def "System is able to collect #stat stats and they grow monotonically"() {
        expect: "#stat stats is available"
        assert stats.get(stat, direction).isGrowingMonotonically()

        where:
        [stat, direction] << [HaFlowStatsMetric.values().findAll { !it.getValue().contains("meter.") },
                              [FORWARD, REVERSE]].combinations()
    }


    def "System is able to collect latency stats for #description in #direction direction"() {
        expect: "The appropriate statistics data is available"
        wait(statsRouterRequestInterval) {
            assert flowStats.of(subFlow).get(FLOW_RTT, direction).hasNonZeroValues()
        }

        where:
        description  | direction | subFlow
        "sub-flow-a" | FORWARD   | haFlow.subFlows.flowId.find { it.contains("haflow-a") }
        "sub-flow-a" | REVERSE   | haFlow.subFlows.flowId.find { it.contains("haflow-a") }
        "sub-flow-b" | FORWARD   | haFlow.subFlows.flowId.find { it.contains("haflow-b") }
        "sub-flow-b" | REVERSE   | haFlow.subFlows.flowId.find { it.contains("haflow-b") }
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
    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Tags(LOW_PRIORITY)
    def "Stats are collected after #data.descr of HA-Flow are updated"() {
        given: "HA-Flow"
        def swT = topologyHelper.getSwitchTriplets(true, false)
                .findAll(SwitchTriplet.ALL_ENDPOINTS_DIFFERENT)
                .findAll(SwitchTriplet.TRAFFGEN_CAPABLE).shuffled().first()

        def haFlow = haFlowFactory.getRandom(swT, false)

        when: "Update the ha-flow"
        haFlow.tap(data.updateClosure)
        def updateRequest = haFlow.convertToUpdateRequest()
        haFlow.update(updateRequest)

        then: "Traffic passes through HA-Flow"
        haFlow.traffExam(traffExamProvider.get()).run().hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats are collected"
        wait(STATS_LOGGING_TIMEOUT) {
            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS, REVERSE,
                    haFlow.subFlows.shuffled().first()).hasNonZeroValues()

            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS, FORWARD,
                    haFlow.sharedEndpoint).hasNonZeroValues()
        }

        where:
        data << [
                [
                        descr        : "shared port and subflow ports",
                        updateClosure: { HaFlowExtended payload ->
                            payload.sharedEndpoint.portNumber = topologyHelper.getTraffgenPortBySwitchId(
                                    payload.sharedEndpoint.switchId)
                            payload.subFlows.each {
                                it.endpointPort = topologyHelper.getTraffgenPortBySwitchId(
                                        it.endpointSwitchId)
                            }
                        }
                ],
                [
                        descr        : "shared switch and subflow switches",
                        updateClosure: { HaFlowExtended payload ->
                            def newSharedSwitchId = payload.subFlows.first().endpointSwitchId
                            def newEp1SwitchId = payload.subFlows.last().endpointSwitchId
                            def newEp2SwitchId = payload.sharedEndpoint.switchId
                            payload.subFlows.first().endpointSwitchId = newEp1SwitchId
                            payload.subFlows.first().endpointPort = topologyHelper.getTraffgenPortBySwitchId(newEp1SwitchId)
                            payload.subFlows.last().endpointSwitchId = newEp2SwitchId
                            payload.subFlows.last().endpointPort = topologyHelper.getTraffgenPortBySwitchId(newEp2SwitchId)
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
        def haFlow = haFlowFactory.getRandom(swT)

        when: "Partially update HA-Flow"
        def newVlanId = haFlow.sharedEndpoint.vlanId - 1
        haFlow.partialUpdate(HaFlowPatchPayload.builder().sharedEndpoint(HaFlowPatchEndpoint.builder().vlanId(newVlanId).build()).build())

        haFlow.sharedEndpoint.tap { vlanId = newVlanId }
        def timeAfterUpdate = new Date().getTime()

        then: "traffic passes through flow"
        haFlow.traffExam(traffExamProvider.get()).run().hasTraffic()
        statsHelper."force kilda to collect stats"()

        then: "Stats are collected"
        wait(STATS_LOGGING_TIMEOUT) {
            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                    REVERSE,
                    haFlow.subFlows.shuffled().first()).hasNonZeroValuesAfter(timeAfterUpdate)
            haFlowStats.of(haFlow.haFlowId).get(HA_FLOW_RAW_BITS,
                    FORWARD,
                    haFlow.sharedEndpoint).hasNonZeroValuesAfter(timeAfterUpdate)
        }
    }
}
