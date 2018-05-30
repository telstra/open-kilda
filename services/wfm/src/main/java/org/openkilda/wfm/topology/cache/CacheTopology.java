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

import org.apache.storm.generated.ComponentObject;
import org.openkilda.messaging.ServiceType;
import org.openkilda.pce.provider.Auth;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CacheTopology extends AbstractTopology {
    private static final Logger logger = LoggerFactory.getLogger(CacheTopology.class);

    private static final String BOLT_ID_COMMON_OUTPUT = "common.out";
    private static final String BOLT_ID_OFE = "event.out";
    private static final String BOLT_ID_TOPOLOGY_OUTPUT = "topology.out";
    static final String BOLT_ID_CACHE = "cache";
    static final String SPOUT_ID_COMMON = "generic";
//    static final String SPOUT_ID_TOPOLOGY = "topology";

    private final Auth pathComputerAuth;

    public CacheTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env);
        pathComputerAuth = config.getNeo4jAuth();

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
        kafkaSpout = createKafkaSpout(config.getKafkaTopoCacheTopic(), SPOUT_ID_COMMON);
        builder.setSpout(SPOUT_ID_COMMON, kafkaSpout, parallelism);

// (carmine) - as part of 0.8 refactor, merged inputs to one topic, so this isn't neccessary
//        /*
//         * Receives cache updates from WFM topology.
//         */
//        kafkaSpout = createKafkaSpout(config.getKafkaTopoCacheTopic(), SPOUT_ID_TOPOLOGY);
//        builder.setSpout(SPOUT_ID_TOPOLOGY, kafkaSpout, parallelism);

        /*
         * Stores network cache.
         */
        CacheBolt cacheBolt = new CacheBolt(pathComputerAuth);
        ComponentObject.serialized_java(org.apache.storm.utils.Utils.javaSerialize(pathComputerAuth));
        boltSetup = builder.setBolt(BOLT_ID_CACHE, cacheBolt, parallelism)
                .shuffleGrouping(SPOUT_ID_COMMON)
// (carmine) as per above comment, only a single input streamt
//                .shuffleGrouping(SPOUT_ID_TOPOLOGY)
        ;
        ctrlTargets.add(new CtrlBoltRef(BOLT_ID_CACHE, cacheBolt, boltSetup));

        KafkaBolt kafkaBolt;
        /*
         * Sends network events to storage.
         */
        kafkaBolt = createKafkaBolt(config.getKafkaTopoEngTopic());
        builder.setBolt(BOLT_ID_COMMON_OUTPUT, kafkaBolt, parallelism)
                .shuffleGrouping(BOLT_ID_CACHE, StreamType.TPE.toString());

        /*
         * Sends cache dump and reroute requests to `flow` topology.
         */
        kafkaBolt = createKafkaBolt(config.getKafkaFlowTopic());
        builder.setBolt(BOLT_ID_TOPOLOGY_OUTPUT, kafkaBolt, parallelism)
                .shuffleGrouping(BOLT_ID_CACHE, StreamType.WFM_DUMP.toString());

        /*
         * Sends requests for ISL to OFE topology.
         */
        // FIXME(surabjin): 2 kafka bold with same topic (see previous bolt)
        KafkaBolt oFEKafkaBolt = createKafkaBolt(config.getKafkaFlowTopic());
        builder.setBolt(BOLT_ID_OFE, oFEKafkaBolt, parallelism)
                .shuffleGrouping(BOLT_ID_CACHE, StreamType.OFE.toString());

        createCtrlBranch(builder, ctrlTargets);
        createHealthCheckHandler(builder, ServiceType.CACHE_TOPOLOGY.getId());

        return builder.createTopology();
    }

    private void initKafkaTopics() {
        checkAndCreateTopic(config.getKafkaFlowTopic());
        checkAndCreateTopic(config.getKafkaTopoEngTopic());
        checkAndCreateTopic(config.getKafkaTopoCacheTopic());
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
