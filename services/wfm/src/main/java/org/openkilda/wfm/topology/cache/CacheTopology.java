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

package org.openkilda.wfm.topology.cache;

import org.apache.storm.topology.BoltDeclarer;
import org.openkilda.wfm.NameCollisionException;
import org.openkilda.messaging.ServiceType;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.LaunchEnvironment;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CacheTopology extends AbstractTopology {
    private static final Logger logger = LoggerFactory.getLogger(CacheTopology.class);

    public static final String SPOUT_ID_COMMON = "generic";
    public static final String SPOUT_ID_TOPOLOGY = "topology";
    public static final String BOLT_ID_CACHE = "cache";
    public static final String BOLT_ID_COMMON_OUTPUT = "common.out";
    public static final String BOLT_ID_TOPOLOGY_OUTPUT = "topology.out";

    public CacheTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);

        logger.debug("Topology built {}: zookeeper={}, kafka={}, parallelism={}, workers={}",
                getTopologyName(), config.getZookeeperHosts(), config.getKafkaHosts(), config.getParallelism(),
                config.getWorkers());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StormTopology createTopology() throws NameCollisionException {
        logger.info("Creating Topology: {}", topologyName);

        initKafkaTopics();

        Integer parallelism = config.getParallelism();

        TopologyBuilder builder = new TopologyBuilder();
        List<CtrlBoltRef> ctrlTargets = new ArrayList<>();
        BoltDeclarer boltSetup;

        KafkaSpout kafkaSpout;
        /*
         * Receives cache from storage.
         */
        kafkaSpout = createKafkaSpout(config.getKafkaInputTopic(), SPOUT_ID_COMMON);
        builder.setSpout(SPOUT_ID_COMMON, kafkaSpout, parallelism);

        /*
         * Receives cache updates from WFM topology.
         */
        kafkaSpout = createKafkaSpout(config.getKafkaOutputTopic(), SPOUT_ID_TOPOLOGY);
        builder.setSpout(SPOUT_ID_TOPOLOGY, kafkaSpout, parallelism);

        /*
         * Stores network cache.
         */
        CacheBolt cacheBolt = new CacheBolt(config.getDiscoveryTimeout());
        boltSetup = builder.setBolt(BOLT_ID_CACHE, cacheBolt, parallelism)
                .shuffleGrouping(SPOUT_ID_COMMON)
                .shuffleGrouping(SPOUT_ID_TOPOLOGY);
        ctrlTargets.add(new CtrlBoltRef(BOLT_ID_CACHE, cacheBolt, boltSetup));

        KafkaBolt kafkaBolt;
        /*
         * Sends network events to storage.
         */
        kafkaBolt = createKafkaBolt(config.getKafkaInputTopic());
        builder.setBolt(BOLT_ID_COMMON_OUTPUT, kafkaBolt, parallelism)
                .shuffleGrouping(BOLT_ID_CACHE, StreamType.TPE.toString());

        /*
         * Sends cache dump to WFM topology.
         */
        kafkaBolt = createKafkaBolt(config.getKafkaNetCacheTopic());
        builder.setBolt(BOLT_ID_TOPOLOGY_OUTPUT, kafkaBolt, parallelism)
                .shuffleGrouping(BOLT_ID_CACHE, StreamType.WFM_DUMP.toString());

        createCtrlBranch(builder, ctrlTargets);
        createHealthCheckHandler(builder, ServiceType.CACHE_TOPOLOGY.getId());

        return builder.createTopology();
    }

    private void initKafkaTopics() {
        checkAndCreateTopic(config.getKafkaInputTopic());
        checkAndCreateTopic(config.getKafkaOutputTopic());
        checkAndCreateTopic(config.getKafkaNetCacheTopic());
    }

    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new CacheTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
