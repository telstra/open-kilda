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

package org.openkilda.wfm.topology.islstats;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.islstats.bolts.IslStatsBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;

public class IslStatsTopology extends AbstractTopology<IslStatsTopologyConfig> {
    private static final String ISL_STATS_SPOUT_ID = "islstats-spout";
    private static final String ISL_STATS_OTSDB_BOLT_ID = "islstats-otsdb-bolt";
    private static final String ISL_STATS_BOLT_ID = IslStatsBolt.class.getSimpleName();

    public IslStatsTopology(LaunchEnvironment env) {
        super(env, IslStatsTopologyConfig.class);
    }

    public StormTopology createTopology() {
        logger.info("Creating IslStatsTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        String topoDiscoTopic = topologyConfig.getKafkaTopoDiscoTopic();

        logger.debug("connecting to {} topic", topoDiscoTopic);
        builder.setSpout(ISL_STATS_SPOUT_ID, createKafkaSpout(topoDiscoTopic, ISL_STATS_SPOUT_ID));

        IslStatsBolt verifyIslStatsBolt = new IslStatsBolt(topologyConfig.getMetricPrefix());
        logger.debug("starting {} bolt", ISL_STATS_BOLT_ID);
        builder.setBolt(ISL_STATS_BOLT_ID, verifyIslStatsBolt, topologyConfig.getParallelism())
                .shuffleGrouping(ISL_STATS_SPOUT_ID);

        String openTsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        KafkaBolt openTsdbBolt = createKafkaBolt(openTsdbTopic);
        builder.setBolt(ISL_STATS_OTSDB_BOLT_ID, openTsdbBolt, topologyConfig.getParallelism())
                .shuffleGrouping(ISL_STATS_BOLT_ID);

        return builder.createTopology();
    }

    /**
     * Entry point for local run of the topology.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new IslStatsTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
