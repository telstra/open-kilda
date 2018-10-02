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

package org.openkilda.wfm.topology.flow;

import org.openkilda.messaging.Utils;
import org.openkilda.pce.provider.PathComputerAuth;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.config.Neo4jConfig;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.bolts.CrudBolt;
import org.openkilda.wfm.topology.flow.bolts.ErrorBolt;
import org.openkilda.wfm.topology.flow.bolts.NorthboundReplyBolt;
import org.openkilda.wfm.topology.flow.bolts.SpeakerBolt;
import org.openkilda.wfm.topology.flow.bolts.SplitterBolt;
import org.openkilda.wfm.topology.flow.bolts.TopologyEngineBolt;
import org.openkilda.wfm.topology.flow.bolts.TransactionBolt;

import org.apache.storm.generated.ComponentObject;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Flow topology.
 */
public class FlowTopology extends AbstractTopology<FlowTopologyConfig> {
    public static final String SWITCH_ID_FIELD = "switch-id";
    public static final String STATUS_FIELD = "status";
    public static final String ERROR_TYPE_FIELD = "error-type";
    public static final Fields fieldFlowId = new Fields(Utils.FLOW_ID);
    public static final Fields fieldSwitchId = new Fields(SWITCH_ID_FIELD);
    public static final Fields fieldsFlowIdStatus = new Fields(Utils.FLOW_ID, STATUS_FIELD);
    public static final Fields fieldsMessageFlowId = new Fields(MESSAGE_FIELD, Utils.FLOW_ID);
    public static final Fields fieldsMessageErrorType = new Fields(MESSAGE_FIELD, ERROR_TYPE_FIELD);
    public static final Fields fieldsMessageSwitchIdFlowIdTransactionId =
            new Fields(MESSAGE_FIELD, SWITCH_ID_FIELD, Utils.FLOW_ID, Utils.TRANSACTION_ID);

    private static final Logger logger = LoggerFactory.getLogger(FlowTopology.class);

    private final PathComputerAuth pathComputerAuth;

    public FlowTopology(LaunchEnvironment env) {
        super(env, FlowTopologyConfig.class);

        Neo4jConfig neo4jConfig = configurationProvider.getConfiguration(Neo4jConfig.class);
        pathComputerAuth = new PathComputerAuth(neo4jConfig.getHost(),
                neo4jConfig.getLogin(), neo4jConfig.getPassword());
    }

    public FlowTopology(LaunchEnvironment env, PathComputerAuth pathComputerAuth) {
        super(env, FlowTopologyConfig.class);

        this.pathComputerAuth = pathComputerAuth;
    }

    @Override
    public StormTopology createTopology() throws NameCollisionException {
        logger.info("Creating FlowTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();
        final List<CtrlBoltRef> ctrlTargets = new ArrayList<>();
        Integer parallelism = topologyConfig.getParallelism();

        // builder.setSpout(
        //         ComponentType.LCM_SPOUT.toString(),
        //         createKafkaSpout(config.getKafkaFlowTopic(), ComponentType.LCM_SPOUT.toString()), 1);
        // builder.setBolt(
        //         ComponentType.LCM_FLOW_SYNC_BOLT.toString(),
        //         new LcmFlowCacheSyncBolt(ComponentType.NORTHBOUND_KAFKA_SPOUT.toString()),
        //         1)
        //         .shuffleGrouping(ComponentType.NORTHBOUND_KAFKA_SPOUT.toString(), LcmKafkaSpout.STREAM_ID_LCM)
        //         .shuffleGrouping(ComponentType.LCM_SPOUT.toString());

        /*
         * Spout receives all Northbound requests.
         */

        KafkaSpoutConfig<String, String> kafkaSpoutConfig = makeKafkaSpoutConfigBuilder(
                ComponentType.NORTHBOUND_KAFKA_SPOUT.toString(), topologyConfig.getKafkaFlowTopic()).build();
        // (crimi) - commenting out LcmKafkaSpout here due to dying worker
        //kafkaSpout = new LcmKafkaSpout<>(kafkaSpoutConfig);
        KafkaSpout<String, String> kafkaSpout = new KafkaSpout<>(kafkaSpoutConfig);
        builder.setSpout(ComponentType.NORTHBOUND_KAFKA_SPOUT.toString(), kafkaSpout, parallelism);

        /*
         * Bolt splits requests on streams.
         * It groups requests by flow-id.
         */
        SplitterBolt splitterBolt = new SplitterBolt();
        builder.setBolt(ComponentType.SPLITTER_BOLT.toString(), splitterBolt, parallelism)
                .shuffleGrouping(ComponentType.NORTHBOUND_KAFKA_SPOUT.toString());

        /*
         * Bolt handles flow CRUD operations.
         * It groups requests by flow-id.
         */
        CrudBolt crudBolt = new CrudBolt(pathComputerAuth);
        ComponentObject.serialized_java(org.apache.storm.utils.Utils.javaSerialize(pathComputerAuth));

        BoltDeclarer boltSetup = builder.setBolt(ComponentType.CRUD_BOLT.toString(), crudBolt, parallelism)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.CREATE.toString(), fieldFlowId)
                // TODO: this READ is used for single and for all flows. But all flows shouldn't be fieldsGrouping.
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.READ.toString(), fieldFlowId)
                .shuffleGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.DUMP.toString())
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.UPDATE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.DELETE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.PUSH.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.UNPUSH.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.REROUTE.toString(), fieldFlowId)
                // TODO: this CACHE_SYNC shouldn't be fields-grouping - there is no field - it should be all - but
                // tackle during multi instance testing
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.CACHE_SYNC.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.STATUS.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPEAKER_BOLT.toString(), StreamType.STATUS.toString(), fieldFlowId)
                .fieldsGrouping(
                        ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), StreamType.STATUS.toString(), fieldFlowId);
        //        .shuffleGrouping(
        //                ComponentType.LCM_FLOW_SYNC_BOLT.toString(), LcmFlowCacheSyncBolt.STREAM_ID_SYNC_FLOW_CACHE);
        ctrlTargets.add(new CtrlBoltRef(ComponentType.CRUD_BOLT.toString(), crudBolt, boltSetup));

        /*
         * Bolt sends cache updates.
         */
        KafkaBolt cacheKafkaBolt = createKafkaBolt(topologyConfig.getKafkaTopoCacheTopic());
        builder.setBolt(ComponentType.CACHE_KAFKA_BOLT.toString(), cacheKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.CREATE.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.UPDATE.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.DELETE.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.STATUS.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.CACHE_SYNC.toString());

        /*
         * Spout receives Topology Engine response
         */
        // FIXME(surabujin): can be replaced with NORTHBOUND_KAFKA_SPOUT (same topic)
        KafkaSpout topologyKafkaSpout = createKafkaSpout(
                topologyConfig.getKafkaFlowTopic(), ComponentType.TOPOLOGY_ENGINE_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.TOPOLOGY_ENGINE_KAFKA_SPOUT.toString(), topologyKafkaSpout, parallelism);

        /*
         * Bolt processes Topology Engine responses, groups by flow-id field
         */
        TopologyEngineBolt topologyEngineBolt = new TopologyEngineBolt();
        builder.setBolt(ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), topologyEngineBolt, parallelism)
                .shuffleGrouping(ComponentType.TOPOLOGY_ENGINE_KAFKA_SPOUT.toString());

        /*
         * Bolt sends Speaker requests
         */
        KafkaBolt speakerKafkaBolt = createKafkaBolt(topologyConfig.getKafkaSpeakerFlowTopic());
        builder.setBolt(ComponentType.SPEAKER_KAFKA_BOLT.toString(), speakerKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.CREATE.toString())
                .shuffleGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.DELETE.toString());

        /*
         * Spout receives Speaker responses
         */
        // FIXME(surabujin): can be replaced with NORTHBOUND_KAFKA_SPOUT (same topic)
        KafkaSpout speakerKafkaSpout = createKafkaSpout(
                topologyConfig.getKafkaFlowTopic(), ComponentType.SPEAKER_KAFKA_SPOUT.toString());
        builder.setSpout(ComponentType.SPEAKER_KAFKA_SPOUT.toString(), speakerKafkaSpout, parallelism);

        /*
         * Bolt processes Speaker responses, groups by flow-id field
         */
        SpeakerBolt speakerBolt = new SpeakerBolt();
        builder.setBolt(ComponentType.SPEAKER_BOLT.toString(), speakerBolt, parallelism)
                .shuffleGrouping(ComponentType.SPEAKER_KAFKA_SPOUT.toString());

        /*
         * Transaction bolt.
         */
        TransactionBolt transactionBolt = new TransactionBolt();
        boltSetup = builder.setBolt(ComponentType.TRANSACTION_BOLT.toString(), transactionBolt, parallelism)
                .fieldsGrouping(
                        ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), StreamType.CREATE.toString(), fieldSwitchId)
                .fieldsGrouping(
                        ComponentType.TOPOLOGY_ENGINE_BOLT.toString(), StreamType.DELETE.toString(), fieldSwitchId)
                .fieldsGrouping(ComponentType.SPEAKER_BOLT.toString(), StreamType.CREATE.toString(), fieldSwitchId)
                .fieldsGrouping(ComponentType.SPEAKER_BOLT.toString(), StreamType.DELETE.toString(), fieldSwitchId);
        ctrlTargets.add(new CtrlBoltRef(ComponentType.TRANSACTION_BOLT.toString(), transactionBolt, boltSetup));

        /*
         * Error processing bolt
         */
        ErrorBolt errorProcessingBolt = new ErrorBolt();
        builder.setBolt(ComponentType.ERROR_BOLT.toString(), errorProcessingBolt, parallelism)
                .shuffleGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.ERROR.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.ERROR.toString());

        /*
         * Bolt forms Northbound responses
         */
        NorthboundReplyBolt northboundReplyBolt = new NorthboundReplyBolt();
        builder.setBolt(ComponentType.NORTHBOUND_REPLY_BOLT.toString(), northboundReplyBolt, parallelism)
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.RESPONSE.toString())
                .shuffleGrouping(ComponentType.ERROR_BOLT.toString(), StreamType.RESPONSE.toString());

        /*
         * Bolt sends Northbound responses
         */
        KafkaBolt northboundKafkaBolt = createKafkaBolt(topologyConfig.getKafkaNorthboundTopic());
        builder.setBolt(ComponentType.NORTHBOUND_KAFKA_BOLT.toString(), northboundKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.NORTHBOUND_REPLY_BOLT.toString(), StreamType.RESPONSE.toString());

        createCtrlBranch(builder, ctrlTargets);

        // builder.setBolt(
        //         ComponentType.TOPOLOGY_ENGINE_OUTPUT.toString(), createKafkaBolt(config.getKafkaTopoEngTopic()), 1)
        //         .shuffleGrouping(ComponentType.LCM_FLOW_SYNC_BOLT.toString(), LcmFlowCacheSyncBolt.STREAM_ID_TPE);

        return builder.createTopology();
    }

    /**
     * Topology entry point.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new FlowTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
