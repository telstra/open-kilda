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

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.opentsdb.bolts.DatapointParseBolt;
import org.openkilda.wfm.topology.opentsdb.bolts.OpenTSDBFilterBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.opentsdb.bolt.OpenTsdbBolt;
import org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper;
import org.apache.storm.opentsdb.client.OpenTsdbClient;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * Apache Storm topology for sending metrics into Open TSDB.
 */
public class OpenTSDBTopology extends AbstractTopology {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTSDBTopology.class);

    public OpenTSDBTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
    }

    private final String topic = config.getKafkaOtsdbTopic();
    private final String spoutId = topic + "-spout";
    private final String boltId = topic + "-bolt";
    private final String parseBoltId = topic + "parse-bolt";

    @Override
    public StormTopology createTopology() {
        LOGGER.info("Creating OpenTSDB topology");
        TopologyBuilder tb = new TopologyBuilder();

        attachInput(tb);

        tb.setBolt(parseBoltId, new DatapointParseBolt(), config.getGetDatapointParseBoltExecutors())
                .setNumTasks(config.getGetDatapointParseBoltWorkers())
                .shuffleGrouping(spoutId);

        tb.setBolt(boltId, new OpenTSDBFilterBolt(), config.getOpenTsdbFilterBoltExecutors())
                .fieldsGrouping(parseBoltId, new Fields("hash"));

        OpenTsdbClient.Builder tsdbBuilder = OpenTsdbClient
                .newBuilder(config.getOpenTsdbHosts())
                // .sync(config.getOpenTsdbTimeout())
                .returnDetails();
        if (config.isOpenTsdbClientChunkedRequestsEnabled()) {
            tsdbBuilder.enableChunkedEncoding();
        }

        OpenTsdbBolt openTsdbBolt = new OpenTsdbBolt(tsdbBuilder,
                Collections.singletonList(TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER));
        openTsdbBolt.withBatchSize(config.getOpenTsdbBatchSize()).withFlushInterval(config.getOpenTsdbFlushInterval());
        //        .failTupleForFailedMetrics();
        tb.setBolt("opentsdb", openTsdbBolt, config.getOpenTsdbBoltExecutors())
                .setNumTasks(config.getOpenTsdbBoltWorkers())
                .shuffleGrouping(boltId);

        return tb.createTopology();
    }

    private void attachInput(TopologyBuilder topology) {
        checkAndCreateTopic(topic);

        KafkaSpoutConfig<String, String> spoutConfig = makeKafkaSpoutConfigBuilder(spoutId, topic)
                .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.UNCOMMITTED_EARLIEST)
                .build();
        KafkaSpout kafkaSpout = new KafkaSpout<>(spoutConfig);
        topology.setSpout(spoutId, kafkaSpout, config.getOpenTsdbNumSpouts());
    }

    /**
     * main.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OpenTSDBTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
