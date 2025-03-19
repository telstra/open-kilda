package org.openkilda.functionaltests.spec.flows.yflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.model.cookie.Cookie

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""This spec tests 'periodic ping' functionality.""")
class YFlowPingSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    @Value('${flow.ping.interval}')
    int pingInterval

    def "Able to turn on periodic pings on a y-flow"() {
        when: "Create a y-flow with periodic pings turned on"
        def swT = switchTriplets.all().first()
        def yFlow = yFlowFactory.getBuilder(swT).withPeriodicPings(true).build().create()

        then: "Periodic pings is really enabled"
        yFlow.periodicPings

        and: "Packet counter on catch ping rules grows due to pings happening"
        def encapsulationType = yFlow.encapsulationType.toString()
        def sharedSwitchPacketCount = swT.shared.rulesManager.pingRule(encapsulationType).packetCount
        def ep1SwitchPacketCount = swT.ep1.rulesManager.pingRule(encapsulationType).packetCount
        def ep2SwitchPacketCount = swT.ep2.rulesManager.pingRule(encapsulationType).packetCount

        wait(pingInterval + WAIT_OFFSET / 2) {
            def sharedPacketCountNow = swT.shared.rulesManager.pingRule(encapsulationType).packetCount
            def ep1PacketCountNow = swT.ep1.rulesManager.pingRule(encapsulationType).packetCount
            def ep2PacketCountNow = swT.ep2.rulesManager.pingRule(encapsulationType).packetCount

            assert sharedPacketCountNow > sharedSwitchPacketCount && ep1PacketCountNow > ep1SwitchPacketCount &&
                    ep2PacketCountNow > ep2SwitchPacketCount
        }
    }

    @Tags([LOW_PRIORITY])
    def "Able to ping y-flow when one of subflows is one-switch one (#5019)"() {
        given: "y-flow which has one-switch subflow"
        def switchTriplet = switchTriplets.all().nonNeighbouring().random().tap {
            it.ep1 = it.shared
            it.pathsEp1 = []
        }
        assumeTrue(switchTriplet != null, "These cases cannot be covered on given topology:")
        def yFlow = yFlowFactory.getRandom(switchTriplet)

        when: "ping y-flow"
        def collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is not successful, but one of subflows ping is successful"
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.error == "One sub flow is one-switch flow"
            assert collectedDiscrepancies.subFlowsDiscrepancies.isEmpty()
        }

        when: "Break the flow by removing flow rules from the intermediate switch"
        String subFlow = yFlow.subFlows.find { it.endpoint.switchId != yFlow.sharedEndpoint.switchId }.flowId
        def intermediateSwId = yFlow.retrieveAllEntityPaths().getSubFlowTransitSwitches(subFlow).first()
        def intermediateSw = switches.all().findSpecific(intermediateSwId)

        def rulesToDelete = intermediateSw.rulesManager.getNotDefaultRules().cookie
        rulesToDelete.each { cookie -> intermediateSw.rulesManager.delete(cookie) }

        collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is not successful, and ping for another sub-flow shows that path is broken"
        def expectedDiscrepancy = [(FlowDirection.FORWARD): "No ping for reasonable time",
                                   (FlowDirection.REVERSE): "No ping for reasonable time"]
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.error == "One sub flow is one-switch flow"
            assert collectedDiscrepancies.subFlowsDiscrepancies
                    .find { it.subFlowId == subFlow }.flowDiscrepancies == expectedDiscrepancy
        }

        when: "All required rules have been installed(sync)"
        intermediateSw.synchronize()
        collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is not successful, but one of subflows ping is successful"
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.error == "One sub flow is one-switch flow"
            assert collectedDiscrepancies.subFlowsDiscrepancies.isEmpty()
        }
    }

    @Tags([LOW_PRIORITY])
    def "Able to ping y-flow and detect when path is broken"() {
        given: "y-flow has been created"
        def switchTriplet = switchTriplets.all().nonNeighbouring().findSwitchTripletWithYPointOnSharedEp()
        assumeTrue(switchTriplet != null, "These cases cannot be covered on given topology:")
        def yFlow = yFlowFactory.getRandom(switchTriplet)

        and: "ping for y-flow detects no discrepancy"
        assert yFlow.ping().pingSuccess

        when: "Break one sub-flow by removing flow rules from the intermediate switch"
        def yFlowPath = yFlow.retrieveAllEntityPaths()
        def subFlow1Id = yFlowPath.subFlowPaths.first().flowId
        def subFlow2Id = yFlowPath.subFlowPaths.last().flowId

        def subFlow1Switch = switches.all().findSpecific(yFlowPath.getSubFlowTransitSwitches(subFlow1Id).last())
        def subFlow2Switch = switches.all().findSpecific(yFlowPath.getSubFlowTransitSwitches(subFlow2Id).last())

        def rulesToDelete = subFlow1Switch.rulesManager.getNotDefaultRules().cookie

        rulesToDelete.each { cookie -> subFlow1Switch.rulesManager.delete(cookie) }
        def collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is not successful, and ping for one sub-flow shows that path is broken"
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
        collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is not successful, and ping for another sub-flow shows that path is broken"
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.subFlowsDiscrepancies
                    .find { it.subFlowId == subFlow2Id }.flowDiscrepancies == expectedDiscrepancy
            assert !collectedDiscrepancies.subFlowsDiscrepancies.find { it.subFlowId == subFlow1Id }
        }

        when: "All required rules have been installed(sync)"
        subFlow2Switch.synchronize()
        collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is successful for both sub-flows"
        verifyAll {
            assert collectedDiscrepancies.pingSuccess
            assert !collectedDiscrepancies.error
            assert collectedDiscrepancies.subFlowsDiscrepancies.isEmpty()
        }
    }
}
