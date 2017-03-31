package org.bitbucket.openkilda.wfm;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.kafka.*;
import org.apache.storm.topology.TopologyBuilder;

import kafka.api.OffsetRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;


import java.util.Properties;


/**
 * A test topology to enquire about Kafka related stuff.
 */
public class KafkaLoggerTopology {

    private static final Logger logger = LogManager.getLogger(WordCount.class);

    public static KafkaProducer<String, String> createStringsProducer(){
        Properties kprops = new Properties();
        kprops.put("bootstrap.servers", "localhost:9092");
        kprops.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kprops.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return new KafkaProducer<>(kprops);
    }

    public static KafkaSpout createKafkaSpout(String topic){
        String spoutID = topic + "_" + System.currentTimeMillis();
        String zkRoot = "/" + topic; // used to store offset information.
        String zkStr = "zookeeper.pendev:2181";
        ZkHosts hosts = new ZkHosts(zkStr);
        SpoutConfig cfg = new SpoutConfig(hosts, topic, zkRoot, spoutID);
        cfg.startOffsetTime = OffsetRequest.EarliestTime();
        cfg.scheme = new SchemeAsMultiScheme(new StringScheme());
        cfg.bufferSizeBytes = 1024 * 1024 * 4;
        cfg.fetchSizeBytes = 1024 * 1024 * 4;
        return new KafkaSpout(cfg);
    }

    //Entry point for the topology
    public static void main(String[] args) throws Exception {
        TopologyBuilder builder = new TopologyBuilder();

        // process command line ... topoName topic level watermark
        String topoName = (args != null && args.length > 0) ? args[0] : "kafka-inspect";
        String topic = (args != null && args.length > 1) ? args[1] : "kilda-speaker";
        String spoutId = "KafkaSpout-" + topic;
        Level level = (args != null && args.length > 2) ? Level.valueOf(args[2]) : Level.DEBUG;
        String watermark = (args != null && args.length > 3) ? args[3] : "";
        int parallelism = 1;

        boolean debug = (level == Level.DEBUG || level == Level.TRACE || level == Level.ALL);
        builder.setSpout(spoutId, createKafkaSpout(topic), parallelism);
        builder.setBolt("Logger", new LoggerBolt().withLevel(level).withWatermark(watermark),
                parallelism)
                .shuffleGrouping(spoutId);

        Config conf = new Config();
        conf.setDebug(debug);
        conf.setNumWorkers(1);
        StormSubmitter.submitTopology(topoName, conf, builder.createTopology());
    }

}
