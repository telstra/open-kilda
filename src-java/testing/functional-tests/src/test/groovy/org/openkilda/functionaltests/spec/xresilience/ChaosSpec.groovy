package org.openkilda.functionaltests.spec.xresilience

import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPortVlan
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@Slf4j
@Narrative("Test system behavior under different factors and events that randomly appear across the topology")
class ChaosSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    /**
     * This test simulates a busy network with a lot of flows. Random ISLs across the topology begin to blink,
     * causing some of the flows to reroute. Verify that system remains stable.
     */
    @Ignore("https://github.com/telstra/open-kilda/issues/5222")
    def "Nothing breaks when multiple flows get rerouted due to randomly failing ISLs"() {
        setup: "Create multiple random flows"
        def flowsAmount = topology.activeSwitches.size() * 10
        List<FlowExtended> flows = []
        List<SwitchPortVlan> busyEndpoints = []
        flowsAmount.times {
            def flow = flowFactory.getRandom(switchPairs.all().random(), false, FlowState.UP, busyEndpoints)
            busyEndpoints.addAll(flow.occupiedEndpoints())
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
                assert flow.retrieveFlowStatus().status == FlowState.UP
                assert flow.validateAndCollectDiscrepancies().isEmpty()
                def flowPathInfo = flow.retrieveAllEntityPaths()
                assert flowPathInfo.getPathNodes(Direction.FORWARD).reverse() == flowPathInfo.getPathNodes(Direction.REVERSE)
            }
        }

        and: "All switches are valid"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(topology.activeSwitches*.dpId).isEmpty()
    }

    def blinkPort(SwitchId swId, int port) {
        northbound.portDown(swId, port)
        northbound.portUp(swId, port)
    }
}
