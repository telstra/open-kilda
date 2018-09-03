/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology;

import static org.openkilda.messaging.Utils.PAYLOAD;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.utils.Utils;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TestKafkaProducer {
    private static final long SEND_TIMEOUT = 1000;
    private final KafkaProducer<String, String> producer;

    public TestKafkaProducer(final Properties properties) {
        this.producer = new KafkaProducer<>(properties);
    }

    public void pushMessage(final String topic, final String data) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, PAYLOAD, data);
        try {
            producer.send(producerRecord).get(SEND_TIMEOUT, TimeUnit.MILLISECONDS);
            producer.flush();
            System.out.println(String.format("send to %s: %s", topic, data));
            Utils.sleep(SEND_TIMEOUT);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            System.out.println(e.getMessage());
        }
    }

    public void pushMessageAsync(final String topic, final String data) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, PAYLOAD, data);
        producer.send(producerRecord);
    }

    public void flush() {
        producer.flush();
    }

    public void close() {
        producer.flush();
        producer.close();
    }
}
