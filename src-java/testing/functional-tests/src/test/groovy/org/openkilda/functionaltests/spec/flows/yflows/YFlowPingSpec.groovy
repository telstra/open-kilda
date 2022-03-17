package org.openkilda.functionaltests.spec.flows.yflows


import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.YFlowHelper
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
    YFlowHelper yFlowHelper

    @Value('${flow.ping.interval}')
    int pingInterval

    @Tidy
    def "Able to turn on periodic pings on a y-flow"() {
        when: "Create a y-flow with periodic pings turned on"
        def swT = topologyHelper.switchTriplets[0]
        def yFlowRequest = yFlowHelper.randomYFlow(swT).tap {
            it.periodicPings = true
        }
        def yFlow = yFlowHelper.addYFlow(yFlowRequest)

        then: "Periodic pings is really enabled"
        northboundV2.getYFlow(yFlow.YFlowId).periodicPings

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

        cleanup:
        yFlow && yFlowHelper.deleteYFlow(yFlow.YFlowId)
    }

    def getPacketCountOfVlanPingRule(SwitchId switchId) {
        return northbound.getSwitchRules(switchId).flowEntries
                .findAll { it.cookie == Cookie.VERIFICATION_UNICAST_RULE_COOKIE }[0].packetCount
    }
}
