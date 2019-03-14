package org.openkilda.functionaltests.spec.resilience

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.Message
import org.openkilda.messaging.info.InfoData
import org.openkilda.messaging.info.InfoMessage
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PortChangeType
import org.openkilda.messaging.info.event.PortInfoData

import groovy.util.logging.Slf4j
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value

@Slf4j
class StormHeavyLoadSpec extends BaseSpecification {
    @Value("#{kafkaTopicsConfig.getTopoDiscoTopic()}")
    String topoDiscoTopic

    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    def r = new Random()

    /**
     * Test produces multiple port up/down and IslInfoData messages to the topo.disco kafka topic,
     * expecting that Storm will be able to swallow that and continue to operate
     */
    def "Storm does not fail under heavy load of topo.disco topic"() {
        requireProfiles("hardware")

        when: "Produce massive amount of messages into topo.disco topic"
        def messages = 100000 //total sum of messages of all types produced
        def operations = 3 //discovery, port up, port down
        def threads = 10
        def producers = (1..threads).collect { new KafkaProducer<>(producerProps) }
        def isl = topology.islsForActiveSwitches[0]
        def islData = northbound.getAllLinks().findAll { it.state == IslChangeType.DISCOVERED }
        withPool(threads) {
            messages.intdiv(threads * operations).times {
                producers.eachParallel {
                    it.send(new ProducerRecord(topoDiscoTopic, buildMessage(new PortInfoData(isl.srcSwitch.dpId,
                            isl.srcPort, null, PortChangeType.DOWN)).toJson()))
                    it.send(new ProducerRecord(topoDiscoTopic, buildMessage(new PortInfoData(isl.srcSwitch.dpId,
                            isl.srcPort, null, PortChangeType.UP)).toJson()))
                    it.send(new ProducerRecord(topoDiscoTopic,
                            buildMessage(islData[r.nextInt(islData.size())]).toJson()))
                }
            }
        }

        then: "Still able to create and delete flows while Storm is swallowing the messages"
        def checkFlowCreation = {
            def flow = flowHelper.randomFlow(topology.islsForActiveSwitches[1].srcSwitch,
                    topology.islsForActiveSwitches[1].dstSwitch)
            flowHelper.addFlow(flow)
            flowHelper.deleteFlow(flow.id)
            sleep(500)
        }
        def endProducing = new Thread({ producers.each({ it.close() }) })
        endProducing.start()
        while (endProducing.isAlive()) {
            checkFlowCreation()
        }
        //check couple more times after producers end sending
        2.times {
            checkFlowCreation()
        }

        and: "Topology is unchanged at the end"
        northbound.activeSwitches.size() == topology.activeSwitches.size()
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll { it.state == IslChangeType.DISCOVERED }
                    .size() == topology.islsForActiveSwitches.size() * 2
        }

        cleanup:
        producers.each { it.close() }
    }

    private static Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null);
    }
}
