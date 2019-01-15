/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.nbworker.bolts.FlowOperationsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.LinkOperationsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.MessageEncoder;
import org.openkilda.wfm.topology.nbworker.bolts.ResponseSplitterBolt;
import org.openkilda.wfm.topology.nbworker.bolts.RouterBolt;
import org.openkilda.wfm.topology.nbworker.bolts.SwitchOperationsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.SwitchValidationsBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

/**
 *  Storm topology to read data from database.
 *  Topology design:
 *  kilda.topo.nb-spout ---> router-bolt ---> validation-operations-bolt ---> response-splitter-bolt ---> nb-kafka-bolt
 *                                     | ---> links-operations-bolt      ---> |   \                       |
 *                                     | ---> flows-operations-bolt      ---> |  \ \                      |
 *                                     | ---> switches-operations-bolt   ---> | \ \ \                     |
 *                                     | ---> switch-validations-bolt    ---> |\ \ \ \                    |
 *                                     | --->                   message-encoder-bolt (in error case) ---> |
 *
 * <p>kilda.topo.nb-spout: reads data from kafka.
 * router-bolt: detects what kind of request is send, defines the stream. neo-bolt: performs operation with the
 * database. response-splitter-bolt: split response into small chunks, because kafka has limited size of messages.
 * nb-kafka-bolt: sends responses back to kafka to northbound topic.
 */
public class NbWorkerTopology extends AbstractTopology<NbWorkerTopologyConfig> {

    private static final String ROUTER_BOLT_NAME = "router-bolt";
    private static final String SWITCHES_BOLT_NAME = "switches-operations-bolt";
    private static final String LINKS_BOLT_NAME = "links-operations-bolt";
    private static final String FLOWS_BOLT_NAME = "flows-operations-bolt";
    private static final String SWITCH_VALIDATIONS_BOLT_NAME = "switch-validations-bolt";
    private static final String MESSAGE_ENCODER_BOLT_NAME = "message-encoder-bolt";
    private static final String SPLITTER_BOLT_NAME = "response-splitter-bolt";
    private static final String NB_KAFKA_BOLT_NAME = "nb-kafka-bolt";
    private static final String FLOW_KAFKA_BOLT_NAME = "flow-kafka-bolt";
    private static final String NB_SPOUT_ID = "nb-spout";

    public NbWorkerTopology(LaunchEnvironment env) {
        super(env, NbWorkerTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating NbWorkerTopology - {}", topologyName);

        TopologyBuilder tb = new TopologyBuilder();

        final Integer parallelism = topologyConfig.getParallelism();

        KafkaSpout kafkaSpout = buildKafkaSpout(topologyConfig.getKafkaTopoNbTopic(), NB_SPOUT_ID);
        tb.setSpout(NB_SPOUT_ID, kafkaSpout, parallelism);

        RouterBolt router = new RouterBolt();
        tb.setBolt(ROUTER_BOLT_NAME, router, parallelism)
                .shuffleGrouping(NB_SPOUT_ID);

        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);

        SwitchOperationsBolt switchesBolt = new SwitchOperationsBolt(persistenceManager);
        tb.setBolt(SWITCHES_BOLT_NAME, switchesBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.SWITCH.toString());

        LinkOperationsBolt linksBolt = new LinkOperationsBolt(persistenceManager,
                topologyConfig.getIslCostWhenUnderMaintenance());
        tb.setBolt(LINKS_BOLT_NAME, linksBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.ISL.toString());

        FlowOperationsBolt flowsBolt = new FlowOperationsBolt(persistenceManager);
        tb.setBolt(FLOWS_BOLT_NAME, flowsBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.FLOW.toString());

        SwitchValidationsBolt validationBolt = new SwitchValidationsBolt(persistenceManager);
        tb.setBolt(SWITCH_VALIDATIONS_BOLT_NAME, validationBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.VALIDATION.toString());

        ResponseSplitterBolt splitterBolt = new ResponseSplitterBolt();
        tb.setBolt(SPLITTER_BOLT_NAME, splitterBolt, parallelism)
                .shuffleGrouping(SWITCHES_BOLT_NAME)
                .shuffleGrouping(LINKS_BOLT_NAME)
                .shuffleGrouping(FLOWS_BOLT_NAME)
                .shuffleGrouping(SWITCH_VALIDATIONS_BOLT_NAME);

        MessageEncoder messageEncoder = new MessageEncoder();
        tb.setBolt(MESSAGE_ENCODER_BOLT_NAME, messageEncoder, parallelism)
                .shuffleGrouping(LINKS_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(LINKS_BOLT_NAME, StreamType.REROUTE.toString())
                .shuffleGrouping(FLOWS_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(FLOWS_BOLT_NAME, StreamType.REROUTE.toString())
                .shuffleGrouping(SWITCHES_BOLT_NAME, StreamType.ERROR.toString());

        KafkaBolt kafkaNbBolt = buildKafkaBolt(topologyConfig.getKafkaNorthboundTopic());
        tb.setBolt(NB_KAFKA_BOLT_NAME, kafkaNbBolt, parallelism)
                .shuffleGrouping(SPLITTER_BOLT_NAME)
                .shuffleGrouping(MESSAGE_ENCODER_BOLT_NAME, StreamType.ERROR.toString());

        KafkaBolt kafkaFlowBolt = buildKafkaBolt(topologyConfig.getKafkaFlowTopic());
        tb.setBolt(FLOW_KAFKA_BOLT_NAME, kafkaFlowBolt, parallelism)
                .shuffleGrouping(MESSAGE_ENCODER_BOLT_NAME, StreamType.REROUTE.toString());

        return tb.createTopology();
    }

    /**
     * Launches and sets up the workflow manager environment.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new NbWorkerTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }

}
