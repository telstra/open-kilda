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
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.opentsdb.OpenTsdbTopologyConfig.OpenTsdbConfig;
import org.openkilda.wfm.topology.opentsdb.bolts.DatapointParseBolt;
import org.openkilda.wfm.topology.opentsdb.bolts.OpenTSDBFilterBolt;

import com.google.common.annotations.VisibleForTesting;
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
public class OpenTsdbTopology extends AbstractTopology<OpenTsdbTopologyConfig> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenTsdbTopology.class);

    public OpenTsdbTopology(LaunchEnvironment env) {
        super(env, OpenTsdbTopologyConfig.class);
    }

    @VisibleForTesting
    static final String OTSDB_SPOUT_ID = "kilda.otsdb-spout";
    private static final String OTSDB_BOLT_ID = "otsdb-bolt";
    private static final String OTSDB_FILTER_BOLT_ID = OpenTSDBFilterBolt.class.getSimpleName();
    private static final String OTSDB_PARSE_BOLT_ID = DatapointParseBolt.class.getSimpleName();

    @Override
    public StormTopology createTopology() {
        LOGGER.info("Creating OpenTsdbTopology - {}", topologyName);

        TopologyBuilder tb = new TopologyBuilder();

        attachInput(tb);

        OpenTsdbConfig openTsdbConfig = topologyConfig.getOpenTsdbConfig();

        tb.setBolt(OTSDB_PARSE_BOLT_ID, new DatapointParseBolt(), openTsdbConfig.getDatapointParseBoltExecutors())
                .setNumTasks(openTsdbConfig.getDatapointParseBoltWorkers())
                .shuffleGrouping(OTSDB_SPOUT_ID);

        tb.setBolt(OTSDB_FILTER_BOLT_ID, new OpenTSDBFilterBolt(), openTsdbConfig.getFilterBoltExecutors())
                .fieldsGrouping(OTSDB_PARSE_BOLT_ID, new Fields("hash"));

        OpenTsdbClient.Builder tsdbBuilder = OpenTsdbClient
                .newBuilder(openTsdbConfig.getHosts())
                .returnDetails();
        if (openTsdbConfig.getClientChunkedRequestsEnabled()) {
            tsdbBuilder.enableChunkedEncoding();
        }

        OpenTsdbBolt openTsdbBolt = new OpenTsdbBolt(tsdbBuilder,
                Collections.singletonList(TupleOpenTsdbDatapointMapper.DEFAULT_MAPPER));
        openTsdbBolt.withBatchSize(openTsdbConfig.getBatchSize()).withFlushInterval(openTsdbConfig.getFlushInterval());
        tb.setBolt(OTSDB_BOLT_ID, openTsdbBolt, openTsdbConfig.getBoltExecutors())
                .setNumTasks(openTsdbConfig.getBoltWorkers())
                .shuffleGrouping(OTSDB_FILTER_BOLT_ID);

        return tb.createTopology();
    }

    private void attachInput(TopologyBuilder topology) {
        String otsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        checkAndCreateTopic(otsdbTopic);

        OpenTsdbConfig openTsdbConfig = topologyConfig.getOpenTsdbConfig();

        KafkaSpoutConfig<String, String> spoutConfig = makeKafkaSpoutConfigBuilder(OTSDB_SPOUT_ID, otsdbTopic)
                .setFirstPollOffsetStrategy(KafkaSpoutConfig.FirstPollOffsetStrategy.UNCOMMITTED_EARLIEST)
                .build();
        KafkaSpout kafkaSpout = new KafkaSpout<>(spoutConfig);
        topology.setSpout(OTSDB_SPOUT_ID, kafkaSpout, openTsdbConfig.getNumSpouts());
    }

    /**
     * main.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new OpenTsdbTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
