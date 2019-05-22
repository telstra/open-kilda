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
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_CREATE;

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
import org.openkilda.wfm.topology.flowhs.bolts.RouterBolt;
import org.openkilda.wfm.topology.flowhs.bolts.SpeakerWorkerBolt;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

public class FlowHsTopology extends AbstractTopology<FlowHsTopologyConfig> {

    @Override
    public StormTopology createTopology() {
        TopologyBuilder tb = new TopologyBuilder();

        int parallelism = topologyConfig.getNewParallelism();

        tb.setSpout(CoordinatorSpout.ID, new CoordinatorSpout());
        tb.setBolt(CoordinatorBolt.ID, new CoordinatorBolt(), parallelism)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(SpeakerWorkerBolt.ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(Component.FLOW_CREATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY);

        KafkaSpout<String, AbstractMessage> flWorkerSpout = buildKafkaSpoutForAbstractMessage(
                getConfig().getKafkaFlowSpeakerWorkerTopic(),
                Component.SPEAKER_WORKER_SPOUT.name());
        tb.setSpout(Component.SPEAKER_WORKER_SPOUT.name(), flWorkerSpout, parallelism);

        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(1000)
                .workerSpoutComponent(Component.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(Component.FLOW_CREATE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_CREATE.name())
                .build());
        tb.setBolt(SpeakerWorkerBolt.ID, speakerWorker, parallelism)
                .fieldsGrouping(Component.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(Component.FLOW_CREATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);

        KafkaSpout<String, Message> mainSpout = buildKafkaSpout(getConfig().getKafkaFlowHsTopic(),
                Component.FLOW_SPOUT.name());
        tb.setSpout(Component.FLOW_SPOUT.name(), mainSpout, parallelism);

        tb.setBolt(Component.ROUTER_BOLT.name(), new RouterBolt(), parallelism)
            .shuffleGrouping(Component.FLOW_SPOUT.name());

        PersistenceManager persistenceManager =
                PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);
        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);

        FlowCreateHubBolt hubBolt = new FlowCreateHubBolt(Component.ROUTER_BOLT.name(), 10000, true,
                persistenceManager, pathComputerConfig, flowResourcesConfig);
        tb.setBolt(Component.FLOW_CREATE_HUB.name(), hubBolt, parallelism)
                .fieldsGrouping(Component.ROUTER_BOLT.name(), ROUTER_TO_FLOW_CREATE_HUB.name(), FIELDS_KEY)
                .directGrouping(SpeakerWorkerBolt.ID, SPEAKER_WORKER_TO_HUB_CREATE.name())
                .directGrouping(CoordinatorBolt.ID);

        KafkaBolt nbKafkaBolt = buildKafkaBolt(getConfig().getKafkaNorthboundTopic());
        tb.setBolt(Component.NB_RESPONSE_SENDER.name(), nbKafkaBolt, parallelism)
                .shuffleGrouping(Component.FLOW_CREATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name());

        KafkaBolt flKafkaBolt = buildKafkaBoltWithAbstractMessageSupport(getConfig().getKafkaSpeakerFlowTopic());
        tb.setBolt(Component.SPEAKER_REQUEST_SENDER.name(), flKafkaBolt, parallelism)
                .shuffleGrouping(SpeakerWorkerBolt.ID, Stream.SPEAKER_WORKER_REQUEST_SENDER.name());

        HistoryBolt historyBolt = new HistoryBolt(persistenceManager);
        tb.setBolt(Component.HISTORY_BOLT.name(), historyBolt, parallelism)
                .shuffleGrouping(Component.FLOW_CREATE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name());

        return tb.createTopology();
    }

    public FlowHsTopology(LaunchEnvironment env) {
        super(env, FlowHsTopologyConfig.class);
    }

    public enum Component {
        FLOW_SPOUT("flow.spout"),
        SPEAKER_WORKER_SPOUT("fl.worker.spout"),

        ROUTER_BOLT("flow.router.bolt"),
        FLOW_CREATE_HUB("flow.create.hub.bolt"),

        NB_RESPONSE_SENDER("nb.kafka.bolt"),
        SPEAKER_REQUEST_SENDER("speaker.kafka.bolt"),

        HISTORY_BOLT("flow.history.bolt");

        private final String value;

        Component(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

    }

    public enum Stream {
        ROUTER_TO_FLOW_CREATE_HUB,
        HUB_TO_SPEAKER_WORKER,
        HUB_TO_HISTORY_BOLT,

        SPEAKER_WORKER_TO_HUB_CREATE,

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
