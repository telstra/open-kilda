package org.openkilda.functionaltests.spec

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowPathPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.messaging.payload.flow.PathNodePayload
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("Test system behavior under different factors or events that randomly appear across the topology")
class ChaosSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Value('${discovery.interval}')
    int discoveryInterval
    @Value('${reroute.delay}')
    int rerouteDelay

    /**
     * This spec simulates a busy network with a lot of flows. Random isls across the topology begin to blink,
     * causing some of the flows to reroute. Verify that system remains stable
     */
    def "Nothing breaks when multiple flows get rerouted due to randomly failing isls"() {
        setup: "Create multiple random flows"
        def flowsAmount = topology.activeSwitches.size() * 15
        List<FlowPayload> flows = []
        flowsAmount.times {
            def flow = flowHelper.randomFlow(*randomSwitchPair, false)
            northbound.addFlow(flow)
            flows << flow
        }
        //and: Observer that will log current amount of active ISLs
        def interrupt = false
        def islReporter = new Thread({
            while(!interrupt) {
                def allIsls = northbound.getAllLinks()
                def activeIsls = allIsls.findAll { it.state == IslChangeType.DISCOVERED }
                log.debug("Currently active isls: ${activeIsls.size()} of ${allIsls.size()}")
                sleep(500)
            }
        })
        def stopReporter = {
            interrupt = true
            islReporter.join()
        }
        islReporter.start()

        when: "Random isls 'blink' for some time"
        def islsAmountToBlink = topology.islsForActiveSwitches.size() * 5
        def r = new Random()
        islsAmountToBlink.times {
            def randomIsl = topology.islsForActiveSwitches[r.nextInt(topology.islsForActiveSwitches.size())]
            blinkPort(randomIsl.srcSwitch.dpId, randomIsl.srcPort)
            //1 of 4 times we will add a minor sleep after blink in order not to fail all ISLs at once
            r.nextInt(4) == 3 && sleep((long)(discoveryInterval / 2) * 1000)
        }

        then: "All flows remain up and valid"
        Wrappers.wait(WAIT_OFFSET) { northbound.getAllLinks().findAll { it.state == IslChangeType.FAILED }.empty }
        stopReporter() || true
        flows.each { flow ->
            Wrappers.wait(WAIT_OFFSET + rerouteDelay, 2) {
                northbound.validateFlow(flow.id).each { direction ->
                    def discrepancies = profile == "virtual" ?
                            direction.discrepancies.findAll {
                                it.field != "meterId"
                            } : //unable to validate meters for virtual
                            direction.discrepancies
                    assert discrepancies.empty
                }
                assert northbound.getFlowStatus(flow.id).status == FlowState.UP
                bothDirectionsHaveSamePath(northbound.getFlowPath(flow.id))
            }
        }

        and: "cleanup: remove flows"
        flows.each { northbound.deleteFlow(it.id) }
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

    /**
     * Get a switch pair with random switches.
     * Src and dst are guaranteed to be different switches
     */
    Tuple2<Switch, Switch> getRandomSwitchPair() {
        def randomSwitch = { List<Switch> switches ->
            switches[new Random().nextInt(switches.size())]
        }
        def src = randomSwitch(topology.activeSwitches)
        def dst = randomSwitch(topology.activeSwitches - src)
        return new Tuple2(src, dst)
    }
}
