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

package org.openkilda.wfm.topology.flowhs;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_REROUTE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_CREATE;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_REROUTE;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.history.bolt.HistoryBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt.Config;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flowhs.bolts.FlowCreateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowCreateHubBolt.FlowCreateConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowRerouteHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.RouterBolt;
import org.openkilda.wfm.topology.flowhs.bolts.SpeakerWorkerBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

import java.util.concurrent.TimeUnit;

public class FlowHsTopology extends AbstractTopology<FlowHsTopologyConfig> {

    private int parallelism;

    public FlowHsTopology(LaunchEnvironment env) {
        super(env, FlowHsTopologyConfig.class);

        parallelism = topologyConfig.getNewParallelism();
    }

    @Override
    public StormTopology createTopology() {
        TopologyBuilder tb = new TopologyBuilder();

        inputSpout(tb);
        inputRouter(tb);

        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);

        flowCreateHub(tb, persistenceManager);
        flowRerouteHub(tb, persistenceManager);

        speakerSpout(tb);
        flowCreateSpeakerWorker(tb);
        flowRerouteSpeakerWorker(tb);
        speakerOutput(tb);

        coordinator(tb);

        northboundOutput(tb);

        history(tb, persistenceManager);

        return tb.createTopology();
    }

    private void inputSpout(TopologyBuilder topologyBuilder) {
        KafkaSpout<String, Message> mainSpout = buildKafkaSpout(getConfig().getKafkaFlowHsTopic(),
                ComponentId.FLOW_SPOUT.name());
        topologyBuilder.setSpout(ComponentId.FLOW_SPOUT.name(), mainSpout, parallelism);
    }

    private void inputRouter(TopologyBuilder topologyBuilder) {
        topologyBuilder.setBolt(ComponentId.FLOW_ROUTER_BOLT.name(), new RouterBolt(), parallelism)
                .shuffleGrouping(ComponentId.FLOW_SPOUT.name());
    }

    private void flowCreateHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateHubTimeoutSeconds());

        FlowCreateConfig config =  FlowCreateConfig.flowCreateBuilder()
                .flowCreationRetriesLimit(topologyConfig.getCreateHubRetries())
                .speakerCommandRetriesLimit(topologyConfig.getCreateHubSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name())
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowCreateHubBolt hubBolt =
                new FlowCreateHubBolt(config, persistenceManager, pathComputerConfig, flowResourcesConfig);
        topologyBuilder.setBolt(ComponentId.FLOW_CREATE_HUB.name(), hubBolt, parallelism)
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_CREATE_HUB.name(), FIELDS_KEY)
                .directGrouping(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_CREATE.name())
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowRerouteHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getRerouteHubTimeoutSeconds());
        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowRerouteHubBolt hubBolt = new FlowRerouteHubBolt(ComponentId.FLOW_ROUTER_BOLT.name(),
                ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(), hubTimeout, true,
                persistenceManager, pathComputerConfig, flowResourcesConfig);
        topologyBuilder.setBolt(ComponentId.FLOW_REROUTE_HUB.name(), hubBolt, parallelism)
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_REROUTE_HUB.name(), FIELDS_KEY)
                .directGrouping(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_REROUTE.name())
                .directGrouping(CoordinatorBolt.ID);
    }

    private void speakerSpout(TopologyBuilder topologyBuilder) {
        KafkaSpout<String, AbstractMessage> flWorkerSpout = buildKafkaSpoutForAbstractMessage(
                getConfig().getKafkaFlowSpeakerWorkerTopic(),
                ComponentId.SPEAKER_WORKER_SPOUT.name());
        topologyBuilder.setSpout(ComponentId.SPEAKER_WORKER_SPOUT.name(), flWorkerSpout, parallelism);
    }

    private void flowCreateSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_CREATE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_CREATE.name())
                .build());
        topologyBuilder.setBolt(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name(), speakerWorker, parallelism)
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowRerouteSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getRerouteSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_REROUTE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_REROUTE.name())
                .build());
        topologyBuilder.setBolt(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(), speakerWorker, parallelism)
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void speakerOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt flKafkaBolt = buildKafkaBoltWithAbstractMessageSupport(getConfig().getKafkaSpeakerFlowTopic());
        topologyBuilder.setBolt(ComponentId.SPEAKER_REQUEST_SENDER.name(), flKafkaBolt, parallelism)
                .shuffleGrouping(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name());
    }

    private void coordinator(TopologyBuilder topologyBuilder) {
        topologyBuilder.setSpout(CoordinatorSpout.ID, new CoordinatorSpout());
        topologyBuilder.setBolt(CoordinatorBolt.ID, new CoordinatorBolt(), parallelism)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY);
    }

    private void northboundOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt nbKafkaBolt = buildKafkaBolt(getConfig().getKafkaNorthboundTopic());
        topologyBuilder.setBolt(ComponentId.NB_RESPONSE_SENDER.name(), nbKafkaBolt, parallelism)
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name());
    }

    private void history(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        HistoryBolt historyBolt = new HistoryBolt(persistenceManager);
        topologyBuilder.setBolt(ComponentId.HISTORY_BOLT.name(), historyBolt, parallelism)
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name());
    }

    public enum ComponentId {
        FLOW_SPOUT("flow.spout"),
        SPEAKER_WORKER_SPOUT("fl.worker.spout"),

        FLOW_ROUTER_BOLT("flow.router.bolt"),
        FLOW_CREATE_HUB("flow.create.hub.bolt"),
        FLOW_REROUTE_HUB("flow.reroute.hub.bolt"),

        FLOW_CREATE_SPEAKER_WORKER("flow.create.worker.bolt"),
        FLOW_REROUTE_SPEAKER_WORKER("flow.reroute.worker.bolt"),

        NB_RESPONSE_SENDER("nb.kafka.bolt"),
        SPEAKER_REQUEST_SENDER("speaker.kafka.bolt"),

        HISTORY_BOLT("flow.history.bolt");

        private final String value;

        ComponentId(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

    }

    public enum Stream {
        ROUTER_TO_FLOW_CREATE_HUB,
        ROUTER_TO_FLOW_REROUTE_HUB,

        HUB_TO_SPEAKER_WORKER,
        HUB_TO_HISTORY_BOLT,

        SPEAKER_WORKER_TO_HUB_CREATE,
        SPEAKER_WORKER_TO_HUB_REROUTE,

        SPEAKER_WORKER_REQUEST_SENDER,
        HUB_TO_NB_RESPONSE_SENDER
    }

    /**
     * Launches and sets up the topology.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new FlowHsTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
