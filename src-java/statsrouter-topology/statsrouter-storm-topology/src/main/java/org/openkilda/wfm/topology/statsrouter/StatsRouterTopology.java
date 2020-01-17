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

package org.openkilda.wfm.topology.statsrouter;

import static org.openkilda.wfm.topology.statsrouter.StatsRouterComponentType.FL_STATS_SWITCHES_KAFKA_SPOUT;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterComponentType.ROUTER_BOLT;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterComponentType.SPEAKER_KAFKA_BOLT;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterComponentType.STATS_REQUEST_KAFKA_SPOUT;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterComponentType.STATS_STATS_REQUEST_KAFKA_BOLT;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterStreamType.MGMT_REQUEST;
import static org.openkilda.wfm.topology.statsrouter.StatsRouterStreamType.STATS_REQUEST;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.statsrouter.bolts.StatsRouterBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.TopologyBuilder;

public final class StatsRouterTopology extends AbstractTopology<StatsRouterTopologyConfig> {
    private StatsRouterTopology(LaunchEnvironment env) {
        super(env, StatsRouterTopologyConfig.class);
    }

    /**
     * main.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new StatsRouterTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating StatsRouterTopology - {}", topologyName);

        final Integer parallelism = topologyConfig.getNewParallelism();
        TopologyBuilder builder = new TopologyBuilder();

        builder.setSpout(
                STATS_REQUEST_KAFKA_SPOUT.name(),
                buildKafkaSpout(getConfig().getStatsRequestPrivTopic(), STATS_REQUEST_KAFKA_SPOUT.name()),
                parallelism
        );

        builder.setSpout(
                FL_STATS_SWITCHES_KAFKA_SPOUT.name(),
                buildKafkaSpout(getConfig().getFlStatsSwtichesPrivTopic(), FL_STATS_SWITCHES_KAFKA_SPOUT.name()),
                parallelism
        );

        IRichBolt routerBolt =
                new StatsRouterBolt(getConfig().getStatsRouterRequestInterval(), getConfig().getStatsRouterTimeout());
        builder.setBolt(ROUTER_BOLT.name(), routerBolt, parallelism)
                .shuffleGrouping(STATS_REQUEST_KAFKA_SPOUT.name())
                .allGrouping(FL_STATS_SWITCHES_KAFKA_SPOUT.name());

        builder.setBolt(SPEAKER_KAFKA_BOLT.name(), buildKafkaBolt(getConfig().getKafkaSpeakerTopic()), parallelism)
                .shuffleGrouping(ROUTER_BOLT.name(), MGMT_REQUEST.name());

        builder.setBolt(
                STATS_STATS_REQUEST_KAFKA_BOLT.name(),
                buildKafkaBolt(getConfig().getStatsStatsRequestPrivTopic()),
                parallelism
        ).shuffleGrouping(ROUTER_BOLT.name(), STATS_REQUEST.name());

        return builder.createTopology();
    }
}
