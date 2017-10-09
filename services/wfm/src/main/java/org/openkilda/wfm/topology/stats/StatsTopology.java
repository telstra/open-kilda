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

import org.openkilda.messaging.ServiceType;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.Topology;
import org.openkilda.wfm.topology.stats.bolts.SpeakerBolt;
import org.openkilda.wfm.topology.stats.metrics.FlowMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.MeterConfigMetricGenBolt;
import org.openkilda.wfm.topology.stats.metrics.PortMetricGenBolt;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.opentsdb.bolt.OpenTsdbBolt;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.opentsdb.client.OpenTsdbClient;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


public class StatsTopology extends AbstractTopology {
    private static final Logger logger = LoggerFactory.getLogger(StatsTopology.class);
    private static final String TOPIC = "kilda-test";

    public StatsTopology(File file) {
        super(file);

        logger.debug("Topology built {}: zookeeper={}, kafka={}, parallelism={}, workers={}",
                topologyName, zookeeperHosts, kafkaHosts, parallelism, workers);
    }

    public static void main(String[] args) throws Exception {
        if (args != null && args.length > 0) {
            File file = new File(args[1]);
            final StatsTopology topology = new StatsTopology(file);
            StormTopology stormTopology = topology.createTopology();
            final Config config = new Config();
            config.setNumWorkers(1);

            logger.info("Start Topology: {}", topology.getTopologyName());

            config.setDebug(false);

            StormSubmitter.submitTopology(args[0], config, stormTopology);
        } else {
            File file = new File(StatsTopology.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
            final StatsTopology topology = new StatsTopology(file);
            StormTopology stormTopology = topology.createTopology();
            final Config config = new Config();
            config.setNumWorkers(1);

            logger.info("Start Topology Locally: {}", topology.topologyName);

            config.setDebug(true);
            config.setMaxTaskParallelism(topology.parallelism);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(topology.topologyName, config, stormTopology);

            logger.info("Sleep", topology.topologyName);
            Thread.sleep(60000);
            cluster.shutdown();
        }
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating Topology: {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        final String kafkaSpoutId = StatsComponentType.STATS_OFS_KAFKA_SPOUT.toString();
        KafkaSpout kafkaSpout = createKafkaSpout(TOPIC, kafkaSpoutId);
        builder.setSpout(kafkaSpoutId, kafkaSpout, parallelism);

        SpeakerBolt speakerBolt = new SpeakerBolt();
        final String statsOfsBolt = StatsComponentType.STATS_OFS_BOLT.toString();
        builder.setBolt(statsOfsBolt, speakerBolt, parallelism)
                .shuffleGrouping(kafkaSpoutId);

        builder.setBolt(PORT_STATS_METRIC_GEN.name(), new PortMetricGenBolt(), parallelism)
                .fieldsGrouping(statsOfsBolt, StatsStreamType.PORT_STATS.toString(), fieldMessage);
        builder.setBolt(METER_CFG_STATS_METRIC_GEN.name(), new MeterConfigMetricGenBolt(), parallelism)
                .fieldsGrouping(statsOfsBolt, StatsStreamType.METER_CONFIG_STATS.toString(), fieldMessage);
        builder.setBolt(FLOW_STATS_METRIC_GEN.name(), new FlowMetricGenBolt(), parallelism)
                .fieldsGrouping(statsOfsBolt, StatsStreamType.FLOW_STATS.toString(), fieldMessage);

        OpenTsdbClient.Builder tsdbBuilder = OpenTsdbClient.newBuilder(topologyProperties.getProperty("statstopology.openTsdbUrl"))
                .sync(30_000).returnDetails();
        OpenTsdbBolt openTsdbBolt = new OpenTsdbBolt(tsdbBuilder, TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER)
                .withBatchSize(10)
                .withFlushInterval(2)
                .failTupleForFailedMetrics();
        builder.setBolt("opentsdb", openTsdbBolt)
                .shuffleGrouping(PORT_STATS_METRIC_GEN.name())
                .shuffleGrouping(METER_CFG_STATS_METRIC_GEN.name())
                .shuffleGrouping(FLOW_STATS_METRIC_GEN.name());

        createHealthCheckHandler(builder, ServiceType.STATS_TOPOLOGY.getId());

        return builder.createTopology();
    }
}
