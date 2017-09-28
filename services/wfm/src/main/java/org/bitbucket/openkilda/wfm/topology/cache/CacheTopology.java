package org.bitbucket.openkilda.wfm.topology.cache;

import org.bitbucket.openkilda.messaging.ServiceType;
import org.bitbucket.openkilda.wfm.topology.AbstractTopology;
import org.bitbucket.openkilda.wfm.topology.Topology;
import org.bitbucket.openkilda.wfm.topology.event.OFEventWFMTopology;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class CacheTopology extends AbstractTopology {
    static final String STATE_DUMP_TOPIC = "kilda.wfm.topo.dump";
    static final String STATE_UPDATE_TOPIC = "kilda.wfm.topo.updown";
    private static final String STATE_TPE_TOPIC = "kilda-test";

    private static final Logger logger = LoggerFactory.getLogger(CacheTopology.class);

    /**
     * Instance constructor.
     */
    public CacheTopology(File file) {
        super(file);

        checkAndCreateTopic(STATE_TPE_TOPIC);
        checkAndCreateTopic(STATE_UPDATE_TOPIC);
        checkAndCreateTopic(STATE_DUMP_TOPIC);

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
        if (args != null && args.length > 0) {
            File file = new File(args[1]);
            final CacheTopology cacheTopology = new CacheTopology(file);
            StormTopology stormTopology = cacheTopology.createTopology();
            final Config config = new Config();
            config.setNumWorkers(cacheTopology.workers);

            logger.info("Start Topology: {}", cacheTopology.getTopologyName());

            config.setDebug(false);

            StormSubmitter.submitTopology(args[0], config, stormTopology);
        } else {
            File file = new File(CacheTopology.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
            final CacheTopology cacheTopology = new CacheTopology(file);
            StormTopology stormTopology = cacheTopology.createTopology();
            final Config config = new Config();
            config.setNumWorkers(cacheTopology.workers);

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

        TopologyBuilder builder = new TopologyBuilder();

       /*
         * Receives cache from storage.
         */
        KafkaSpout storageSpout = createKafkaSpout(STATE_TPE_TOPIC, ComponentType.TPE_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.TPE_KAFKA_SPOUT.toString(), storageSpout, parallelism);

        /*
         * Receives cache updates from WFM topology.
         */
        KafkaSpout stateSpout = createKafkaSpout(STATE_UPDATE_TOPIC, ComponentType.WFM_UPDATE_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.WFM_UPDATE_KAFKA_SPOUT.toString(), stateSpout, parallelism);

        /*
         * Stores network cache.
         */
        CacheBolt cacheBolt = new CacheBolt(OFEventWFMTopology.DEFAULT_DISCOVERY_TIMEOUT);
        builder.setBolt(ComponentType.CACHE_BOLT.toString(), cacheBolt, parallelism)
                .shuffleGrouping(ComponentType.WFM_UPDATE_KAFKA_SPOUT.toString())
                .shuffleGrouping(ComponentType.TPE_KAFKA_SPOUT.toString());

        /*
         * Sends network events to storage.
         */
        KafkaBolt storageBolt = createKafkaBolt(STATE_TPE_TOPIC);
        builder.setBolt(ComponentType.TPE_KAFKA_BOLT.toString(), storageBolt, parallelism)
                .shuffleGrouping(ComponentType.CACHE_BOLT.toString(), StreamType.TPE.toString());

        /*
         * Sends cache dump to WFM topology.
         */
        KafkaBolt stateDump = createKafkaBolt(STATE_DUMP_TOPIC);
        builder.setBolt(ComponentType.WFM_DUMP_KAFKA_BOLT.toString(), stateDump, parallelism)
                .shuffleGrouping(ComponentType.CACHE_BOLT.toString(), StreamType.WFM_DUMP.toString());

        createHealthCheckHandler(builder, ServiceType.CACHE_TOPOLOGY.getId());

        return builder.createTopology();
    }
}
