package org.openkilda.functionaltests.spec.resilience

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore

import java.util.concurrent.TimeUnit

class FloodlightKafkaConnectionSpec extends HealthCheckSpecification {
    static final int PERIODIC_SYNC_TIME = 60

    @Value('${floodlight.alive.timeout}')
    int floodlightAliveTimeout
    @Value('${floodlight.alive.interval}')
    int floodlightAliveInterval
    @Value('${antiflap.cooldown}')
    int antiflapCooldown

    @Ignore("Since now we have 2 regions, the ISL between regions will inevitably fail(L49). Need to refactor the test")
    def "System survives temporary connection outage between Floodlight and Kafka"() {
        when: "Controller loses connection to Kafka"
        def flOut = false
        regions.each { lockKeeper.knockoutFloodlight(it) }
        flOut = true

        then: "Right before controller alive timeout switches are still active and links are discovered"
        double interval = floodlightAliveTimeout * 0.4
        Wrappers.timedLoop(floodlightAliveTimeout - interval) {
            assert northbound.activeSwitches.size() == topology.activeSwitches.size()
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
            sleep(500)
        }

        and: "After controller alive timeout switches become inactive but links are still discovered"
        Wrappers.wait(interval + WAIT_OFFSET) { assert northbound.activeSwitches.size() == 0 }
        northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2

        when: "System remains in this state for discovery timeout for ISLs"
        TimeUnit.SECONDS.sleep(discoveryTimeout + 1)

        then: "All links are still discovered"
        northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2

        when: "Controller restores connection to Kafka"
        regions.each { lockKeeper.reviveFloodlight(it) }
        flOut = false

        then: "All links are discovered and switches become active"
        northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        Wrappers.wait(PERIODIC_SYNC_TIME) {
            assert northbound.activeSwitches.size() == topology.activeSwitches.size()
        }

        and: "System is able to successfully create a valid flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        northbound.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET * 3) { //it takes longer than usual in these conditions. why?
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            northbound.validateFlow(flow.id).each { assert it.asExpected }
        }

        and: "Cleanup: remove the flow"
        flowHelperV2.deleteFlow(flow.id)

        cleanup:
        flOut && regions.each { lockKeeper.reviveFloodlight(it) }
    }

    def "System can detect switch changes if they happen while Floodlight was disconnected after it reconnects"() {
        when: "Controller loses connection to kafka"
        regions.each { lockKeeper.knockoutFloodlight(it) }
        Wrappers.wait(floodlightAliveTimeout + WAIT_OFFSET) { assert northbound.activeSwitches.size() == 0 }

        and: "Switch port for certain ISL goes down"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort }
        //port down on A-switch will lead to a port down on a connected Kilda switch
        lockKeeper.portsDown([isl.aswitch.inPort])

        and: "Controller restores connection to kafka"
        regions.each { lockKeeper.reviveFloodlight(it) }

        then: "System detects that certain port has been brought down and fails the related link"
        Wrappers.wait(WAIT_OFFSET) {
            def isls = northbound.getAllLinks()
            assert islUtils.getIslInfo(isls, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(isls, isl.reversed).get().state == IslChangeType.FAILED
        }

        and: "Cleanup: restore the broken link"
        lockKeeper.portsUp([isl.aswitch.inPort])
        Wrappers.wait(WAIT_OFFSET + discoveryInterval + antiflapCooldown) {
            def isls = northbound.getAllLinks()
            assert islUtils.getIslInfo(isls, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(isls, isl.reversed).get().state == IslChangeType.DISCOVERED
        }
    }
}
