package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.HaFlowFactory
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Shared

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.LATENCY
import static org.openkilda.functionaltests.model.stats.Status.ERROR
import static org.openkilda.functionaltests.model.stats.Status.SUCCESS
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Narrative("""This spec tests 'periodic ping' functionality.""")
@Tags([HA_FLOW])
class HaFlowPingSpec extends HealthCheckSpecification {
    @Value('${flow.ping.interval}')
    int pingInterval

    @Autowired
    @Shared
    FlowStats flowStats

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Tags([LOW_PRIORITY])
    def "Able to turn off periodic pings on an HA-Flow"() {
        given: "An HA-Flow with periodic pings turned on"
        def swT = switchTriplets.all(true).findSwitchTripletWithYPointOnSharedEp()
        def haFlow = haFlowFactory.getBuilder(swT).withPeriodicPing(true)
                .build().create()
        assert haFlow.periodicPings

        and: "Neither of the sub-flows end on Y-Point (ping is disabled for such kind of HA-Flow)"
        def paths = haFlow.retrievedAllEntityPaths()
        assert !paths.sharedPath.path.forward.nodes.nodes

        wait(STATS_LOGGING_TIMEOUT) {
            assert flowStats.of(haFlow.subFlows.first().haSubFlowId).get(LATENCY, REVERSE).hasNonZeroValues()
        }
        when: "Turn off periodic pings"
        def updatedHaFlow = haFlow.partialUpdate(HaFlowPatchPayload.builder().periodicPings(false).build())

        then: "Periodic pings are really disabled"
        !updatedHaFlow.periodicPings
        !haFlow.retrieveDetails().periodicPings
        def afterUpdateTime = new Date().getTime()

        and: "There is no metrics for HA-subflows"
        timedLoop(pingInterval + WAIT_OFFSET) {
            [haFlow.subFlows*.haSubFlowId, [FORWARD, REVERSE]].combinations().each {String flowId, Direction direction ->
                    def stats = flowStats.of(flowId).get(LATENCY, direction)
                    assert stats != null && !stats.hasNonZeroValuesAfter(afterUpdateTime + 1000)
            }
        }
    }

    @Tags([LOW_PRIORITY, ISL_RECOVER_ON_FAIL])
    def "Unable to ping one of the HA-subflows via periodic pings if related ISL is broken"() {
        given: "Pinned HA-flow with periodic pings turned on which won't be rerouted after ISL fails"
        def swT = switchTriplets.all(true).findSwitchTripletWithYPointOnSharedEp()
        def haFlow = haFlowFactory.getBuilder(swT).withPeriodicPing(true).withPinned(true)
                .build().create()
        assert haFlow.periodicPings

        and: "Neither of the sub-flows end on Y-Point (ping is disabled for such kind of HA-Flow)"
        def paths = haFlow.retrievedAllEntityPaths()
        assert !paths.sharedPath.path.forward.nodes.nodes

        String subFlowWithBrokenIsl = paths.subFlowPaths.first().flowId
        def islToFail = paths.subFlowPaths.find { it.flowId == subFlowWithBrokenIsl}
                .path.forward.getInvolvedIsls().first()
        String subFlowWithActiveIsl = paths.subFlowPaths.flowId.find { it != subFlowWithBrokenIsl }

        when: "Fail one of the HA-subflows ISL (bring switch port down)"
        islHelper.breakIsl(islToFail)
        def afterFailTime = new Date().getTime()

        then: "Periodic pings are still enabled"
        haFlow.retrieveDetails().periodicPings

        and: "Metrics for the HA-subflow with broken ISL have 'error' status in tsdb"
        wait(pingInterval + WAIT_OFFSET * 2, 2) {
            def stats = flowStats.of(subFlowWithBrokenIsl)
            [FORWARD, REVERSE].each { Direction direction ->
                stats.get(LATENCY, direction, ERROR).dataPoints.keySet().find { it >= afterFailTime}
            }
        }

        and: "Metrics for HA-subflow with active ISL have 'success' status in tsdb"
        wait(pingInterval + WAIT_OFFSET * 4, 2) {
            def stats = flowStats.of(subFlowWithActiveIsl)
            [FORWARD, REVERSE].each { Direction direction ->
                stats.get(LATENCY, direction, SUCCESS).hasNonZeroValuesAfter(afterFailTime)
            }
        }
    }

    def "Able to turn on periodic pings on an Ha-flow"() {
        given: "Create an Ha-flow without periodic pings turned on"
        def swT = switchTriplets.all(true).findSwitchTripletWithYPointOnSharedEp()
        def beforeCreationTime = new Date().getTime()
        def haFlow = haFlowFactory.getRandom(swT)

        and: "Neither of the sub-flows end on Y-Point (ping is disabled for such kind of HA-Flow)"
        def paths = haFlow.retrievedAllEntityPaths()
        assert !paths.sharedPath.path.forward.nodes.nodes

        when: "Turn on periodic ping on an HA-Flow"
        haFlow.partialUpdate(HaFlowPatchPayload.builder().periodicPings(true).build())
        then: "Periodic pings are really enabled"
        haFlow.retrieveDetails().periodicPings

        and: "Packet counter on catch ping rules grows due to pings happening"
        String encapsulationType = haFlow.encapsulationType.toString()
        def sharedSwitchPacketCount = swT.shared.rulesManager.pingRule(encapsulationType).packetCount
        def ep1SwitchPacketCount = swT.ep1.rulesManager.pingRule(encapsulationType).packetCount
        def ep2SwitchPacketCount = swT.ep2.rulesManager.pingRule(encapsulationType).packetCount

        wait(pingInterval + STATS_LOGGING_TIMEOUT + WAIT_OFFSET) {
            def sharedPacketCountNow = swT.shared.rulesManager.pingRule(encapsulationType).packetCount
            def ep1PacketCountNow = swT.ep1.rulesManager.pingRule(encapsulationType).packetCount
            def ep2PacketCountNow = swT.ep2.rulesManager.pingRule(encapsulationType).packetCount

            assert sharedPacketCountNow > sharedSwitchPacketCount && ep1PacketCountNow > ep1SwitchPacketCount &&
                    ep2PacketCountNow > ep2SwitchPacketCount
        }

        and: "Metrics for HA-subflows have 'success' in tsdb"
        wait(pingInterval + WAIT_OFFSET, 2) {
            withPool {
                [haFlow.subFlows*.haSubFlowId, [FORWARD, REVERSE]].combinations().eachParallel {
                    String flowId, Direction direction ->
                        flowStats.of(flowId).get(LATENCY, direction, SUCCESS).hasNonZeroValuesAfter(beforeCreationTime)
                }
            }
        }
    }


    @Tags([LOW_PRIORITY])
    def "Unable to ping HA-Flow when one of subflows is one-switch one"() {
        given: "HA-Flow which has one-switch subflow"
        def switchTriplet = switchTriplets.all(true, true)
                .findSwitchTripletForOneSwitchSubflow()
        assumeTrue(switchTriplet != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowFactory.getRandom(switchTriplet)

        when: "Ping HA-Flow"
        def response = haFlow.pingAndCollectDiscrepancies()

        then: "HA-Flow ping is not successful and the appropriate error message has been returned"
        verifyAll {
            assert !response.pingSuccess
            assert response.error == "Temporary disabled. HaFlow ${haFlow.haFlowId} has one sub-flow with endpoint switch equals to Y-point switch"
            assert response.subFlowsDiscrepancies.isEmpty()
        }
    }

    @Tags([LOW_PRIORITY])
    def "Able to ping HA-Flow when neither of the sub-flows end on Y-Point"() {
        given: "HA-Flow has been created"
        def swT = switchTriplets.all().nonNeighbouring().findSwitchTripletWithYPointOnSharedEp()
        def haFlow = haFlowFactory.getRandom(swT)

        and: "Neither of the sub-flows end on Y-Point (ping is disabled for such kind of HA-Flow)"
        def paths = haFlow.retrievedAllEntityPaths()
        assert !paths.sharedPath.path.forward.nodes.nodes

        when: "Ping HA-Flow"
        def pingResult = haFlow.ping()

        then: "HA-Flow ping is successful"
        pingResult.isPingSuccess()
        pingResult.haFlowId == haFlow.haFlowId
        !pingResult.error

        when: "Break one sub-flow by removing flow rules from the intermediate switch"
        def haFlowPath = haFlow.retrievedAllEntityPaths()

        def subFlow1Id = haFlowPath.subFlowPaths.first().flowId
        def subFlow1Switch = switches.all().findSpecific(haFlowPath.getSubFlowIsls(subFlow1Id).last().srcSwitch.dpId)

        def subFlow2Id = haFlowPath.subFlowPaths.last().flowId
        def subFlow2Switch = switches.all().findSpecific(haFlowPath.getSubFlowIsls(subFlow2Id).last().srcSwitch.dpId)

        def rulesToDelete = subFlow1Switch.rulesManager.getNotDefaultRules().cookie
        rulesToDelete.each { cookie -> subFlow1Switch.rulesManager.delete(cookie) }

        def collectedDiscrepancies = haFlow.pingAndCollectDiscrepancies()

        then: "HA-Flow ping is not successful, and ping for one sub-flow shows that path is broken"
        def expectedDiscrepancy = [(FlowDirection.FORWARD): "No ping for reasonable time",
                                   (FlowDirection.REVERSE): "No ping for reasonable time"]
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.subFlowsDiscrepancies
                    .find { it.subFlowId == subFlow1Id }.flowDiscrepancies == expectedDiscrepancy
            assert !collectedDiscrepancies.subFlowsDiscrepancies.find { it.subFlowId == subFlow2Id }
        }

        when: "Break another sub-flow by removing flow rules from the intermediate switch(after fixing previous discrepancy)"
        subFlow1Switch.synchronize()

        rulesToDelete = subFlow2Switch.rulesManager.getNotDefaultRules().cookie
        rulesToDelete.each { cookie -> subFlow2Switch.rulesManager.delete(cookie) }

        collectedDiscrepancies = haFlow.pingAndCollectDiscrepancies()

        then: "HA-Flow ping is not successful, and ping for another sub-flow shows that path is broken"
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.subFlowsDiscrepancies
                    .find { it.subFlowId == subFlow2Id }.flowDiscrepancies == expectedDiscrepancy
            assert !collectedDiscrepancies.subFlowsDiscrepancies.find { it.subFlowId == subFlow1Id }
        }

        when: "All required rules have been installed(sync)"
        subFlow2Switch.synchronize()
        collectedDiscrepancies = haFlow.pingAndCollectDiscrepancies()

        then: "HA-Flow ping is successful for both sub-flows"
        verifyAll {
            assert collectedDiscrepancies.pingSuccess
            assert !collectedDiscrepancies.error
            assert collectedDiscrepancies.subFlowsDiscrepancies.isEmpty()
        }
    }
}
