package org.openkilda.functionaltests.spec.flows.haflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult
import org.openkilda.northbound.dto.v2.yflows.SubFlowPingPayload
import org.openkilda.northbound.dto.v2.yflows.UniSubFlowPingPayload

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Ignore

@Narrative("""This spec tests 'periodic ping' functionality.""")
class HAFlowPingSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Value('${flow.ping.interval}')
    int pingInterval

    @Tidy
    @Ignore("https://github.com/telstra/open-kilda/issues/5217")
    def "Able to turn on periodic pings on a ha-flow"() {
        when: "Create a ha-flow with periodic pings turned on"
        def swT = topologyHelper.switchTriplets.first()
        def haFlowRequest = haFlowHelper.randomHaFlow(swT).tap {
            it.periodicPings = true
        }
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        then: "Periodic pings is really enabled"
        northboundV2.getHaFlow(haFlow.haFlowId).periodicPings

        and: "Packet counter on catch ping rules grows due to pings happening"
        def sharedSwitchPacketCount = getPacketCountOfVlanPingRule(swT.shared.dpId)
        def ep1SwitchPacketCount = getPacketCountOfVlanPingRule(swT.ep1.dpId)
        def ep2SwitchPacketCount = getPacketCountOfVlanPingRule(swT.ep2.dpId)

        Wrappers.wait(pingInterval + WAIT_OFFSET) {
            def sharedPacketCountNow = getPacketCountOfVlanPingRule(swT.shared.dpId)
            def ep1PacketCountNow = getPacketCountOfVlanPingRule(swT.ep1.dpId)
            def ep2PacketCountNow = getPacketCountOfVlanPingRule(swT.ep2.dpId)

            sharedPacketCountNow > sharedSwitchPacketCount && ep1PacketCountNow > ep1SwitchPacketCount &&
                    ep2PacketCountNow > ep2SwitchPacketCount
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    @Tidy
    @Ignore("unignore after https://github.com/telstra/open-kilda/pull/5209")
    @Tags([LOW_PRIORITY])
    def "Able to ping ha-flow when one of subflows is one-switch one"() {
        given: "ha-flow which has one-switch subflow"
        def switchTriplet = topologyHelper.getSwitchTriplets(true, true).find {
            it.shared == it.ep1 && it.shared != it.ep2
        }
        assumeTrue(switchTriplet != null, "These cases cannot be covered on given topology:")
        def haFLow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(switchTriplet))

        and: "expected ping response"
        def multiSwitchSubFlowId = haFLow.getSubFlows()
                .find { it.getEndpoint().getSwitchId().equals(switchTriplet.getEp2().getDpId()) }
                .getFlowId()
        def expectedResponseSubflowPart = UniSubFlowPingPayload.builder()
                .pingSuccess(true)
                .build()
        def expectedResponse = HaFlowPingResult.builder()
                .haFlowId(haFLow.getHaFlowId())
                .pingSuccess(false)
                .error("One sub flow is one-switch flow")
                .subFlows([SubFlowPingPayload.builder()
                                   .flowId(multiSwitchSubFlowId)
                                   .forward(expectedResponseSubflowPart)
                                   .reverse(expectedResponseSubflowPart)
                                   .build()])
                .build()

        when: "ping ha-flow"
        def response = northboundV2.pingHaFlow(haFLow.getHaFlowId(), new HaFlowPingPayload(2000))
        response = 'replace unpredictable latency values from ping response'(response)

        then: "ha-flow ping is not successful, but one of subflows ping is successful"
        response == expectedResponse

        cleanup:
        Wrappers.silent{haFlowHelper.deleteHaFlow(haFLow.getHaFlowId())}
    }

    def getPacketCountOfVlanPingRule(SwitchId switchId) {
        return northbound.getSwitchRules(switchId).flowEntries
                .findAll { it.cookie == Cookie.VERIFICATION_UNICAST_RULE_COOKIE }[0].packetCount
    }

    def 'replace unpredictable latency values from ping response'(HaFlowPingResult originalResponse) {
        //TODO: implement PingResponse model in test package to safely compare expected and actual responses without
        //manipulating original response
        def subFlowPingPayloadWithZeroLatency = originalResponse.subFlows[0].tap {
            it.forward.latency = 0
            it.reverse.latency = 0
        }
        originalResponse.subFlows[0] = subFlowPingPayloadWithZeroLatency
        return originalResponse
    }
}
