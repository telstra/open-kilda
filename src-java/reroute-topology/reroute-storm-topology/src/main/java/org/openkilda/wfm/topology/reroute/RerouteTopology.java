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

package org.openkilda.wfm.topology.reroute;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.reroute.bolts.FlowThrottlingBolt;
import org.openkilda.wfm.topology.reroute.bolts.RerouteBolt;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

public class RerouteTopology extends AbstractTopology<RerouteTopologyConfig> {

    private static final String SPOUT_ID_REROUTE = "reroute-spout";
    private static final String BOLT_ID_REROUTE = "reroute-bolt";
    private static final String BOLT_ID_REROUTE_THROTTLING = "reroute-throttling-bolt";
    private static final String BOLT_ID_KAFKA_FLOW = "kafka-flow-bolt";
    private static final String BOLT_ID_KAFKA_FLOWHS = "kafka-flowhs-bolt";

    public static final Fields KAFKA_FIELDS =
            new Fields(MessageKafkaTranslator.KEY_FIELD, MessageKafkaTranslator.FIELD_ID_PAYLOAD);

    public RerouteTopology(LaunchEnvironment env) {
        super(env, RerouteTopologyConfig.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StormTopology createTopology() {
        logger.info("Creating RerouteTopology - {}", topologyName);

        TopologyBuilder topologyBuilder = new TopologyBuilder();

        final Integer parallelism = topologyConfig.getParallelism();

        KafkaSpout kafkaSpout = buildKafkaSpout(topologyConfig.getKafkaTopoRerouteTopic(), SPOUT_ID_REROUTE);
        topologyBuilder.setSpout(SPOUT_ID_REROUTE, kafkaSpout, parallelism);

        PersistenceManager persistenceManager = PersistenceProvider.getInstance()
                .createPersistenceManager(configurationProvider);

        RerouteBolt rerouteBolt = new RerouteBolt(persistenceManager);
        topologyBuilder.setBolt(BOLT_ID_REROUTE, rerouteBolt, parallelism)
                .shuffleGrouping(SPOUT_ID_REROUTE);

        FlowThrottlingBolt flowThrottlingBolt = new FlowThrottlingBolt(persistenceManager,
                topologyConfig.getRerouteThrottlingMinDelay(),
                topologyConfig.getRerouteThrottlingMaxDelay(),
                topologyConfig.getDefaultFlowPriority());
        //TODO(siakovenko): fix ThrottlingBolt with parallelism > 1 : see topologyConfig.getNewParallelism()
        topologyBuilder.setBolt(BOLT_ID_REROUTE_THROTTLING, flowThrottlingBolt, parallelism)
                .fieldsGrouping(BOLT_ID_REROUTE, new Fields(RerouteBolt.FLOW_ID_FIELD));

        KafkaBolt kafkaFlowBolt = buildKafkaBolt(topologyConfig.getKafkaFlowTopic());
        topologyBuilder.setBolt(BOLT_ID_KAFKA_FLOW, kafkaFlowBolt, parallelism)
                .shuffleGrouping(BOLT_ID_REROUTE, StreamType.SWAP.toString())
                .shuffleGrouping(BOLT_ID_REROUTE_THROTTLING, FlowThrottlingBolt.STREAM_FLOW_ID);

        KafkaBolt kafkaFlowHsBolt = buildKafkaBolt(topologyConfig.getKafkaFlowHsTopic());
        topologyBuilder.setBolt(BOLT_ID_KAFKA_FLOWHS, kafkaFlowHsBolt, parallelism)
                .shuffleGrouping(BOLT_ID_REROUTE_THROTTLING, FlowThrottlingBolt.STREAM_FLOWHS_ID);

        return topologyBuilder.createTopology();
    }

    /**
     * Launches and sets up the workflow manager environment.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new RerouteTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
