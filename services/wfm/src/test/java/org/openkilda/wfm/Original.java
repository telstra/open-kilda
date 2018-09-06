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

package org.openkilda.wfm;

/**
 * Created by carmine on 3/20/17.
 */

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import kafka.serializer.StringDecoder;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import org.apache.curator.test.TestingServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


public class Original {
    public static final String topic = "topic1-" + System.currentTimeMillis();

    private KafkaTestFixture server;
    private Producer<String, String> producer;
    private ConsumerConnector consumerConnector;

    @Before
    public void setup() throws Exception {
        server = new KafkaTestFixture();
        server.start(serverProperties());
    }

    @After
    public void teardown() throws Exception {
        producer.close();
        consumerConnector.shutdown();
        server.stop();
    }

    @Test
    public void shouldWriteThenRead() throws Exception {

        //Create a consumer
        ConsumerIterator<String, String> it = buildConsumer(Original.topic);

        //Create a producer
        producer = new KafkaProducer<>(producerProps());

        //send a message
        producer.send(new ProducerRecord<>(Original.topic, "message")).get();

        //read it back
        MessageAndMetadata<String, String> messageAndMetadata = it.next();
        String value = messageAndMetadata.message();
        assertThat(value, is("message"));
    }

    private ConsumerIterator<String, String> buildConsumer(String topic) {
        Properties props = consumerProperties();

        Map<String, Integer> topicCountMap = new HashMap<>();
        topicCountMap.put(topic, 1);
        ConsumerConfig consumerConfig = new ConsumerConfig(props);
        consumerConnector = Consumer.createJavaConsumerConnector(consumerConfig);
        Map<String, List<KafkaStream<String, String>>> consumers =
                consumerConnector.createMessageStreams(topicCountMap, new StringDecoder(null), new StringDecoder(null));
        KafkaStream<String, String> stream = consumers.get(topic).get(0);
        return stream.iterator();
    }

    private Properties consumerProperties() {
        Properties props = new Properties();
        props.put("zookeeper.connect", serverProperties().get("zookeeper.connect"));
        props.put("group.id", "group1");
        props.put("auto.offset.reset", "smallest");
        return props;
    }

    private Properties producerProps() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("request.required.acks", "1");
        return props;
    }

    private Properties serverProperties() {
        Properties props = new Properties();
        props.put("zookeeper.connect", "localhost:2181");
        props.put("broker.id", "1");
        return props;
    }

    private static class KafkaTestFixture {
        private TestingServer zk;
        private KafkaServerStartable kafka;

        public void start(Properties properties) throws Exception {
            Integer port = getZkPort(properties);
            zk = new TestingServer(port);
            zk.start();

            KafkaConfig kafkaConfig = new KafkaConfig(properties);
            kafka = new KafkaServerStartable(kafkaConfig);
            kafka.startup();
        }

        public void stop() throws IOException {
            kafka.shutdown();
            zk.stop();
            zk.close();
        }

        private int getZkPort(Properties properties) {
            String url = (String) properties.get("zookeeper.connect");
            String port = url.split(":")[1];
            return Integer.valueOf(port);
        }
    }
}
