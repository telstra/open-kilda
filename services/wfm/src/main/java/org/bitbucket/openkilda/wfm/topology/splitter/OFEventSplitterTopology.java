package org.bitbucket.openkilda.wfm.topology.splitter;

import org.bitbucket.openkilda.wfm.KafkaUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.TopologyBuilder;

import java.util.Properties;

/**
 * This topology does the following:
 * 1) Listen to the kilda open flow speaker topic and split it into info / command / other.
 * - It does that by listening to kafka via a kafka spout (createKafkaSpout) and adding
 * a bolt to that (OFEventSplitterBolt). The bolt is the thing that creates 3 storm streams.
 * 2) Create bolts to listen to each of those streams
 * - The bolts are KafkaBolts, tied to the streams emitted in #1.
 * 3) At present, we only have another splitter for the INFO channel, and the same strategy is
 * followed, with the exception that there isn't a need to listen to the INFO kafka topic,
 * since we already have the stream within storm.
 */
public class OFEventSplitterTopology {

    private static Logger logger = LogManager.getLogger(OFEventSplitterTopology.class);

    public String topic = "kilda-test";//Topic.OFS_WFM_DISCOVERY.getId();
    public String defaultTopoName = "OF_Event_Splitter";
    public KafkaUtils kutils;
    public Properties kafkaProps;
    public int parallelism = 1;

    public OFEventSplitterTopology() {
        this(new KafkaUtils());
    }

    public OFEventSplitterTopology(KafkaUtils kutils) {
        this.kutils = kutils;
        this.kafkaProps = kutils.createStringsKafkaProps();
    }

    //Entry point for the topology
    public static void main(String[] args) throws Exception {
        Config conf = new Config();
        conf.setDebug(false);
        OFEventSplitterTopology splitterTopology = new OFEventSplitterTopology();
        StormTopology topo = splitterTopology.createTopology();

        //If there are arguments, we are running on a cluster; otherwise, we are running locally
        if (args != null && args.length > 0) {
            conf.setNumWorkers(1);
            StormSubmitter.submitTopology(args[0], conf, topo);
        } else {
            conf.setMaxTaskParallelism(3);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(splitterTopology.defaultTopoName, conf, topo);

            Thread.sleep(20000);
            cluster.shutdown();
        }

    }

    public OFEventSplitterTopology withKafkaProps(Properties kprops) {
        kafkaProps.putAll(kprops);
        return this;
    }

    /**
     * The default behavior is to use the default KafkaUtils.
     * If more specialized behavior is needed, pass in a different one;
     */
    public OFEventSplitterTopology withKafkaUtils(KafkaUtils kutils) {
        this.kutils = kutils;
        return this;
    }

    /**
     * Build the Topology .. ie add it to the TopologyBuilder
     */
    public StormTopology createTopology() {

        logger.debug("Building Topology - OFEventSplitterTopology");

        TopologyBuilder builder = new TopologyBuilder();

        /*
         * Setup the initial kafka spout and bolts to write the streams to kafka.
         */
        String primarySpout = topic + "-spout";
        String primaryBolt = topic + "-bolt";
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
            builder.setBolt(channel + "-kafkabolt", kutils.createKafkaBolt(channel), parallelism)
                    .shuffleGrouping(primaryBolt, channel);

        }
        // NB: This is the extra bolt for INFO to generate more streams. If the others end up with
        //     their own splitter bolt, then we can move this logic into the for loop above.
        String infoSplitterBoltID = OFEventSplitterBolt.INFO + "-bolt";
        builder.setBolt(infoSplitterBoltID, new InfoEventSplitterBolt(), 3).shuffleGrouping
                (primaryBolt, OFEventSplitterBolt.INFO);

        // Create the output from the InfoSplitter to Kafka
        // TODO: Can convert part of this to a test .. see if the right messages land in right topic
        for (String stream : InfoEventSplitterBolt.outputStreams) {
            primeKafkaTopic(stream);
            builder.setBolt(stream + "-kafkabolt", kutils.createKafkaBolt(stream), parallelism)
                    .shuffleGrouping(infoSplitterBoltID, stream);
        }
        return builder.createTopology();
    }

    public void primeKafkaTopic(String topic) {
        if (!kutils.topicExists(topic)) {
            kutils.createTopics(new String[]{topic});
        }
    }

}
