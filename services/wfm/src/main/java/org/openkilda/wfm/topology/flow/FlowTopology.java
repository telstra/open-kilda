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

import static org.openkilda.wfm.AbstractBolt.FIELD_ID_CONTEXT;

import org.openkilda.messaging.Utils;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.CtrlBoltRef;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.NameCollisionException;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flow.bolts.CrudBolt;
import org.openkilda.wfm.topology.flow.bolts.ErrorBolt;
import org.openkilda.wfm.topology.flow.bolts.NorthboundReplyBolt;
import org.openkilda.wfm.topology.flow.bolts.SpeakerBolt;
import org.openkilda.wfm.topology.flow.bolts.SplitterBolt;
import org.openkilda.wfm.topology.flow.bolts.StatusBolt;
import org.openkilda.wfm.topology.flow.bolts.TransactionBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.util.ArrayList;
import java.util.List;

/**
 * Flow topology.
 */
public class FlowTopology extends AbstractTopology<FlowTopologyConfig> {
    public static final String FLOW_STATUS_FIELD = "status";
    public static final String ERROR_TYPE_FIELD = "error-type";
    public static final Fields fieldFlowId = new Fields(Utils.FLOW_ID);
    public static final Fields fieldsFlowIdStatusContext =
            new Fields(Utils.FLOW_ID, FLOW_STATUS_FIELD, FIELD_ID_CONTEXT);
    public static final Fields fieldsMessageFlowId = new Fields(MESSAGE_FIELD, Utils.FLOW_ID);
    public static final Fields fieldsMessageErrorType = new Fields(MESSAGE_FIELD, ERROR_TYPE_FIELD);

    public FlowTopology(LaunchEnvironment env) {
        super(env, FlowTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() throws NameCollisionException {
        logger.info("Creating FlowTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();
        final List<CtrlBoltRef> ctrlTargets = new ArrayList<>();
        Integer parallelism = topologyConfig.getParallelism();

        /*
         * Spout receives all Northbound requests.
         */

        KafkaSpoutConfig<String, String> kafkaSpoutConfig = makeKafkaSpoutConfigBuilder(
                ComponentType.NORTHBOUND_KAFKA_SPOUT.toString(), topologyConfig.getKafkaFlowTopic()).build();
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
        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);
        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        CrudBolt crudBolt = new CrudBolt(persistenceManager, pathComputerConfig);
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
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.METER_MODE.toString(), fieldFlowId)
                // TODO: this CACHE_SYNC shouldn't be fields-grouping - there is no field - it should be all - but
                // tackle during multi instance testing
                .fieldsGrouping(ComponentType.SPLITTER_BOLT.toString(), StreamType.CACHE_SYNC.toString(), fieldFlowId);
        ctrlTargets.add(new CtrlBoltRef(ComponentType.CRUD_BOLT.toString(), crudBolt, boltSetup));

        StatusBolt statusBolt = new StatusBolt(persistenceManager);
        builder.setBolt(ComponentType.STATUS_BOLT.toString(), statusBolt, parallelism)
                .shuffleGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.STATUS.toString());

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
        TransactionBolt transactionBolt = new TransactionBolt(topologyConfig.getCommandTransactionExpirationTime());
        boltSetup = builder.setBolt(ComponentType.TRANSACTION_BOLT.toString(), transactionBolt, parallelism)
                .fieldsGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.CREATE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.UPDATE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.DELETE.toString(), fieldFlowId)
                .fieldsGrouping(ComponentType.SPEAKER_BOLT.toString(), fieldFlowId);
        ctrlTargets.add(new CtrlBoltRef(ComponentType.TRANSACTION_BOLT.toString(), transactionBolt, boltSetup));

        /*
         * Bolt sends Speaker requests
         */
        KafkaBolt speakerKafkaBolt = createKafkaBolt(topologyConfig.getKafkaSpeakerFlowTopic());
        builder.setBolt(ComponentType.SPEAKER_KAFKA_BOLT.toString(), speakerKafkaBolt, parallelism)
                .shuffleGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.CREATE.toString())
                .shuffleGrouping(ComponentType.TRANSACTION_BOLT.toString(), StreamType.DELETE.toString())
                .shuffleGrouping(ComponentType.CRUD_BOLT.toString(), StreamType.METER_MODE.toString());

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
