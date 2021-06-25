package org.openkilda.functionaltests.spec.stats


import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.Destination
import org.openkilda.messaging.Message
import org.openkilda.messaging.info.InfoData
import org.openkilda.messaging.info.InfoMessage
import org.openkilda.messaging.info.stats.FlowStatsData
import org.openkilda.messaging.info.stats.FlowStatsEntry
import org.openkilda.model.cookie.Cookie
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.tools.SoftAssertions

import groovy.time.TimeCategory
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Shared
import spock.util.mop.Use

@Narrative("""
In this spec we'll try to simulate certain stats entries by pushing them directly to Kafka and then checking that
they are correctly processed and saved to Otsdb.
""")
@Use(TimeCategory)
class SimulateStatsSpec extends HealthCheckSpecification {

    //This is Noviflow specific. Per spec, noviflow packet counter will roll over after 2^31
    static final int NOVI_MAX_PACKET_COUNT = Integer.MAX_VALUE
    static final int MAX_PACKET_SIZE = 9200 //assumed, bytes
    @Value("#{kafkaTopicsConfig.getStatsTopic()}")
    String statsTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Shared
    @Value('${opentsdb.metric.prefix}')
    String metricPrefix

    @Tidy
    def "Flow stats with big values are properly being saved to stats db (noviflow boundaries)"() {
        given: "A flow"
        def (Switch src, Switch dst) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(src, dst)
        flowHelperV2.addFlow(flow)
        def srcRules = northbound.getSwitchRules(src.dpId).flowEntries.findAll { !new Cookie(it.cookie).serviceFlag }

        when: "Flow stats information with noviflow-specific right boundary packet/byte counts is written to kafka"
        def producer = new KafkaProducer(producerProps)
        def sw = topology.activeSwitches.first()
        def data = new FlowStatsData(sw.dpId, srcRules.collect {
            /*For noviflow we assume that packet counter will always roll over BEFORE byte count, so not testing
            Long.MAX_VALUE for bytes. That's fine, since packets should be 4G+ for bytes to roll over before packets
            on Novis, which is unlikely.
            Actually, we are not able to properly handle 2^63 bytes at this point, for reasons:
            1. OTSDB itself is only capable of storing up to 2^63, while switch bytes are up to 2^64
            2. Our default Long implementation in Java backend is also capable of only 2^63
            3. Even for 2^63 we get overflowed 'negative' values when converting bytes to bits (doing bytesx8)
             */
            new FlowStatsEntry(0, it.cookie, NOVI_MAX_PACKET_COUNT, NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE, 10,
            10)
        })
        producer.send(new ProducerRecord(statsTopic, sw.dpId.toString(), buildMessage(data).toJson()))
        producer.flush()
        
        then: "Corresponding entries appear in otsdb"
        def expectedMetricValueMap = [
                "flow.bytes": NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE,
                "flow.packets": NOVI_MAX_PACKET_COUNT,
                "flow.bits": NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE * 8,
                "flow.ingress.bytes": NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE,
                "flow.ingress.packets": NOVI_MAX_PACKET_COUNT,
                "flow.ingress.bits": NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE * 8,
                "flow.raw.packets": NOVI_MAX_PACKET_COUNT * 2L, //x2 since we send data for ingress+egress rules
                "flow.raw.bytes": NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE * 2,
                "flow.raw.bits": NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE * 2 * 8
        ]
        Wrappers.retry(8, 3) {
            def soft = new SoftAssertions()
            expectedMetricValueMap.each { metric, expectedValue ->
                soft.checkSucceeds {
                    def values = otsdb.query(1.minute.ago, "$metricPrefix$metric", [flowid: flow.flowId]).dps.values()
                    assert values.contains(expectedValue), "metric: $metric"
                }
            }
            soft.verify()
            true
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        producer && producer.close()
    }

    private static Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(),
                Destination.WFM_STATS, null)
    }
    
}
