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

package org.openkilda.wfm.topology.nbworker;

import org.openkilda.pce.PathComputerConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt.Config;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.nbworker.bolts.DiscoveryEncoderBolt;
import org.openkilda.wfm.topology.nbworker.bolts.FeatureTogglesBolt;
import org.openkilda.wfm.topology.nbworker.bolts.FlowMeterModifyHubBolt;
import org.openkilda.wfm.topology.nbworker.bolts.FlowOperationsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.FlowValidationHubBolt;
import org.openkilda.wfm.topology.nbworker.bolts.HistoryOperationsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.LinkOperationsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.MessageEncoder;
import org.openkilda.wfm.topology.nbworker.bolts.PathsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.ResponseSplitterBolt;
import org.openkilda.wfm.topology.nbworker.bolts.RouterBolt;
import org.openkilda.wfm.topology.nbworker.bolts.SpeakerWorkerBolt;
import org.openkilda.wfm.topology.nbworker.bolts.SwitchOperationsBolt;
import org.openkilda.wfm.topology.nbworker.bolts.SwitchValidationsBolt;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

/**
 *  Storm topology to read data from database.
 *  Topology design:
 *  kilda.topo.nb-spout ---> router-bolt ---> validation-operations-bolt ---> response-splitter-bolt ---> nb-kafka-bolt
 *                                     | ---> links-operations-bolt      ---> |   \                       |
 *                                     | ---> flows-operations-bolt      ---> |  \ \                      |
 *                                     | ---> switches-operations-bolt   ---> | \ \ \                     |
 *                                     | ---> switch-validations-bolt    ---> |\ \ \ \                    |
 *                                     | ---> history-operations-bolt    ---> | \ \ \ \                   |
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
    private static final String FEATURE_TOGGLES_BOLT_NAME = "feature-toggles-bolt";
    private static final String PATHS_BOLT_NAME = "paths-bolt";
    private static final String SWITCH_VALIDATIONS_BOLT_NAME = "switch-validations-bolt";
    private static final String MESSAGE_ENCODER_BOLT_NAME = "message-encoder-bolt";
    private static final String DISCOVERY_ENCODER_BOLT_NAME = "discovery-encoder-bolt";
    private static final String SPLITTER_BOLT_NAME = "response-splitter-bolt";
    private static final String NB_KAFKA_BOLT_NAME = "nb-kafka-bolt";
    private static final String FLOW_KAFKA_BOLT_NAME = "flow-kafka-bolt";
    private static final String DISCO_KAFKA_BOLT_NAME = "disco-kafka-bolt";
    private static final String HISTORY_BOLT_NAME = "history-operations-bolt";
    private static final String NB_SPOUT_ID = "nb-spout";
    private static final String SPEAKER_KAFKA_BOLT = "speaker-bolt";
    private static final String VALIDATION_WORKER_BOLT = "validation." + WorkerBolt.ID;
    private static final String METER_MODIFY_WORKER_BOLT = "meter.modify." + WorkerBolt.ID;

    private static final int WORKER_TIMEOUT = 3000;

    private static final Fields FIELDS_KEY = new Fields(MessageTranslator.KEY_FIELD);

    public NbWorkerTopology(LaunchEnvironment env) {
        super(env, NbWorkerTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating NbWorkerTopology - {}", topologyName);

        TopologyBuilder tb = new TopologyBuilder();

        final Integer parallelism = topologyConfig.getParallelism();

        tb.setSpout(CoordinatorSpout.ID, new CoordinatorSpout());
        tb.setBolt(CoordinatorBolt.ID, new CoordinatorBolt())
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(FlowValidationHubBolt.ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(VALIDATION_WORKER_BOLT, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(FlowMeterModifyHubBolt.ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(METER_MODIFY_WORKER_BOLT, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY);

        KafkaSpout kafkaSpout = buildKafkaSpout(topologyConfig.getKafkaTopoNbTopic(), NB_SPOUT_ID);
        tb.setSpout(NB_SPOUT_ID, kafkaSpout, parallelism);

        RouterBolt router = new RouterBolt();
        tb.setBolt(ROUTER_BOLT_NAME, router, parallelism)
                .shuffleGrouping(NB_SPOUT_ID)
                .fieldsGrouping(FlowValidationHubBolt.ID, RouterBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(FlowMeterModifyHubBolt.ID, RouterBolt.INCOME_STREAM, FIELDS_KEY);

        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);
        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);

        tb.setBolt(FlowValidationHubBolt.ID, new FlowValidationHubBolt(ROUTER_BOLT_NAME, persistenceManager,
                topologyConfig.getFlowMeterMinBurstSizeInKbits(), topologyConfig.getFlowMeterBurstCoefficient()))
                .fieldsGrouping(ROUTER_BOLT_NAME, FlowValidationHubBolt.INCOME_STREAM, FIELDS_KEY)
                .directGrouping(VALIDATION_WORKER_BOLT, FlowValidationHubBolt.INCOME_STREAM)
                .directGrouping(CoordinatorBolt.ID);

        Config speakerValidationWorkerConfig = Config.builder()
                .hubComponent(FlowValidationHubBolt.ID)
                .streamToHub(FlowValidationHubBolt.INCOME_STREAM)
                .workerSpoutComponent(ROUTER_BOLT_NAME)
                .defaultTimeout(WORKER_TIMEOUT)
                .build();
        tb.setBolt(VALIDATION_WORKER_BOLT, new SpeakerWorkerBolt(speakerValidationWorkerConfig))
                .fieldsGrouping(ROUTER_BOLT_NAME, StreamType.FLOW_VALIDATION_WORKER.toString(), FIELDS_KEY)
                .fieldsGrouping(FlowValidationHubBolt.ID, StreamType.FLOW_VALIDATION_WORKER.toString(), FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);

        tb.setBolt(FlowMeterModifyHubBolt.ID, new FlowMeterModifyHubBolt(ROUTER_BOLT_NAME, persistenceManager))
                .fieldsGrouping(ROUTER_BOLT_NAME, FlowMeterModifyHubBolt.INCOME_STREAM, FIELDS_KEY)
                .directGrouping(METER_MODIFY_WORKER_BOLT, FlowMeterModifyHubBolt.INCOME_STREAM)
                .directGrouping(CoordinatorBolt.ID);

        Config speakerMeterModifyWorkerConfig = Config.builder()
                .hubComponent(FlowMeterModifyHubBolt.ID)
                .streamToHub(FlowMeterModifyHubBolt.INCOME_STREAM)
                .workerSpoutComponent(ROUTER_BOLT_NAME)
                .defaultTimeout(WORKER_TIMEOUT)
                .build();
        tb.setBolt(METER_MODIFY_WORKER_BOLT, new SpeakerWorkerBolt(speakerMeterModifyWorkerConfig))
                .fieldsGrouping(ROUTER_BOLT_NAME, StreamType.METER_MODIFY_WORKER.toString(), FIELDS_KEY)
                .fieldsGrouping(FlowMeterModifyHubBolt.ID, StreamType.METER_MODIFY_WORKER.toString(), FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);

        SwitchOperationsBolt switchesBolt = new SwitchOperationsBolt(persistenceManager,
                topologyConfig.getIslCostWhenUnderMaintenance());
        tb.setBolt(SWITCHES_BOLT_NAME, switchesBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.SWITCH.toString());

        LinkOperationsBolt linksBolt = new LinkOperationsBolt(persistenceManager,
                topologyConfig.getIslCostWhenUnderMaintenance());
        tb.setBolt(LINKS_BOLT_NAME, linksBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.ISL.toString());

        FlowOperationsBolt flowsBolt = new FlowOperationsBolt(persistenceManager);
        tb.setBolt(FLOWS_BOLT_NAME, flowsBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.FLOW.toString());

        FeatureTogglesBolt featureTogglesBolt = new FeatureTogglesBolt(persistenceManager);
        tb.setBolt(FEATURE_TOGGLES_BOLT_NAME, featureTogglesBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.FEATURE_TOGGLES.toString());

        PathsBolt pathsBolt = new PathsBolt(persistenceManager, pathComputerConfig);
        tb.setBolt(PATHS_BOLT_NAME, pathsBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.PATHS.toString());

        SwitchValidationsBolt validationBolt = new SwitchValidationsBolt(persistenceManager);
        tb.setBolt(SWITCH_VALIDATIONS_BOLT_NAME, validationBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.VALIDATION.toString());

        HistoryOperationsBolt historyBolt = new HistoryOperationsBolt(persistenceManager);
        tb.setBolt(HISTORY_BOLT_NAME, historyBolt, parallelism)
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.HISTORY.toString());

        ResponseSplitterBolt splitterBolt = new ResponseSplitterBolt();
        tb.setBolt(SPLITTER_BOLT_NAME, splitterBolt, parallelism)
                .shuffleGrouping(SWITCHES_BOLT_NAME)
                .shuffleGrouping(LINKS_BOLT_NAME)
                .shuffleGrouping(FLOWS_BOLT_NAME)
                .shuffleGrouping(FEATURE_TOGGLES_BOLT_NAME)
                .shuffleGrouping(PATHS_BOLT_NAME)
                .shuffleGrouping(SWITCH_VALIDATIONS_BOLT_NAME)
                .shuffleGrouping(HISTORY_BOLT_NAME)
                .shuffleGrouping(FlowValidationHubBolt.ID)
                .shuffleGrouping(FlowMeterModifyHubBolt.ID);

        MessageEncoder messageEncoder = new MessageEncoder();
        tb.setBolt(MESSAGE_ENCODER_BOLT_NAME, messageEncoder, parallelism)
                .shuffleGrouping(LINKS_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(LINKS_BOLT_NAME, StreamType.REROUTE.toString())
                .shuffleGrouping(FLOWS_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(FLOWS_BOLT_NAME, StreamType.REROUTE.toString())
                .shuffleGrouping(SWITCHES_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(SWITCHES_BOLT_NAME, StreamType.REROUTE.toString())
                .shuffleGrouping(ROUTER_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(FEATURE_TOGGLES_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(PATHS_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(HISTORY_BOLT_NAME, StreamType.ERROR.toString())
                .shuffleGrouping(FlowValidationHubBolt.ID, StreamType.ERROR.toString())
                .shuffleGrouping(FlowMeterModifyHubBolt.ID, StreamType.ERROR.toString());

        DiscoveryEncoderBolt discoveryEncoder = new DiscoveryEncoderBolt();
        tb.setBolt(DISCOVERY_ENCODER_BOLT_NAME, discoveryEncoder, parallelism)
                .shuffleGrouping(LINKS_BOLT_NAME, StreamType.DISCO.toString())
                .shuffleGrouping(SWITCHES_BOLT_NAME, StreamType.DISCO.toString())
                .shuffleGrouping(FEATURE_TOGGLES_BOLT_NAME, FeatureTogglesBolt.STREAM_NOTIFICATION_ID);

        KafkaBolt kafkaNbBolt = buildKafkaBolt(topologyConfig.getKafkaNorthboundTopic());
        tb.setBolt(NB_KAFKA_BOLT_NAME, kafkaNbBolt, parallelism)
                .shuffleGrouping(SPLITTER_BOLT_NAME)
                .shuffleGrouping(MESSAGE_ENCODER_BOLT_NAME, StreamType.ERROR.toString());

        KafkaBolt kafkaFlowBolt = buildKafkaBolt(topologyConfig.getKafkaFlowTopic());
        tb.setBolt(FLOW_KAFKA_BOLT_NAME, kafkaFlowBolt, parallelism)
                .shuffleGrouping(MESSAGE_ENCODER_BOLT_NAME, StreamType.REROUTE.toString());

        KafkaBolt kafkaDiscoBolt = buildKafkaBolt(topologyConfig.getKafkaDiscoTopic());
        tb.setBolt(DISCO_KAFKA_BOLT_NAME, kafkaDiscoBolt, parallelism)
                .shuffleGrouping(DISCOVERY_ENCODER_BOLT_NAME);

        tb.setBolt(SPEAKER_KAFKA_BOLT, buildKafkaBolt(topologyConfig.getKafkaSpeakerTopic()))
                .shuffleGrouping(VALIDATION_WORKER_BOLT, StreamType.TO_SPEAKER.toString())
                .shuffleGrouping(METER_MODIFY_WORKER_BOLT, StreamType.TO_SPEAKER.toString());

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
