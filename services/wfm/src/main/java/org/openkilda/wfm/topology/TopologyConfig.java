package org.openkilda.wfm.topology;

import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.PropertiesReader;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

@Value
@Slf4j
public class TopologyConfig {
    private Boolean useLocalCluster;
    private Integer localExecutionTime;

    private Integer parallelism;
    private Integer workers;
    private Integer discoveryInterval;
    private Integer discoveryTimeout;
    private Integer discoveryLimit;
    private final float discoverySpeakerFailureTimeout;
    private final float discoveryDumpRequestTimeout;
    private String filterDirectory;
    private Level loggerLevel;
    private String loggerWatermark;

    private String zookeeperHosts;
    private Integer zookeeperSessionTimeout;
    private Integer zookeeperConnectTimeout;

    private String kafkaHosts;
    private Integer kafkaPartitionsDefault;
    private Integer kafkaReplicationDefault;

    private String kafkaCtrlTopic;
    private String kafkaFlowTopic;
    private String kafkaHealthCheckTopic;
    private String kafkaNorthboundTopic;
    private String kafkaOtsdbTopic;
    private String kafkaSimulatorTopic;
    private String kafkaSpeakerTopic;
    private String kafkaStatsTopic;
    private String kafkaTopoEngTopic;
    private String kafkaTopoDiscoTopic;
    private String kafkaTopoCacheTopic;


    private String openTsDBHosts;
    private Integer openTsdbTimeout;
    private boolean openTsdbClientChunkedRequestsEnabled;

    private Integer openTsdbNumSpouts;
    private Integer openTsdbFilterBoltExecutors;
    private Integer openTsdbBoltExecutors;
    private Integer openTsdbBoltWorkers;
    private Integer openTsdbBatchSize;
    private Integer openTsdbFlushInterval;
    private Integer getDatapointParseBoltExecutors;
    private Integer getDatapointParseBoltWorkers;

    private String neo4jHost;
    private String neo4jLogin;
    private String neo4jPassword;

    private String topologyEngineRestEndpoint;

    private PropertiesReader config;

    public TopologyConfig(PropertiesReader config) throws ConfigurationException {
        this.config = config;
        useLocalCluster = config.getBoolean("cli.local");
        localExecutionTime = (int)(config.getFloat("local.execution.time") * 1000);

        parallelism = config.getInteger("parallelism");
        workers = config.getInteger("workers");
        discoveryInterval = config.getInteger("discovery.interval");
        discoveryTimeout = config.getInteger("discovery.timeout");
        discoveryLimit = config.getInteger("discovery.limit");
        discoverySpeakerFailureTimeout = config.getFloat("discovery.speaker-failure-timeout");
        filterDirectory = config.getString("filter.directory");
        loggerLevel = Level.valueOf(config.getString("logger.level"));
        loggerWatermark = config.getString("logger.watermark");
        discoveryDumpRequestTimeout = config.getFloat("discovery.dump-request-timeout-seconds");

        zookeeperHosts = config.getString("zookeeper.hosts");
        zookeeperSessionTimeout = (int)(config.getFloat("zookeeper.session.timeout") * 1000);
        zookeeperConnectTimeout = (int)(config.getFloat("zookeeper.connect.timeout") * 1000);
        kafkaHosts = config.getString("kafka.hosts");
        kafkaPartitionsDefault = config.getInteger("kafka.partitions.default");
        kafkaReplicationDefault = config.getInteger("kafka.replication.default");

        kafkaCtrlTopic = config.getString("kafka.topic.ctrl");
        kafkaFlowTopic = config.getString("kafka.topic.flow");
        kafkaHealthCheckTopic = config.getString("kafka.topic.health.check");
        kafkaNorthboundTopic = config.getString("kafka.topic.northbound");
        kafkaOtsdbTopic = config.getString("kafka.topic.opentsdb");
        kafkaSimulatorTopic = config.getString("kafka.topic.simulator");
        kafkaSpeakerTopic = config.getString("kafka.topic.speaker");
        kafkaStatsTopic = config.getString("kafka.topic.stats");
        kafkaTopoCacheTopic = config.getString("kafka.topic.topo.cache");
        kafkaTopoDiscoTopic = config.getString("kafka.topic.topo.disco");
        kafkaTopoEngTopic = config.getString("kafka.topic.topo.eng");

        openTsDBHosts = config.getString("opentsdb.hosts");
        openTsdbTimeout = (int)(config.getFloat("opentsdb.timeout") * 1000);
        openTsdbClientChunkedRequestsEnabled = config.getBoolean("opentsdb.client.chunked-requests.enabled");
        openTsdbNumSpouts = config.getInteger("opentsdb.num.spouts");
        openTsdbFilterBoltExecutors = config.getInteger("opentsdb.num.opentsdbfilterbolt");
        openTsdbBoltExecutors = config.getInteger("opentsdb.num.opentsdbbolt");
        openTsdbBoltWorkers = config.getInteger("opentsdb.workers.opentsdbolt");
        openTsdbBatchSize = config.getInteger("opentsdb.batch.size");
        openTsdbFlushInterval = config.getInteger("opentsdb.flush.interval");
        getDatapointParseBoltExecutors = config.getInteger("opentsdb.num.datapointparserbolt");
        getDatapointParseBoltWorkers = config.getInteger("opentsdb.workers.datapointparserbolt");

        neo4jHost = config.getString("neo4j.hosts");
        neo4jLogin = config.getString("neo4j.user");
        neo4jPassword = config.getString("neo4j.pswd");

        topologyEngineRestEndpoint = config.getString("topology.engine.rest.endpoint");
    }

    public Integer getWorkers() {
        return workers;
    }

    public Integer getWorkers(String name) {
        int value = workers;
        try {
            value = config.getInteger(name + ".workers");
        } catch (ConfigurationException e) {
            log.warn("could not find {}.workers so using global default", name);
        }
        return value;
    }
}
