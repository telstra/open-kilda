package org.bitbucket.openkilda.wfm;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.BrokerHosts;
import org.apache.storm.kafka.ZkHosts;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Properties;

/**
 * This topology does the following:
 *  1) Listen to the kilda open flow speaker topic and split it into info / command / other.
 *      - It does that by listening to kafka via a kafka spout (createKafkaSpout) and adding
 *        a bolt to that (OFEventSplitterBolt). The bolt is the thing that creates 3 storm streams.
 *  2) Create bolts to listen to each of those streams
 *      - The bolts are KafkaBolts, tied to the streams emitted in #1.
 *  3) At present, we only have another splitter for the INFO channel, and the same strategy is
 *      followed, with the exception that there isn't a need to listen to the INFO kafka topic,
 *      since we already have the stream within storm.
 *
 */
public class OFEventSplitterTopology {

    private static Logger logger = LogManager.getLogger(OFEventSplitterTopology.class);

    public String topic = "kilda-test"; // + System.currentTimeMillis();
    public String defaultTopoName = "OF_Event_Splitter";
    public KafkaUtils kutils = new KafkaUtils();
    public Properties kafkaProps = new Properties();
    public int parallelism = 3;

    public OFEventSplitterTopology(){
        kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProps.put("bootstrap.servers", "kafka.pendev:9092");
    }

    public OFEventSplitterTopology withKafkaProps(Properties kprops){
        kafkaProps.putAll(kprops);
        return this;
    }

    /**
     * The default behavior is to use the default KafkaUtils.
     * If more specialized behavior is needed, pass in a different one;
     */
    public OFEventSplitterTopology withKafkaUtils(KafkaUtils kutils){
        this.kutils = kutils;
        return this;
    }

    /**
     * Build the Topology .. ie add it to the TopologyBuilder
     */
    public StormTopology createTopology(){

        logger.debug("Building Topology - OFEventSplitterTopology");

        TopologyBuilder builder = new TopologyBuilder();

        /*
         * Setup the initial kafka spout and bolts to write the streams to kafka.
         */
        String primarySpout = topic+"-spout";
        String primaryBolt = topic+"-bolt";
        primeKafkaTopic(topic); // in case the topic doesn't exist yet
        builder.setSpout(primarySpout, kutils.createKafkaSpout(topic));
        builder.setBolt(primaryBolt,
                new OFEventSplitterBolt(), parallelism)
                .shuffleGrouping(primarySpout);

        /*
         * Setup the next level of spouts / bolts .. take the streams emitted and push to kafka.
         * At present, only INFO has further processing, so it has an additional bolt to create
         * more streams.  The others just need to write the stream their stream to a kafka topic.
         */
        for (String channel : OFEventSplitterBolt.CHANNELS) {
            primeKafkaTopic(channel);
            builder.setBolt(channel+"-kafkabolt", kutils.createKafkaBolt(channel), parallelism)
                    .shuffleGrouping(primaryBolt, channel);

        }
        // NB: This is the extra bolt for INFO to generate more streams. If the others end up with
        //     their own splitter bolt, then we can move this logic into the for loop above.
        String infoSplitterBoltID = OFEventSplitterBolt.INFO+"-bolt";
        builder.setBolt(infoSplitterBoltID, new InfoEventSplitterBolt(),3).shuffleGrouping
                (primaryBolt, OFEventSplitterBolt.INFO);

        // Create the output from the InfoSplitter to Kafka
        // TODO: Can convert part of this to a test .. see if the right messages land in right topic
        for (String stream : InfoEventSplitterBolt.outputStreams){
            primeKafkaTopic(stream);
            builder.setBolt(stream+"-kafkabolt", kutils.createKafkaBolt(stream), parallelism)
                    .shuffleGrouping(infoSplitterBoltID, stream);
        }
        return builder.createTopology();
    }

    // TODO: KafkaUtils should be passed the configured Kafka server.
    KafkaProducer<String,String> kProducer = new KafkaUtils().createStringsProducer();
    public void primeKafkaTopic(String topic){
        if (!kutils.topicExists(topic)){
            kutils.createTopics(new String[]{topic});
        }
        // the old approach - just send a message to autocreate the topic
        //kProducer.send(new ProducerRecord<>(topic, "no_op", "{\"type\": \"NO_OP\"}"));
    }


    //Entry point for the topology
    public static void main(String[] args) throws Exception {
        Config conf = new Config();
        conf.setDebug(false);
        OFEventSplitterTopology splitterTopology = new OFEventSplitterTopology();
        StormTopology topo = splitterTopology.createTopology();

        //If there are arguments, we are running on a cluster; otherwise, we are running locally
        if (args != null && args.length > 0) {
            conf.setNumWorkers(3);
            StormSubmitter.submitTopology(args[0], conf, topo);
        }
        else {
            conf.setMaxTaskParallelism(3);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(splitterTopology.defaultTopoName, conf,topo);

            Thread.sleep(20000);
            cluster.shutdown();
        }

    }

}
