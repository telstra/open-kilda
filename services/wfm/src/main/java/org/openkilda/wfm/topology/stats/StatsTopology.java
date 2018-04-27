/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.stats;

import static org.openkilda.wfm.topology.stats.StatsComponentType.FLOW_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.METER_CFG_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.PORT_STATS_METRIC_GEN;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_CACHE_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_CACHE_FILTER_BOLT;
import static org.openkilda.wfm.topology.stats.StatsComponentType.STATS_KILDA_SPEAKER_SPOUT;
import static org.openkilda.wfm.topology.stats.StatsStreamType.CACHE_UPDATE;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.openkilda.messaging.ServiceType;
import org.openkilda.pce.provider.AuthNeo4j;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.stats.bolts.CacheBolt;
import org.openkilda.wfm.topology.stats.bolts.CacheFilterBolt;
import org.openkilda.wfm.topology.stats.bolts.SpeakerBolt;
import org.openkilda.wfm.topology.stats.metrics.FlowMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.MeterConfigMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.PortMetricGenBolt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StatsTopology extends AbstractTopology {

    private static final Logger logger = LoggerFactory.getLogger(StatsTopology.class);
    private final AuthNeo4j pathComputerAuth;

    public StatsTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
        pathComputerAuth = new AuthNeo4j(config.getNeo4jHost(), config.getNeo4jLogin(),
                config.getNeo4jPassword());
        logger.debug("Topology built {}: zookeeper={}, kafka={}, parallelism={}, workers={}",
                getTopologyName(), config.getZookeeperHosts(), config.getKafkaHosts(),
                config.getParallelism(),
                config.getWorkers());
    }

    public static void main(String[] args) throws Exception {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new StatsTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating Topology: {}", topologyName);

        final Integer parallelism = config.getParallelism();
        TopologyBuilder builder = new TopologyBuilder();


        final String kafkaSpoutId = StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString();
        KafkaSpout kafkaSpout = createKafkaSpout(config.getKafkaStatsTopic(), kafkaSpoutId);
        builder.setSpout(kafkaSpoutId, kafkaSpout, parallelism);

        SpeakerBolt speakerBolt = new SpeakerBolt();
        final String statsOfsBolt = StatsComponentType.STATS_OFS_BOLT.toString();
        builder.setBolt(statsOfsBolt, speakerBolt, parallelism)
                .shuffleGrouping(kafkaSpoutId);

        // Spout for listening kilda.speaker topic and collect changes for cache
        KafkaSpout kafkaSpeakerSpout = createKafkaSpout(config.getKafkaSpeakerTopic(),
                STATS_KILDA_SPEAKER_SPOUT.name());
        builder.setSpout(STATS_KILDA_SPEAKER_SPOUT.name(), kafkaSpeakerSpout, parallelism);

        // CacheFilterBolt catch data from kilda.speaker spout and tried to find InstallEgressFlow
        // or InstallOneSwitchFlow and throw tuple to CacheBolt
        builder.setBolt(STATS_CACHE_FILTER_BOLT.name(), new CacheFilterBolt(),
                parallelism)
                .shuffleGrouping(STATS_KILDA_SPEAKER_SPOUT.name());

        // Cache bolt get data from NEO4J on start
        builder.setBolt(STATS_CACHE_BOLT.name(), new CacheBolt(pathComputerAuth), parallelism)
                .allGrouping(STATS_CACHE_FILTER_BOLT.name(), CACHE_UPDATE.name())
                .fieldsGrouping(statsOfsBolt, StatsStreamType.FLOW_STATS.toString(), fieldMessage);

        builder.setBolt(PORT_STATS_METRIC_GEN.name(), new PortMetricGenBolt(), parallelism)
                .fieldsGrouping(statsOfsBolt, StatsStreamType.PORT_STATS.toString(), fieldMessage);
        builder.setBolt(METER_CFG_STATS_METRIC_GEN.name(), new MeterConfigMetricGenBolt(),
                parallelism)
                .fieldsGrouping(statsOfsBolt, StatsStreamType.METER_CONFIG_STATS.toString(),
                        fieldMessage);

        logger.debug("starting flow_stats_metric_gen");
        builder.setBolt(FLOW_STATS_METRIC_GEN.name(),
                new FlowMetricGenBolt(),
                parallelism)
                .fieldsGrouping(STATS_CACHE_BOLT.name(), StatsStreamType.FLOW_STATS.toString(), fieldMessage);

        final String openTsdbTopic = config.getKafkaOtsdbTopic();
        checkAndCreateTopic(openTsdbTopic);
        builder.setBolt("stats-opentsdb", createKafkaBolt(openTsdbTopic))
                .shuffleGrouping(PORT_STATS_METRIC_GEN.name())
                .shuffleGrouping(METER_CFG_STATS_METRIC_GEN.name())
                .shuffleGrouping(FLOW_STATS_METRIC_GEN.name());

        createHealthCheckHandler(builder, ServiceType.STATS_TOPOLOGY.getId());

        return builder.createTopology();
    }

}
