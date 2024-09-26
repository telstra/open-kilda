package org.openkilda.functionaltests.spec.flows.yflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.model.SwitchId
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

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Value('${flow.ping.interval}')
    int pingInterval

    def "Able to turn on periodic pings on a y-flow"() {
        when: "Create a y-flow with periodic pings turned on"
        def swT = switchTriplets.all().first()
        def yFlow = yFlowFactory.getBuilder(swT).withPeriodicPings(true).build().create()

        then: "Periodic pings is really enabled"
        yFlow.periodicPings

        and: "Packet counter on catch ping rules grows due to pings happening"
        def sharedSwitchPacketCount = getPacketCountOfVlanPingRule(swT.shared.dpId)
        def ep1SwitchPacketCount = getPacketCountOfVlanPingRule(swT.ep1.dpId)
        def ep2SwitchPacketCount = getPacketCountOfVlanPingRule(swT.ep2.dpId)

        Wrappers.wait(pingInterval + WAIT_OFFSET / 2) {
            def sharedPacketCountNow = getPacketCountOfVlanPingRule(swT.shared.dpId)
            def ep1PacketCountNow = getPacketCountOfVlanPingRule(swT.ep1.dpId)
            def ep2PacketCountNow = getPacketCountOfVlanPingRule(swT.ep2.dpId)

            sharedPacketCountNow > sharedSwitchPacketCount && ep1PacketCountNow > ep1SwitchPacketCount &&
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
        def intermediateSwId = yFlow.retrieveAllEntityPaths().subFlowPaths.find { it.flowId == subFlow }
                .getInvolvedIsls().first().dstSwitch.dpId
        def rulesToDelete = switchRulesFactory.get(intermediateSwId).getRules().findAll {
            !new Cookie(it.cookie).serviceFlag
        }*.cookie
        rulesToDelete.each { cookie ->
            switchHelper.deleteSwitchRules(intermediateSwId, cookie)
        }
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
        switchHelper.synchronize(intermediateSwId)
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
        def subFlow1Switch = yFlowPath.subFlowPaths.first().getInvolvedIsls().last().srcSwitch.dpId
        def subFlow1Id = yFlowPath.subFlowPaths.first().flowId
        def subFlow2Switch = yFlowPath.subFlowPaths.last().getInvolvedIsls().last().srcSwitch.dpId
        def rulesToDelete = switchRulesFactory.get(subFlow1Switch).getRules().findAll {
            !new Cookie(it.cookie).serviceFlag
        }*.cookie
        rulesToDelete.each { cookie ->
            switchHelper.deleteSwitchRules(subFlow1Switch, cookie)
        }
        def collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is not successful, and ping for one sub-flow shows that path is broken"
        def expectedDiscrepancy = [(FlowDirection.FORWARD): "No ping for reasonable time",
                                   (FlowDirection.REVERSE): "No ping for reasonable time"]
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.subFlowsDiscrepancies
                    .find { it.subFlowId == subFlow1Id }.flowDiscrepancies == expectedDiscrepancy
            assert !collectedDiscrepancies.subFlowsDiscrepancies.find { it.subFlowId != subFlow1Id }
        }

        when: "Break another sub-flow by removing flow rules from the intermediate switch(after fixing previous discrepancy)"
        switchHelper.synchronize(subFlow1Switch)
        rulesToDelete = switchRulesFactory.get(subFlow2Switch).getRules().findAll {
            !new Cookie(it.cookie).serviceFlag
        }*.cookie
        rulesToDelete.each { cookie ->
            switchHelper.deleteSwitchRules(subFlow2Switch, cookie)
        }
        collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is not successful, and ping for another sub-flow shows that path is broken"
        verifyAll {
            assert !collectedDiscrepancies.pingSuccess
            assert collectedDiscrepancies.subFlowsDiscrepancies
                    .find { it.subFlowId != subFlow1Id }.flowDiscrepancies == expectedDiscrepancy
            assert !collectedDiscrepancies.subFlowsDiscrepancies.find { it.subFlowId == subFlow1Id }
        }

        when: "All required rules have been installed(sync)"
        switchHelper.synchronize(subFlow2Switch)
        collectedDiscrepancies = yFlow.pingAndCollectDiscrepancies()

        then: "y-flow ping is successful for both sub-flows"
        verifyAll {
            assert collectedDiscrepancies.pingSuccess
            assert !collectedDiscrepancies.error
            assert collectedDiscrepancies.subFlowsDiscrepancies.isEmpty()
        }
    }

    def getPacketCountOfVlanPingRule(SwitchId switchId) {
        return northbound.getSwitchRules(switchId).flowEntries
                .findAll { it.cookie == Cookie.VERIFICATION_UNICAST_RULE_COOKIE }[0].packetCount
    }
}
