package org.openkilda.wfm.topology;

import org.apache.logging.log4j.Level;
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
    private String kafkaInputTopic;
    private String kafkaOutputTopic;
    private String kafkaNetCacheTopic;
    private String kafkaSpeakerTopic;
    private String kafkaSimulatorTopic;
    private String kafkaTsdbTopic;
    private String kafkaDiscoveryTopic;

    private String openTsDBHosts;
    private Integer openTsdbTimeout;

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
        kafkaInputTopic = config.getString("kafka.topic.input");
        kafkaOutputTopic = config.getString("kafka.topic.output");
        kafkaNetCacheTopic = config.getString("kafka.topic.netcache");
        kafkaSpeakerTopic = config.getString("kafka.topic.speaker");
        kafkaSimulatorTopic = config.getString("kafka.topic.simulator");
        kafkaTsdbTopic = config.getString("kafka.topic.opentsdb");
        kafkaDiscoveryTopic = config.getString("kafka.topic.discovery");

        openTsDBHosts = config.getString("opentsdb.hosts");
        openTsdbTimeout = (int)(config.getFloat("opentsdb.timeout") * 1000);

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

    public String getKafkaInputTopic() {
        return kafkaInputTopic;
    }

    public String getKafkaOutputTopic() {
        return kafkaOutputTopic;
    }

    public String getKafkaNetCacheTopic() {
        return kafkaNetCacheTopic;
    }

    public String getKafkaSpeakerTopic() {
        return kafkaSpeakerTopic;
    }

    public String getKafkaSimulatorTopic() {
        return kafkaSimulatorTopic;
    }

    public String getKafkaTsdbTopic() {
        return kafkaTsdbTopic;
    }

    public String getKafkaDiscoveryTopic() {
        return kafkaDiscoveryTopic;
    }

    public String getOpenTsDBHosts() {
        return openTsDBHosts;
    }

    public Integer getOpenTsdbTimeout() {
        return openTsdbTimeout;
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
