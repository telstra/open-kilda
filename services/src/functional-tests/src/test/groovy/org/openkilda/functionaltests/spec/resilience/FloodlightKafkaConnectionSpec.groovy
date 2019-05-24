package org.openkilda.functionaltests.spec.resilience

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.ctrl.KafkaBreakTarget
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.service.kafka.KafkaBreaker

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore

import java.util.concurrent.TimeUnit

class FloodlightKafkaConnectionSpec extends BaseSpecification {
    @Autowired
    KafkaBreaker kafkaBreaker

    @Value('${floodlight.alive.timeout}')
    int floodlightAliveTimeout

    @Value('${floodlight.alive.interval}')
    int floodlightAliveInterval
    
    def region = 1

    def "System survives temporary connection outage between Floodlight and Kafka"() {
        when: "Controller loses connection to Kafka"
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_PRODUCER, region)
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_CONSUMER, region)

        then: "Right before controller alive timeout switches are still active and links are discovered"
        double interval = floodlightAliveTimeout * 0.4
        Wrappers.timedLoop(floodlightAliveTimeout - interval) {
            assert northbound.activeSwitches.size() == topology.activeSwitches.size()
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
            sleep(500)
        }

        and: "After controller alive timeout switches become inactive and links are discovered"
        Wrappers.wait(interval + WAIT_OFFSET) { assert northbound.activeSwitches.size() == 0 }
        northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2

        when: "System remains in this state for discovery timeout for ISLs"
        TimeUnit.SECONDS.sleep(discoveryTimeout + 1)

        then: "All links are discovered"
        northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2

        when: "Controller restores connection to Kafka"
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_PRODUCER, region)
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_CONSUMER, region)

        then: "All links are discovered and switches become active"
        northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        Wrappers.wait(floodlightAliveInterval + WAIT_OFFSET) {
            assert northbound.activeSwitches.size() == topology.activeSwitches.size()
        }

        and: "System is able to successfully create a flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        and: "Cleanup: remove the flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Ignore("Due to defect https://github.com/telstra/open-kilda/issues/2214")
    def "System can detect switch changes if they happen while Floodlight was disconnected after it reconnects"() {
        when: "Controller loses connection to kafka"
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_PRODUCER, region)
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_CONSUMER, region)

        and: "Switch port for certain ISL goes down"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort }
        //port down on A-switch will lead to a port down on a connected Kilda switch
        lockKeeper.portsDown([isl.aswitch.inPort])

        and: "Controller restores connection to kafka"
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_PRODUCER, region)
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_CONSUMER, region)

        then: "System detects that certain port has been brought down and fails the related link"
        Wrappers.wait(WAIT_OFFSET) {
            def isls = northbound.getAllLinks()
            assert islUtils.getIslInfo(isls, isl).get().state == IslChangeType.FAILED
            assert islUtils.getIslInfo(isls, isl.reversed).get().state == IslChangeType.FAILED
        }

        and: "Cleanup: restore the broken link"
        lockKeeper.portsUp([isl.aswitch.inPort])
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            def isls = northbound.getAllLinks()
            assert islUtils.getIslInfo(isls, isl).get().state == IslChangeType.DISCOVERED
            assert islUtils.getIslInfo(isls, isl.reversed).get().state == IslChangeType.DISCOVERED
        }
    }
}
