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

package org.openkilda.wfm.topology.opentsdb;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.opentsdb.bolt.OpenTsdbBolt;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.opentsdb.client.OpenTsdbClient;
import org.apache.storm.topology.TopologyBuilder;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.Topology;
import org.openkilda.wfm.topology.opentsdb.bolts.OpenTSDBFilterBolt;

import java.io.File;

/**
 * Apache Storm topology for sending metrics into Open TSDB.
 */
public class OpenTSDBTopology extends AbstractTopology {

    private static final Logger LOGGER = LogManager.getLogger(OpenTSDBTopology.class);

    private static final String TOPIC_KEY = "opentsdbtopology.kafka.opentsdb-topic";
    private static final String OPENTSDB_URL_KEY = "opentsdbtopology.open-tsdb.url";
    private static final String OPENTSDB_TIMEOUT_KEY = "opentsdbtopology.open-tsdb.timeout";

    OpenTSDBTopology(File file) {
        super(file);

        String topic = topologyProperties.getProperty(TOPIC_KEY);
        if (StringUtils.isBlank(topic)) {
            LOGGER.error("OpenTSDBTopology requires {} property", TOPIC_KEY);

            throw new IllegalStateException(TOPIC_KEY + " property should be defined");
        }
    }

    public static void main(String[] args) throws Exception {

            //If there are arguments, we are running on a cluster; otherwise, we are running locally
        if (args != null && args.length > 0) {
            File file = new File(args[1]);
            Config conf = new Config();
            conf.setDebug(false);
            OpenTSDBTopology topology = new OpenTSDBTopology(file);
            StormTopology topo = topology.createTopology();

            conf.setNumWorkers(1);
            StormSubmitter.submitTopology(args[0], conf, topo);
        } else {
            File file = new File(OpenTSDBTopology.class.getResource(Topology.TOPOLOGY_PROPERTIES).getFile());
            Config conf = new Config();
            conf.setDebug(false);
            OpenTSDBTopology openTSDBTopology = new OpenTSDBTopology(file);
            StormTopology topo = openTSDBTopology.createTopology();

            conf.setMaxTaskParallelism(3);

            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(openTSDBTopology.topologyName, conf, topo);

            Thread.sleep(60000);
            cluster.shutdown();
        }
    }

    @Override
    public StormTopology createTopology() {
        LOGGER.info("Creating OpenTSDB topology");
        TopologyBuilder tb = new TopologyBuilder();

        final String topic = topologyProperties.getProperty(TOPIC_KEY);
        final String spoutId = topic + "-spout";
        final String boltId = topic + "-bolt";
        checkAndCreateTopic(topic);

        KafkaSpout kafkaSpout = createKafkaSpout(topic, spoutId);
        tb.setSpout(spoutId, kafkaSpout);

        tb.setBolt(boltId, new OpenTSDBFilterBolt())
                .shuffleGrouping(spoutId);

        OpenTsdbClient.Builder tsdbBuilder = OpenTsdbClient
                .newBuilder(topologyProperties.getProperty(OPENTSDB_URL_KEY))
                .sync(Long.parseLong(topologyProperties.getProperty(OPENTSDB_TIMEOUT_KEY)))
                .returnDetails();
        OpenTsdbBolt openTsdbBolt = new OpenTsdbBolt(tsdbBuilder, TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER)
                .withBatchSize(10)
                .withFlushInterval(2)
                .failTupleForFailedMetrics();
        tb.setBolt("opentsdb", openTsdbBolt, parallelism)
                .shuffleGrouping(boltId);

        return tb.createTopology();
    }
}
