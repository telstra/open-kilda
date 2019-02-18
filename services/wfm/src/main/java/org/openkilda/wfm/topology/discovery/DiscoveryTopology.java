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

package org.openkilda.wfm.topology.discovery;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.discovery.model.DiscoveryOptions;
import org.openkilda.wfm.topology.discovery.storm.ComponentId;
import org.openkilda.wfm.topology.discovery.storm.bolt.RerouteEncoder;
import org.openkilda.wfm.topology.discovery.storm.bolt.SpeakerEncoder;
import org.openkilda.wfm.topology.discovery.storm.bolt.SpeakerMonitor;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.BfdPortHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.bfdport.BfdSpeakerWorker;
import org.openkilda.wfm.topology.discovery.storm.bolt.decisionmaker.DecisionMakerHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.isl.IslHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.port.PortHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.sw.SwitchHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.uniisl.UniIslHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.watcher.WatcherHandler;
import org.openkilda.wfm.topology.discovery.storm.bolt.watchlist.WatchListHandler;
import org.openkilda.wfm.topology.discovery.storm.spout.NetworkHistory;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.util.concurrent.TimeUnit;

public class DiscoveryTopology extends AbstractTopology<DiscoveryTopologyConfig> {
    private final PersistenceManager persistenceManager;
    private final DiscoveryOptions options;

    public DiscoveryTopology(LaunchEnvironment env) {
        super(env, DiscoveryTopologyConfig.class);

        persistenceManager = PersistenceProvider.getInstance().createPersistenceManager(configurationProvider);
        options = new DiscoveryOptions(getConfig());
    }

    /**
     * Discovery topology factory.
     */
    @Override
    public StormTopology createTopology() {
        int scaleFactor = topologyConfig.getScaleFactor();

        TopologyBuilder topology = new TopologyBuilder();

        inputSpeaker(topology, scaleFactor);

        coordinator(topology);
        networkHistory(topology);

        speakerMonitor(topology, scaleFactor);

        watchList(topology, scaleFactor);
        watcher(topology, scaleFactor);
        decisionMaker(topology, scaleFactor);

        switchHandler(topology, scaleFactor);
        portHandler(topology, scaleFactor);
        bfdPortHandler(topology, scaleFactor);
        uniIslHandler(topology, scaleFactor);
        islHandler(topology, scaleFactor);

        speakerOutput(topology, scaleFactor);
        rerouteOutput(topology, scaleFactor);

        return topology.createTopology();
    }

    private void inputSpeaker(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                topologyConfig.getTopoDiscoTopic(), ComponentId.INPUT_SPEAKER.toString());
        topology.setSpout(ComponentId.INPUT_SPEAKER.toString(), spout, scaleFactor);
    }

    private void coordinator(TopologyBuilder topology) {
        topology.setSpout(CoordinatorSpout.ID, new CoordinatorSpout(), 1);
        topology.setBolt(CoordinatorBolt.ID, new CoordinatorBolt(), 1)
                .allGrouping(CoordinatorSpout.ID);
    }

    private void speakerMonitor(TopologyBuilder topology, int scaleFactor) {
        SpeakerMonitor bolt = new SpeakerMonitor();
        topology.setBolt(SpeakerMonitor.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(ComponentId.INPUT_SPEAKER.toString());
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
        Fields speakerGrouping = new Fields(SpeakerMonitor.FIELD_ID_DATAPATH, SpeakerMonitor.FIELD_ID_PORT_NUMBER);
        topology.setBolt(WatcherHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(WatchListHandler.BOLT_ID, watchListGrouping)
                .fieldsGrouping(SpeakerMonitor.BOLT_ID, SpeakerMonitor.STREAM_WATCHER_ID, speakerGrouping);
    }

    private void decisionMaker(TopologyBuilder topology, int scaleFactor) {
        DecisionMakerHandler bolt = new DecisionMakerHandler(options);
        Fields watcherGrouping = new Fields(WatcherHandler.FIELD_ID_DATAPATH, WatcherHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(DecisionMakerHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(WatcherHandler.BOLT_ID, watcherGrouping);
    }

    private void switchHandler(TopologyBuilder topology, int scaleFactor) {
        SwitchHandler bolt = new SwitchHandler(options, persistenceManager);
        Fields grouping = new Fields(SpeakerMonitor.FIELD_ID_DATAPATH);
        topology.setBolt(SwitchHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(NetworkHistory.SPOUT_ID, grouping)
                .fieldsGrouping(SpeakerMonitor.BOLT_ID, grouping);
    }

    private void portHandler(TopologyBuilder topology, int scaleFactor) {
        PortHandler bolt = new PortHandler();
        Fields endpointGrouping = new Fields(SwitchHandler.FIELD_ID_DATAPATH, SwitchHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(PortHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(SwitchHandler.BOLT_ID, SwitchHandler.STREAM_PORT_ID, endpointGrouping);
    }

    private void bfdPortHandler(TopologyBuilder topology, int scaleFactor) {
        BfdPortHandler bolt = new BfdPortHandler(persistenceManager);
        Fields switchGrouping = new Fields(SwitchHandler.FIELD_ID_DATAPATH);
        Fields islGrouping = new Fields(IslHandler.FIELD_ID_DATAPATH);
        topology.setBolt(BfdPortHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(SwitchHandler.BOLT_ID, SwitchHandler.STREAM_BFD_PORT_ID, switchGrouping)
                .fieldsGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_BFD_PORT_ID, islGrouping);

        // speaker worker
        long speakerIoTimeout = TimeUnit.SECONDS.toMillis(topologyConfig.getSpeakerIoTimeoutSeconds());
        final WorkerBolt.Config workerConfig = BfdSpeakerWorker.Config.builder()
                .hubComponent(BfdPortHandler.BOLT_ID)
                .workerSpoutComponent(ComponentId.INPUT_SPEAKER.toString())
                .streamToHub(BfdSpeakerWorker.STREAM_HUB_ID)
                .defaultTimeout((int) speakerIoTimeout)
                .build();
        BfdSpeakerWorker speakerWorker = new BfdSpeakerWorker(workerConfig);
        Fields keyGrouping = new Fields(MessageTranslator.KEY_FIELD);
        topology.setBolt(BfdSpeakerWorker.BOLD_ID, speakerWorker, scaleFactor)
                .fieldsGrouping(workerConfig.getHubComponent(), BfdPortHandler.STREAM_SPEAKER_ID, keyGrouping)
                .fieldsGrouping(workerConfig.getWorkerSpoutComponent(), keyGrouping);
    }

    private void uniIslHandler(TopologyBuilder topology, int scaleFactor) {
        UniIslHandler bolt = new UniIslHandler();
        Fields portGrouping = new Fields(PortHandler.FIELD_ID_DATAPATH, PortHandler.FIELD_ID_PORT_NUMBER);
        Fields decisionMakerGrouping = new Fields(DecisionMakerHandler.FIELD_ID_DATAPATH,
                                                  DecisionMakerHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(UniIslHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(PortHandler.BOLT_ID, portGrouping)
                .fieldsGrouping(DecisionMakerHandler.BOLT_ID, decisionMakerGrouping);
    }

    private void islHandler(TopologyBuilder topology, int scaleFactor) {
        IslHandler bolt = new IslHandler(persistenceManager);
        Fields islGrouping = new Fields(UniIslHandler.FIELD_ID_ISL_SOURCE, UniIslHandler.FIELD_ID_ISL_DEST);
        topology.setBolt(IslHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(UniIslHandler.BOLT_ID, islGrouping)
                .fieldsGrouping(SpeakerMonitor.BOLT_ID, SpeakerMonitor.STREAM_ISL_ID, islGrouping);
    }

    private void speakerOutput(TopologyBuilder topology, int scaleFactor) {
        SpeakerEncoder bolt = new SpeakerEncoder();
        topology.setBolt(SpeakerEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(WatcherHandler.BOLT_ID, WatcherHandler.STREAM_SPEAKER_ID);

        KafkaBolt output = buildKafkaBolt(topologyConfig.getKafkaSpeakerTopic());
        topology.setBolt(ComponentId.SPEAKER_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(SpeakerEncoder.BOLT_ID);
    }

    private void rerouteOutput(TopologyBuilder topology, int scaleFactor) {
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
            (new DiscoveryTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
