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

package org.openkilda.wfm.topology.switchmanager;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.kafka.AbstractMessageSerializer;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.switchmanager.bolt.HeavyOperationBolt;
import org.openkilda.wfm.topology.switchmanager.bolt.SpeakerWorkerBolt;
import org.openkilda.wfm.topology.switchmanager.bolt.SwitchManagerHub;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import com.google.common.collect.Lists;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SwitchManagerTopology extends AbstractTopology<SwitchManagerTopologyConfig> {

    private static final String HUB_SPOUT = "hub.spout";
    private static final String WORKER_SPOUT = "worker.spout";
    private static final String WORKER_RESPONSE_SPOUT = "worker.response.spout";
    private static final String NB_KAFKA_BOLT = "nb.bolt";
    private static final String SPEAKER_KAFKA_BOLT = "speaker.bolt";
    private static final String SPEAKER_REQUEST_KAFKA_BOLT = "speaker.request.bolt";
    private static final String GRPC_SPEAKER_KAFKA_BOLT = "grpc.speaker.bolt";
    private static final String METRICS_BOLT = "metrics.bolt";

    private static final Fields FIELDS_KEY = new Fields(MessageKafkaTranslator.FIELD_ID_KEY);

    public SwitchManagerTopology(LaunchEnvironment env) {
        super(env, "swmanager-topology", SwitchManagerTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating SwitchManagerTopology - {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(builder, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);

        declareSpout(builder, new CoordinatorSpout(), CoordinatorSpout.ID);
        declareBolt(builder, new CoordinatorBolt(), CoordinatorBolt.ID)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(SwitchManagerHub.ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(SpeakerWorkerBolt.ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY);

        PersistenceManager persistenceManager = new PersistenceManager(configurationProvider);

        HubBolt.Config hubConfig = HubBolt.Config.builder()
                .requestSenderComponent(HUB_SPOUT)
                .workerComponent(SpeakerWorkerBolt.ID)
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .timeoutMs((int) TimeUnit.SECONDS.toMillis(topologyConfig.getProcessTimeout()))
                .build();
        List<String> inputTopics = Lists.newArrayList(topologyConfig.getKafkaSwitchManagerNbTopic(),
                topologyConfig.getKafkaSwitchManagerNetworkTopic(),
                topologyConfig.getKafkaSwitchManagerNbWorkerTopic());
        declareKafkaSpout(builder, inputTopics, HUB_SPOUT);
        declareBolt(builder, new SwitchManagerHub(hubConfig, persistenceManager,
                        topologyConfig, configurationProvider.getConfiguration(RuleManagerConfig.class)),
                SwitchManagerHub.ID)
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .fieldsGrouping(HUB_SPOUT, FIELDS_KEY)
                .directGrouping(HeavyOperationBolt.ID, SwitchManagerHub.INCOME_STREAM)
                .directGrouping(SpeakerWorkerBolt.ID, SwitchManagerHub.INCOME_STREAM)
                .directGrouping(CoordinatorBolt.ID);

        WorkerBolt.Config speakerWorkerConfig = WorkerBolt.Config.builder()
                .hubComponent(SwitchManagerHub.ID)
                .streamToHub(SwitchManagerHub.INCOME_STREAM)
                .workerSpoutComponent(WORKER_SPOUT)
                .workerSpoutComponent(WORKER_RESPONSE_SPOUT)
                .defaultTimeout((int) TimeUnit.SECONDS.toMillis(topologyConfig.getOperationTimeout()))
                .build();
        declareKafkaSpout(builder, Lists.newArrayList(topologyConfig.getKafkaSwitchManagerTopic(),
                topologyConfig.getGrpcResponseTopic()), WORKER_SPOUT);
        declareKafkaSpoutForAbstractMessage(builder, topologyConfig.getKafkaSwitchManagerSpeakerWorkerTopic(),
                WORKER_RESPONSE_SPOUT);
        declareBolt(builder, new SpeakerWorkerBolt(
                speakerWorkerConfig, topologyConfig.getChunkedMessagesExpirationMinutes()),
                SpeakerWorkerBolt.ID)
                .fieldsGrouping(WORKER_SPOUT, FIELDS_KEY)
                .fieldsGrouping(WORKER_RESPONSE_SPOUT, FIELDS_KEY)
                .fieldsGrouping(SwitchManagerHub.ID, SpeakerWorkerBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(SwitchManagerHub.ID, SpeakerWorkerBolt.OF_COMMANDS_INCOME_STREAM, FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);

        declareBolt(builder, new HeavyOperationBolt(
                persistenceManager, configurationProvider.getConfiguration(RuleManagerConfig.class)),
                HeavyOperationBolt.ID)
                .shuffleGrouping(SwitchManagerHub.ID, HeavyOperationBolt.INCOME_STREAM);

        declareBolt(builder, buildKafkaBolt(topologyConfig.getKafkaNorthboundTopic()), NB_KAFKA_BOLT)
                .shuffleGrouping(SwitchManagerHub.ID, StreamType.TO_NORTHBOUND.toString());

        declareBolt(builder, buildKafkaBolt(topologyConfig.getKafkaSpeakerTopic()), SPEAKER_KAFKA_BOLT)
                .shuffleGrouping(SpeakerWorkerBolt.ID, StreamType.TO_FLOODLIGHT.toString());

        KafkaBolt<String, AbstractMessage> flKafkaBolt = makeKafkaBolt(
                topologyConfig.getKafkaSpeakerSwitchManagerTopic(), AbstractMessageSerializer.class);
        declareBolt(builder, flKafkaBolt, SPEAKER_REQUEST_KAFKA_BOLT)
                .shuffleGrouping(SpeakerWorkerBolt.ID, StreamType.REQUEST_TO_FLOODLIGHT.toString());

        declareBolt(builder, buildKafkaBolt(topologyConfig.getGrpcSpeakerTopic()), GRPC_SPEAKER_KAFKA_BOLT)
                .shuffleGrouping(SpeakerWorkerBolt.ID, StreamType.TO_GRPC.toString());

        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(),
                getBoltInstancesCount(SwitchManagerHub.ID));
        declareBolt(builder, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(SwitchManagerHub.ID, ZkStreams.ZK.toString());

        metrics(builder);

        return builder.createTopology();
    }

    @Override
    protected String getZkTopoName() {
        return "swmanager";
    }

    private void metrics(TopologyBuilder topologyBuilder) {
        String openTsdbTopic = topologyConfig.getKafkaTopics().getOtsdbTopic();
        KafkaBolt kafkaBolt = createKafkaBolt(openTsdbTopic);
        topologyBuilder.setBolt(METRICS_BOLT, kafkaBolt)
                .shuffleGrouping(SwitchManagerHub.ID, StreamType.HUB_TO_METRICS_BOLT.name());
    }

    /**
     * Launches and sets up the workflow manager environment.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new SwitchManagerTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
