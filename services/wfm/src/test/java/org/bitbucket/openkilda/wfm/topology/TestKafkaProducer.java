package org.bitbucket.openkilda.wfm.topology;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.utils.Utils;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by atopilin on 20/04/2017.
 */
public class TestKafkaProducer {
    private static final long SEND_TIMEOUT = 1000;
    private final KafkaProducer<String, String> producer;

    public TestKafkaProducer(final Properties properties) {
        this.producer = new KafkaProducer<>(properties);
    }

    public void pushMessage(final String topic, final String data) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "payload", data);
        try {
            producer.send(producerRecord).get(SEND_TIMEOUT, TimeUnit.MILLISECONDS);
            producer.flush();
            System.out.println(String.format("send to %s: %s", topic, data));
            Utils.sleep(SEND_TIMEOUT);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            System.out.println(e.getMessage());
        }
    }

    public void close() {
        producer.flush();
        producer.close();
    }
}
