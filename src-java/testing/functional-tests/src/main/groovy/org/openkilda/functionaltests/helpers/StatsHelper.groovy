package org.openkilda.functionaltests.helpers

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.stats.StatsRequest

import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT

import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.otsdb.OtsdbQueryService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StatsHelper {

    @Autowired
    OtsdbQueryService otsdb
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired @Qualifier("kafkaProducerProperties")
    Properties producerProps

    @Value('${opentsdb.metric.prefix}')
    String metricPrefix
    final String KAFKA_STORM_SPEAKER_TOPIC = "kilda.speaker.storm"

    void verifyFlowWritesStats(String flowId, Date from, boolean expectTraffic) {
        "force kilda to collect stats"(flowId)
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            def dps = otsdb.query(from, metricPrefix + "flow.raw.bytes", [flowid: flowId]).dps
            if(expectTraffic) {
                assert dps.values().any { it > 0 }, flowId
            } else {
                assert dps.size() > 0, flowId
            }
        }
    }

    void verifyYFlowWritesMeterStats(YFlow yFlow, Date from, boolean expectTraffic) {
        "force kilda to collect stats"(yFlow.getYFlowId())
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            def yFlowId = yFlow.YFlowId
            def dpsShared = otsdb.query(from, metricPrefix + "yFlow.meter.shared.bytes", ["y_flow_id": yFlowId]).dps
            def dpsYPoint = otsdb.query(from, metricPrefix + "yFlow.meter.yPoint.bytes", ["y_flow_id": yFlowId]).dps
            def dpsSubFlows = otsdb.query(from, metricPrefix + "flow.raw.bytes", ["flowid": "${yFlow.subFlows[0].flowId}|${yFlow.subFlows[1].flowId}"]).dps
            if (expectTraffic) {
                assert dpsShared.values().any { it > 0 }, yFlowId
                assert dpsYPoint.values().any { it > 0 }, yFlowId
                assert dpsSubFlows.values().any { it > 2}, yFlowId
            } else {
                assert dpsShared.size() > 0, yFlowId
                assert dpsYPoint.size() > 0, yFlowId
                assert dpsSubFlows.size() > 1, yFlowId
            }
        }
    }

    private void "force kilda to collect stats"(String flowId) {
        KafkaProducer kafkaProducer = new KafkaProducer(producerProps)
        kafkaProducer.send(new ProducerRecord(KAFKA_STORM_SPEAKER_TOPIC,
                new CommandMessage(
                        new StatsRequest(),
                        System.currentTimeMillis(),
                "artificial autotest stats collection enforcement for flow ${flowId}").toJson())).get()
    }
}
