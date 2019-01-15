package org.openkilda.functionaltests.helpers.thread

import org.openkilda.messaging.Message
import org.openkilda.messaging.info.InfoData
import org.openkilda.messaging.info.InfoMessage
import org.openkilda.messaging.info.event.PortChangeType
import org.openkilda.messaging.info.event.PortInfoData
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

/**
 * Simulates blinking port on switch by sending port up/down messages to kafka
 */
class PortBlinker extends Thread {
    private Producer producer
    private String topic
    private long interval
    private volatile boolean running

    final Switch sw
    final int port
    Date timeStarted
    Date timeStopped

    PortBlinker(Properties producerProps, String topic, Switch sw, int port, long interval) {
        super()
        this.producer = new KafkaProducer(producerProps)
        this.topic = topic
        this.interval = interval
        this.sw = sw
        this.port = port
    }

    def kafkaChangePort(PortChangeType status) {
        producer.send(new ProducerRecord(topic, sw.dpId.toString(),
                buildMessage(new PortInfoData(sw.dpId, port, null, status)).toJson()))
    }

    private static Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null);
    }

    @Override
    synchronized void start() {
        super.start()
        timeStarted = new Date()
    }

    @Override
    void run() {
        running = true
        while (running) {
            kafkaChangePort(PortChangeType.DOWN)
            sleep(interval)
            kafkaChangePort(PortChangeType.UP)
            sleep(interval)
        }
    }

    void stop(boolean endWithPortUp) {
        running = false
        join()
        if (!endWithPortUp) {
            kafkaChangePort(PortChangeType.DOWN)
        }
        producer.close()
        timeStopped = new Date()
    }

    boolean isRunning() {
        return running
    }
}
