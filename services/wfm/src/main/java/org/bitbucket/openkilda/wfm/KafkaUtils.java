package org.bitbucket.openkilda.wfm;

import kafka.api.OffsetRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.storm.kafka.BrokerHosts;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.SpoutConfig;
import org.apache.storm.kafka.StringScheme;
import org.apache.storm.spout.SchemeAsMultiScheme;

import java.util.Properties;

/**
 * Common utilities for Kafka
 */
public class KafkaUtils {

    /**
     * @return a Basic Kafka Producer where both key/value are strings.
     */
    public static KafkaProducer<String, String> createStringsProducer(){
        Properties kprops = new Properties();
        kprops.put("bootstrap.servers", "localhost:9092");
        kprops.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kprops.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return createStringsProducer(kprops);
    }

    /**
     * @param kprops the properties to use to build the KafkaProducer
     */
    public static KafkaProducer<String, String> createStringsProducer(Properties kprops){
        return new KafkaProducer<>(kprops);
    }

    /**
     * Creates a basic Kafka Spout.
     * TODO: make the properties configurable.
     *
     * @param topic the topic to listen on
     * @return a KafkaSpout that can be used in a topology
     */
    public static KafkaSpout createKafkaSpout(String topic, BrokerHosts hosts){
        String spoutID = topic + "_" + System.currentTimeMillis();
        SpoutConfig kafkaSpoutConfig = new SpoutConfig (hosts, topic, "/" + topic, spoutID);
        kafkaSpoutConfig.bufferSizeBytes = 1024 * 1024 * 4;
        kafkaSpoutConfig.fetchSizeBytes = 1024 * 1024 * 4;
        kafkaSpoutConfig.startOffsetTime = OffsetRequest.EarliestTime();  // start later
        kafkaSpoutConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        return new KafkaSpout(kafkaSpoutConfig);
    }

}
