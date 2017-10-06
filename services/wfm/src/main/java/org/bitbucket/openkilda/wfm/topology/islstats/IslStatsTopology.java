package org.bitbucket.openkilda.wfm.topology.islstats;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.opentsdb.bolt.OpenTsdbBolt;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.opentsdb.client.OpenTsdbClient;
import org.apache.storm.topology.TopologyBuilder;
import org.bitbucket.openkilda.wfm.KafkaUtils;
import org.bitbucket.openkilda.wfm.topology.AbstractTopology;
import org.bitbucket.openkilda.wfm.topology.event.InfoEventSplitterBolt;
import org.bitbucket.openkilda.wfm.topology.islstats.bolts.IslStatsBolt;

public class IslStatsTopology extends AbstractTopology {
    private static Logger logger = LogManager.getLogger(IslStatsTopology.class);

    private final String topoName = "WFM_IslStats";
    private final KafkaUtils kutils;
    private final int parallelism = 1;

    private String topic = "kilda-test";

    public IslStatsTopology() {
        this.kutils = new KafkaUtils();
    }

    public IslStatsTopology(KafkaUtils kutils) {
        this.kutils = kutils;
    }


    public static void main(String[] args) throws Exception {

        IslStatsTopology kildaTopology = new IslStatsTopology();
        StormTopology topo = kildaTopology.createTopology();
        String name = (args != null && args.length > 0) ?
                args[0] : kildaTopology.topoName;

        Config conf = new Config();
        conf.setDebug(false);

        //If there are arguments, we are running on a cluster; otherwise, we are running locally
        if (args != null && args.length > 0) {
            conf.setNumWorkers(kildaTopology.parallelism);
            StormSubmitter.submitTopology(name, conf, topo);
        } else {
            try {
                conf.setMaxTaskParallelism(kildaTopology.parallelism);

                LocalCluster cluster = new LocalCluster();
                cluster.submitTopology(name, conf, topo);

                logger.info("sleeping");
                while (true) {
                    //
                }
//                Thread.sleep(1000 * 1000);
//                logger.debug("shutting down topology");
//                cluster.shutdown();
            } catch (Exception e) {
                logger.error("error submitting topology");
                logger.error(e.toString());
            }
        }
    }


    public StormTopology createTopology() {
        logger.debug("Building Topology - " + this.getClass().getSimpleName());

        TopologyBuilder builder = new TopologyBuilder();

        if (!kutils.topicExists(topic)) {
            logger.debug(topic + " did not exist in Kafka, creating");
            kutils.createTopics(new String[]{topic});
        }

        final String spoutName = topic + "-spout";
        logger.debug("connecting to " + topic + " topic");
        builder.setSpout(spoutName, kutils.createKafkaSpout(topic));

        final String verifyIslStatsBoltName = IslStatsBolt.class.getSimpleName();
        IslStatsBolt verifyIslStatsBolt = new IslStatsBolt();
        logger.debug("starting " + verifyIslStatsBoltName + " bolt");
        builder.setBolt(verifyIslStatsBoltName, verifyIslStatsBolt, parallelism).shuffleGrouping(spoutName);

//        OpenTsdbClient.Builder tsdbBuilder = OpenTsdbClient.newBuilder(topologyProperties.getProperty("statstopology.openTsdbUrl"))
//                .sync(30_000).returnDetails();
//        OpenTsdbBolt openTsdbBolt = new OpenTsdbBolt(tsdbBuilder, TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER)
//                .withBatchSize(10)
//                .withFlushInterval(2)
//                .failTupleForFailedMetrics();
//        logger.debug("starting opentsdb bolt");
//        builder.setBolt("opentsdb", openTsdbBolt)
//                .shuffleGrouping(verifyIslStatsBoltName);

        return builder.createTopology();
    }

    public String getTopoName() {
        return topoName;
    }
}
