package org.bitbucket.openkilda.wfm;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.bitbucket.openkilda.wfm.topology.TestKafkaProducer;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Properties;

/**
 * Created by carmine on 4/4/17.
 */
public class AbstractStormTest {
    static KafkaUtils kutils;
    static TestUtils.KafkaTestFixture server;
    protected static TestKafkaProducer kProducer;
    protected static LocalCluster cluster;

    protected static Properties kafkaProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, TestUtils.kafkaUrl);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("request.required.acks", "1");
        return properties;
    }

    protected static Config stormConfig() {
        Config config = new Config();
        config.setDebug(false);
        config.setNumWorkers(1);
        return config;
    }

    @BeforeClass
    public static void setupOnce() throws Exception {
        System.out.println("------> Creating Sheep \uD83D\uDC11\n");
        cluster = new LocalCluster();
        server = new TestUtils.KafkaTestFixture();
        server.start();
        kutils = new KafkaUtils()
                .withZookeeperHost(TestUtils.zookeeperUrl)
                .withKafkaHosts(TestUtils.kafkaUrl);
        kProducer = new TestKafkaProducer(kafkaProperties());
    }

    @AfterClass
    public static void teardownOnce() throws Exception {
        System.out.println("------> Killing Sheep \uD83D\uDC11\n");
        kProducer.close();
        cluster.shutdown();
        server.stop();
    }
}
