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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.openkilda.config.KafkaConfig;
import org.openkilda.wfm.config.ZookeeperConfig;
import org.openkilda.wfm.config.provider.ConfigurationProvider;
import org.openkilda.wfm.error.ConfigurationException;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import kafka.serializer.StringDecoder;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This class is mostly an example of how to startup zookeeper/kafka and send/receive messages.
 * It should be useful when developing Storm Topologies that have a Kafka Spout
 */
public class SimpleKafkaTest {
    public static final String topic = "simple-kafka"; // + System.currentTimeMillis();
    private static final String[] NO_ARGS = {};

    private static ZookeeperConfig zooKeeperConfig;
    private static KafkaConfig kafkaConfig;

    private TestUtils.KafkaTestFixture server;
    private Producer<String, String> producer;
    private ConsumerConnector consumerConnector;

    @BeforeClass
    public static void allocateConfig() throws ConfigurationException, CmdLineException {
        ConfigurationProvider configurationProvider = new LaunchEnvironment(NO_ARGS).getConfigurationProvider();
        zooKeeperConfig = configurationProvider.getConfiguration(ZookeeperConfig.class);
        kafkaConfig = configurationProvider.getConfiguration(KafkaConfig.class);
    }

    @Before
    public void setup() throws Exception {
        server = new TestUtils.KafkaTestFixture(zooKeeperConfig);
        server.start();
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
        ConsumerIterator<String, String> it = buildConsumer(SimpleKafkaTest.topic);

        //Create a producer
        producer = new KafkaProducer<>(producerProps());

        //send a message
        producer.send(new ProducerRecord<>(SimpleKafkaTest.topic, "message")).get();

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
        Map<String, List<KafkaStream<String, String>>> consumers = consumerConnector.createMessageStreams(
                topicCountMap, new StringDecoder(null), new StringDecoder(null));
        KafkaStream<String, String> stream = consumers.get(topic).get(0);
        return stream.iterator();
    }

    private Properties consumerProperties() {
        Properties props = new Properties();
        props.put("zookeeper.connect", zooKeeperConfig.getHosts());
        props.put("group.id", "group1");
        props.put("auto.offset.reset", "smallest");
        return props;
    }

    private Properties producerProps() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaConfig.getHosts());
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("request.required.acks", "1");
        return props;
    }
}
