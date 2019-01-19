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

package org.openkilda.wfm.topology.history;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.history.bolts.HistoryBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

/**
 * Apache Storm topology for processing History logs.
 */
public class HistoryTopology extends AbstractTopology<HistoryTopologyConfig> {
    public HistoryTopology(LaunchEnvironment env) {
        super(env, HistoryTopologyConfig.class);
    }

    private static final String HISTORY_SPOUT_ID = "kilda.history-spout";
    private static final String HISTORY_BOLT = "history-bolt";

    @Override
    public StormTopology createTopology() {
        logger.info("Creating HistoryTopology - {}", topologyName);

        TopologyBuilder tb = new TopologyBuilder();
        final Integer parallelism = topologyConfig.getParallelism();

        KafkaSpout kafkaSpout = buildKafkaSpout(topologyConfig.getKafkaHistoryTopic(), HISTORY_SPOUT_ID);
        tb.setSpout(HISTORY_SPOUT_ID, kafkaSpout, parallelism);

        tb.setBolt(HISTORY_BOLT, new HistoryBolt(), parallelism)
                .shuffleGrouping(HISTORY_SPOUT_ID);

        return tb.createTopology();
    }

    /**
     * main.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new HistoryTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
