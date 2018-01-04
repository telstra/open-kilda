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
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.opentsdb.bolt.OpenTsdbBolt;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.opentsdb.client.OpenTsdbClient;
import org.apache.storm.topology.TopologyBuilder;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.Topology;
import org.openkilda.wfm.topology.opentsdb.bolts.OpenTSDBFilterBolt;

import java.io.File;

/**
 * Apache Storm topology for sending metrics into Open TSDB.
 */
public class OpenTSDBTopology extends AbstractTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTSDBTopology.class);

    public OpenTSDBTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    @Override
    public StormTopology createTopology() {
        LOGGER.info("Creating OpenTSDB topology");
        TopologyBuilder tb = new TopologyBuilder();

        final String topic = config.getKafkaOtsdbTopic();
        final String spoutId = topic + "-spout";
        final String boltId = topic + "-bolt";
        checkAndCreateTopic(topic);

        KafkaSpout kafkaSpout = createKafkaSpout(topic, spoutId);
        tb.setSpout(spoutId, kafkaSpout);

        tb.setBolt(boltId, new OpenTSDBFilterBolt())
                .shuffleGrouping(spoutId);

        OpenTsdbClient.Builder tsdbBuilder = OpenTsdbClient
                .newBuilder(config.getOpenTsDBHosts())
                .sync(config.getOpenTsdbTimeout())
                .returnDetails();
        OpenTsdbBolt openTsdbBolt = new OpenTsdbBolt(tsdbBuilder, TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER)
                .withBatchSize(10)
                .withFlushInterval(2)
                .failTupleForFailedMetrics();
        tb.setBolt("opentsdb", openTsdbBolt, config.getParallelism())
                .shuffleGrouping(boltId);

        return tb.createTopology();
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OpenTSDBTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
