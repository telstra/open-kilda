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

import org.openkilda.messaging.ServiceType;
import org.openkilda.wfm.ConfigurationException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.LaunchEnvironment;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.KafkaSpout;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheTopology extends AbstractTopology {
    static final String STATE_DUMP_TOPIC = "kilda.wfm.topo.dump";
    static final String STATE_UPDATE_TOPIC = "kilda.wfm.topo.updown";
    private static final String STATE_TPE_TOPIC = "kilda-test";

    private static final Logger logger = LoggerFactory.getLogger(CacheTopology.class);

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
    public StormTopology createTopology() {
        logger.info("Creating Topology: {}", topologyName);

        initKafka();

        Integer parallelism = config.getParallelism();

        TopologyBuilder builder = new TopologyBuilder();

       /*
         * Receives cache from storage.
         */
        KafkaSpout storageSpout = createKafkaSpout(STATE_TPE_TOPIC, ComponentType.TPE_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.TPE_KAFKA_SPOUT.toString(), storageSpout, parallelism);

        /*
         * Receives cache updates from WFM topology.
         */
        KafkaSpout stateSpout = createKafkaSpout(STATE_UPDATE_TOPIC, ComponentType.WFM_UPDATE_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.WFM_UPDATE_KAFKA_SPOUT.toString(), stateSpout, parallelism);

        /*
         * Stores network cache.
         */
        CacheBolt cacheBolt = new CacheBolt(config.getDiscoveryTimeout());
        builder.setBolt(ComponentType.CACHE_BOLT.toString(), cacheBolt, parallelism)
                .shuffleGrouping(ComponentType.WFM_UPDATE_KAFKA_SPOUT.toString())
                .shuffleGrouping(ComponentType.TPE_KAFKA_SPOUT.toString());

        /*
         * Sends network events to storage.
         */
        KafkaBolt storageBolt = createKafkaBolt(STATE_TPE_TOPIC);
        builder.setBolt(ComponentType.TPE_KAFKA_BOLT.toString(), storageBolt, parallelism)
                .shuffleGrouping(ComponentType.CACHE_BOLT.toString(), StreamType.TPE.toString());

        /*
         * Sends cache dump to WFM topology.
         */
        KafkaBolt stateDump = createKafkaBolt(STATE_DUMP_TOPIC);
        builder.setBolt(ComponentType.WFM_DUMP_KAFKA_BOLT.toString(), stateDump, parallelism)
                .shuffleGrouping(ComponentType.CACHE_BOLT.toString(), StreamType.WFM_DUMP.toString());

        createHealthCheckHandler(builder, ServiceType.CACHE_TOPOLOGY.getId());

        return builder.createTopology();
    }

    private void initKafka() {
        checkAndCreateTopic(STATE_TPE_TOPIC);
        checkAndCreateTopic(STATE_UPDATE_TOPIC);
        checkAndCreateTopic(STATE_DUMP_TOPIC);
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
