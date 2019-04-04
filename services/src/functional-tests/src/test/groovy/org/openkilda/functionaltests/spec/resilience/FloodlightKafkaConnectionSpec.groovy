package org.openkilda.functionaltests.spec.resilience

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.ctrl.KafkaBreakTarget
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.service.kafka.KafkaBreaker

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore

import java.util.concurrent.TimeUnit

class FloodlightKafkaConnectionSpec extends BaseSpecification {
    @Autowired
    KafkaBreaker kafkaBreaker

    def "System survives temporary connection outage between Floodlight and Kafka"() {
        when: "Controller loses connection to kafka"
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_PRODUCER)
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_CONSUMER)

        and: "Remains in this state for 10 seconds"
        //10s is just a casual value to simulate a temp connection loss. Not bind to any actual timeouts.
        TimeUnit.SECONDS.sleep(10)

        and: "Controller restores connection to kafka"
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_PRODUCER)
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_CONSUMER)

        then: "Topology state is unchanged"
        northbound.getAllLinks().findAll {
            it.state == IslChangeType.DISCOVERED
        }.size() == topology.islsForActiveSwitches.size() * 2
        Wrappers.wait(WAIT_OFFSET) { assert northbound.activeSwitches.size() == topology.activeSwitches.size() }

        and: "System is able to successfully create a flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        and: "Cleanup: Remove flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Ignore("Due to defect https://github.com/telstra/open-kilda/issues/2214")
    def "System can detect switch changes if they happen while Floodlight was disconnected after it reconnects"() {
        when: "Controller loses connection to kafka"
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_PRODUCER)
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_CONSUMER)

        and: "Switch port for certain ISL goes down"
        def isl = topology.islsForActiveSwitches.find { it.aswitch?.inPort && it.aswitch?.outPort }
        //port down on A-switch will lead to a port down on a connected Kilda switch
        lockKeeper.portsDown([isl.aswitch.inPort])

        and: "Controller restores connection to kafka"
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_PRODUCER)
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_CONSUMER)

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
