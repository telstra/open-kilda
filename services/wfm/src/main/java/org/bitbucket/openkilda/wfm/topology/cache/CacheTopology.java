package org.bitbucket.openkilda.wfm.topology.cache;

import org.bitbucket.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheTopology extends AbstractTopology {
    private static final String STATE_UPDATE_TOPIC = "kilda.wfm.topo.updown";
    private static final String STATE_DUMP_TOPIC = "kilda.wfm.topo.dump";
    private static final String STATE_STORAGE_TOPIC = "kilda-test";

    private static final Logger logger = LoggerFactory.getLogger(CacheTopology.class);

    /**
     * Instance constructor.
     */
    public CacheTopology() {
        logger.debug("Topology built {}: zookeeper={}, kafka={}, parallelism={}, workers={}",
                topologyName, zookeeperHosts, kafkaHosts, parallelism, workers);
    }

    /**
     * Loads topology.
     *
     * @param args topology args
     * @throws Exception if topology submitting fails
     */
    public static void main(String[] args) throws Exception {
        final CacheTopology cacheTopology = new CacheTopology();
        StormTopology stormTopology = cacheTopology.createTopology();
        final Config config = new Config();
        config.setNumWorkers(cacheTopology.workers);

        if (args != null && args.length > 0) {
            logger.info("Start Topology: {}", cacheTopology.getTopologyName());

            config.setDebug(false);

            StormSubmitter.submitTopology(args[0], config, stormTopology);
        } else {
            logger.info("Start Topology Locally: {}", cacheTopology.topologyName);

            config.setDebug(true);
            config.setMaxTaskParallelism(cacheTopology.parallelism);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(cacheTopology.topologyName, config, stormTopology);

            logger.info("Sleep", cacheTopology.topologyName);
            Thread.sleep(60000);
            cluster.shutdown();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StormTopology createTopology() {
        logger.info("Creating Topology: {}", topologyName);

        checkAndCreateTopic(STATE_DUMP_TOPIC);

        TopologyBuilder builder = new TopologyBuilder();

        /*
         * Receives cache from storage.
         */
        KafkaSpout storageSpout = createKafkaSpout(STATE_STORAGE_TOPIC);
        builder.setSpout(ComponentType.STATE_STORAGE_KAFKA_SPOUT.toString(), storageSpout, parallelism);

        /*
         * Receives cache updates from WFM topology.
         */
        KafkaSpout stateSpout = createKafkaSpout(STATE_UPDATE_TOPIC);
        builder.setSpout(ComponentType.STATE_UPDATE_KAFKA_SPOUT.toString(), stateSpout, parallelism);

        /*
         * Receives cache dump request from WFM topology.
         */
        KafkaSpout dumpSpout = createKafkaSpout(STATE_DUMP_TOPIC);
        builder.setSpout(ComponentType.STATE_DUMP_KAFKA_SPOUT.toString(), dumpSpout, parallelism);

        /*
         * Stores network cache.
         */
        StateBolt stateBolt = new StateBolt();
        builder.setBolt(ComponentType.STATE_BOLT.toString(), stateBolt, parallelism)
                .allGrouping(ComponentType.STATE_STORAGE_KAFKA_SPOUT.toString())
                .allGrouping(ComponentType.STATE_UPDATE_KAFKA_SPOUT.toString())
                .shuffleGrouping(ComponentType.STATE_DUMP_KAFKA_SPOUT.toString());

        /*
         * Sends network events to storage.
         */
        KafkaBolt storageBolt = createKafkaBolt(STATE_STORAGE_TOPIC);
        builder.setBolt(ComponentType.STATE_STORAGE_KAFKA_BOLT.toString(), storageBolt, parallelism)
                .shuffleGrouping(ComponentType.STATE_BOLT.toString(), StreamType.STORE.toString());

        /*
         * Sends cache dump to WFM topology.
         */
        KafkaBolt stateDump = createKafkaBolt(STATE_DUMP_TOPIC);
        builder.setBolt(ComponentType.STATE_DUMP_KAFKA_BOLT.toString(), stateDump, parallelism)
                .shuffleGrouping(ComponentType.STATE_BOLT.toString(), StreamType.DUMP.toString());

        return builder.createTopology();
    }
}
