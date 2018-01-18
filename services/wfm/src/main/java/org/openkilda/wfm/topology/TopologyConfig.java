package org.openkilda.wfm.topology;

import org.slf4j.event.Level;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.PropertiesReader;

public class TopologyConfig {
    private Boolean isLocal;
    private Integer localExecutionTime;

    private Integer parallelism;
    private Integer workers;
    private Integer discoveryInterval;
    private Integer discoveryTimeout;
    private String filterDirectory;
    private Level loggerLevel;
    private String loggerWatermark;

    private String zookeeperHosts;
    private Integer zookeeperSessionTimeout;
    private Integer zookeeperConnectTimeout;

    private String kafkaHosts;

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
    private Integer openTsdbNumSpouts;
    private Integer openTsdbNumOpentasbFilterBolt;
    private Integer openTsdbNumOpentsdbBolt;

    private String neo4jHost;
    private String neo4jLogin;
    private String neo4jPassword;

    public TopologyConfig(PropertiesReader config) throws ConfigurationException {
        isLocal = config.getBoolean("cli.local");
        localExecutionTime = (int)(config.getFloat("local.execution.time") * 1000);

        parallelism = config.getInteger("parallelism");
        workers = config.getInteger("workers");
        discoveryInterval = config.getInteger("discovery.interval");
        discoveryTimeout = config.getInteger("discovery.timeout");
        filterDirectory = config.getString("filter.directory");
        loggerLevel = Level.valueOf(config.getString("logger.level"));
        loggerWatermark = config.getString("logger.watermark");

        zookeeperHosts = config.getString("zookeeper.hosts");
        zookeeperSessionTimeout = (int)(config.getFloat("zookeeper.session.timeout") * 1000);
        zookeeperConnectTimeout = (int)(config.getFloat("zookeeper.connect.timeout") * 1000);
        kafkaHosts = config.getString("kafka.hosts");

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
        openTsdbNumSpouts = config.getInteger("opentsdb.num.spouts");
        openTsdbNumOpentasbFilterBolt = config.getInteger("opentsdb.num.opentsdbfilterbolt");
        openTsdbNumOpentsdbBolt = config.getInteger("opentsdb.num.opentsdbbolt");

        neo4jHost = config.getString("neo4j.hosts");
        neo4jLogin = config.getString("neo4j.user");
        neo4jPassword = config.getString("neo4j.pswd");
    }

    public Boolean getLocal() {
        return isLocal;
    }

    public Integer getLocalExecutionTime() {
        return localExecutionTime;
    }

    public Integer getParallelism() {
        return parallelism;
    }

    public Integer getWorkers() {
        return workers;
    }

    public Integer getDiscoveryInterval() {
        return discoveryInterval;
    }

    public Integer getDiscoveryTimeout() {
        return discoveryTimeout;
    }

    public String getFilterDirectory() {
        return filterDirectory;
    }

    public Level getLoggerLevel() {
        return loggerLevel;
    }

    public String getLoggerWatermark() {
        return loggerWatermark;
    }

    public String getZookeeperHosts() {
        return zookeeperHosts;
    }

    public Integer getZookeeperSessionTimeout() {
        return zookeeperSessionTimeout;
    }

    public Integer getZookeeperConnectTimeout() {
        return zookeeperConnectTimeout;
    }

    public String getKafkaHosts() {
        return kafkaHosts;
    }

    // --- kafka topics

    public String getKafkaCtrlTopic() {
        return kafkaCtrlTopic;
    }

    public String getKafkaFlowTopic() {
        return kafkaFlowTopic;
    }

    public String getKafkaHealthCheckTopic() {
        return kafkaHealthCheckTopic;
    }

    public String getKafkaNorthboundTopic() {
        return kafkaNorthboundTopic;
    }

    public String getKafkaOtsdbTopic() {
        return kafkaOtsdbTopic;
    }

    public String getKafkaSimulatorTopic() {
        return kafkaSimulatorTopic;
    }

    public String getKafkaSpeakerTopic() {
        return kafkaSpeakerTopic;
    }

    public String getKafkaStatsTopic() {
        return kafkaStatsTopic;
    }

    public String getKafkaTopoCacheTopic() {
        return kafkaTopoCacheTopic;
    }

    public String getKafkaTopoDiscoTopic() {
        return kafkaTopoDiscoTopic;
    }

    public String getKafkaTopoEngTopic() {
        return kafkaTopoEngTopic;
    }

    // ---

    public String getOpenTsDBHosts() {
        return openTsDBHosts;
    }

    public Integer getOpenTsdbTimeout() {
        return openTsdbTimeout;
    }

    public Integer getOpenTsdbNumSpouts() {
        return openTsdbNumSpouts;
    }

    public Integer getOpenTsdbNumOpentasbFilterBolt() {
        return openTsdbNumOpentasbFilterBolt;
    }

    public Integer getOpenTsdbNumOpentsdbBolt() {
        return openTsdbNumOpentsdbBolt;
    }

    public String getNeo4jHost() {
        return neo4jHost;
    }

    public String getNeo4jLogin() {
        return neo4jLogin;
    }

    public String getNeo4jPassword() {
        return neo4jPassword;
    }
}
