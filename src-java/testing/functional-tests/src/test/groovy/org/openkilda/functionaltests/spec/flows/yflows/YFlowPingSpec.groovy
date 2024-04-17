package org.openkilda.functionaltests.spec.flows.yflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.helpers.model.YFlowFactory
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v2.yflows.SubFlowPingPayload
import org.openkilda.northbound.dto.v2.yflows.UniSubFlowPingPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPingPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPingResult

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
        def swT = topologyHelper.switchTriplets.first()
        def yFlow = yFlowFactory.getBuilder(swT).withPeriodicPings(true).build().waitForBeingInState(FlowState.UP)

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

        cleanup:
        yFlow && yFlow.delete()
    }

    @Tags([LOW_PRIORITY])
    def "Able to ping y-flow when one of subflows is one-switch one (#5019)"() {
        given: "y-flow which has one-switch subflow"
        def switchTriplet = topologyHelper.getSwitchTriplets(true, true)
                .find(SwitchTriplet.ONE_SUB_FLOW_IS_ONE_SWITCH_FLOW)
        assumeTrue(switchTriplet != null, "These cases cannot be covered on given topology:")
        def yFlow = yFlowFactory.getRandom(switchTriplet)

        and: "expected ping response"
        def multiSwitchSubFlowId = yFlow.getSubFlows()
                .find { it.getEndpoint().getSwitchId().equals(switchTriplet.getEp2().getDpId()) }
                .getFlowId()
        def expectedResponseSubflowPart = UniSubFlowPingPayload.builder()
                .pingSuccess(true)
                .build()
        def expectedResponse = YFlowPingResult.builder()
                .yFlowId(yFlow.yFlowId)
                .pingSuccess(false)
                .error("One sub flow is one-switch flow")
                .subFlows([SubFlowPingPayload.builder()
                                   .flowId(multiSwitchSubFlowId)
                                   .forward(expectedResponseSubflowPart)
                                   .reverse(expectedResponseSubflowPart)
                                   .build()])
                .build()

        when: "ping y-flow"
        def response = yFlow.ping(new YFlowPingPayload(2000))
        response = 'replace unpredictable latency values from ping response'(response)

        then: "y-flow ping is not successful, but one of subflows ping is successful"
        response == expectedResponse

        cleanup:
        yFlow && yFlow.delete()
    }

    def getPacketCountOfVlanPingRule(SwitchId switchId) {
        return northbound.getSwitchRules(switchId).flowEntries
                .findAll { it.cookie == Cookie.VERIFICATION_UNICAST_RULE_COOKIE }[0].packetCount
    }

    def 'replace unpredictable latency values from ping response'(YFlowPingResult originalResponse) {
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
