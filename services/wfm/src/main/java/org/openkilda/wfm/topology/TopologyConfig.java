/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology;

import org.openkilda.pce.provider.AuthNeo4j;
import org.openkilda.wfm.PropertiesReader;
import org.openkilda.wfm.error.ConfigurationException;

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
    private Integer keepRemovedIslTimeout;
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
    private String kafkaTopoNbTopic;

    private String openTsdbHosts;
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

    private PropertiesReader config;

    /**
     * Constructs {@link TopologyConfig} from properties file.
     */
    public TopologyConfig(PropertiesReader config) throws ConfigurationException {
        this.config = config;
        useLocalCluster = config.getBoolean("cli.local");
        localExecutionTime = (int) (config.getFloat("local.execution.time") * 1000);

        parallelism = config.getInteger("parallelism");
        workers = config.getInteger("workers");
        discoveryInterval = config.getInteger("discovery.interval");
        discoveryTimeout = config.getInteger("discovery.timeout");
        discoveryLimit = config.getInteger("discovery.limit");
        discoverySpeakerFailureTimeout = config.getFloat("discovery.speaker-failure-timeout");
        keepRemovedIslTimeout = config.getInteger("discovery.keep.removed.isl");
        filterDirectory = config.getString("filter.directory");
        loggerLevel = Level.valueOf(config.getString("logger.level"));
        loggerWatermark = config.getString("logger.watermark");
        discoveryDumpRequestTimeout = config.getFloat("discovery.dump-request-timeout-seconds");

        zookeeperHosts = config.getString("zookeeper.hosts");
        zookeeperSessionTimeout = (int) (config.getFloat("zookeeper.session.timeout") * 1000);
        zookeeperConnectTimeout = (int) (config.getFloat("zookeeper.connect.timeout") * 1000);
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
        kafkaTopoNbTopic = config.getString("kafka.topic.topo.nbworker");

        openTsdbHosts = config.getString("opentsdb.hosts");
        openTsdbTimeout = (int) (config.getFloat("opentsdb.timeout") * 1000);
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
    }

    /**
     * Returns amount of workers for specified topology.
     */
    public Integer getWorkers(String name) {
        int value = workers;
        try {
            value = config.getInteger(name + ".workers");
        } catch (ConfigurationException e) {
            log.warn("could not find {}.workers so using global default", name);
        }
        return value;
    }

    public AuthNeo4j getNeo4jAuth() {
        return new AuthNeo4j(neo4jHost, neo4jLogin, neo4jPassword);
    }
}
