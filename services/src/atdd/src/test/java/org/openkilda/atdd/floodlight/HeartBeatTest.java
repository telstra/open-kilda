package org.openkilda.atdd.floodlight;

import static org.junit.Assert.assertTrue;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.KafkaParameters;
import org.openkilda.KafkaUtils;
import org.openkilda.messaging.HeartBeat;
import org.openkilda.messaging.Message;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.io.IOException;
import java.util.Collections;

public class HeartBeatTest {
    private final KafkaUtils kafkaUtils;
    private final KafkaConsumer<String, String> heartBeatConsumer;

    public HeartBeatTest() throws IOException {
        kafkaUtils = new KafkaUtils();
        heartBeatConsumer = kafkaUtils.createConsumer();
    }

    @Given("^rewind heart beat kafka position to the end$")
    public void heart_beat_consumer_installed() throws Throwable {
        KafkaParameters options = new KafkaParameters();

        String topic = options.getDiscoTopic();
        heartBeatConsumer.subscribe(Collections.singletonList(topic));

        // bind to "start" offset inside topic
        heartBeatConsumer.poll(100);
        heartBeatConsumer.seekToEnd(Collections.emptySet());
        heartBeatConsumer.poll(100);
    }

    @Then("^got at least (\\d+) heart beat event$")
    public void got_heart_beat_event(int expect) throws Throwable {
        int beatsCount = 0;

        for (ConsumerRecord<String, String> record : heartBeatConsumer.poll(500)) {
            Message raw = MAPPER.readValue(record.value(), Message.class);

            if (raw instanceof HeartBeat) {
                beatsCount += 1;
            }
        }

        System.out.println(String.format("Got %d heart beats.", beatsCount));
        assertTrue(
                String.format("Actual heart beats count is %d, expect more than %d", beatsCount, expect),
                expect <= beatsCount);
    }
}
