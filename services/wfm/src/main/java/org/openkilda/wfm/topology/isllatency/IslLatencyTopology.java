/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.isllatency;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.isllatency.bolts.IslStatsBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;

public class IslLatencyTopology extends AbstractTopology<IslLatencyTopologyConfig> {
    private static final String ISL_LATENCY_SPOUT_ID = "isl-latency-spout";
    private static final String ISL_LATENCY_OTSDB_BOLT_ID = "isl-latency-otsdb-bolt";
    private static final String ISL_STATS_BOLT_ID = IslStatsBolt.class.getSimpleName();

    public IslLatencyTopology(LaunchEnvironment env) {
        super(env, IslLatencyTopologyConfig.class);
    }

    /**
     * Isl latency topology factory.
     */
    public StormTopology createTopology() {
        logger.info("Creating IslLatencyTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        String topoDiscoTopic = topologyConfig.getKafkaTopoDiscoTopic();

        logger.debug("connecting to {} topic", topoDiscoTopic);
        builder.setSpout(ISL_LATENCY_SPOUT_ID, createKafkaSpout(topoDiscoTopic, ISL_LATENCY_SPOUT_ID));

        IslStatsBolt verifyIslStatsBolt = new IslStatsBolt(topologyConfig.getMetricPrefix());
        logger.debug("starting {} bolt", ISL_STATS_BOLT_ID);
        builder.setBolt(ISL_STATS_BOLT_ID, verifyIslStatsBolt, topologyConfig.getNewParallelism())
                .shuffleGrouping(ISL_LATENCY_SPOUT_ID);

        String openTsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        KafkaBolt openTsdbBolt = createKafkaBolt(openTsdbTopic);
        builder.setBolt(ISL_LATENCY_OTSDB_BOLT_ID, openTsdbBolt, topologyConfig.getNewParallelism())
                .shuffleGrouping(ISL_STATS_BOLT_ID);

        return builder.createTopology();
    }

    /**
     * Entry point for local run of the topology.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new IslLatencyTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
