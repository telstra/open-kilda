package org.openkilda.functionaltests.spec.stats


import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.model.stats.FlowStats
import org.openkilda.messaging.Destination
import org.openkilda.messaging.Message
import org.openkilda.messaging.info.InfoData
import org.openkilda.messaging.info.InfoMessage
import org.openkilda.messaging.info.stats.FlowStatsData
import org.openkilda.messaging.info.stats.FlowStatsEntry
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.time.TimeCategory
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll
import spock.util.mop.Use

import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.stats.Direction.FORWARD
import static org.openkilda.functionaltests.model.stats.Direction.REVERSE
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_EGRESS_BITS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_EGRESS_BYTES
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_EGRESS_PACKETS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_INGRESS_BITS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_INGRESS_BYTES
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_INGRESS_PACKETS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BITS
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_BYTES
import static org.openkilda.functionaltests.model.stats.FlowStatsMetric.FLOW_RAW_PACKETS
import static org.openkilda.testing.Constants.WAIT_OFFSET

@Narrative("""
In this spec we'll try to simulate certain stats entries by pushing them directly to Kafka and then checking that
they are correctly processed and saved to tsdb.
""")
@Use(TimeCategory)
class SimulateStatsSpec extends HealthCheckSpecification {

    //This is Noviflow specific. Per spec, noviflow packet counter will roll over after 2^31
    static final int NOVI_MAX_PACKET_COUNT = Integer.MAX_VALUE
    static final int MAX_PACKET_SIZE = 9200 //assumed, bytes
    @Value("#{kafkaTopicsConfig.getStatsTopic()}")
    @Shared
    String statsTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    @Shared
    Properties producerProps
    @Autowired
    @Shared
    FlowStats flowStats
    @Shared
    FlowStats stats
    @Shared
    FlowRequestV2 flow
    @Shared
    KafkaProducer producer
    @Shared
    final int inPort = 10
    @Shared
    final int outPort = 10
    @Shared
    final int tableId = 0
    @Shared
    Switch sw

    @Override
    def setupSpec() {
        def (Switch src, Switch dst) = topology.activeSwitches
        flow = flowHelperV2.randomFlow(src, dst)
        flowHelperV2.addFlow(flow)
        def srcRules = northbound.getSwitchRules(src.dpId).flowEntries.findAll { !new Cookie(it.cookie).serviceFlag }
        producer = new KafkaProducer(producerProps)
        sw = topology.activeSwitches.first()
        def data = new FlowStatsData(sw.dpId, srcRules.collect {
            /*For noviflow we assume that packet counter will always roll over BEFORE byte count, so not testing
            Long.MAX_VALUE for bytes. That's fine, since packets should be 4G+ for bytes to roll over before packets
            on Novis, which is unlikely.
            Actually, we are not able to properly handle 2^63 bytes at this point, for reasons:
            1. OTSDB itself is only capable of storing up to 2^63, while switch bytes are up to 2^64
            2. Our default Long implementation in Java backend is also capable of only 2^63
            3. Even for 2^63 we get overflowed 'negative' values when converting bytes to bits (doing bytesx8)
             */
            new FlowStatsEntry(tableId,
                    it.cookie,
                    NOVI_MAX_PACKET_COUNT,
                    NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE,
                    inPort,
                    outPort)
        })
        producer.send(new ProducerRecord(statsTopic, sw.dpId.toString(), buildMessage(data).toJson())).get()
        producer.flush()
        wait(statsRouterRequestInterval + WAIT_OFFSET) {
            stats = flowStats.of(flow.getFlowId())
            assert stats.get(FLOW_RAW_PACKETS, inPort, outPort).hasValue(NOVI_MAX_PACKET_COUNT)
        }

    }

    @Unroll
    def "Flow stats #metric with big values are properly being saved to stats db (noviflow boundaries)"() {
        expect: "Corresponding entries appear in tsdb"
        getStats(stats).hasValue(value)

        where:
        metric               |getStats | value
        FLOW_EGRESS_PACKETS  | {FlowStats flStats -> flStats.get(FLOW_EGRESS_PACKETS, REVERSE)} |NOVI_MAX_PACKET_COUNT
        FLOW_EGRESS_BYTES    | {FlowStats flStats -> flStats.get(FLOW_EGRESS_BYTES, REVERSE)}   |NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE
        FLOW_EGRESS_BITS     | {FlowStats flStats -> flStats.get(FLOW_EGRESS_BITS, REVERSE)}    |NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE * 8
        FLOW_INGRESS_PACKETS | {FlowStats flStats -> flStats.get(FLOW_INGRESS_PACKETS, FORWARD)} |NOVI_MAX_PACKET_COUNT
        FLOW_INGRESS_BYTES   | {FlowStats flStats -> flStats.get(FLOW_INGRESS_BYTES, FORWARD)}|NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE
        FLOW_INGRESS_BITS    |  {FlowStats flStats -> flStats.get(FLOW_INGRESS_BITS, FORWARD)}|NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE * 8
        FLOW_RAW_PACKETS     | {FlowStats flStats -> flStats.get(FLOW_RAW_PACKETS, inPort, outPort)}|NOVI_MAX_PACKET_COUNT
        FLOW_RAW_BYTES       | {FlowStats flStats -> flStats.get(FLOW_RAW_BYTES, inPort, outPort)}|NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE
        FLOW_RAW_BITS        | {FlowStats flStats -> flStats.get(FLOW_RAW_BITS, inPort, outPort)}|NOVI_MAX_PACKET_COUNT * MAX_PACKET_SIZE * 8
    }

    @Override
    def cleanupSpec() {
        flow && flowHelperV2.deleteFlow(flow.flowId)
        producer && producer.close()
    }

    private static Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(),
                Destination.WFM_STATS, null)
    }
}
