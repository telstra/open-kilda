package org.bitbucket.openkilda.wfm.topology.flow;

import org.bitbucket.openkilda.messaging.ServiceType;
import org.bitbucket.openkilda.messaging.Utils;
import org.bitbucket.openkilda.pce.provider.NeoDriver;
import org.bitbucket.openkilda.pce.provider.PathComputer;
import org.bitbucket.openkilda.wfm.topology.AbstractTopology;
import org.bitbucket.openkilda.wfm.topology.Topology;
import org.bitbucket.openkilda.wfm.topology.flow.bolts.CrudBolt;
import org.bitbucket.openkilda.wfm.topology.flow.bolts.ErrorBolt;
import org.bitbucket.openkilda.wfm.topology.flow.bolts.NorthboundReplyBolt;
import org.bitbucket.openkilda.wfm.topology.flow.bolts.SpeakerBolt;
import org.bitbucket.openkilda.wfm.topology.flow.bolts.SplitterBolt;
import org.bitbucket.openkilda.wfm.topology.flow.bolts.TopologyEngineBolt;
import org.bitbucket.openkilda.wfm.topology.flow.bolts.TransactionBolt;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.io.File;

/**
 * Flow topology.
 */
public class FlowTopology extends AbstractTopology {
    public static final String SWITCH_ID_FIELD = "switch-id";
    public static final String STATUS_FIELD = "status";
    public static final String ERROR_TYPE_FIELD = "error-type";
    public static final Fields fieldFlowId = new Fields(Utils.FLOW_ID);
    public static final Fields fieldSwitchId = new Fields(SWITCH_ID_FIELD);
    public static final Fields fieldsFlowIdStatus = new Fields(Utils.FLOW_ID, STATUS_FIELD);
    public static final Fields fieldsMessageFlowId = new Fields(MESSAGE_FIELD, Utils.FLOW_ID);
    public static final Fields fieldsMessageErrorType = new Fields(MESSAGE_FIELD, ERROR_TYPE_FIELD);
    public static final Fields fieldsMessageSwitchIdFlowIdTransactionId =
            new Fields(MESSAGE_FIELD, SWITCH_ID_FIELD, Utils.FLOW_ID, Utils.TRANSACTION_ID);
    static final String STATE_UPDATE_TOPIC = "kilda.wfm.topo.updown";
    private static final Logger logger = LogManager.getLogger(FlowTopology.class);
    private static final String TOPIC = "kilda-test";
    private static final String NETWORK_CACHE_TOPIC = "kilda.wfm.topo.dump";

    /**
     * Path computation instance.
     */
    private final PathComputer pathComputer;

    public FlowTopology(File file, PathComputer pathComputer) {
        super(file);
        this.pathComputer = pathComputer;

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
            final FlowTopology flowTopology = new FlowTopology(file, new NeoDriver());
            StormTopology stormTopology = flowTopology.createTopology();
            final Config config = new Config();
            config.setNumWorkers(flowTopology.workers);

            logger.info("Start Topology: {}", flowTopology.getTopologyName());

            config.setDebug(false);

            StormSubmitter.submitTopology(args[0], config, stormTopology);
        } else {
            File file = new File(FlowTopology.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
            final FlowTopology flowTopology = new FlowTopology(file, new NeoDriver());
            StormTopology stormTopology = flowTopology.createTopology();
            final Config config = new Config();
            config.setNumWorkers(flowTopology.workers);

            logger.info("Start Topology Locally: {}", flowTopology.topologyName);

            config.setDebug(true);
            config.setMaxTaskParallelism(flowTopology.parallelism);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(flowTopology.topologyName, config, stormTopology);

            logger.info("Sleep", flowTopology.topologyName);
            Thread.sleep(60000);
            cluster.shutdown();
        }
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating Topology: {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        checkAndCreateTopic(NETWORK_CACHE_TOPIC);

        /*
         * Spout receives network cache dump.
         */
        KafkaSpout networkCacheKafkaSpout = createKafkaSpout(NETWORK_CACHE_TOPIC, ComponentType.NETWORK_CACHE_SPOUT.toString());
        builder.setSpout(ComponentType.NETWORK_CACHE_SPOUT.toString(), networkCacheKafkaSpout, parallelism);

        /*
         * Spout receives all Northbound requests.
         */
        KafkaSpout northboundKafkaSpout = createKafkaSpout(TOPIC, ComponentType.NORTHBOUND_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.NORTHBOUND_KAFKA_SPOUT.toString(), northboundKafkaSpout, parallelism);

        /*
         * Bolt splits requests on streams.
         * It groups requests by flow-id.
         */
        SplitterBolt splitterBolt = new SplitterBolt();
        builder.setBolt(ComponentType.SPLITTER_BOLT.toString(), splitterBolt, parallelism)
                .shuffleGrouping(ComponentType.NETWORK_CACHE_SPOUT.toString())
                .shuffleGrouping(ComponentType.NORTHBOUND_KAFKA_SPOUT.toString());

        /*
         * Bolt handles flow CRUD operations.
         * It groups requests by flow-id.
         */
        CrudBolt crudBolt = new CrudBolt(pathComputer);
        builder.setBolt(ComponentType.CRUD_BOLT.toString(), crudBolt, parallelism)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.CREATE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.READ.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.UPDATE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.DELETE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.PATH.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.RESTORE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.REROUTE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.STATUS.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.STATUS.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPEAKER_BOLT.toString(), StreamType.STATUS.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), StreamType.STATUS.toString(), fieldFlowId);

        /*
         * Bolt sends cache updates.
         */
        KafkaBolt cacheKafkaBolt = createKafkaBolt(STATE_UPDATE_TOPIC);
        builder.setBolt(ComponentType.CACHE_KAFKA_BOLT.toString(), cacheKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.CREATE.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.UPDATE.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.DELETE.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.STATUS.toString());

        /*
         * Spout receives Topology Engine response
         */
        KafkaSpout topologyKafkaSpout = createKafkaSpout(TOPIC, ComponentType.TOPOLOGY_ENGINE_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.TOPOLOGY_ENGINE_KAFKA_SPOUT.toString(), topologyKafkaSpout, parallelism);

        /*
         * Bolt processes Topology Engine responses, groups by flow-id field
         */
        TopologyEngineBolt topologyEngineBolt = new TopologyEngineBolt();
        builder.setBolt(ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), topologyEngineBolt, parallelism)
                .shuffleGrouping(ComponentType.TOPOLOGY_ENGINE_KAFKA_SPOUT.toString());

        /*
         * Bolt sends Speaker requests
         */
        KafkaBolt speakerKafkaBolt = createKafkaBolt(TOPIC);
        builder.setBolt(ComponentType.SPEAKER_KAFKA_BOLT.toString(), speakerKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.CREATE.toString())
                .shuffleGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.DELETE.toString());

        /*
         * Spout receives Speaker responses
         */
        KafkaSpout speakerKafkaSpout = createKafkaSpout(TOPIC, ComponentType.SPEAKER_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.SPEAKER_KAFKA_SPOUT.toString(), speakerKafkaSpout, parallelism);

        /*
         * Bolt processes Speaker responses, groups by flow-id field
         */
        SpeakerBolt speakerBolt = new SpeakerBolt();
        builder.setBolt(ComponentType.SPEAKER_BOLT.toString(), speakerBolt, parallelism)
                .shuffleGrouping(ComponentType.SPEAKER_KAFKA_SPOUT.toString());

        /*
         * Transaction bolt.
         */
        TransactionBolt transactionBolt = new TransactionBolt();
        builder.setBolt(ComponentType.TRANSACTION_BOLT.toString(), transactionBolt, parallelism)
                .fieldsGrouping(ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), StreamType.CREATE.toString(), fieldSwitchId)
                .fieldsGrouping(ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), StreamType.DELETE.toString(), fieldSwitchId)
                .fieldsGrouping(ComponentType.SPEAKER_BOLT.toString(), StreamType.CREATE.toString(), fieldSwitchId)
                .fieldsGrouping(ComponentType.SPEAKER_BOLT.toString(), StreamType.DELETE.toString(), fieldSwitchId);

        /*
         * Error processing bolt
         */
        ErrorBolt errorProcessingBolt = new ErrorBolt();
        builder.setBolt(ComponentType.ERROR_BOLT.toString(), errorProcessingBolt, parallelism)
                .shuffleGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.ERROR.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.ERROR.toString());

        /*
         * Bolt forms Northbound responses
         */
        NorthboundReplyBolt northboundReplyBolt = new NorthboundReplyBolt();
        builder.setBolt(ComponentType.NORTHBOUND_REPLY_BOLT.toString(), northboundReplyBolt, parallelism)
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.RESPONSE.toString())
                .shuffleGrouping(ComponentType.ERROR_BOLT.toString(), StreamType.RESPONSE.toString());

        /*
         * Bolt sends Northbound responses
         */
        KafkaBolt northboundKafkaBolt = createKafkaBolt(TOPIC);
        builder.setBolt(ComponentType.NORTHBOUND_KAFKA_BOLT.toString(), northboundKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.NORTHBOUND_REPLY_BOLT.toString(), StreamType.RESPONSE.toString());

        createHealthCheckHandler(builder, ServiceType.FLOW_TOPOLOGY.getId());

        return builder.createTopology();
    }
}
