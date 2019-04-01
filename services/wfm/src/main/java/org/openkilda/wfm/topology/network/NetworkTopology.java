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

package org.openkilda.wfm.topology.network;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.RerouteEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.SpeakerEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.bfdport.BfdPortHandler;
import org.openkilda.wfm.topology.network.storm.bolt.decisionmaker.DecisionMakerHandler;
import org.openkilda.wfm.topology.network.storm.bolt.isl.IslHandler;
import org.openkilda.wfm.topology.network.storm.bolt.port.PortHandler;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRouter;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerWorker;
import org.openkilda.wfm.topology.network.storm.bolt.sw.SwitchHandler;
import org.openkilda.wfm.topology.network.storm.bolt.uniisl.UniIslHandler;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.WatcherHandler;
import org.openkilda.wfm.topology.network.storm.bolt.watchlist.WatchListHandler;
import org.openkilda.wfm.topology.network.storm.spout.NetworkHistory;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.util.concurrent.TimeUnit;

public class NetworkTopology extends AbstractTopology<NetworkTopologyConfig> {
    private final PersistenceManager persistenceManager;
    private final NetworkOptions options;

    public NetworkTopology(LaunchEnvironment env) {
        super(env, NetworkTopologyConfig.class);

        persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);
        options = new NetworkOptions(getConfig());
    }

    /**
     * Discovery topology factory.
     */
    @Override
    public StormTopology createTopology() {
        int scaleFactor = topologyConfig.getScaleFactor();

        TopologyBuilder topology = new TopologyBuilder();

        inputSpeaker(topology, scaleFactor);
        workerSpeaker(topology, scaleFactor);

        coordinator(topology);
        networkHistory(topology);

        speakerRouter(topology, scaleFactor);

        watchList(topology, scaleFactor);
        watcher(topology, scaleFactor);
        decisionMaker(topology, scaleFactor);

        switchHandler(topology, scaleFactor);
        portHandler(topology, scaleFactor);
        bfdPortHandler(topology, scaleFactor);
        uniIslHandler(topology, scaleFactor);
        islHandler(topology, scaleFactor);

        outputSpeaker(topology, scaleFactor);
        outputReroute(topology, scaleFactor);

        return topology.createTopology();
    }

    private void coordinator(TopologyBuilder topology) {
        topology.setSpout(CoordinatorSpout.ID, new CoordinatorSpout(), 1);

        Fields keyGrouping = new Fields(MessageTranslator.KEY_FIELD);
        topology.setBolt(CoordinatorBolt.ID, new CoordinatorBolt(), 1)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(SpeakerWorker.BOLT_ID, CoordinatorBolt.INCOME_STREAM, keyGrouping);
    }

    private void inputSpeaker(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                topologyConfig.getTopoDiscoTopic(), ComponentId.INPUT_SPEAKER.toString());
        topology.setSpout(ComponentId.INPUT_SPEAKER.toString(), spout, scaleFactor);
    }

    private void workerSpeaker(TopologyBuilder topology, int scaleFactor) {
        long speakerIoTimeout = TimeUnit.SECONDS.toMillis(topologyConfig.getSpeakerIoTimeoutSeconds());
        WorkerBolt.Config workerConfig = SpeakerWorker.Config.builder()
                .hubComponent(BfdPortHandler.BOLT_ID)
                .workerSpoutComponent(SpeakerRouter.BOLT_ID)
                .streamToHub(SpeakerWorker.STREAM_HUB_ID)
                .defaultTimeout((int) speakerIoTimeout)
                .build();
        SpeakerWorker speakerWorker = new SpeakerWorker(workerConfig);
        Fields keyGrouping = new Fields(MessageTranslator.KEY_FIELD);
        topology.setBolt(SpeakerWorker.BOLT_ID, speakerWorker, scaleFactor)
                .directGrouping(CoordinatorBolt.ID)
                .fieldsGrouping(workerConfig.getHubComponent(), BfdPortHandler.STREAM_SPEAKER_ID, keyGrouping)
                .fieldsGrouping(workerConfig.getWorkerSpoutComponent(), SpeakerRouter.STREAM_WORKER_ID, keyGrouping);
    }

    private void speakerRouter(TopologyBuilder topology, int scaleFactor) {
        Fields keyGrouping = new Fields(MessageTranslator.KEY_FIELD);
        SpeakerRouter bolt = new SpeakerRouter();
        topology.setBolt(SpeakerRouter.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(ComponentId.INPUT_SPEAKER.toString(), keyGrouping);
    }

    private void networkHistory(TopologyBuilder topology) {
        NetworkHistory spout = new NetworkHistory(persistenceManager);
        topology.setSpout(NetworkHistory.SPOUT_ID, spout, 1);
    }

    private void watchList(TopologyBuilder topology, int scaleFactor) {
        WatchListHandler bolt = new WatchListHandler(options);
        Fields portGrouping = new Fields(PortHandler.FIELD_ID_DATAPATH, PortHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(WatchListHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(PortHandler.BOLT_ID, PortHandler.STREAM_POLL_ID, portGrouping);
    }

    private void watcher(TopologyBuilder topology, int scaleFactor) {
        WatcherHandler bolt = new WatcherHandler(options);
        Fields watchListGrouping = new Fields(WatchListHandler.FIELD_ID_DATAPATH,
                                              WatchListHandler.FIELD_ID_PORT_NUMBER);
        Fields speakerGrouping = new Fields(SpeakerRouter.FIELD_ID_DATAPATH, SpeakerRouter.FIELD_ID_PORT_NUMBER);
        topology.setBolt(WatcherHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(WatchListHandler.BOLT_ID, watchListGrouping)
                .fieldsGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_WATCHER_ID, speakerGrouping);
    }

    private void decisionMaker(TopologyBuilder topology, int scaleFactor) {
        DecisionMakerHandler bolt = new DecisionMakerHandler(options);
        Fields watcherGrouping = new Fields(WatcherHandler.FIELD_ID_DATAPATH, WatcherHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(DecisionMakerHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(WatcherHandler.BOLT_ID, watcherGrouping);
    }

    private void switchHandler(TopologyBuilder topology, int scaleFactor) {
        SwitchHandler bolt = new SwitchHandler(options, persistenceManager);
        Fields grouping = new Fields(SpeakerRouter.FIELD_ID_DATAPATH);
        topology.setBolt(SwitchHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(NetworkHistory.SPOUT_ID, grouping)
                .fieldsGrouping(SpeakerRouter.BOLT_ID, grouping);
    }

    private void portHandler(TopologyBuilder topology, int scaleFactor) {
        PortHandler bolt = new PortHandler(options);
        Fields endpointGrouping = new Fields(SwitchHandler.FIELD_ID_DATAPATH, SwitchHandler.FIELD_ID_PORT_NUMBER);
        Fields decisionMakerGrouping = new Fields(DecisionMakerHandler.FIELD_ID_DATAPATH,
                                                  DecisionMakerHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(PortHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(SwitchHandler.BOLT_ID, SwitchHandler.STREAM_PORT_ID, endpointGrouping)
                .fieldsGrouping(DecisionMakerHandler.BOLT_ID, decisionMakerGrouping);
    }

    private void bfdPortHandler(TopologyBuilder topology, int scaleFactor) {
        BfdPortHandler bolt = new BfdPortHandler(persistenceManager);
        Fields switchGrouping = new Fields(SwitchHandler.FIELD_ID_DATAPATH);
        Fields islGrouping = new Fields(IslHandler.FIELD_ID_DATAPATH);
        topology.setBolt(BfdPortHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(SwitchHandler.BOLT_ID, SwitchHandler.STREAM_BFD_PORT_ID, switchGrouping)
                .fieldsGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_BFD_PORT_ID, islGrouping)
                .directGrouping(SpeakerWorker.BOLT_ID, SpeakerWorker.STREAM_HUB_ID)
                .allGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_BCAST_ID);
    }

    private void uniIslHandler(TopologyBuilder topology, int scaleFactor) {
        UniIslHandler bolt = new UniIslHandler();
        Fields portGrouping = new Fields(PortHandler.FIELD_ID_DATAPATH, PortHandler.FIELD_ID_PORT_NUMBER);
        Fields bfdPortGrouping = new Fields(BfdPortHandler.FIELD_ID_DATAPATH, BfdPortHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(UniIslHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(PortHandler.BOLT_ID, portGrouping)
                .fieldsGrouping(BfdPortHandler.BOLT_ID, BfdPortHandler.STREAM_UNIISL_ID, bfdPortGrouping);
    }

    private void islHandler(TopologyBuilder topology, int scaleFactor) {
        IslHandler bolt = new IslHandler(persistenceManager, options);
        Fields islGrouping = new Fields(UniIslHandler.FIELD_ID_ISL_SOURCE, UniIslHandler.FIELD_ID_ISL_DEST);
        topology.setBolt(IslHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(UniIslHandler.BOLT_ID, islGrouping)
                .fieldsGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_ISL_ID, islGrouping);
    }

    private void outputSpeaker(TopologyBuilder topology, int scaleFactor) {
        SpeakerEncoder bolt = new SpeakerEncoder();
        topology.setBolt(SpeakerEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(WatcherHandler.BOLT_ID, WatcherHandler.STREAM_SPEAKER_ID)
                .shuffleGrouping(SpeakerWorker.BOLT_ID);

        KafkaBolt output = buildKafkaBolt(topologyConfig.getKafkaSpeakerDiscoTopic());
        topology.setBolt(ComponentId.SPEAKER_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(SpeakerEncoder.BOLT_ID);
    }

    private void outputReroute(TopologyBuilder topology, int scaleFactor) {
        RerouteEncoder bolt = new RerouteEncoder();
        topology.setBolt(RerouteEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_REROUTE_ID);

        KafkaBolt output = buildKafkaBolt(topologyConfig.getKafkaTopoRerouteTopic());
        topology.setBolt(ComponentId.REROUTE_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(RerouteEncoder.BOLT_ID);
    }

    /**
     * Discovery topology uploader.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new NetworkTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
