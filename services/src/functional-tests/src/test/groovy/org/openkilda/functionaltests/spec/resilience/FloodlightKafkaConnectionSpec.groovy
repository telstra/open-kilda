package org.openkilda.functionaltests.spec.resilience

import static org.openkilda.testing.Constants.HEARTBEAT_INTERVAL

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.HeartBeat
import org.openkilda.messaging.Message
import org.openkilda.messaging.ctrl.KafkaBreakTarget
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.testing.service.kafka.KafkaBreaker

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value

import java.util.concurrent.TimeUnit

class FloodlightKafkaConnectionSpec extends BaseSpecification {
    @Autowired
    KafkaBreaker kafkaBreaker

    @Autowired
    @Qualifier("kafkaConsumerProperties")
    Properties consumerProps

    @Value("#{kafkaTopicsConfig.getTopoDiscoTopic()}")
    String topoDiscoTopic

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

        then: "Floodlight emits heartbeat messages to notify about its availability"
        def consumer = new KafkaConsumer<String, String>(consumerProps)
        consumer.subscribe([topoDiscoTopic])
        consumer.seekToEnd([])
        Wrappers.wait(HEARTBEAT_INTERVAL, 0) {
            assert consumer.poll(100).find { it.value().to(Message) instanceof HeartBeat }
        }

        and: "Topology state is unchanged"
        northbound.activeSwitches.size() == topology.activeSwitches.size()
        northbound.getAllLinks().findAll {
            it.state == IslChangeType.DISCOVERED
        }.size() == topology.islsForActiveSwitches.size() * 2

        and: "System is able to successfully create a flow"
        def flow = flowHelper.randomFlow(topology.activeSwitches[0], topology.activeSwitches[1])
        flowHelper.addFlow(flow)

        and: "Cleanup: Remove flow"
        flowHelper.deleteFlow(flow.id)

        cleanup:
        consumer?.close()
    }
}
