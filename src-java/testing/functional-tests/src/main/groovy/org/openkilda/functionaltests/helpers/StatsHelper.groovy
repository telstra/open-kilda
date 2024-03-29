package org.openkilda.functionaltests.helpers


import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.stats.StatsRequest
import org.openkilda.testing.service.tsdb.TsdbQueryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import static java.util.concurrent.TimeUnit.SECONDS

@Component
class StatsHelper {
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    final String KAFKA_STORM_SPEAKER_TOPIC = "kilda.speaker.storm"
    final int KAFKA_MESSAGE_SEND_TIMEOUT = 5 //seconds

    KafkaProducer kafkaProducer = null

    void "force kilda to collect stats"(String flowId = "generic") {
        getKafkaProducer().send(new ProducerRecord(KAFKA_STORM_SPEAKER_TOPIC,
                new CommandMessage(
                        new StatsRequest(),
                        System.currentTimeMillis(),
                        "artificial autotest stats collection enforcement for flow ${flowId}").toJson()))
                .get(KAFKA_MESSAGE_SEND_TIMEOUT, SECONDS)
    }

    //Something like singleton here. If you know the better approach, please, suggest in comments or refactor
    private KafkaProducer getKafkaProducer() {
        if (this.kafkaProducer == null) {
            this.kafkaProducer = new KafkaProducer(producerProps)
        }
        return this.kafkaProducer
    }
}
