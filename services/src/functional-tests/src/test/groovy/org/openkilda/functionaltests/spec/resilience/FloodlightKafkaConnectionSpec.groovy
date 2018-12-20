package org.openkilda.functionaltests.spec.resilience

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.messaging.ctrl.KafkaBreakTarget
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.kafka.KafkaBreaker

import org.springframework.beans.factory.annotation.Autowired

import java.util.concurrent.TimeUnit

class FloodlightKafkaConnectionSpec extends BaseSpecification {
    @Autowired
    KafkaBreaker kafkaBreaker
    @Autowired
    TopologyDefinition topology
    
    def "System survives temporary connection outage between Floodlight and Kafka"() {
        when: "Controller loses connection to kafka"
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_PRODUCER)
        kafkaBreaker.shutoff(KafkaBreakTarget.FLOODLIGHT_CONSUMER)

        and: "Remains in this state for 12 seconds"
        TimeUnit.SECONDS.sleep(12)

        and: "Controller restores connection to kafka"
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_PRODUCER)
        kafkaBreaker.restore(KafkaBreakTarget.FLOODLIGHT_CONSUMER)

        then: "Topology state is unchanged"
        northbound.activeSwitches.size() == topology.activeSwitches.size()
        northbound.getAllLinks().findAll {
            it.state == IslChangeType.DISCOVERED
        }.size() == topology.islsForActiveSwitches.size() * 2

        and: "System is able to successfully create a flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        and: "Remove flow"
        flowHelper.deleteFlow(flow.id)
    }
}
