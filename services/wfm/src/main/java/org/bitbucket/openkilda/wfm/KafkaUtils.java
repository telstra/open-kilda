package org.bitbucket.openkilda.wfm;

import kafka.api.OffsetRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.storm.kafka.*;
import org.apache.storm.spout.SchemeAsMultiScheme;

import java.util.Properties;

/**
 * Common utilities for Kafka
 *
 * Reference:
 * (1) Basics - https://kafka.apache.org/quickstart
 * (2) System Tools - https://cwiki.apache.org/confluence/display/KAFKA/System+Tools
 */
public class KafkaUtils {

    public String zookeeperHost = "zookeeper.pendev:2181";
    public String kafkaHosts = "kafka.pendev:9092";
    public Long offset = OffsetRequest.EarliestTime();

    public KafkaUtils () {}
    public KafkaUtils withZookeeperHost(String zookeeperHost){
        this.zookeeperHost = zookeeperHost;
        return this;
    }
    public KafkaUtils withKafkaHosts(String kafkaHosts){
        this.kafkaHosts = kafkaHosts;
        return this;
    }
    /** @param offset either OffsetRequest.EarliestTime() or OffsetRequest.LatestTime()  */
    public KafkaUtils withOffset(Long offset){
        this.offset = offset;
        return this;
    }

    /**
     * @return a Basic Kafka Producer where both key/value are strings.
     */
    public KafkaProducer<String, String> createStringsProducer(){
        Properties kprops = new Properties();
        kprops.put("bootstrap.servers", kafkaHosts);
        kprops.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kprops.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return createStringsProducer(kprops);
    }

    /**
     * @param kprops the properties to use to build the KafkaProducer
     */
    public KafkaProducer<String, String> createStringsProducer(Properties kprops){
        return new KafkaProducer<>(kprops);
    }

    /**
     * Creates a basic Kafka Spout.
     *
     * @param topic the topic to listen on
     * @return a KafkaSpout that can be used in a topology
     */
    public KafkaSpout createKafkaSpout(String topic){
        String spoutID = topic + "_" + System.currentTimeMillis();
        String zkRoot = "/" + topic; // used to store offset information.
        ZkHosts hosts = new ZkHosts(zookeeperHost);
        SpoutConfig cfg = new SpoutConfig(hosts, topic, zkRoot, spoutID);
        cfg.startOffsetTime = offset;
        cfg.scheme = new SchemeAsMultiScheme(new StringScheme());
        cfg.bufferSizeBytes = 1024 * 1024 * 4;
        cfg.fetchSizeBytes = 1024 * 1024 * 4;
        return new KafkaSpout(cfg);
    }


}
