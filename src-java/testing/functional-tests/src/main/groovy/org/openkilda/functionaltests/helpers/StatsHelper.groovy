package org.openkilda.functionaltests.helpers

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException
import org.openkilda.functionaltests.model.stats.StatsQueryFilter
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.stats.StatsRequest
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.testing.service.otsdb.OtsdbQueryService
import org.openkilda.testing.service.otsdb.model.StatsResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import static org.openkilda.testing.Constants.DefaultRule
import static org.openkilda.testing.Constants.STATS_LOGGING_TIMEOUT
import static org.openkilda.testing.model.topology.TopologyDefinition.Switch
import static groovyx.gpars.GParsPool.withPool

@Component
class StatsHelper {

    @Autowired
    OtsdbQueryService otsdb
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    @Value('${opentsdb.metric.prefix}')
    String metricPrefix
    final String KAFKA_STORM_SPEAKER_TOPIC = "kilda.speaker.storm"

    KafkaProducer kafkaProducer = null

    void verifyFlowWritesStats(String flowId, Date from, boolean expectTraffic) {
        "force kilda to collect stats"(flowId)
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            def dps = otsdb.query(from, metricPrefix + "flow.raw.bytes", [flowid: flowId]).dps
            if (expectTraffic) {
                assert dps.values().any { it > 0 }, flowId
            } else {
                assert dps.size() > 0, flowId
            }
        }
    }

    void verifyYFlowWritesStats(YFlow yFlow, Date from, boolean expectTraffic) {
        "force kilda to collect stats"(yFlow.getYFlowId())
        Wrappers.wait(STATS_LOGGING_TIMEOUT) {
            def yFlowId = yFlow.YFlowId
            def dpsShared = otsdb.query(from, metricPrefix + "yFlow.meter.shared.bytes", ["y_flow_id": yFlowId]).dps
            def dpsYPoint = otsdb.query(from, metricPrefix + "yFlow.meter.yPoint.bytes", ["y_flow_id": yFlowId]).dps
            def dpsSubFlows = otsdb.query(from,
                    metricPrefix + "flow.raw.bytes",
                    ["flowid": "${yFlow.subFlows[0].flowId}|${yFlow.subFlows[1].flowId}"]).dps
            if (expectTraffic) {
                assert dpsShared.values().any { it > 0 }, yFlowId
                assert dpsYPoint.values().any { it > 0 }, yFlowId
                assert dpsSubFlows.values().any { it > 2 }, yFlowId
            } else {
                assert dpsShared.size() > 0, yFlowId
                assert dpsYPoint.size() > 0, yFlowId
                assert dpsSubFlows.size() > 1, yFlowId
            }
        }
    }

    long "ping packets count on switch"(Date beforePingTime, Switch srcSwitch, DefaultRule rule) {
        def statsData = null
        "force kilda to collect stats"()
        Wrappers.wait(STATS_LOGGING_TIMEOUT, 2) {
            statsData = otsdb.query(beforePingTime, metricPrefix + "switch.flow.system.bytes",
                    [switchid : srcSwitch.dpId.toOtsdFormat(),
                     cookieHex: rule.toHexString()]).dps
            assert statsData && !statsData.empty
        }
        return statsData.values().last().toLong()
    }

    List<StatsResult> "get multiple stats results"(Date from, List<StatsQueryFilter> statsToRetrieve) {
        List<StatsResult> results = null
        "force kilda to collect stats"()
        try {
            Wrappers.wait(STATS_LOGGING_TIMEOUT, 2) {
                withPool {
                    results = statsToRetrieve.collectParallel {
                        "get stats from DB"(from, it)
                    }
                }
                assert results.every {it.getDps().values().any { it > 0 }
                }
            }
        } catch (WaitTimeoutException e) {
            //don't do anything, we will verify stats correctness in StatsResult's class methods
        }
        return results
    }

    StatsResult "get single stats result"(Date from, StatsQueryFilter statsToRetrieve) {
        return "get multiple stats results"(from, [statsToRetrieve])[0]
    }

    void "force kilda to collect stats"(String flowId = "generic") {
        getKafkaProducer().send(new ProducerRecord(KAFKA_STORM_SPEAKER_TOPIC,
                new CommandMessage(
                        new StatsRequest(),
                        System.currentTimeMillis(),
                        "artificial autotest stats collection enforcement for flow ${flowId}").toJson())).get()
    }

    private StatsResult "get stats from DB"(Date from, StatsQueryFilter filter) {
        return otsdb.query(from, "${metricPrefix}${filter.getMetric().getMetric()}", filter.getTags())
    }

    //Something like singleton here. If you know the better approach, please, suggest in comments or refactor
    private KafkaProducer getKafkaProducer() {
        if (this.kafkaProducer == null) {
            this.kafkaProducer = new KafkaProducer(producerProps)
        }
        return this.kafkaProducer
    }
}
