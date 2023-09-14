package org.openkilda.functionaltests.spec.flows.haflows

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult
import org.openkilda.northbound.dto.v2.yflows.SubFlowPingPayload
import org.openkilda.northbound.dto.v2.yflows.UniSubFlowPingPayload
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.LATENCY
import static org.openkilda.functionaltests.model.stats.Status.ERROR
import static org.openkilda.functionaltests.model.stats.Status.SUCCESS
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Narrative("""This spec tests 'periodic ping' functionality.""")
class HaFlowPingSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    @Value('${flow.ping.interval}')
    int pingInterval

    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Autowired
    @Shared
    FlowStats flowStats

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Able to turn off periodic pings on a HA-flow"() {
        given: "An HA-flow with periodic pings turned on"
        def swT = topologyHelper.findSwitchTripletWithDifferentEndpoints()
        def haFlowRequest = haFlowHelper.randomHaFlow(swT).tap {
            it.periodicPings = true
        }
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        assert northboundV2.getHaFlow(haFlow.haFlowId).periodicPings
        wait(STATS_LOGGING_TIMEOUT) {
            assert flowStats.latencyOf(haFlow.getSubFlows().get(0).getFlowId()).get(LATENCY, REVERSE).hasNonZeroValues()

        }


        when: "Turn off periodic pings"
        def updatedHaFlow = haFlowHelper.partialUpdateHaFlow(
                haFlow.haFlowId, HaFlowPatchPayload.builder().periodicPings(false).build())

        then: "Periodic pings are really disabled"
        !updatedHaFlow.periodicPings
        !northboundV2.getHaFlow(haFlow.haFlowId).periodicPings
        def afterUpdateTime = new Date().getTime()

        and: "There is no metrics for HA-subflows"
        timedLoop(pingInterval + WAIT_OFFSET) {
            [haFlow.subFlows*.flowId, [FORWARD, REVERSE]].combinations().each {String flowId, Direction direction ->
                    def stats = flowStats.latencyOf(flowId).get(LATENCY, direction)
                    assert stats != null && !stats.hasNonZeroValuesAfter(afterUpdateTime)
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    @Tidy
    @Tags([LOW_PRIORITY])
    def "Unable to ping HA-flow via periodic pings if ISL is broken"() {
        given: "Pinned HA-flow with periodic pings turned on which won't be rerouted after ISL fails"
        def swT = topologyHelper.findSwitchTripletWithDifferentEndpoints()
        def haFlowRequest = haFlowHelper.randomHaFlow(swT).tap {
            it.periodicPings = true
            it.pinned = true
        }
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)
        assert northboundV2.getHaFlow(haFlow.haFlowId).periodicPings
        def paths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def islToFail = pathHelper.getInvolvedIsls(PathHelper.convert(paths.subFlowPaths[0].forward)).first()

        when: "Fail an HA-flow ISL (bring switch port down)"
        antiflap.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)
        wait(WAIT_OFFSET) { northbound.getLink(islToFail).state == FAILED }
        def afterFailTime = new Date().getTime()

        then: "Periodic pings are still enabled"
        northboundV2.getHaFlow(haFlow.haFlowId).periodicPings

        and: "Metrics for HA-subflows have 'error' in tsdb"
        wait(pingInterval + WAIT_OFFSET * 2, 2) {
            withPool {
                [haFlow.subFlows*.flowId, [FORWARD, REVERSE]].combinations().eachParallel {
                    String flowId, Direction direction ->
                        flowStats.latencyOf(flowId).get(LATENCY, direction, ERROR).hasNonZeroValuesAfter(afterFailTime)
                }
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        islToFail && antiflap.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        wait(WAIT_OFFSET) { northbound.getLink(islToFail).state == DISCOVERED }
        database.resetCosts(topology.isls)
    }

    @Tidy
    def "Able to turn on periodic pings on a HA-flow"() {
        when: "Create a HA-flow with periodic pings turned on"
        def swT = topologyHelper.findSwitchTripletWithDifferentEndpoints()
        def beforeCreationTime = new Date().getTime()
        def haFlowRequest = haFlowHelper.randomHaFlow(swT).tap {
            it.periodicPings = true
        }
        def haFlow = haFlowHelper.addHaFlow(haFlowRequest)

        then: "Periodic pings are really enabled"
        northboundV2.getHaFlow(haFlow.haFlowId).periodicPings

        and: "Packet counter on catch ping rules grows due to pings happening"
        arePingRuleCountersGrow(swT, haFlow)

        and: "Metrics for HA-subflows have 'success' in tsdb"
        wait(pingInterval + WAIT_OFFSET, 2) {
            withPool {
                [haFlow.subFlows*.flowId, [FORWARD, REVERSE]].combinations().eachParallel {
                    String flowId, Direction direction ->
                        flowStats.latencyOf(flowId).get(LATENCY, direction, SUCCESS).hasNonZeroValuesAfter(beforeCreationTime)
                }
            }
        }

        cleanup:
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
    }

    @Tidy
    @Ignore("unignore after https://github.com/telstra/open-kilda/issues/5224")
    @Tags([LOW_PRIORITY])
    def "Able to ping HA-flow when one of subflows is one-switch one"() {
        given: "HA-flow which has one-switch subflow"
        def switchTriplet = topologyHelper.getSwitchTriplets(true, true).find {
            SwitchTriplet.ONE_SUB_FLOW_IS_ONE_SWITCH_FLOW(it)
        }
        assumeTrue(switchTriplet != null, "These cases cannot be covered on given topology:")
        def haFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(switchTriplet))

        and: "expected ping response"
        def multiSwitchSubFlowId = haFlow.getSubFlows()
                .find { it.getEndpoint().getSwitchId().equals(switchTriplet.getEp2().getDpId()) }
                .getFlowId()
        def expectedResponseSubflowPart = UniSubFlowPingPayload.builder()
                .pingSuccess(true)
                .build()
        def expectedResponse = HaFlowPingResult.builder()
                .haFlowId(haFlow.getHaFlowId())
                .pingSuccess(false)
                .error("One sub flow is one-switch flow")
                .subFlows([SubFlowPingPayload.builder()
                                   .flowId(multiSwitchSubFlowId)
                                   .forward(expectedResponseSubflowPart)
                                   .reverse(expectedResponseSubflowPart)
                                   .build()])
                .build()

        when: "ping HA-flow"
        def response = northboundV2.pingHaFlow(haFlow.getHaFlowId(), new HaFlowPingPayload(2000))
        response = 'replace unpredictable latency values from ping response'(response)

        then: "HA-flow ping is not successful, but one of subflows ping is successful"
        response == expectedResponse

        cleanup:
        Wrappers.silent { haFlowHelper.deleteHaFlow(haFlow.getHaFlowId()) }
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

    private void arePingRuleCountersGrow(SwitchTriplet swT, HaFlow haFlow) {
        def sharedSwitchPacketCount = getPacketCountOfVlanPingRule(swT.shared.dpId, haFlow)
        def ep1SwitchPacketCount = getPacketCountOfVlanPingRule(swT.ep1.dpId, haFlow)
        def ep2SwitchPacketCount = getPacketCountOfVlanPingRule(swT.ep2.dpId, haFlow)

        wait(pingInterval + STATS_LOGGING_TIMEOUT + WAIT_OFFSET) {
            assert getPacketCountOfVlanPingRule(swT.shared.dpId, haFlow) > sharedSwitchPacketCount
            assert getPacketCountOfVlanPingRule(swT.ep1.dpId, haFlow) > ep1SwitchPacketCount
            assert getPacketCountOfVlanPingRule(swT.ep2.dpId, haFlow) > ep2SwitchPacketCount
        }
    }

    private long getPacketCountOfVlanPingRule(SwitchId switchId, HaFlow haFlow) {
        return switchRulesFactory.get(switchId).pingRule(haFlow.encapsulationType).packetCount
    }
}
