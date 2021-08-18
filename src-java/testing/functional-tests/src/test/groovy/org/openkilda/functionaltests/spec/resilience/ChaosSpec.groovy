package org.openkilda.functionaltests.spec.resilience

import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("Test system behavior under different factors and events that randomly appear across the topology")
class ChaosSpec extends HealthCheckSpecification {

    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    /**
     * This test simulates a busy network with a lot of flows. Random ISLs across the topology begin to blink,
     * causing some of the flows to reroute. Verify that system remains stable.
     */
    @Ignore("https://github.com/telstra/open-kilda/issues/3256")
    def "Nothing breaks when multiple flows get rerouted due to randomly failing ISLs"() {
        setup: "Create multiple random flows"
        def flowsAmount = topology.activeSwitches.size() * 10
        List<FlowRequestV2> flows = []
        flowsAmount.times {
            def flow = flowHelperV2.randomFlow(*topologyHelper.randomSwitchPair, false, flows)
            northboundV2.addFlow(flow)
            flows << flow
        }

        when: "Random ISLs 'blink' for some time"
        def islsAmountToBlink = topology.islsForActiveSwitches.size() * 5
        def r = new Random()
        islsAmountToBlink.times {
            //have certain instabilities with blinking centec ports, thus exclude them here
            def isls = topology.islsForActiveSwitches.findAll { !it.srcSwitch.centec }
            def randomIsl = isls[r.nextInt(isls.size())]
            blinkPort(randomIsl.srcSwitch.dpId, randomIsl.srcPort)
            //1 of 4 times we will add a minor sleep after blink in order not to fail all ISLs at once
            r.nextInt(4) == 3 && sleep((long) (discoveryInterval / 2) * 1000)
        }

        then: "All flows remain up and valid"
        Wrappers.wait(WAIT_OFFSET + antiflapCooldown + discoveryInterval) {
            northbound.getAllLinks().findAll { it.state == IslChangeType.FAILED }.empty
        }
        TimeUnit.SECONDS.sleep(rerouteDelay) //all throttled reroutes should start executing

        Wrappers.wait(PATH_INSTALLATION_TIME * 3 + flowsAmount) {
            flows.each { flow ->
                assert northboundV2.getFlowStatus(flow.flowId).status == FlowState.UP
                northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
                bothDirectionsHaveSamePath(northbound.getFlowPath(flow.flowId))
            }
        }

        and: "Cleanup: remove flows and reset costs"
        flows.each { northboundV2.deleteFlow(it.flowId) }
        // Wait for meters deletion from all OF_13 switches since it impacts other tests.
        Wrappers.wait(WAIT_OFFSET * 2 + flowsAmount * RULES_DELETION_TIME) {
            topology.activeSwitches.findAll { it.ofVersion == "OF_13" }.each {
                assert northbound.getAllMeters(it.dpId).meterEntries.findAll {
                    it.meterId > MAX_SYSTEM_RULE_METER_ID
                }.empty
            }
        }
        database.resetCosts(topology.isls)
    }

    def bothDirectionsHaveSamePath(FlowPathPayload path) {
        [path.forwardPath, path.reversePath.reverse()].transpose().each { PathNodePayload forwardNode,
                                                                          PathNodePayload reverseNode ->
            def failureMessage = "Failed nodes: $forwardNode $reverseNode"
            assert forwardNode.switchId == reverseNode.switchId, failureMessage
            assert forwardNode.outputPort == reverseNode.inputPort, failureMessage
            assert forwardNode.inputPort == reverseNode.outputPort, failureMessage
        }
    }

    def blinkPort(SwitchId swId, int port) {
        northbound.portDown(swId, port)
        northbound.portUp(swId, port)
    }
}
