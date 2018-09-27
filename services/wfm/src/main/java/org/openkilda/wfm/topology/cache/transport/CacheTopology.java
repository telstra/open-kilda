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

package org.openkilda.wfm.topology.cache.transport;

import org.openkilda.pce.provider.Auth;
import org.openkilda.pce.provider.PathComputerAuth;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.config.Neo4jConfig;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.cache.StreamType;

import org.apache.storm.generated.ComponentObject;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CacheTopology extends AbstractTopology<CacheTopologyConfig> {
    private static final Logger logger = LoggerFactory.getLogger(CacheTopology.class);

    private static final String BOLT_ID_COMMON_OUTPUT = "common.out";
    private static final String BOLT_ID_OFE = "event.out";
    private static final String BOLT_ID_TOPOLOGY_OUTPUT = "topology.out";
    static final String BOLT_ID_CACHE = "cache";
    private static final String BOLT_ID_REROUTE_THROTTLING = "rerouteThrottling";
    private static final String SPOUT_ID_COMMON = "generic";
    //    static final String SPOUT_ID_TOPOLOGY = "topology";

    public CacheTopology(LaunchEnvironment env) {
        super(env, CacheTopologyConfig.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StormTopology createTopology() throws NameCollisionException {
        logger.info("Creating CacheTopology - {}", topologyName);

        initKafkaTopics();

        int parallelism = topologyConfig.getParallelism();

        TopologyBuilder builder = new TopologyBuilder();
        List<CtrlBoltRef> ctrlTargets = new ArrayList<>();

        /*
         * Receives cache from storage.
         */
        KafkaSpout kafkaSpout = createKafkaSpout(topologyConfig.getKafkaTopoCacheTopic(), SPOUT_ID_COMMON);
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
        Neo4jConfig neo4jConfig = configurationProvider.getConfiguration(Neo4jConfig.class);
        Auth pathComputerAuth = new PathComputerAuth(neo4jConfig.getHost(),
                neo4jConfig.getLogin(), neo4jConfig.getPassword());
        CacheBolt cacheBolt = new CacheBolt(pathComputerAuth);
        ComponentObject.serialized_java(org.apache.storm.utils.Utils.javaSerialize(pathComputerAuth));
        BoltDeclarer boltSetup = builder.setBolt(BOLT_ID_CACHE, cacheBolt, parallelism)
                .shuffleGrouping(SPOUT_ID_COMMON);
        // (carmine) as per above comment, only a single input streamt
        //                .shuffleGrouping(SPOUT_ID_TOPOLOGY)
        ctrlTargets.add(new CtrlBoltRef(BOLT_ID_CACHE, cacheBolt, boltSetup));

        /*
         * Sends network events to storage.
         */
        KafkaBolt kafkaTopoEngBolt = createKafkaBolt(topologyConfig.getKafkaTopoEngTopic());
        builder.setBolt(BOLT_ID_COMMON_OUTPUT, kafkaTopoEngBolt, parallelism)
                .shuffleGrouping(BOLT_ID_CACHE, StreamType.TPE.toString());

        /*
         * Sends cache dump and reroute requests to `flow` topology via throttling bolt.
         */
        FlowThrottlingBolt flowThrottlingBolt = new FlowThrottlingBolt(
                topologyConfig.getRerouteThrottlingMinDelay(), topologyConfig.getRerouteThrottlingMaxDelay());
        int newParallelism = topologyConfig.getNewParallelism();
        builder.setBolt(BOLT_ID_REROUTE_THROTTLING, flowThrottlingBolt, newParallelism)
                .fieldsGrouping(BOLT_ID_CACHE, StreamType.WFM_REROUTE.toString(), new Fields(CacheBolt.FLOW_ID_FIELD));

        KafkaBolt kafkaFlowBolt = createKafkaBolt(topologyConfig.getKafkaFlowTopic());
        builder.setBolt(BOLT_ID_TOPOLOGY_OUTPUT, kafkaFlowBolt, parallelism)
                .shuffleGrouping(BOLT_ID_REROUTE_THROTTLING);

        /*
         * Sends requests for ISL to OFE topology.
         */
        // FIXME(surabjin): 2 kafka bold with same topic (see previous bolt)
        KafkaBolt ofeKafkaBolt = createKafkaBolt(topologyConfig.getKafkaFlowTopic());
        builder.setBolt(BOLT_ID_OFE, ofeKafkaBolt, parallelism)
                .shuffleGrouping(BOLT_ID_CACHE, StreamType.OFE.toString());

        createCtrlBranch(builder, ctrlTargets);
        return builder.createTopology();
    }

    private void initKafkaTopics() {
        checkAndCreateTopic(topologyConfig.getKafkaFlowTopic());
        checkAndCreateTopic(topologyConfig.getKafkaTopoEngTopic());
        checkAndCreateTopic(topologyConfig.getKafkaTopoCacheTopic());
    }

    /**
     * Launches and sets up the workflow manager environment.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new CacheTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
