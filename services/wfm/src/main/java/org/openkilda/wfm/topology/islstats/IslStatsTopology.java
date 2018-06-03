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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IslStatsTopology extends AbstractTopology<IslStatsTopologyConfig> {
    private static final Logger logger = LoggerFactory.getLogger(IslStatsTopology.class);

    public IslStatsTopology(LaunchEnvironment env) {
        super(env, IslStatsTopologyConfig.class);
    }

    public StormTopology createTopology() {
        final String clazzName = this.getClass().getSimpleName();
        final String spoutName = "islstats-spout";
        logger.debug("Building Topology - {}", clazzName);

        TopologyBuilder builder = new TopologyBuilder();

        String topic = topologyConfig.getKafkaTopoDiscoTopic();
        checkAndCreateTopic(topic);

        logger.debug("connecting to {} topic", topic);
        builder.setSpout(spoutName, createKafkaSpout(topic, clazzName));

        final String verifyIslStatsBoltName = IslStatsBolt.class.getSimpleName();
        IslStatsBolt verifyIslStatsBolt = new IslStatsBolt();
        logger.debug("starting {} bolt", verifyIslStatsBoltName);
        builder.setBolt(verifyIslStatsBoltName, verifyIslStatsBolt, topologyConfig.getParallelism())
                .shuffleGrouping(spoutName);

        final String openTsdbTopic = topologyConfig.getKafkaOtsdbTopic();
        checkAndCreateTopic(openTsdbTopic);
        KafkaBolt openTsdbBolt = createKafkaBolt(openTsdbTopic);
        builder.setBolt("isl-stats-opentsdb", openTsdbBolt, topologyConfig.getParallelism())
                .shuffleGrouping(verifyIslStatsBoltName);

        return builder.createTopology();
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new IslStatsTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
